// Cổng file rác: chạy trên repo git tạm (có remote + origin/dev) để kiểm cả 3 chế độ và git hook thật.
import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { copyFileSync, mkdirSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { test } from "node:test";
import { fileURLToPath } from "node:url";
import { findJunk, reportJunk } from "../ai-layer/junk-files.mjs";

const REPO = join(dirname(fileURLToPath(import.meta.url)), "..", "..");
const HOOKS = join(REPO, ".githooks").replace(/\\/g, "/");

function git(root, args) {
  const r = spawnSync("git", ["-c", "user.email=t@t", "-c", "user.name=t", ...args], { cwd: root, encoding: "utf8" });
  return { ok: r.status === 0, out: `${r.stdout}${r.stderr}` };
}

function write(root, files) {
  for (const [rel, content] of Object.entries(files)) {
    mkdirSync(dirname(join(root, rel)), { recursive: true });
    writeFileSync(join(root, rel), content);
  }
}

// Repo có nhánh dev đã push (origin/dev) và đang đứng ở nhánh task tách từ dev
function makeRepo(files = {}) {
  const base = mkdtempSync(join(tmpdir(), "carenest-junk-"));
  const root = join(base, "repo");
  const remote = join(base, "remote.git");
  mkdirSync(root);
  spawnSync("git", ["init", "-q", "--bare", "-b", "dev", remote]);
  git(root, ["init", "-q", "-b", "dev"]);
  write(root, { "README.md": "# r\n", "src/App.java": "class App {}\n", ".gitignore": "*.log\nbuild/\n", ...files });
  git(root, ["add", "-A"]);
  git(root, ["commit", "-q", "-m", "G94-1: Init"]);
  git(root, ["remote", "add", "origin", remote]);
  git(root, ["push", "-q", "origin", "dev"]);
  git(root, ["checkout", "-q", "-b", "feature/G94-1-BE-FEAT-1-x"]);
  return root;
}

const stage = (root, files, force = false) => {
  write(root, files);
  git(root, ["add", ...(force ? ["-f"] : []), ...Object.keys(files)]);
};
const commit = (root, files, force) => {
  stage(root, files, force);
  git(root, ["commit", "-q", "--no-verify", "-m", "G94-1: Add files"]);
  return git(root, ["rev-parse", "HEAD"]).out.trim();
};
const errorPaths = (result) => result.findings.filter((f) => f.level === "error").map((f) => f.path).sort();
const warnPaths = (result) => result.findings.filter((f) => f.level === "warn").map((f) => f.path).sort();

test("staged: chặn mẫu rác, file bị ignore mà vẫn add, file quá lớn; file thường và junk.allow đi qua", () => {
  const root = makeRepo({ ".claude/hooks/harness.config.json": JSON.stringify({ junk: { maxBytes: 100, allow: ["docs/keep.zip"] } }) });
  stage(root, {
    "src/New.java": "class New {}\n",
    "tools/__pycache__/a.cpython-312.pyc": "x",
    "dump.zip": "x",
    "docs/keep.zip": "x",
    ".tmp_doc_figma/page.png": "x",
    "docs/big.png": "x".repeat(500),
  });
  stage(root, { "debug.log": "x\n" }, true);
  const result = findJunk({ root, mode: "staged" });
  assert.deepEqual(errorPaths(result), [".tmp_doc_figma/page.png", "debug.log", "docs/big.png", "dump.zip", "tools/__pycache__/a.cpython-312.pyc"]);
  assert.match(result.findings.find((f) => f.path === "debug.log").msg, /khớp \.gitignore/);
  assert.match(result.findings.find((f) => f.path === "docs/big.png").msg, /file lớn/);
});

test("tree: file track mà bị ignore luôn lỗi; mẫu rác chỉ xét file nhánh thêm so với origin/dev", () => {
  // old.bak có sẵn trên dev ⇒ không phải lỗi của nhánh này
  const root = makeRepo({ "legacy/old.bak": "x" });
  assert.equal(findJunk({ root }).errors, 0);

  commit(root, { "notes.patch": "x", "src/Ok.java": "class Ok {}\n" });
  commit(root, { "build/out.bin": "x" }, true);
  const result = findJunk({ root });
  assert.deepEqual(errorPaths(result), ["build/out.bin", "notes.patch"]);
  assert.equal(reportJunk(result, () => {}), 2);
});

test("tree: file untracked chưa ignore chỉ cảnh báo — mục mới ở gốc gom 1 dòng, file mới trong thư mục source không nhắc", () => {
  const root = makeRepo();
  write(root, {
    "ui-review/a.png": "x",
    "ui-review/b/c.png": "x",
    "skills-lock.json": "{}",
    "src/Draft.java": "class Draft {}\n",
    "src/dump.rar": "x",
    "debug.log": "ignored\n",
  });
  const result = findJunk({ root });
  assert.equal(result.errors, 0);
  assert.deepEqual(warnPaths(result), ["skills-lock.json", "src/dump.rar", "ui-review/"]);
  assert.match(result.findings.find((f) => f.path === "ui-review/").msg, /\.gitignore/);
});

test("tree: không có origin/dev ⇒ ghi chú, vẫn kiểm file staged", () => {
  const root = makeRepo();
  git(root, ["remote", "remove", "origin"]);
  stage(root, { "dump.zip": "x" });
  const result = findJunk({ root });
  assert.deepEqual(errorPaths(result), ["dump.zip"]);
  assert.match(result.notes.join(" "), /origin\/dev/);
});

test("pushed: xét file thêm trong các commit sắp push và còn tồn tại ở đỉnh nhánh", () => {
  const root = makeRepo();
  const first = commit(root, { "dump.zip": "x", "trace.patch": "x" });
  rmSync(join(root, "trace.patch"));
  git(root, ["add", "-A"]);
  git(root, ["commit", "-q", "--no-verify", "-m", "G94-1: Drop patch"]);
  const tip = git(root, ["rev-parse", "HEAD"]).out.trim();
  assert.deepEqual(errorPaths(findJunk({ root, mode: "pushed", tip, shas: [first, tip] })), ["dump.zip"]);
});

// Hook gọi script theo đường dẫn tương đối của repo đang commit ⇒ chép script vào repo tạm
function withHooks(root) {
  for (const rel of ["scripts/ai-layer/junk-files.mjs", "scripts/ai-layer/lib.mjs"]) {
    mkdirSync(dirname(join(root, rel)), { recursive: true });
    copyFileSync(join(REPO, rel), join(root, rel));
  }
  git(root, ["add", "-A"]);
  git(root, ["commit", "-q", "-m", "G94-1: Add harness scripts"]);
  git(root, ["config", "core.hooksPath", HOOKS]);
  return root;
}

test("pre-commit: chặn commit có file rác, nhắc mục lạ chưa ignore; commit sạch đi qua", () => {
  const root = withHooks(makeRepo());
  stage(root, { "dump.zip": "x" });
  write(root, { "scratch/notes.md": "x\n" });
  const blocked = git(root, ["commit", "-q", "-m", "G94-1: Add dump"]);
  assert.equal(blocked.ok, false);
  assert.match(blocked.out, /\[JUNK\] ERROR dump\.zip/);
  assert.match(blocked.out, /\[JUNK\] WARN {2}scratch\//);
  assert.match(blocked.out, /commit có file rác/);

  git(root, ["rm", "-q", "--cached", "dump.zip"]);
  stage(root, { "src/Clean.java": "class Clean {}\n" });
  const clean = git(root, ["commit", "-q", "-m", "G94-1: Add clean class"]);
  assert.ok(clean.ok, clean.out);
});

test("pre-push: file rác commit bằng --no-verify vẫn bị chặn khi push", () => {
  const root = withHooks(makeRepo());
  commit(root, { "dump.zip": "x" });
  const pushed = git(root, ["push", "origin", "feature/G94-1-BE-FEAT-1-x"]);
  assert.equal(pushed.ok, false);
  assert.match(pushed.out, /\[JUNK\] ERROR dump\.zip/);
  assert.match(pushed.out, /nhánh sắp push có file rác/);
});
