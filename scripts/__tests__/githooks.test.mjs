// Chạy git hook thật trong repo tạm (core.hooksPath trỏ về .githooks của repo này).
import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { test } from "node:test";
import { fileURLToPath } from "node:url";

const HOOKS = join(dirname(fileURLToPath(import.meta.url)), "..", "..", ".githooks").replace(/\\/g, "/");

function git(root, args, env = {}) {
  const r = spawnSync("git", ["-c", "user.email=t@t", "-c", "user.name=t", ...args], {
    cwd: root,
    encoding: "utf8",
    env: { ...process.env, ...env },
  });
  return { ok: r.status === 0, out: `${r.stdout}${r.stderr}` };
}

function write(root, files) {
  for (const [rel, content] of Object.entries(files)) {
    mkdirSync(dirname(join(root, rel)), { recursive: true });
    writeFileSync(join(root, rel), content);
  }
}

// Repo có 1 commit gốc (tạo trước khi bật hook) + remote bare
function makeRepo() {
  const base = mkdtempSync(join(tmpdir(), "carenest-githooks-"));
  const root = join(base, "repo");
  const remote = join(base, "remote.git");
  mkdirSync(root);
  spawnSync("git", ["init", "-q", "--bare", "-b", "main", remote]);
  git(root, ["init", "-q", "-b", "main"]);
  write(root, { "README.md": "# r\n", "src/main/resources/db/migration/V1__init.sql": "create table t(id int);\n" });
  git(root, ["add", "-A"]);
  git(root, ["commit", "-q", "-m", "chore: init"]);
  git(root, ["remote", "add", "origin", remote]);
  git(root, ["push", "-q", "origin", "main"]);
  git(root, ["config", "core.hooksPath", HOOKS]);
  return root;
}

// Bỏ file của lần commit bị chặn để bước sau không add lại
const discard = (root, rel) => {
  git(root, ["reset", "-q"]);
  rmSync(join(root, rel), { force: true });
};

const commit = (root, message, files = { [`f${Math.random()}.txt`]: "x\n" }, env) => {
  write(root, files);
  git(root, ["add", "-A"]);
  return git(root, ["commit", "-q", "-m", message], env);
};

test("commit-msg: chấp nhận '[<mã-công-việc>] <mã-jira>: <mô tả>' cho cả 4 loại", () => {
  const root = makeRepo();
  for (const msg of [
    "[FE-FEAT-44] G94-181: complete half of the lesson plan UI",
    "[BE-FEAT-7] G94-120: add child profile endpoint",
    "[FE-FIX-12] G94-200: keep campus filter after reload",
    "[BE-FIX-03] G94-190: correct meal-count validation\n\n- Reject counts below zero",
    `[FE-FEAT-44] G94-181: ${"a".repeat(50)}`,
  ]) {
    const r = commit(root, msg);
    assert.ok(r.ok, `${msg}\n${r.out}`);
  }
});

test("commit-msg: chặn sai định dạng, tiếng Việt, quá dài, dấu chấm, AI attribution", () => {
  const root = makeRepo();
  const cases = [
    ["update code", /dòng đầu phải dạng/],
    ["feat(child): add child profile endpoint", /dòng đầu phải dạng/],
    ["[FE-FEAT-44] add lesson plan form", /dòng đầu phải dạng/],
    ["G94-181 [FE-FEAT-44]: add lesson plan form", /dòng đầu phải dạng/],
    ["[FE-FEAT-44] g94-181: add lesson plan form", /dòng đầu phải dạng/],
    ["[UI-FEAT-1] G94-181: add lesson plan form", /dòng đầu phải dạng/],
    ["[Module-01] G94-181: add lesson plan form", /dòng đầu phải dạng/],
    ["[FE-FEAT-44] G94-181:add lesson plan form", /dòng đầu phải dạng/],
    ["[FE-FEAT-44] G94-181: thêm chức năng", /ASCII/],
    [`[FE-FEAT-44] G94-181: ${"a".repeat(51)}`, /tối đa 72/],
    ["[FE-FEAT-44] G94-181: add x.", /dấu chấm/],
    ["[FE-FEAT-44] G94-181: add x\nbody liền dòng 2", /dòng 2 phải để trống/],
    ["[FE-FEAT-44] G94-181: add x\n\nCo-Authored-By: Claude <noreply@anthropic.com>", /AI attribution/],
  ];
  for (const [msg, expected] of cases) {
    const r = commit(root, msg);
    assert.equal(r.ok, false, msg);
    assert.match(r.out, expected, msg);
  }
});

