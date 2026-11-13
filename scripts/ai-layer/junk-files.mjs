#!/usr/bin/env node
// Cổng file rác: không để log, cache, file nén, thư mục tạm lọt vào commit/PR; nhắc bổ sung .gitignore.
// Dùng: node scripts/ai-layer/junk-files.mjs                  (toàn cây: file nhánh này thêm so với junk.baseRef)
//       node scripts/ai-layer/junk-files.mjs --staged         (pre-commit: chỉ file đang staged)
//       <danh sách sha qua stdin> | node scripts/ai-layer/junk-files.mjs --pushed <tip>   (pre-push)
// Exit: 0 ok (kể cả khi chỉ có cảnh báo) · 1 có lỗi · 2 lỗi dùng/crash.
import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { globToRegExp, loadConfig } from "./lib.mjs";

const MAX_WARNINGS = 10;

function git(root, args, input) {
  try {
    return execFileSync("git", args, {
      cwd: root,
      encoding: "utf8",
      input,
      stdio: [input === undefined ? "ignore" : "pipe", "pipe", "ignore"],
      maxBuffer: 64 * 1024 * 1024,
    });
  } catch {
    return null;
  }
}

const list = (root, args) => (git(root, args) ?? "").split("\0").filter(Boolean);

// Trong số `paths`, file nào khớp .gitignore (kể cả khi đang được track)
function ignoredAmong(root, paths) {
  if (!paths.length) return [];
  const out = git(root, ["check-ignore", "--no-index", "-z", "--stdin"], paths.join("\0"));
  return (out ?? "").split("\0").filter(Boolean);
}

// Kích thước blob theo tree-ish (":" = index, "<sha>:" = commit); không đọc được ⇒ 0
function blobSizes(root, prefix, paths) {
  const sizes = new Map();
  if (!paths.length) return sizes;
  const out = git(root, ["cat-file", "--batch-check=%(objectsize)"], paths.map((p) => `${prefix}${p}`).join("\n"));
  const lines = (out ?? "").split("\n");
  paths.forEach((p, i) => sizes.set(p, Number(lines[i]) || 0));
  return sizes;
}

const mb = (bytes) => `${(bytes / 1024 / 1024).toFixed(1)}MB`;

// Gom file untracked theo mục cấp cao nhất chưa có gì được track (vd. cả thư mục `ui-review/` thành 1 dòng)
function untrackedFindings(root, junk, isJunk, allowed) {
  const untracked = list(root, ["ls-files", "-o", "--exclude-standard", "-z"]).filter((p) => !allowed(p));
  if (!untracked.length) return [];
  const trackedTop = new Set(list(root, ["ls-files", "-z"]).map((p) => p.split("/")[0]));
  const seen = new Set();
  const findings = [];
  for (const path of untracked) {
    const top = path.split("/")[0];
    const newTop = !trackedTop.has(top);
    const glob = isJunk(path);
    if (!newTop && !glob) continue;
    const shown = newTop ? (path.includes("/") ? `${top}/` : top) : path;
    if (seen.has(shown)) continue;
    seen.add(shown);
    const why = glob ? `khớp mẫu rác ${glob}` : "mục mới ở gốc repo, chưa track và chưa ignore";
    findings.push({ level: "warn", path: shown, msg: `${why} — là source thì commit, không thì thêm vào .gitignore` });
  }
  return findings;
}

