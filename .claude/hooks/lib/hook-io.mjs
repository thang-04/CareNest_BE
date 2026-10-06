// I/O chung cho Claude hooks: đọc stdin, git, chuẩn hóa path (Windows), state theo session, fail-open.
import { execFileSync } from "node:child_process";
import { appendFileSync, existsSync, mkdirSync, readFileSync, readdirSync, statSync, unlinkSync, writeFileSync } from "node:fs";
import { homedir, tmpdir } from "node:os";
import { isAbsolute, join, posix } from "node:path";
import { loadConfig } from "../../../scripts/ai-layer/lib.mjs";

const WIN = process.platform === "win32";

export function readInput() {
  try {
    return JSON.parse(readFileSync(0, "utf8") || "{}");
  } catch {
    return {};
  }
}

export function git(args, cwd) {
  return execFileSync("git", args, { cwd, encoding: "utf8", stdio: ["ignore", "pipe", "ignore"], timeout: 10000 });
}

export function tryGit(args, cwd, fallback = "") {
  try {
    return git(args, cwd).trim();
  } catch {
    return fallback;
  }
}

// Path Windows/Git Bash ⇒ dạng `D:/a/b`: bỏ `\\?\`, `\`→`/`, `/d/x`→`D:/x`, chữ ổ đĩa viết hoa
export function normPath(p) {
  let out = String(p ?? "").replace(/^\\\\\?\\/, "").replace(/\\/g, "/");
  if (WIN) out = out.replace(/^\/([a-zA-Z])\//, "$1:/");
  out = out.replace(/^([a-z]):/, (_, d) => `${d.toUpperCase()}:`);
  return posix.normalize(out);
}

// Đường dẫn tương đối với gốc repo (posix); ngoài repo ⇒ null
export function toRel(root, file, cwd = root) {
  if (!file) return null;
  const base = normPath(root).replace(/\/$/, "");
  let abs = normPath(file);
  if (!isAbsolute(abs) && !/^[A-Z]:\//.test(abs)) abs = posix.normalize(`${normPath(cwd)}/${abs}`);
  const same = WIN ? abs.toLowerCase().startsWith(`${base.toLowerCase()}/`) : abs.startsWith(`${base}/`);
  return same ? abs.slice(base.length + 1) : null;
}

// Vùng ngoài repo agent được ghi tự do: scratchpad, temp, ~/.claude (plan, memory)
export function isFreeZone(file, input) {
  const abs = normPath(file).toLowerCase();
  const zones = [input.scratchpad_dir, tmpdir(), join(homedir(), ".claude")].filter(Boolean);
  return zones.some((z) => abs.startsWith(`${normPath(z).toLowerCase()}/`));
}

export function context(input) {
  const start = process.env.CLAUDE_PROJECT_DIR || input.cwd || process.cwd();
  const root = tryGit(["rev-parse", "--show-toplevel"], start) || normPath(start);
  const gitDir = tryGit(["rev-parse", "--absolute-git-dir"], root);
  const { config } = loadConfig(root);
  const stateDir = gitDir ? join(gitDir, "carenest-harness") : join(tmpdir(), "carenest-harness");
  mkdirSync(stateDir, { recursive: true });
  const sid = String(input.session_id || "unknown").replace(/[^\w-]/g, "_");
  return { root, gitDir, config, stateDir, sid, cwd: input.cwd || root };
}

// ---- Output (luôn exit 0, quyết định nằm trong JSON) ----
export const emit = (obj) => process.stdout.write(JSON.stringify(obj));

export function preToolUse(decision, reason, extraContext) {
  emit({
    hookSpecificOutput: {
      hookEventName: "PreToolUse",
      permissionDecision: decision,
      permissionDecisionReason: reason,
      ...(extraContext ? { additionalContext: extraContext } : {}),
    },
  });
}

export function addContext(event, text) {
  emit({ hookSpecificOutput: { hookEventName: event, additionalContext: text } });
}

// ---- Fail-open: hook lỗi không được chặn agent; ghi errors.log để orient báo ----
export function run(name, main) {
  process.exitCode = 0;
  if (process.env.CARENEST_HARNESS === "off") return;
  const input = readInput();
  let ctx = null;
  const fail = (err) => {
    try {
      const dir = ctx?.stateDir ?? join(tmpdir(), "carenest-harness");
      mkdirSync(dir, { recursive: true });
      appendFileSync(join(dir, "errors.log"), `${new Date().toISOString()} ${name}: ${err?.stack ?? err}\n`);
    } catch {
      // không làm gì: hook tuyệt đối không được làm hỏng phiên làm việc
    }
    process.exitCode = 0;
  };
  try {
    ctx = context(input);
    if (process.env.CARENEST_HARNESS_PROBE === "1") probe(ctx, name, input);
    const result = main(input, ctx);
    if (result?.then) result.catch(fail);
  } catch (err) {
    fail(err);
  }
}

// Ghi payload thô (cắt ngắn) để kiểm shape thật của Claude Code khi debug
function probe(ctx, name, input) {
  const shorten = (v) => (typeof v === "string" ? v.slice(0, 200) : v);
  const slim = JSON.parse(JSON.stringify(input, (_, v) => shorten(v)));
  appendFileSync(join(ctx.stateDir, "probe.jsonl"), `${JSON.stringify({ hook: name, input: slim })}\n`);
}

// ---- State file theo session ----
export function readJson(file, fallback) {
  try {
    return JSON.parse(readFileSync(file, "utf8"));
  } catch {
    return fallback;
  }
}

export const writeJson = (file, data) => writeFileSync(file, JSON.stringify(data));

// Dọn state cũ hơn 7 ngày
export function cleanOldState(stateDir) {
  const limit = Date.now() - 7 * 24 * 3600 * 1000;
  for (const name of existsSync(stateDir) ? readdirSync(stateDir) : []) {
    const file = join(stateDir, name);
    if (name !== "errors.log" && statSync(file).mtimeMs < limit) unlinkSync(file);
  }
}
