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

test("commit-msg: chấp nhận Conventional Commits; Refs thiếu chỉ cảnh báo", () => {
  const root = makeRepo();
  const ok = commit(root, "feat(child): add child profile endpoint");
  assert.ok(ok.ok, ok.out);
  assert.match(ok.out, /thiếu footer 'Refs/);
  assert.ok(commit(root, "fix: handle null campus\n\n- reason\n\nRefs: CN-12").ok);
});

test("commit-msg: chặn sai định dạng, tiếng Việt, quá dài, dấu chấm, Refs sai, AI attribution", () => {
  const root = makeRepo();
  const cases = [
    ["update code", /dòng đầu phải dạng/],
    ["feat: thêm chức năng", /ASCII/],
    [`feat: ${"a".repeat(70)}`, /tối đa 72/],
    ["feat: add x.", /dấu chấm/],
    ["feat: add x\nbody liền dòng 2", /dòng 2 phải để trống/],
    ["feat: add x\n\nRefs: cn-1", /Refs: CN-123/],
    ["feat: add x\n\nRefs: CN-1\nRefs: CN-2", /chỉ 1 dòng/],
    ["feat: add x\n\nCo-Authored-By: Claude <noreply@anthropic.com>", /AI attribution/],
  ];
  for (const [msg, expected] of cases) {
    const r = commit(root, msg);
    assert.equal(r.ok, false, msg);
    assert.match(r.out, expected, msg);
  }
});

test("pre-commit: chặn .env, khóa, secret; cho .env.example và placeholder", () => {
  const root = makeRepo();
  assert.match(commit(root, "chore: env", { ".env": "A=1\n" }).out, /không commit file bí mật: .env/);
  discard(root, ".env");

  assert.ok(commit(root, "chore: env example", { ".env.example": "DB_PASSWORD=\n" }).ok);
  // carenest:allow-secret — mật khẩu giả để kiểm pre-commit chặn secret
  const leaked = commit(root, "feat: config", { "src/main/resources/application-x.yml": "db:\n  password: SuperSecret123\n" }); // carenest:allow-secret
  assert.equal(leaked.ok, false);
  assert.match(leaked.out, /có thể chứa secret: src\/main\/resources\/application-x.yml/);
  discard(root, "src/main/resources/application-x.yml");
  assert.ok(commit(root, "feat: config", { "src/main/resources/application-y.yml": "db:\n  password: ${DB_PASSWORD}\n" }).ok, "giá trị ${...} hợp lệ");
  const key = commit(root, "feat: key", { "certs/server.pem": "x\n" });
  assert.match(key.out, /file bí mật: certs\/server.pem/);
});

test("pre-commit: migration đã commit bất biến (trừ khi được cho phép), trùng version bị chặn", () => {
  const root = makeRepo();
  const edit = commit(root, "fix: tweak", { "src/main/resources/db/migration/V1__init.sql": "create table t(id bigint);\n" });
  assert.equal(edit.ok, false);
  assert.match(edit.out, /migration đã commit là bất biến/);
  const allowed = git(root, ["commit", "-q", "-m", "fix(db): tweak unreleased migration"], { CARENEST_ALLOW_MIGRATION_EDIT: "1" });
  assert.ok(allowed.ok, allowed.out);
  const dup = commit(root, "feat(db): add table", { "src/main/resources/db/migration/V1__other.sql": "select 1;\n" });
  assert.match(dup.out, /trùng version migration/);
  discard(root, "src/main/resources/db/migration/V1__other.sql");
  assert.ok(commit(root, "feat(db): add table", { "src/main/resources/db/migration/V2__add_table.sql": "select 1;\n" }).ok);
});

test("pre-commit: conflict marker bị chặn", () => {
  const root = makeRepo();
  const r = commit(root, "fix: merge", { "a.txt": "<<<<<<< HEAD\nx\n>>>>>>> other\n" });
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

  git(root, ["commit", "-q", "--amend", "-m", "feat: add a"]);
  assert.ok(git(root, ["push", "-q", "origin", "main"]).ok);
  git(root, ["commit", "-q", "--amend", "-m", "feat: add a again"]);
  const forced = git(root, ["push", "-q", "--force", "origin", "main"]);
  assert.equal(forced.ok, false);
  assert.match(forced.out, /force push/);
  assert.ok(git(root, ["push", "-q", "--force", "origin", "main"], { CARENEST_ALLOW_FORCE_PUSH: "1" }).ok);
});