// mode: "tree" (mặc định) | "staged" | "pushed" (cần tip + shas)
export function findJunk({ root, mode = "tree", tip, shas = [] }) {
  const { junk } = loadConfig(root).config;
  const globs = junk.globs.map((g) => [g, globToRegExp(g)]);
  const allow = junk.allow.map(globToRegExp);
  const allowed = (path) => allow.some((re) => re.test(path));
  const isJunk = (path) => globs.find(([, re]) => re.test(path))?.[0] ?? null;
  const findings = [];
  const notes = [];

  let added = [];
  let sizes = new Map();
  let trackedIgnored = [];
  if (mode === "staged") {
    added = list(root, ["diff", "--cached", "--name-only", "--diff-filter=AR", "-z"]);
    sizes = blobSizes(root, ":", added);
    trackedIgnored = ignoredAmong(root, added);
  } else if (mode === "pushed") {
    const names = new Set();
    for (const sha of shas) {
      for (const p of list(root, ["diff-tree", "--no-commit-id", "--name-only", "-r", "--diff-filter=AR", "-z", sha])) names.add(p);
    }
    // Chỉ xét file còn tồn tại ở đỉnh nhánh sắp push (thêm rồi đã xóa ⇒ bỏ qua)
    const atTip = new Set(list(root, ["ls-tree", "-r", "--name-only", "-z", tip]));
    added = [...names].filter((p) => atTip.has(p));
    sizes = blobSizes(root, `${tip}:`, added);
    trackedIgnored = ignoredAmong(root, added);
  } else {
    trackedIgnored = list(root, ["ls-files", "-ci", "--exclude-standard", "-z"]);
    const base = (git(root, ["merge-base", "HEAD", junk.baseRef]) ?? "").trim();
    const staged = list(root, ["diff", "--cached", "--name-only", "--diff-filter=AR", "-z"]);
    if (base) added = [...new Set([...list(root, ["diff", "--name-only", "--diff-filter=AR", "-z", base, "HEAD"]), ...staged])];
    else {
      added = staged;
      notes.push(`không thấy ${junk.baseRef} — chỉ kiểm file đang staged`);
    }
    sizes = blobSizes(root, ":", added);
  }

  for (const path of trackedIgnored) {
    if (allowed(path)) continue;
    findings.push({ level: "error", path, msg: "khớp .gitignore nhưng đang được git track — gỡ: git rm --cached (hoặc sửa .gitignore nếu là source)" });
  }
  const flagged = new Set(trackedIgnored);
  for (const path of added) {
    if (flagged.has(path) || allowed(path)) continue;
    const glob = isJunk(path);
    const size = sizes.get(path) ?? 0;
    if (glob) findings.push({ level: "error", path, msg: `file rác/tạm (mẫu ${glob}) — bỏ khỏi commit; cần giữ thật thì thêm vào junk.allow` });
    else if (size > junk.maxBytes) {
      findings.push({ level: "error", path, msg: `file lớn ${mb(size)} > ${mb(junk.maxBytes)} — không commit file nặng; cần giữ thật thì thêm vào junk.allow` });
    }
  }
  if (mode !== "pushed") findings.push(...untrackedFindings(root, junk, isJunk, allowed));
  return { findings, notes, errors: findings.filter((f) => f.level === "error").length };
}

// In kết quả, gom thư mục có nhiều file lỗi để không tràn màn hình. Trả về số lỗi.
export function reportJunk(result, print = console.log) {
  const errors = result.findings.filter((f) => f.level === "error");
  const warns = result.findings.filter((f) => f.level === "warn");
  const byDir = new Map();
  for (const f of errors) {
    const key = `${f.path.includes("/") ? f.path.slice(0, f.path.lastIndexOf("/") + 1) : ""}|${f.msg}`;
    byDir.set(key, [...(byDir.get(key) ?? []), f]);
  }
  for (const group of byDir.values()) {
    if (group.length <= 3) for (const f of group) print(`[JUNK] ERROR ${f.path}  ${f.msg}`);
    else print(`[JUNK] ERROR ${group[0].path} (+${group.length - 1} file cùng thư mục)  ${group[0].msg}`);
  }
  for (const f of warns.slice(0, MAX_WARNINGS)) print(`[JUNK] WARN  ${f.path}  ${f.msg}`);
  if (warns.length > MAX_WARNINGS) print(`[JUNK] WARN  … và ${warns.length - MAX_WARNINGS} mục khác`);
  for (const note of result.notes) print(`[JUNK] ghi chú: ${note}`);
  if (errors.length || warns.length) print(`junk-files: ${errors.length} lỗi, ${warns.length} cảnh báo`);
  return errors.length;
}

function main(argv) {
  const root = resolve(dirname(fileURLToPath(import.meta.url)), "..", "..");
  if (git(root, ["rev-parse", "--git-dir"]) === null) return 0;
  // Hook ghi ra stderr để không lẫn vào stdout của git
  const print = argv.includes("--stderr") ? console.error : console.log;
  if (argv.includes("--staged")) return reportJunk(findJunk({ root, mode: "staged" }), print) ? 1 : 0;
  if (argv.includes("--pushed")) {
    const tip = argv[argv.indexOf("--pushed") + 1];
    if (!tip) {
      console.error("Dùng: junk-files.mjs --pushed <tip>  (danh sách sha qua stdin)");
      return 2;
    }
    const shas = readFileSync(0, "utf8").split(/\s+/).filter(Boolean);
    return reportJunk(findJunk({ root, mode: "pushed", tip, shas }), print) ? 1 : 0;
  }
  return reportJunk(findJunk({ root }), print) ? 1 : 0;
}

if (import.meta.url === pathToFileURL(process.argv[1] ?? "").href) {
  try {
    process.exitCode = main(process.argv.slice(2));
  } catch (err) {
    console.error(`junk-files crash: ${err.stack ?? err}`);
    process.exitCode = 2;
  }
}
