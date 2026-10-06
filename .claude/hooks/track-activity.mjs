// PostToolUse + PostToolUseFailure: ghi file đã sửa và kết quả verify làm bằng chứng cho stop-gate.
// Chỉ in (1 dòng) khi bất thường: verify không tính là bằng chứng, hoặc có test bị skip.
import { addContext, run, toRel } from "./lib/hook-io.mjs";
import { appendEvent, sourceFingerprint } from "./lib/session.mjs";
import { responseText, verifyEvidence } from "./lib/verify-events.mjs";

const WRITE_TOOLS = new Set(["Edit", "Write", "MultiEdit", "NotebookEdit"]);
const SHELL_TOOLS = new Set(["Bash", "PowerShell"]);

run("track-activity", (input, ctx) => {
  const tool = input.tool_name;
  const toolInput = input.tool_input ?? {};
  const failed = input.hook_event_name === "PostToolUseFailure";

  if (WRITE_TOOLS.has(tool)) {
    const rel = toRel(ctx.root, toolInput.file_path ?? toolInput.notebook_path, ctx.cwd);
    if (rel && !failed) appendEvent(ctx, { kind: "edit", path: rel });
    return;
  }
  if (!SHELL_TOOLS.has(tool)) return;

  const evidence = verifyEvidence({
    command: String(toolInput.command ?? ""),
    text: responseText(input),
    failed,
    background: toolInput.run_in_background === true,
  });
  if (!evidence) return;
  appendEvent(ctx, { kind: "verify", ...evidence, fp: sourceFingerprint(ctx) });

  const notes = [];
  if (evidence.note) notes.push(`[CareNest] Lệnh verify này không tính là bằng chứng: ${evidence.note} — chạy lại \`${ctx.config.verify.display}\` foreground.`);
  if (evidence.skip > 0) notes.push(`[CareNest] ${evidence.skip} test bị skip (thường do Docker tắt) ⇒ báo rõ phần đó "chưa kiểm chứng".`);
  if (notes.length) addContext(input.hook_event_name || "PostToolUse", notes.join("\n"));
});
