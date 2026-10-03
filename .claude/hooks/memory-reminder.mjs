// Stop hook: nhắc agent cập nhật engineering memory khi có thay đổi code mà chưa đụng tới docs/.
// Chỉ nhắc một lần cho mỗi trạng thái diff để không lặp lại ở các lượt sau.
import { execFileSync } from "node:child_process";
import { createHash } from "node:crypto";
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";

const NON_CODE = /^(docs\/|\.ai\/|\.claude\/|\.agents\/|plans\/)|\.md$/;

function git(args, cwd) {
  return execFileSync("git", args, { cwd, encoding: "utf8", stdio: ["ignore", "pipe", "ignore"] });
}

function readInput() {
  try {
    return JSON.parse(readFileSync(0, "utf8") || "{}");
  } catch {
    return {};
  }
}

try {
  const input = readInput();
  // Claude đang tiếp tục do chính hook này chặn trước đó ⇒ không chặn lần nữa
  if (input.stop_hook_active) process.exit(0);

  const cwd = process.env.CLAUDE_PROJECT_DIR || input.cwd || process.cwd();
  const changed = git(["status", "--porcelain", "-uall"], cwd)
    .split("\n")
    .filter(Boolean)
    .map((line) => line.slice(3).replace(/^"|"$/g, "").split(" -> ").pop());

  const code = changed.filter((p) => !NON_CODE.test(p));
  const docsTouched = changed.some((p) => p.startsWith("docs/"));
  if (code.length === 0 || docsTouched) process.exit(0);

  const gitDir = git(["rev-parse", "--absolute-git-dir"], cwd).trim();
  const marker = join(gitDir, "carenest-memory-reminder");
  const state = createHash("sha1").update(code.join("\n")).update(git(["diff"], cwd)).digest("hex");
  if (existsSync(marker) && readFileSync(marker, "utf8") === state) process.exit(0);
  writeFileSync(marker, state);

  const reason = [
    "[CareNest memory check] Có thay đổi code nhưng chưa cập nhật docs/. Tự đánh giá nhanh:",
    "- Bug mới / không hiển nhiên / phải thử >1 cách ⇒ ghi theo .ai/workflows/update-knowledge.md (T2): KNOWN_ISSUES nếu chưa rõ root cause; incident + ISSUE_INDEX (kèm cách đã thử thất bại) nếu đã fix.",
    "- User đã đưa thông tin nghiệp vụ mới / chốt PENDING trong phiên này ⇒ update-knowledge.md (T1).",
    "- Edge case đáng nhớ ⇒ Known pitfalls / incident edge-case (T3).",
    "Nếu không cần: trả lời đúng 1 dòng 'Memory: không cần cập nhật — <lý do>' rồi dừng. Không tạo incident giả, không commit.",
  ].join("\n");
  process.stdout.write(JSON.stringify({ decision: "block", reason }));
} catch {
  // Không phải git repo hoặc git lỗi ⇒ không cản agent
  process.exit(0);
}
