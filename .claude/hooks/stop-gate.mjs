// Stop: không cho báo xong khi thiếu bằng chứng — verify theo làn, nhắc memory khi có dấu hiệu bug khó,
// kiểm AI-layer khi session sửa docs. Mỗi điều kiện chặn tối đa 1 lần cho mỗi trạng thái code (fingerprint).
import { createHash } from "node:crypto";
import { matchAny } from "../../scripts/ai-layer/lib.mjs";
import { emit, run } from "./lib/hook-io.mjs";
import { appendEvent, hasEvent, laneOf, readEvents, sessionDelta, sourceFingerprint, verifyActive } from "./lib/session.mjs";

const MAX_BLOCKS_PER_SESSION = 6; // van an toàn: hook lỗi logic không được giữ agent mãi
const DISCLOSED = /^\s*Verify:\s*(chưa|không|bỏ qua|skip)/im;

function verifyReason(ctx, events, delta, message) {
  const { config } = ctx;
  if (!verifyActive(ctx) || !delta.some((p) => matchAny(config.verify.globs, p))) return null;
  if (DISCLOSED.test(message) || message.trim().endsWith("?")) return null;

  const lane = laneOf(delta, config);
  const fp = sourceFingerprint(ctx);
  const verifies = events.filter((e) => e.kind === "verify");
  const fresh = verifies.filter((e) => e.fp === fp).at(-1);
  const modeOk = (m) => (lane === "S" ? ["quick", "quick-all", "full"] : ["full"]).includes(m);
  if (fresh && fresh.status === "PASS" && !fresh.weak && modeOk(fresh.mode) && (lane === "S" || !fresh.skip)) return null;

  let why = "chưa chạy verify trong phiên";
  if (fresh?.status === "FAIL") why = "lần verify cuối FAIL";
  else if (fresh && !modeOk(fresh.mode)) why = `làn ${lane} cần full, lần cuối chạy ${fresh.mode}`;
  else if (fresh?.skip) why = `lần cuối có ${fresh.skip} test bị skip`;
  else if (fresh?.weak) why = "lần cuối không tính là bằng chứng (skipTests/chạy nền/output bị cắt)";
  else if (verifies.length) why = "code đã đổi sau lần verify cuối";
  const cmd = lane === "S" ? `${config.verify.display} --quick` : config.verify.display;
  return {
    // Lý do nằm trong key: bằng chứng mới nhưng vẫn thiếu (vd. quick ⇒ full có skip) phải được nhắc lại
    key: `verify:${fp}:${lane}:${why}`,
    text: `[CareNest verify] Làn ${lane}: ${why}. Chạy \`${cmd}\` rồi báo dòng VERIFY; không chạy được ⇒ ghi 1 dòng "Verify: chưa chạy — <lý do>".`,
  };
}

// Dấu hiệu bug khó: verify FAIL rồi PASS, hoặc ≥2 lần FAIL ⇒ đáng ghi vào engineering memory
function memoryReason(ctx, events, delta, message) {
  const statuses = events.filter((e) => e.kind === "verify").map((e) => e.status);
  const fails = statuses.filter((s) => s === "FAIL").length;
  const struggled = fails >= 2 || (fails >= 1 && statuses.lastIndexOf("PASS") > statuses.indexOf("FAIL"));
  const codeChanged = delta.some((p) => !matchAny(ctx.config.nonCodeGlobs, p));
  if (!struggled || !codeChanged || delta.some((p) => matchAny(ctx.config.memoryGlobs, p))) return null;
  if (/^\s*Memory:/m.test(message)) return null;
  return {
    key: "memory",
    text: "[CareNest memory] Phiên này verify fail rồi mới pass — tự đánh giá theo `.ai/workflows/update-knowledge.md`: bug không hiển nhiên/thử >1 cách ⇒ T2 (incident + 1 dòng ISSUE_INDEX, ghi Attempts). Không cần ⇒ 1 dòng `Memory: không cần — <lý do>`.",
  };
}

async function aiLayerReason(ctx, delta) {
  if (!delta.some((p) => matchAny(ctx.config.aiLayerGlobs, p))) return null;
  const { runChecks } = await import("../../scripts/check-ai-layer.mjs");
  const result = runChecks({ root: ctx.root, only: ["E1", "E2", "E3", "E4", "E5", "E7"] });
  const errors = result.findings.filter((f) => f.level === "error");
  if (!errors.length) return null;
  const lines = errors.slice(0, 3).map((f) => `  - ${f.file}${f.line ? `:${f.line}` : ""} ${f.msg}`);
  const key = `ai:${createHash("sha1").update(lines.join("\n")).digest("hex").slice(0, 12)}`;
  return { key, text: [`[CareNest ai-layer] ${errors.length} lỗi; sửa rồi chạy \`node scripts/check-ai-layer.mjs\`:`, ...lines].join("\n") };
}

run("stop-gate", (input, ctx) => {
  const events = readEvents(ctx);
  if (events.filter((e) => e.kind === "block").length >= MAX_BLOCKS_PER_SESSION) return;
  const delta = sessionDelta(ctx, events);
  if (!delta.length) return;
  const message = String(input.last_assistant_message ?? "");

  return aiLayerReason(ctx, delta).then((ai) => {
    const reasons = [verifyReason(ctx, events, delta, message), memoryReason(ctx, events, delta, message), ai]
      .filter(Boolean)
      .filter((r) => !hasEvent(events, "block", r.key));
    if (!reasons.length) return;
    for (const r of reasons) appendEvent(ctx, { kind: "block", key: r.key });
    emit({ decision: "block", reason: reasons.map((r) => r.text).join("\n") });
  });
});
