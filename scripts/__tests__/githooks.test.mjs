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

test("commit-msg: chấp nhận '<mã-jira>: <mô tả>'", () => {
  const root = makeRepo();
  for (const msg of [
    "G94-181: Implement lesson plan UI",
    "G94-182: Add lesson plan validation",
    "G94-190: Fix meal-count validation\n\n- Reject counts below zero",
    `G94-181: ${"a".repeat(63)}`,
  ]) {
    const r = commit(root, msg);
    assert.ok(r.ok, `${msg}\n${r.out}`);
  }
});

test("commit-msg: chặn sai định dạng, còn mã công việc, tiếng Việt, quá dài, dấu chấm, AI attribution", () => {
  const root = makeRepo();
  const cases = [
    ["update code", /dòng đầu phải dạng/],
    ["feat(child): add child profile endpoint", /dòng đầu phải dạng/],
    ["[FE-FEAT-44] G94-181: Add lesson plan form", /dòng đầu phải dạng/],
    ["FE-FEAT-44: Add lesson plan form", /dòng đầu phải dạng/],
    ["G94-181 Add lesson plan form", /dòng đầu phải dạng/],
    ["g94-181: Add lesson plan form", /dòng đầu phải dạng/],
    ["[Module-01] G94-181: Add lesson plan form", /dòng đầu phải dạng/],
    ["G94-181:Add lesson plan form", /dòng đầu phải dạng/],
    ["G94-181: Thêm chức năng", /ASCII/],
    [`G94-181: ${"a".repeat(64)}`, /tối đa 72/],
    ["G94-181: Add x.", /dấu chấm/],
    ["G94-181: Add x\nbody liền dòng 2", /dòng 2 phải để trống/],
    ["G94-181: Add x\n\nCo-Authored-By: Claude <noreply@anthropic.com>", /AI attribution/],
  ];
  for (const [msg, expected] of cases) {
    const r = commit(root, msg);
    assert.equal(r.ok, false, msg);
    assert.match(r.out, expected, msg);
  }
});

test("commit-msg: nhánh sai định dạng, commit thẳng main/dev chỉ cảnh báo; task khác cùng luồng không cảnh báo", () => {
  const root = makeRepo();
  const onMain = commit(root, "G94-181: Add lesson plan form");
  assert.ok(onMain.ok, onMain.out);
  assert.match(onMain.out, /commit thẳng lên 'main'/);

  for (const branch of ["feature/G94-181-FE-FEAT-44-lesson-plan", "fix/G94-190-BE-FIX-03-meal-count-validation", "fix/G94-195-FE-FEAT-44-lesson-plan-crash"]) {
    git(root, ["checkout", "-q", "-b", branch]);
    for (const msg of ["G94-181: Implement lesson plan UI", "G94-182: Add lesson plan validation"]) {
      const r = commit(root, msg);
      assert.ok(r.ok, r.out);
      assert.doesNotMatch(r.out, /cảnh báo/, `${branch} ${msg}`);
    }
  }

  for (const branch of ["feat/lesson-plan", "feature/G94-181-lesson-plan", "feature/G94-181-FE-FEAT-44-Lesson_Plan", "chore/G94-181-FE-FEAT-44-x"]) {
    git(root, ["checkout", "-q", "-b", branch]);
    const r = commit(root, "G94-181: Add lesson plan form");
    assert.ok(r.ok, r.out);
    assert.match(r.out, /tên nhánh phải dạng/, branch);
  }

  git(root, ["checkout", "-q", "-b", "release/1.0"]);
  const release = commit(root, "G94-190: Fix meal-count validation");
  assert.ok(release.ok, release.out);
  assert.doesNotMatch(release.out, /cảnh báo/);
});

