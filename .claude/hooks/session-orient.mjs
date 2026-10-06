// SessionStart: định hướng đầu phiên, chỉ in khi có điều đáng nói (0–3 dòng) để không tốn token.
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { ensureGitHooks } from "../../scripts/ai-layer/lib.mjs";
import { addContext, cleanOldState, run, tryGit } from "./lib/hook-io.mjs";
import { activePlans, ensureBaseline, readEvents } from "./lib/session.mjs";

const AI_LAYER = ["AGENTS.md", "CLAUDE.md", ".ai", ".claude", ".agents", "docs"];

function orientLines(ctx, source) {
  const lines = [];
  const branch = tryGit(["branch", "--show-current"], ctx.root);

  const plans = activePlans(ctx).filter((p) => p.branch === branch && ctx.config.plans.gateStatuses.includes(p.status));
  for (const p of plans.slice(0, 1)) {
    lines.push(`[CareNest] Plan đang làm trên ${branch}: ${p.file} [${p.status}] — log cuối: ${p.lastLog ?? "chưa có"}. Đọc Progress log trước khi làm tiếp.`);
  }

  // Không fetch: so với lần fetch gần nhất của origin/main
  const behind = Number(tryGit(["rev-list", "--count", "HEAD..origin/main", "--", ...AI_LAYER], ctx.root, "0")) || 0;
  if (behind > 0) {
    lines.push(`[CareNest] Nhánh ${branch || "?"} thiếu ${behind} commit AI-layer của origin/main — báo user cân nhắc merge trước khi sửa docs/quy trình.`);
  }

  // Tự bật git hook; core.hooksPath đang trỏ chỗ khác thì chỉ báo một lần mỗi clone
  const hooks = ensureGitHooks(ctx.root);
  const marker = join(ctx.stateDir, "hookspath-warned");
  if (hooks === "enabled") lines.push("[CareNest] Đã tự bật git hook (core.hooksPath=.githooks).");
  if (hooks === "other" && !existsSync(marker)) {
    lines.push("[CareNest] core.hooksPath đang trỏ chỗ khác — git hook CareNest chưa chạy; hỏi user có muốn đổi sang .githooks.");
    writeFileSync(marker, new Date().toISOString());
  }

  if (source === "compact") {
    const verifies = readEvents(ctx).filter((e) => e.kind === "verify");
    const last = verifies.at(-1);
    const verifyText = last ? `verify cuối ${last.status} ${last.mode}` : "chưa verify trong phiên";
    lines.push(`[CareNest] Sau compact: ${verifyText}. Bước đang chờ user duyệt ⇒ hỏi lại, không tự coi là đã duyệt.`);
  }

  const errorLog = join(ctx.stateDir, "errors.log");
  if (existsSync(errorLog)) {
    const count = readFileSync(errorLog, "utf8").split("\n").filter(Boolean).length;
    if (count) lines.push(`[CareNest] Hook có ${count} lỗi nội bộ (${errorLog}) — báo user nếu hook hành xử lạ.`);
  }
  return lines.slice(0, 3);
}

run("session-orient", (input, ctx) => {
  ensureBaseline(ctx);
  if (input.source === "startup") cleanOldState(ctx.stateDir);
  const lines = orientLines(ctx, input.source);
  if (lines.length) addContext("SessionStart", lines.join("\n"));
});
