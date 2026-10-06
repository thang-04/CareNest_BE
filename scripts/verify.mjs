#!/usr/bin/env node
// Cổng kiểm chứng (ADR-0012): chạy build/test, ghi log đầy đủ ra file, chỉ in tóm tắt ≤10 dòng để tiết kiệm token.
// Dùng: node scripts/verify.mjs            (full: mvnw verify = test + ArchUnit + snapshot OpenAPI + Spotless)
//       node scripts/verify.mjs --quick    (làn S: test liên quan file đổi + ArchUnit + Spotless)
// Luôn in 1 dòng `VERIFY PASS|FAIL|SKIP <mode> | ...` — hook đọc dòng này làm bằng chứng.
import { execFileSync, spawn } from "node:child_process";
import { createWriteStream, existsSync, mkdirSync } from "node:fs";
import { basename, dirname, join, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { ensureGitHooks } from "./ai-layer/lib.mjs";

const SUMMARY = /^\[(?:INFO|WARNING|ERROR)\]\s+Tests run:\s*(\d+),\s*Failures:\s*(\d+),\s*Errors:\s*(\d+),\s*Skipped:\s*(\d+)\s*$/;
const PER_CLASS = /Tests run:\s*(\d+),\s*Failures:\s*(\d+),\s*Errors:\s*(\d+),\s*Skipped:\s*(\d+),.*\bin\s+([\w.$]+)/;

// Tóm tắt output Maven: tổng test (dòng tổng của surefire/failsafe, không lấy dòng từng class), cổng, lỗi đầu tiên
export function parseMavenOutput(text) {
  const result = { tests: 0, failures: 0, errors: 0, skipped: 0, build: null, classes: {}, problems: [], spotless: "-", docker: true };
  const lines = text.split(/\r?\n/);
  const strip = (line) => line.replace(/^\[ERROR\]\s+/, "");
  const add = (problem) => (result.problems.length < 5 ? result.problems.push(problem.slice(0, 220)) - 1 : -1);
  let inResults = false; // đang trong khối "Failures:/Errors:" của surefire
  let pendingDetail = -1; // vị trí problem chờ ghép dòng chi tiết (vd. "... in (TmpService.java:7)")
  let goalError = null;
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    if (/^\[ERROR\] (Failures|Errors):\s*$/.test(line)) {
      inResults = true;
      continue;
    }
    if (inResults) {
      if (/^\[ERROR\]\s{2,}\S/.test(line)) {
        pendingDetail = add(strip(line).slice(0, 120));
        continue;
      }
      if (!line.startsWith("[") && line.trim()) {
        if (pendingDetail >= 0) result.problems[pendingDetail] += ` → ${line.trim()}`;
        pendingDetail = -1;
        continue;
      }
      inResults = false;
    }
    if (!goalError && /^\[ERROR\] Failed to execute goal/.test(line)) goalError = strip(line);
    const total = line.match(SUMMARY);
    if (total) {
      result.tests += +total[1];
      result.failures += +total[2];
      result.errors += +total[3];
      result.skipped += +total[4];
    }
    const perClass = line.match(PER_CLASS);
    if (perClass) result.classes[perClass[5].split(".").pop()] = { failed: +perClass[2] + +perClass[3], skipped: +perClass[4] };
    if (/BUILD SUCCESS/.test(line)) result.build = "SUCCESS";
    if (/BUILD FAILURE/.test(line)) result.build = "FAILURE";
    if (/--- spotless:[\w.]+:check/.test(line) && result.spotless === "-") result.spotless = "ok";
    if (/format violations/.test(line)) {
      result.spotless = "FAIL";
      // Spotless liệt kê file rồi diff; chỉ lấy tên file
      for (let j = i + 1; j < lines.length && /^\[ERROR\]\s{3,}\S/.test(lines[j]); j++) {
        if (/\.java$/.test(strip(lines[j]))) add(`format: ${strip(lines[j])} (sửa: mvnw spotless:apply)`);
      }
    }
    if (/Could not find a valid Docker environment/.test(line)) result.docker = false;
    // Lỗi compile: `/path/X.java:[r,c] msg`
    if (/^\[ERROR\]\s+\S+\.java:\[\d+,\d+\]/.test(line)) add(strip(line));
  }
  // Build fail mà không bắt được lỗi cụ thể ⇒ ít nhất báo goal nào fail
  if (result.build === "FAILURE" && !result.problems.length && goalError) add(goalError);
  return result;
}