test("pre-commit: chặn .env, khóa, secret; cho .env.example và placeholder", () => {
  const root = makeRepo();
  assert.match(commit(root, "G94-1: Add env", { ".env": "A=1\n" }).out, /không commit file bí mật: .env/);
  discard(root, ".env");

  assert.ok(commit(root, "G94-1: Add env example", { ".env.example": "DB_PASSWORD=\n" }).ok);
  // carenest:allow-secret — mật khẩu giả để kiểm pre-commit chặn secret
  const leaked = commit(root, "G94-1: Add config", { "src/main/resources/application-x.yml": "db:\n  password: SuperSecret123\n" }); // carenest:allow-secret
  assert.equal(leaked.ok, false);
  assert.match(leaked.out, /có thể chứa secret: src\/main\/resources\/application-x.yml/);
  discard(root, "src/main/resources/application-x.yml");
  assert.ok(commit(root, "G94-1: Add config", { "src/main/resources/application-y.yml": "db:\n  password: ${DB_PASSWORD}\n" }).ok, "giá trị ${...} hợp lệ");
  const key = commit(root, "G94-1: Add key", { "certs/server.pem": "x\n" });
  assert.match(key.out, /file bí mật: certs\/server.pem/);
});

test("pre-commit: migration đã commit bất biến (trừ khi được cho phép), trùng version bị chặn", () => {
  const root = makeRepo();
  const edit = commit(root, "G94-2: Tweak migration", { "src/main/resources/db/migration/V1__init.sql": "create table t(id bigint);\n" });
  assert.equal(edit.ok, false);
  assert.match(edit.out, /migration đã commit là bất biến/);
  const allowed = git(root, ["commit", "-q", "-m", "G94-2: Tweak unreleased migration"], { CARENEST_ALLOW_MIGRATION_EDIT: "1" });
  assert.ok(allowed.ok, allowed.out);
  const dup = commit(root, "G94-3: Add table", { "src/main/resources/db/migration/V1__other.sql": "select 1;\n" });
  assert.match(dup.out, /trùng version migration/);
  discard(root, "src/main/resources/db/migration/V1__other.sql");
  assert.ok(commit(root, "G94-3: Add table", { "src/main/resources/db/migration/V2__add_table.sql": "select 1;\n" }).ok);
});

test("pre-commit: conflict marker bị chặn", () => {
  const root = makeRepo();
  const r = commit(root, "G94-2: Resolve merge", { "a.txt": "<<<<<<< HEAD\nx\n>>>>>>> other\n" });
  assert.match(r.out, /conflict marker/);
});

test("pre-push: commit tạo bằng --no-verify vẫn bị bắt; force push bị chặn", () => {
  const root = makeRepo();
  const branch = "feature/G94-1-BE-FEAT-1-add-a";
  git(root, ["checkout", "-q", "-b", branch]);
  write(root, { "a.txt": "a\n" });
  git(root, ["add", "-A"]);
  git(root, ["commit", "-q", "--no-verify", "-m", "sửa linh tinh"]);
  const bad = git(root, ["push", "origin", branch]);
  assert.equal(bad.ok, false);
  assert.match(bad.out, /dòng đầu phải dạng/);

  git(root, ["commit", "-q", "--amend", "-m", "G94-1: Add a"]);
  assert.ok(git(root, ["push", "-q", "origin", branch]).ok);
  git(root, ["commit", "-q", "--amend", "-m", "G94-1: Add a again"]);
  const forced = git(root, ["push", "-q", "--force", "origin", branch]);
  assert.equal(forced.ok, false);
  assert.match(forced.out, /force push/);
  assert.ok(git(root, ["push", "-q", "--force", "origin", branch], { CARENEST_ALLOW_FORCE_PUSH: "1" }).ok);
});

test("pre-push: cấm push trực tiếp lên main (trừ khi được cho phép)", () => {
  const root = makeRepo();
  git(root, ["commit", "-q", "--allow-empty", "-m", "G94-1: Add a"]);
  const direct = git(root, ["push", "-q", "origin", "main"]);
  assert.equal(direct.ok, false);
  assert.match(direct.out, /cấm push trực tiếp lên main/);
  const viaRefspec = git(root, ["push", "-q", "origin", "HEAD:refs/heads/main"]);
  assert.equal(viaRefspec.ok, false);
  assert.ok(git(root, ["push", "-q", "origin", "main"], { CARENEST_ALLOW_MAIN_PUSH: "1" }).ok);
});
