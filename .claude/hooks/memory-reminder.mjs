// Stop hook: nhắc agent cập nhật engineering memory khi có thay đổi code mà chưa đụng tới tri thức trong docs/.
// Nhắc tối đa một lần mỗi session để không tốn thêm lượt model sau mỗi lần sửa code.
import { execFileSync } from "node:child_process";
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";

const NON_CODE = /^(docs\/|\.ai\/|\.claude\/|\.agents\/|plans\/)|\.md$/;
// Chỉ các vùng này mới tính là "đã cập nhật tri thức"; sửa docs khác (vd. CURRENT_STATE) không tắt nhắc
const MEMORY = /^docs\/(knowledge|modules|business|features)\//;

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

// -z giữ nguyên path Unicode/có khoảng trắng; entry rename có thêm một path gốc ngay sau
function changedPaths(cwd) {
  const parts = git(["status", "--porcelain=v1", "-z", "-uall"], cwd).split("\0");
  const paths = [];
  for (let i = 0; i < parts.length; i++) {
    const entry = parts[i];
    if (entry.length < 4) continue;
    paths.push(entry.slice(3));
    if (entry[0] === "R" || entry[0] === "C") i++;
  }
  return paths;
}

try {
  const input = readInput();
  // Claude đang tiếp tục do chính hook này chặn trước đó ⇒ không chặn lần nữa
  if (input.stop_hook_active) process.exit(0);

  const cwd = process.env.CLAUDE_PROJECT_DIR || input.cwd || process.cwd();
  const changed = changedPaths(cwd);
  const hasCode = changed.some((p) => !NON_CODE.test(p));
  if (!hasCode || changed.some((p) => MEMORY.test(p))) process.exit(0);

  const marker = join(git(["rev-parse", "--absolute-git-dir"], cwd).trim(), "carenest-memory-reminder");
  const session = input.session_id || "unknown";
  if (existsSync(marker) && readFileSync(marker, "utf8") === session) process.exit(0);
  writeFileSync(marker, session);

  const reason = [
    "[CareNest memory check] Có thay đổi code nhưng chưa cập nhật tri thức (docs/knowledge|modules|business|features). Tự đánh giá nhanh theo .ai/workflows/update-knowledge.md:",
    "- Bug mới / không hiển nhiên / phải thử >1 cách ⇒ T2: incident (status open nếu chưa rõ root cause) + 1 dòng ISSUE_INDEX, kèm các cách đã thử.",
    "- Thông tin nghiệp vụ mới / chốt PENDING ⇒ T1. Edge case đáng nhớ ⇒ T3.",
    "Nếu không cần: trả lời đúng 1 dòng 'Memory: không cần cập nhật — <lý do>' rồi dừng. Không tạo incident giả, không commit.",
  ].join("\n");
  process.stdout.write(JSON.stringify({ decision: "block", reason }));
} catch {
  // Không phải git repo hoặc git lỗi ⇒ không cản agent
  process.exit(0);
}