const gate = (classes, name) => {
  const c = classes[name];
  if (!c) return "-";
  if (c.failed) return "FAIL";
  return c.skipped ? "SKIP" : "ok";
};

export function formatSummary(mode, parsed, exitCode, seconds, logFile) {
  const pass = exitCode === 0 && parsed.build === "SUCCESS";
  const head = [
    `VERIFY ${pass ? "PASS" : "FAIL"} ${mode}`,
    `tests ${parsed.tests} fail ${parsed.failures} err ${parsed.errors} skip ${parsed.skipped}`,
    `archunit ${gate(parsed.classes, "ArchitectureRulesTest")}`,
    `snapshot ${gate(parsed.classes, "OpenApiSnapshotTest")}`,
    `spotless ${parsed.spotless}`,
    `${seconds}s`,
    `log ${logFile}`,
  ].join(" | ");
  const notes = [];
  if (parsed.skipped) notes.push(`CẢNH BÁO: ${parsed.skipped} test bị skip${parsed.docker ? "" : " (không có Docker)"} ⇒ phần đó CHƯA kiểm chứng`);
  for (const p of parsed.problems) notes.push(`- ${p}`);
  if (!pass) notes.push(`Chi tiết: grep -n "ERROR" ${logFile}`);
  return [head, ...notes.slice(0, 8)].join("\n");
}

function gitLines(root, args) {
  try {
    return execFileSync("git", args, { cwd: root, encoding: "utf8", stdio: ["ignore", "pipe", "ignore"] })
      .split("\n")
      .map((l) => l.trim())
      .filter(Boolean);
  } catch {
    return [];
  }
}

// Làn S: chọn test theo file đổi (XxxTest/XxxTests/XxxIntegrationTest cùng tên class), luôn kèm ArchUnit
export function selectQuickTests(changed, testFiles) {
  const tests = new Set(["ArchitectureRulesTest"]);
  let mapped = 0;
  let needsFull = false;
  for (const file of changed) {
    if (file === "pom.xml" || file.startsWith("src/main/resources/")) needsFull = true;
    if (!file.endsWith(".java")) continue;
    const name = basename(file, ".java");
    if (file.startsWith("src/test/")) {
      if (/Tests?$/.test(name)) {
        tests.add(name);
        mapped++;
      }
      continue;
    }
    if (/\/(controller|dto)\/|OpenApiConfig/.test(file)) tests.add("OpenApiSnapshotTest");
    for (const t of testFiles) {
      const tName = basename(t, ".java");
      if (new RegExp(`^${name}(Integration)?Tests?$`).test(tName)) {
        tests.add(tName);
        mapped++;
      }
    }
  }
  const mainChanged = changed.some((f) => f.startsWith("src/main/java/"));
  return { tests: [...tests], needsFull, allTests: mainChanged && mapped === 0 };
}

