// Chạy hook thật (spawn node) trên git repo tạm: payload giống Claude Code gửi qua stdin.
import assert from "node:assert/strict";
import { execFileSync, spawnSync } from "node:child_process";
import { mkdirSync, mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { test } from "node:test";
import { fileURLToPath } from "node:url";

const HOOKS = join(dirname(fileURLToPath(import.meta.url)), "..");

const CONFIG = {
  repo: "BE",
  lanes: { smallMaxFiles: 2, mediumMaxFiles: 8, riskGlobs: ["src/main/resources/db/migration/**", "pom.xml"] },
  verify: { activeWhen: ["pom.xml"], globs: ["src/**", "pom.xml"], display: "node scripts/verify.mjs" },
  guard: {
    immutableGlobs: ["src/main/resources/db/migration/**"],
    dependencyGlobs: ["pom.xml"],
    contractGlobs: ["src/main/java/utils/ApiCode.java"],
    highRisk: [
      { glob: "src/main/java/security/**", when: "any" },
      { glob: "src/main/resources/db/migration/**", when: "new" },
    ],
  },
};

function write(root, files) {
  for (const [rel, content] of Object.entries(files)) {
    mkdirSync(dirname(join(root, rel)), { recursive: true });
    writeFileSync(join(root, rel), content);
  }
}

const git = (root, ...args) =>
  execFileSync("git", ["-c", "user.email=t@t", "-c", "user.name=t", ...args], { cwd: root, encoding: "utf8", stdio: ["ignore", "pipe", "ignore"] });

function makeGitRepo(extra = {}) {
  const root = join(mkdtempSync(join(tmpdir(), "carenest-hooks-")), "CareNest_BE");
  mkdirSync(root, { recursive: true });
  write(root, {
    ".claude/hooks/harness.config.json": JSON.stringify(CONFIG),
    ".ai/CONTEXT_MAP.yaml": "available:\n  docs: [docs/a.md]\n",
    "docs/a.md": "# a\n",
    "pom.xml": "<project/>\n",
    "src/main/java/A.java": "class A {}\n",
    "src/main/resources/db/migration/V1__init.sql": "create table t(id int);\n",
    ...extra,
  });
  git(root, "init", "-q", "-b", "main");
  git(root, "add", "-A");
  git(root, "commit", "-q", "-m", "init");
  return root;
}

function hook(name, root, payload) {
  const r = spawnSync(process.execPath, [join(HOOKS, `${name}.mjs`)], {
    input: JSON.stringify({ cwd: root, ...payload }),
    encoding: "utf8",
    env: { ...process.env, CLAUDE_PROJECT_DIR: root, CARENEST_HARNESS: "" },
  });
  assert.equal(r.status, 0, `${name} exit ${r.status}: ${r.stderr}`);
  return r.stdout ? JSON.parse(r.stdout) : null;
}

const decisionOf = (out) => out?.hookSpecificOutput?.permissionDecision ?? null;

// Giả lập 1 lượt Claude sửa file: ghi file + PostToolUse Edit
function edit(root, sid, rel, content) {
  write(root, { [rel]: content });
  hook("track-activity", root, { session_id: sid, hook_event_name: "PostToolUse", tool_name: "Edit", tool_input: { file_path: join(root, rel) } });
}

const verify = (root, sid, line, failed = false) =>
  hook("track-activity", root, {
    session_id: sid,
    hook_event_name: failed ? "PostToolUseFailure" : "PostToolUse",
    tool_name: "Bash",
    tool_input: { command: line.includes("quick") ? "node scripts/verify.mjs --quick" : "node scripts/verify.mjs" },
    tool_response: { stdout: line, stderr: "" },
  });

const stop = (root, sid, message = "Đã xong.") => hook("stop-gate", root, { session_id: sid, last_assistant_message: message });

test("guard: migration đã commit ⇒ ask; migration mới không plan ⇒ ask; có plan approved cùng branch ⇒ im lặng", () => {
  const root = makeGitRepo();
  const write_ = (rel) => hook("guard-edits", root, { session_id: "g1", tool_name: "Write", tool_input: { file_path: join(root, rel) } });
  const committed = write_("src/main/resources/db/migration/V1__init.sql");
  assert.equal(decisionOf(committed), "ask");
  assert.match(committed.hookSpecificOutput.permissionDecisionReason, /bất biến/);
  assert.match(write_("src/main/resources/db/migration/V2__new.sql").hookSpecificOutput.permissionDecisionReason, /chưa có plan approved/);
  assert.match(write_("src/main/java/security/Policy.java").hookSpecificOutput.permissionDecisionReason, /vùng rủi ro cao/);
  assert.equal(decisionOf(write_("src/main/java/utils/ApiCode.java")), "ask");
  assert.equal(write_("src/main/java/B.java"), null);

  write(root, { "docs/plans/active/x.md": "---\nstatus: draft\nbranch: main\n---\n" });
  assert.equal(decisionOf(write_("src/main/resources/db/migration/V2__new.sql")), "ask", "plan draft chưa mở cổng");
  write(root, { "docs/plans/active/x.md": "---\nstatus: approved\nbranch: other\n---\n" });
  assert.equal(decisionOf(write_("src/main/resources/db/migration/V2__new.sql")), "ask", "plan của branch khác không tính");
  write(root, { "docs/plans/active/x.md": "---\nstatus: approved\nbranch: main\n---\n" });
  assert.equal(write_("src/main/resources/db/migration/V2__new.sql"), null);
  assert.equal(write_("src/main/java/security/Policy.java"), null);
});

test("guard: user đã duyệt (ask rồi edit thành công) ⇒ không hỏi lại cùng file trong session", () => {
  const root = makeGitRepo();
  const ask = () => hook("guard-edits", root, { session_id: "g2", tool_name: "Edit", tool_input: { file_path: join(root, "pom.xml") } });
  assert.equal(decisionOf(ask()), "ask");
  edit(root, "g2", "pom.xml", "<project><x/></project>\n");
  assert.equal(ask(), null);
  assert.equal(decisionOf(hook("guard-edits", root, { session_id: "khac", tool_name: "Edit", tool_input: { file_path: join(root, "pom.xml") } })), "ask");
});

test("orient: repo sạch im lặng; có plan cùng branch thì in 1 dòng; sau compact nhắc xác nhận lại", () => {
  const root = makeGitRepo();
  assert.equal(hook("session-orient", root, { session_id: "o1", source: "startup" }), null);
  write(root, { "docs/plans/active/p.md": "---\nstatus: in-progress\nbranch: main\n---\n## Progress log\n### 2026-10-06 — Phase 1 (chờ review)\n" });
  const out = hook("session-orient", root, { session_id: "o1", source: "resume" });
  assert.match(out.hookSpecificOutput.additionalContext, /Plan đang làm trên main: docs\/plans\/active\/p.md \[in-progress\] — log cuối: 2026-10-06 — Phase 1/);
  const compact = hook("session-orient", root, { session_id: "o1", source: "compact" });
  assert.match(compact.hookSpecificOutput.additionalContext, /Sau compact: chưa verify trong phiên/);
});

test("stop: làn S — chặn 1 lần khi chưa verify, nhận quick PASS, đổi code sau verify thì chặn lại", () => {
  const root = makeGitRepo({ "src/main/java/Pre.java": "class Pre {}\n" });
  write(root, { "src/main/java/Pre.java": "class Pre { int wip; }\n" }); // WIP có từ trước session
  hook("session-orient", root, { session_id: "s1", source: "startup" });
  assert.equal(stop(root, "s1"), null, "session chưa đổi gì ⇒ không chặn dù có WIP cũ");

  edit(root, "s1", "src/main/java/A.java", "class A { int x; }\n");
  const first = stop(root, "s1");
  assert.equal(first.decision, "block");
  assert.match(first.reason, /Làn S: chưa chạy verify trong phiên.*verify.mjs --quick/);
  assert.equal(stop(root, "s1"), null, "chỉ chặn 1 lần cho cùng trạng thái code");

  verify(root, "s1", "VERIFY PASS quick | tests 3 fail 0 err 0 skip 0 | archunit ok");
  assert.equal(stop(root, "s1"), null);

  edit(root, "s1", "src/main/java/A.java", "class A { int y; }\n");
  assert.match(stop(root, "s1").reason, /code đã đổi sau lần verify cuối/);
  assert.equal(stop(root, "s1", "Đã sửa.\nVerify: chưa chạy — Docker tắt"), null, "khai báo trung thực ⇒ không chặn");
});

test("stop: làn M cần full; quick hoặc có test skip không đủ", () => {
  const root = makeGitRepo();
  hook("session-orient", root, { session_id: "m1", source: "startup" });
  for (const f of ["A", "B", "C"]) edit(root, "m1", `src/main/java/${f}.java`, `class ${f} { int m; }\n`);
  verify(root, "m1", "VERIFY PASS quick | tests 3 fail 0 err 0 skip 0");
  assert.match(stop(root, "m1").reason, /Làn M: làn M cần full, lần cuối chạy quick/);
  verify(root, "m1", "VERIFY PASS full | tests 9 fail 0 err 0 skip 1");
  assert.match(stop(root, "m1").reason, /có 1 test bị skip/);
  verify(root, "m1", "VERIFY PASS full | tests 9 fail 0 err 0 skip 0");
  assert.equal(stop(root, "m1"), null);
});

test("stop: làn L (đổi pom.xml) cần full", () => {
  const root = makeGitRepo();
  hook("session-orient", root, { session_id: "l1", source: "startup" });
  edit(root, "l1", "pom.xml", "<project><dep/></project>\n");
  verify(root, "l1", "VERIFY PASS quick | tests 1 fail 0 err 0 skip 0");
  assert.match(stop(root, "l1").reason, /Làn L/);
});

test("stop: memory chỉ nhắc khi có dấu hiệu bug khó (FAIL rồi PASS), 1 lần; dòng Memory: tắt nhắc", () => {
  const root = makeGitRepo();
  hook("session-orient", root, { session_id: "k1", source: "startup" });
  edit(root, "k1", "src/main/java/A.java", "class A { int z; }\n");
  verify(root, "k1", "VERIFY FAIL quick | tests 3 fail 1 err 0 skip 0", true);
  verify(root, "k1", "VERIFY PASS quick | tests 3 fail 0 err 0 skip 0");
  const out = stop(root, "k1");
  assert.match(out.reason, /\[CareNest memory\]/);
  assert.doesNotMatch(out.reason, /\[CareNest verify\]/);
  assert.equal(stop(root, "k1"), null);

  const clean = makeGitRepo();
  hook("session-orient", clean, { session_id: "k2", source: "startup" });
  edit(clean, "k2", "src/main/java/A.java", "class A { int z; }\n");
  verify(clean, "k2", "VERIFY PASS quick | tests 3 fail 0 err 0 skip 0");
  assert.equal(stop(clean, "k2"), null, "verify pass ngay ⇒ không nhắc memory");

  const declared = makeGitRepo();
  hook("session-orient", declared, { session_id: "k3", source: "startup" });
  edit(declared, "k3", "src/main/java/A.java", "class A { int z; }\n");
  verify(declared, "k3", "VERIFY FAIL quick | tests 3 fail 1 err 0 skip 0", true);
  verify(declared, "k3", "VERIFY PASS quick | tests 3 fail 0 err 0 skip 0");
  assert.equal(stop(declared, "k3", "Xong.\nMemory: không cần — lỗi gõ nhầm"), null);
});

test("stop: sửa docs làm hỏng AI-layer ⇒ chặn kèm lỗi check-ai-layer", () => {
  const root = makeGitRepo();
  hook("session-orient", root, { session_id: "a1", source: "startup" });
  edit(root, "a1", "docs/b.md", "Xem `docs/khong-ton-tai.md`\n");
  const out = stop(root, "a1");
  assert.match(out.reason, /\[CareNest ai-layer\] 1 lỗi/);
  assert.match(out.reason, /docs\/b.md:1 đường dẫn không tồn tại: docs\/khong-ton-tai.md/);
  assert.doesNotMatch(out.reason, /\[CareNest verify\]/, "chỉ sửa docs ⇒ không đòi Maven verify");
});

test("CARENEST_HARNESS=off ⇒ mọi hook im lặng", () => {
  const root = makeGitRepo();
  const r = spawnSync(process.execPath, [join(HOOKS, "guard-edits.mjs")], {
    input: JSON.stringify({ cwd: root, tool_name: "Read", tool_input: { file_path: join(root, ".env") } }),
    encoding: "utf8",
    env: { ...process.env, CLAUDE_PROJECT_DIR: root, CARENEST_HARNESS: "off" },
  });
  assert.equal(r.status, 0);
  assert.equal(r.stdout, "");
});

test("orient: tự bật core.hooksPath khi repo có .githooks; không ghi đè cấu hình khác", () => {
  const root = makeGitRepo({ ".githooks/commit-msg": "#!/bin/sh\nexit 0\n" });
  const first = hook("session-orient", root, { session_id: "h1", source: "startup" });
  assert.match(first.hookSpecificOutput.additionalContext, /Đã tự bật git hook/);
  assert.equal(git(root, "config", "--get", "core.hooksPath").trim(), ".githooks");
  assert.equal(hook("session-orient", root, { session_id: "h2", source: "startup" }), null, "đã bật ⇒ im lặng");

  const other = makeGitRepo({ ".githooks/commit-msg": "#!/bin/sh\nexit 0\n" });
  git(other, "config", "core.hooksPath", ".husky");
  assert.match(hook("session-orient", other, { session_id: "h3", source: "startup" }).hookSpecificOutput.additionalContext, /đang trỏ chỗ khác/);
  assert.equal(git(other, "config", "--get", "core.hooksPath").trim(), ".husky");
});