test("commit-msg: nhánh sai định dạng, commit thẳng main/dev, mã lệch nhánh chỉ cảnh báo", () => {
  const root = makeRepo();
  const onMain = commit(root, "[FE-FEAT-44] G94-181: add lesson plan form");
  assert.ok(onMain.ok, onMain.out);
  assert.match(onMain.out, /commit thẳng lên 'main'/);

  git(root, ["checkout", "-q", "-b", "feature/G94-181-FE-FEAT-44-lesson-plan"]);
  const match = commit(root, "[FE-FEAT-44] G94-181: add lesson plan form");
  assert.ok(match.ok, match.out);
  assert.doesNotMatch(match.out, /cảnh báo/);
  const other = commit(root, "[FE-FEAT-45] G94-182: add lesson plan list");
  assert.ok(other.ok, other.out);
  assert.match(other.out, /mong đợi '\[FE-FEAT-44\] G94-181: \.\.\.'/);

  for (const branch of ["feat/lesson-plan", "fix/G94-181-FE-FEAT-44-lesson-plan", "feature/G94-181-FE-FEAT-44-Lesson_Plan"]) {
    git(root, ["checkout", "-q", "-b", branch]);
    const r = commit(root, "[FE-FEAT-44] G94-181: add lesson plan form");
    assert.ok(r.ok, r.out);
    assert.match(r.out, /tên nhánh phải dạng/, branch);
  }

  git(root, ["checkout", "-q", "-b", "release/1.0"]);
  const release = commit(root, "[BE-FIX-03] G94-190: correct meal-count validation");
  assert.ok(release.ok, release.out);
  assert.doesNotMatch(release.out, /cảnh báo/);
});

test("pre-commit: chặn .env, khóa, secret; cho .env.example và placeholder", () => {
  const root = makeRepo();
  assert.match(commit(root, "[BE-FEAT-1] G94-1: add env", { ".env": "A=1\n" }).out, /không commit file bí mật: .env/);
  discard(root, ".env");

  assert.ok(commit(root, "[BE-FEAT-1] G94-1: add env example", { ".env.example": "DB_PASSWORD=\n" }).ok);
  // carenest:allow-secret — mật khẩu giả để kiểm pre-commit chặn secret
  const leaked = commit(root, "[BE-FEAT-1] G94-1: add config", { "src/main/resources/application-x.yml": "db:\n  password: SuperSecret123\n" }); // carenest:allow-secret
  assert.equal(leaked.ok, false);
  assert.match(leaked.out, /có thể chứa secret: src\/main\/resources\/application-x.yml/);
  discard(root, "src/main/resources/application-x.yml");
  assert.ok(commit(root, "[BE-FEAT-1] G94-1: add config", { "src/main/resources/application-y.yml": "db:\n  password: ${DB_PASSWORD}\n" }).ok, "giá trị ${...} hợp lệ");
  const key = commit(root, "[BE-FEAT-1] G94-1: add key", { "certs/server.pem": "x\n" });
  assert.match(key.out, /file bí mật: certs\/server.pem/);
});

test("pre-commit: migration đã commit bất biến (trừ khi được cho phép), trùng version bị chặn", () => {
  const root = makeRepo();
  const edit = commit(root, "[BE-FIX-1] G94-2: tweak migration", { "src/main/resources/db/migration/V1__init.sql": "create table t(id bigint);\n" });
  assert.equal(edit.ok, false);
  assert.match(edit.out, /migration đã commit là bất biến/);
  const allowed = git(root, ["commit", "-q", "-m", "[BE-FIX-1] G94-2: tweak unreleased migration"], { CARENEST_ALLOW_MIGRATION_EDIT: "1" });
  assert.ok(allowed.ok, allowed.out);
  const dup = commit(root, "[BE-FEAT-2] G94-3: add table", { "src/main/resources/db/migration/V1__other.sql": "select 1;\n" });
  assert.match(dup.out, /trùng version migration/);
  discard(root, "src/main/resources/db/migration/V1__other.sql");
  assert.ok(commit(root, "[BE-FEAT-2] G94-3: add table", { "src/main/resources/db/migration/V2__add_table.sql": "select 1;\n" }).ok);
});

test("pre-commit: conflict marker bị chặn", () => {
  const root = makeRepo();
  const r = commit(root, "[BE-FIX-1] G94-2: resolve merge", { "a.txt": "<<<<<<< HEAD\nx\n>>>>>>> other\n" });
  assert.match(r.out, /conflict marker/);
});

test("pre-push: commit tạo bằng --no-verify vẫn bị bắt; force push bị chặn", () => {
  const root = makeRepo();
  write(root, { "a.txt": "a\n" });
  git(root, ["add", "-A"]);
  git(root, ["commit", "-q", "--no-verify", "-m", "sửa linh tinh"]);
  const bad = git(root, ["push", "origin", "main"]);
  assert.equal(bad.ok, false);
  assert.match(bad.out, /dòng đầu phải dạng/);

  git(root, ["commit", "-q", "--amend", "-m", "[BE-FEAT-1] G94-1: add a"]);
  assert.ok(git(root, ["push", "-q", "origin", "main"]).ok);
  git(root, ["commit", "-q", "--amend", "-m", "[BE-FEAT-1] G94-1: add a again"]);
  const forced = git(root, ["push", "-q", "--force", "origin", "main"]);
  assert.equal(forced.ok, false);
  assert.match(forced.out, /force push/);
  assert.ok(git(root, ["push", "-q", "--force", "origin", "main"], { CARENEST_ALLOW_FORCE_PUSH: "1" }).ok);
});