function mavenCommand(root, quick) {
  // Đường dẫn tuyệt đối: cmd có thể không tìm file trong thư mục hiện tại (NoDefaultCurrentDirectoryInExePath)
  const exe = join(root, process.platform === "win32" ? "mvnw.cmd" : "mvnw");
  if (!quick) return { mode: "full", args: ["-B", "verify"], exe };
  const changed = [
    ...gitLines(root, ["diff", "--name-only", "HEAD"]),
    ...gitLines(root, ["ls-files", "--others", "--exclude-standard"]),
  ].filter((f) => f.startsWith("src/") || f === "pom.xml");
  const testFiles = gitLines(root, ["ls-files", "-co", "--exclude-standard", "src/test/java"]);
  const pick = selectQuickTests(changed, testFiles);
  if (pick.needsFull) return { mode: "full", note: "--quick chuyển sang full vì đổi pom.xml/resources", args: ["-B", "verify"], exe };
  if (pick.allTests) return { mode: "quick-all", note: "không map được test theo tên ⇒ chạy toàn bộ test", args: ["-B", "test", "spotless:check"], exe };
  return {
    mode: "quick",
    args: ["-B", "test", "spotless:check", `-Dtest=${pick.tests.join(",")}`, "-Dsurefire.failIfNoSpecifiedTests=false"],
    exe,
  };
}

function runToLog(root, exe, args, logFile) {
  mkdirSync(dirname(join(root, logFile)), { recursive: true });
  const log = createWriteStream(join(root, logFile));
  let text = "";
  return new Promise((done) => {
    // JVM ghi stdout UTF-8 khi bị pipe (mặc định theo code page Windows ⇒ lỗi font tiếng Việt)
    const utf8 = "-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8";
    const env = { ...process.env, MAVEN_OPTS: `${process.env.MAVEN_OPTS ?? ""} ${utf8}`.trim() };
    // .cmd cần shell trên Windows; args do script tự sinh (không lấy từ input) nên ghép chuỗi an toàn
    const child =
      process.platform === "win32"
        ? spawn(`"${exe}" ${args.join(" ")}`, { cwd: root, shell: true, env })
        : spawn(exe, args, { cwd: root, env });
    const collect = (chunk) => {
      log.write(chunk);
      text += chunk.toString();
    };
    child.stdout.on("data", collect);
    child.stderr.on("data", collect);
    child.on("error", (err) => {
      text += `\n[ERROR]   verify: không chạy được ${exe}: ${err.message}\n`;
      log.end(() => done({ code: 1, text }));
    });
    child.on("close", (code) => log.end(() => done({ code: code ?? 1, text })));
  });
}

async function main(argv) {
  const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
  // Người/agent không dùng Claude hook vẫn được bật git hook khi chạy verify
  if (ensureGitHooks(root) === "enabled") console.log("Ghi chú: đã tự bật git hook (core.hooksPath=.githooks)");
  if (existsSync(join(root, "pom.xml"))) {
    const { mode, note, args, exe } = mavenCommand(root, argv.includes("--quick"));
    const logFile = "target/verify.log";
    const started = Date.now();
    const { code, text } = await runToLog(root, exe, args, logFile);
    const seconds = Math.round((Date.now() - started) / 1000);
    console.log(formatSummary(mode, parseMavenOutput(text), code, seconds, logFile));
    if (note) console.log(`Ghi chú: ${note}`);
    return code === 0 ? 0 : 1;
  }
  if (existsSync(join(root, "package.json"))) {
    const exe = process.platform === "win32" ? "npm.cmd" : "npm"; // npm nằm trên PATH, không ở thư mục repo
    const logFile = "verify.log";
    const { code } = await runToLog(root, exe, ["run", "verify"], logFile);
    console.log(`VERIFY ${code === 0 ? "PASS" : "FAIL"} full | npm run verify | log ${logFile}`);
    return code === 0 ? 0 : 1;
  }
  console.log("VERIFY SKIP | chưa có code (không có pom.xml/package.json)");
  return 0;
}

if (import.meta.url === pathToFileURL(process.argv[1] ?? "").href) {
  main(process.argv.slice(2)).then(
    (code) => (process.exitCode = code),
    (err) => {
      console.error(`VERIFY FAIL | verify.mjs crash: ${err.stack ?? err}`);
      process.exitCode = 2;
    },
  );
}
