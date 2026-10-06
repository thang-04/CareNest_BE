import assert from "node:assert/strict";
import { test } from "node:test";
import { classify } from "../lib/commands.mjs";
import { normPath, toRel } from "../lib/hook-io.mjs";
import { laneOf } from "../lib/session.mjs";
import { verifyEvidence, responseText } from "../lib/verify-events.mjs";
import { DEFAULTS, deepMerge } from "../../../scripts/ai-layer/lib.mjs";

const WIN = process.platform === "win32";

test("classify: deny lách hook / đọc secret", () => {
  for (const cmd of [
    'git commit -nm "x"',
    "git commit --no-verify -m x",
    "git -c core.hooksPath=/dev/null commit -m x",
    "git push --no-verify",
    "cat .env",
    "type .env.local",
    "Get-Content .\\.env",
    "gc ./docker/.env",
    "less < .env",
    "cp id_rsa /tmp/x",
  ]) {
    assert.equal(classify(cmd).decision, "deny", cmd);
  }
});

test("classify: ask cho commit/push/tạo branch/lệnh phá hủy", () => {
  for (const cmd of [
    'git commit -m "feat: x"',
    'git commit -m "fix -n flag"',
    "git add -A && git commit -m x",
    "git -C ../CareNest_FE commit -m x",
    "FOO=1 git push",
    "git push --force origin main",
    "git checkout -b feature/x",
    "git switch -c chore/x",
    "git branch feature/x",
    "git branch -D old",
    "git worktree add ../x",
    "git reset --hard HEAD~1",
    "git rebase main",
    "git clean -fd",
    "git checkout -- .",
    "git restore src/A.java",
    "git stash drop",
    "git config core.hooksPath other",
    "gh pr create --fill",
  ]) {
    assert.equal(classify(cmd).decision, "ask", cmd);
  }
});

test("classify: lệnh bình thường không can thiệp", () => {
  for (const cmd of [
    "git status --short",
    "git branch",
    "git branch -a",
    "git branch --show-current",
    "git checkout main",
    "git restore --staged src/A.java",
    "git clean -n",
    "git log --oneline -n 5",
    "git diff HEAD",
    "cat .env.example",
    "docker compose --env-file .env up -d",
    'rg "process.env" src',
    'echo "git push"',
    "node scripts/verify.mjs",
  ]) {
    assert.equal(classify(cmd).decision, null, cmd);
  }
});

test("normPath/toRel: path Windows, Git Bash, tương đối, ngoài repo", () => {
  const root = WIN ? "D:/repo/CareNest_BE" : "/repo/CareNest_BE";
  assert.equal(toRel(root, `${root}/src/A.java`), "src/A.java");
  assert.equal(toRel(root, "src/../pom.xml", root), "pom.xml");
  assert.equal(toRel(root, `${root}-other/x.md`), null, "repo khác có tiền tố giống không bị nhận nhầm");
  assert.equal(toRel(root, `${root}/../CareNest_FE/x.md`), null);
  if (WIN) {
    assert.equal(normPath("d:\\repo\\a.md"), "D:/repo/a.md");
    assert.equal(normPath("\\\\?\\D:\\repo\\a.md"), "D:/repo/a.md");
    assert.equal(normPath("/d/repo/a.md"), "D:/repo/a.md");
    assert.equal(toRel(root, "D:\\REPO\\CareNest_BE\\src\\A.java"), "src/A.java", "không phân biệt hoa thường trên Windows");
  }
});

test("laneOf: S/M/L theo số file source và vùng rủi ro", () => {
  const config = deepMerge(DEFAULTS, { lanes: { riskGlobs: ["src/main/resources/db/migration/**", "pom.xml"] } });
  assert.equal(laneOf(["src/A.java", "docs/x.md", "AGENTS.md"], config), "S");
  assert.equal(laneOf(["src/A.java", "src/B.java", "src/C.java"], config), "M");
  assert.equal(laneOf(["src/A.java", "pom.xml"], config), "L");
  assert.equal(laneOf(["src/main/resources/db/migration/V2__x.sql"], config), "L");
  assert.equal(laneOf(Array.from({ length: 9 }, (_, i) => `src/F${i}.java`), config), "L");
  assert.equal(laneOf([], config), "S");
});

test("verifyEvidence: dòng VERIFY của scripts/verify.mjs", () => {
  const ok = verifyEvidence({ command: "node scripts/verify.mjs", text: "VERIFY PASS full | tests 24 fail 0 err 0 skip 0 | archunit ok", failed: false });
  assert.deepEqual(ok, { status: "PASS", mode: "full", skip: 0, weak: false, note: null });
  const quick = verifyEvidence({ command: "node scripts/verify.mjs --quick", text: "VERIFY PASS quick | tests 3 fail 0 err 0 skip 1 |", failed: false });
  assert.equal(quick.mode, "quick");
  assert.equal(quick.skip, 1);
  const fail = verifyEvidence({ command: "node scripts/verify.mjs", text: "VERIFY FAIL full | tests 24 fail 1 err 0 skip 0", failed: true });
  assert.equal(fail.status, "FAIL");
  const bg = verifyEvidence({ command: "node scripts/verify.mjs", text: "", failed: false, background: true });
  assert.equal(bg.weak, true);
  assert.equal(verifyEvidence({ command: "node scripts/check-ai-layer.mjs", text: "", failed: false }), null);
});

test("verifyEvidence: gọi Maven trực tiếp", () => {
  const full = verifyEvidence({ command: "./mvnw -B verify", text: "[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0\n[INFO] BUILD SUCCESS", failed: false });
  assert.deepEqual([full.status, full.mode, full.weak], ["PASS", "full", false]);
  const one = verifyEvidence({ command: "mvnw.cmd test -Dtest=ChildServiceTest", text: "[INFO] BUILD SUCCESS", failed: false });
  assert.equal(one.mode, "quick");
  const skipped = verifyEvidence({ command: "./mvnw verify -DskipTests", text: "[INFO] BUILD SUCCESS", failed: false });
  assert.equal(skipped.weak, true);
  const piped = verifyEvidence({ command: "./mvnw verify | tail -3", text: "Tests run: 3", failed: false });
  assert.equal(piped.status, "UNKNOWN");
  const failed = verifyEvidence({ command: "./mvnw verify", text: "", failed: true });
  assert.equal(failed.status, "FAIL");
});

test("responseText: string, {stdout,stderr}, content block, error", () => {
  assert.equal(responseText({ tool_response: "a" }), "a");
  assert.equal(responseText({ tool_response: { stdout: "a", stderr: "b" } }), "a\nb");
  assert.equal(responseText({ tool_response: [{ type: "text", text: "a" }] }), "a");
  assert.equal(responseText({ error: "boom" }), "boom");
});
