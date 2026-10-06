// Nhận diện lệnh verify trong Bash/PowerShell và rút kết quả làm bằng chứng cho stop-gate.
import { parseMavenOutput } from "../../../scripts/verify.mjs";

const VERIFY_SCRIPT = /\bscripts[\\/]verify\.mjs\b/;
const MAVEN = /(?:^|[\s;&|(])(?:\.[\\/])?(?:mvnw(?:\.cmd)?|mvn)\b[^|;&\n]*?\b(?:verify|test|install)\b/;
const WEAK = /-D(?:skipTests|skipITs|maven\.test\.skip|spotless\.check\.skip)\b/;
const VERIFY_LINE = /VERIFY (PASS|FAIL|SKIP) (\S+)(?: \| tests (\d+) fail (\d+) err (\d+) skip (\d+))?/;

// tool_response có thể là string, {stdout, stderr}, {output}, hoặc mảng content block — đọc phòng thủ
export function responseText(input) {
  const r = input.tool_response ?? input.error ?? "";
  if (typeof r === "string") return r;
  if (Array.isArray(r)) return r.map((b) => b?.text ?? "").join("\n");
  return [r.stdout, r.stderr, r.output, r.text, r.error].filter((x) => typeof x === "string").join("\n");
}

export const isVerifyCommand = (command) => VERIFY_SCRIPT.test(command) || MAVEN.test(command);

// ⇒ {status: PASS|FAIL|SKIP|UNKNOWN, mode: full|quick|quick-all, skip, weak, note} hoặc null nếu không phải lệnh verify
export function verifyEvidence({ command, text, failed, background }) {
  if (!isVerifyCommand(command)) return null;
  const weak = WEAK.test(command);
  if (background) return { status: "UNKNOWN", mode: "?", skip: 0, weak: true, note: "chạy nền nên không có output làm bằng chứng" };

  const line = text.match(VERIFY_LINE);
  if (line) {
    return {
      status: failed && line[1] === "PASS" ? "FAIL" : line[1],
      mode: line[2],
      skip: Number(line[6] ?? 0),
      weak,
      note: weak ? "có cờ bỏ test/format nên không tính là bằng chứng" : null,
    };
  }
  if (VERIFY_SCRIPT.test(command)) {
    return { status: failed ? "FAIL" : "UNKNOWN", mode: "?", skip: 0, weak: true, note: "không thấy dòng VERIFY (output bị cắt/pipe?)" };
  }

  // Gọi Maven trực tiếp: -Dtest ⇒ chỉ một phần test (tương đương quick); không có BUILD ⇒ dựa vào exit
  const parsed = parseMavenOutput(text);
  const mode = /-Dtest=/.test(command) || !/\bverify\b/.test(command) ? "quick" : "full";
  let status = failed ? "FAIL" : parsed.build === "FAILURE" ? "FAIL" : "PASS";
  // `mvnw verify | tail` ⇒ output bị cắt; `||` không phải pipe
  const piped = /(^|[^|])\|(?!\|)/.test(command.slice(command.search(MAVEN)));
  if (!failed && !parsed.build && piped) status = "UNKNOWN";
  return {
    status,
    mode,
    skip: parsed.skipped,
    weak: weak || status === "UNKNOWN",
    note: weak ? "có cờ bỏ test/format nên không tính là bằng chứng" : status === "UNKNOWN" ? "output bị pipe/cắt, không rõ kết quả" : null,
  };
}
