// PreToolUse: chặn đọc/ghi secret, hỏi user trước các thay đổi "Hỏi trước khi làm" (AGENTS.md) và vùng rủi ro chưa có plan.
// Không in gì = để luồng xin quyền bình thường của Claude Code quyết định (không bao giờ tự "allow").
import { existsSync, readFileSync } from "node:fs";
import { join, resolve } from "node:path";
import { matchAny } from "../../scripts/ai-layer/lib.mjs";
import { classify } from "./lib/commands.mjs";
import { isFreeZone, normPath, preToolUse, run, toRel, tryGit } from "./lib/hook-io.mjs";
import { appendEvent, approvedPlanFor, readEvents } from "./lib/session.mjs";

const WRITE_TOOLS = new Set(["Edit", "Write", "MultiEdit", "NotebookEdit"]);
const SHELL_TOOLS = new Set(["Bash", "PowerShell"]);
// Lệnh không đụng git/secret ⇒ bỏ qua sớm, không tốn thời gian parse
const SHELL_FAST_PATH = /\bgit\b|\bgh\b|\.env|\.pem|\.key|\.p12|\.pfx|\.jks|id_rsa|id_ed25519/;

const isSecret = (guard, rel) => matchAny(guard.denyRead, rel) && !matchAny(guard.denyReadExcept, rel);

// File message của `git commit -F <file>` (tương đối theo cwd của lệnh); không đọc được ⇒ null
function readMessageFile(ctx, file) {
  if (isSecret(ctx.config.guard, normPath(file).split("/").pop())) return null;
  try {
    return readFileSync(resolve(ctx.cwd, file), "utf8");
  } catch {
    return null;
  }
}
const existsInHead = (ctx, rel) => tryGit(["cat-file", "-e", `HEAD:${rel}`], ctx.root, null) !== null;

// User đã duyệt (rule, path) trong session này ⇒ có event edit sau event ask ⇒ không hỏi lại
function alreadyApproved(events, key, rel) {
  const askAt = events.findLast((e) => e.kind === "ask" && e.key === key)?.at;
  return askAt !== undefined && events.some((e) => e.kind === "edit" && e.path === rel && e.at >= askAt);
}

function editRules(ctx, rel, branch) {
  const { guard } = ctx.config;
  const asks = [];
  if (matchAny(guard.harnessGlobs, rel)) asks.push(["harness", "sửa file harness (hook/script/settings) — xác nhận đây là chủ đích của user"]);
  if (matchAny(guard.immutableGlobs, rel) && existsInHead(ctx, rel)) {
    asks.push(["migration", "migration đã commit là bất biến — tạo migration mới V{n+1}; chỉ sửa khi user đồng ý rõ"]);
  }
  if (matchAny(guard.dependencyGlobs, rel)) asks.push(["dependency", "đổi dependency/hạ tầng cần hỏi trước (AGENTS.md › Hỏi trước khi làm) — nêu lý do + version"]);
  if (matchAny(guard.contractGlobs, rel)) asks.push(["contract", "đổi API contract cần hỏi trước, kèm tác động FE/APP (.ai/workflows/update-api.md)"]);
  for (const risk of guard.highRisk) {
    if (!matchAny([risk.glob], rel)) continue;
    const isNew = !existsSync(join(ctx.root, rel)) && !existsInHead(ctx, rel);
    if ((risk.when === "new" && !isNew) || approvedPlanFor(ctx, branch)) continue;
    asks.push(["plan", `vùng rủi ro cao, chưa có plan approved/in-progress cho branch "${branch}" (.ai/workflows/plan-change.md)`]);
  }
  return asks;
}

run("guard-edits", (input, ctx) => {
  const tool = input.tool_name;
  const toolInput = input.tool_input ?? {};

  if (SHELL_TOOLS.has(tool)) {
    const command = String(toolInput.command ?? "");
    if (!SHELL_FAST_PATH.test(command)) return;
    const env = { branch: tryGit(["branch", "--show-current"], ctx.root), readFile: (p) => readMessageFile(ctx, p) };
    const { decision, reasons } = classify(command, env);
    if (decision) preToolUse(decision, `[CareNest] ${reasons.join("; ")}`);
    return;
  }

  const file = toolInput.file_path ?? toolInput.notebook_path;
  if (!file) return;
  const rel = toRel(ctx.root, file, ctx.cwd);
  // Ngoài repo (vd. .env của repo sibling) ⇒ xét theo tên file
  if (isSecret(ctx.config.guard, rel ?? normPath(file).split("/").pop())) {
    return preToolUse("deny", "[CareNest] Không đọc/ghi .env hay khóa bí mật — dùng .env.example; cần giá trị thì user tự kiểm tra.");
  }
  if (!WRITE_TOOLS.has(tool)) return;
  if (!rel) {
    if (isFreeZone(file, input)) return;
    return preToolUse("ask", "[CareNest] Ghi file ngoài repo này — sửa repo khác cần user cho phép rõ (AGENTS.md › Phạm vi).");
  }

  const branch = tryGit(["branch", "--show-current"], ctx.root);
  const events = readEvents(ctx);
  const pending = editRules(ctx, rel, branch).filter(([key]) => !alreadyApproved(events, `${key}:${rel}`, rel));
  if (!pending.length) return;
  for (const [key] of pending) appendEvent(ctx, { kind: "ask", key: `${key}:${rel}` });
  preToolUse("ask", `[CareNest] ${rel}: ${pending.map(([, why]) => why).join("; ")}`);
});
