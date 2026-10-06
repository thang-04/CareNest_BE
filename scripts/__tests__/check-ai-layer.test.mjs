import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { dirname, join } from "node:path";
import { test } from "node:test";
import { fileURLToPath } from "node:url";
import { runChecks } from "../check-ai-layer.mjs";
import { errorsOf, makeRepo, makeWorkspace, warnsOf } from "./helpers.mjs";

const SCRIPT = join(dirname(fileURLToPath(import.meta.url)), "..", "check-ai-layer.mjs");
const SKILL = "---\nname: fix-bug\ndescription: x\n---\nbody\n";
const run = (root, extra = {}) => runChecks({ root, ...extra });
const msgs = (list) => list.map((f) => f.msg).join("\n");

test("E1: mirror skills — thiếu bản, khác nội dung; CRLF không tính là khác", () => {
  const ok = makeRepo({ ".claude/skills/fix-bug/SKILL.md": SKILL, ".agents/skills/fix-bug/SKILL.md": SKILL.replace(/\n/g, "\r\n") });
  assert.equal(errorsOf(run(ok, { only: ["E1"] })).length, 0);

  const bad = makeRepo({
    ".claude/skills/fix-bug/SKILL.md": SKILL,
    ".claude/skills/review/SKILL.md": "---\nname: other\n---\n",
    ".agents/skills/fix-bug/SKILL.md": `${SKILL}khác\n`,
    ".agents/skills/extra/SKILL.md": SKILL,
  });
  const result = run(bad, { only: ["E1"] });
  assert.match(msgs(errorsOf(result)), /khác nội dung/);
  assert.match(msgs(errorsOf(result)), /thiếu bản mirror .agents\/skills\/review\/SKILL.md/);
  assert.match(msgs(errorsOf(result)), /không có bản gốc/);
  assert.match(msgs(warnsOf(result)), /name "other" khác tên thư mục/);
});

test("E2: đường dẫn trong repo phải tồn tại; placeholder/code fence/token mơ hồ bỏ qua; planned chỉ cảnh báo", () => {
  const root = makeRepo({
    ".ai/CONTEXT_MAP.yaml": "planned:\n  - scripts/verify.mjs\n",
    "docs/a.md": [
      "Xem `docs/b.md` và `docs/missing.md`, link [x](./nope.md), [ok](b.md), [web](https://x.y).",
      "Placeholder `docs/modules/<module>.md`, glob `docs/*.md`, package `service/meals`, `integration/`.",
      "Planned `scripts/verify.mjs`.",
      "```",
      "`docs/in-fence.md`",
      "```",
    ].join("\n"),
    "docs/b.md": "# b\n",
  });
  const result = run(root, { only: ["E2"] });
  const errors = msgs(errorsOf(result));
  assert.match(errors, /docs\/missing.md/);
  assert.match(errors, /link không tồn tại: .\/nope.md/);
  assert.doesNotMatch(errors, /module|service|integration|in-fence|https/);
  assert.equal(errorsOf(result).length, 2);
  assert.match(msgs(warnsOf(result)), /planned, chưa tồn tại: scripts\/verify.mjs/);
});

test("E2: tham chiếu BE: / ../CareNest_BE/ giải theo repo sibling, sibling vắng thì bỏ qua", () => {
  const ws = makeWorkspace();
  makeRepo({ "docs/modules/a.md": "# a\n" }, { workspace: ws, name: "CareNest_BE" });
  const fe = makeRepo(
    { "docs/x.md": "`BE:docs/modules/a.md` `BE:docs/modules/zz.md` `../CareNest_BE/docs/modules/a.md` `APP:docs/q.md`" },
    { workspace: ws, name: "CareNest_FE" },
  );
  const result = run(fe, { only: ["E2"], overrides: { repo: "FE" } });
  assert.deepEqual(errorsOf(result).map((f) => f.msg), ["đường dẫn không tồn tại: BE:docs/modules/zz.md"]);
});

const BE_MAP = [
  "keywords:",
  '  "điểm danh": [attendance]',
  '  "ma": [ghost]',
  "modules:",
  "  attendance: {card: docs/modules/attendance.md, feature: att}",
  "  nutrition: {card: docs/modules/nutrition.md}",
  "available:",
  "  routing: [.ai/CONTEXT_MAP.yaml, .ai/ROUTER.md]",
  "  skeleton_only:",
  "    - docs/system/DEPLOYMENT.md",
  "planned:",
  "  - docs/api/",
  "  - CONTRIBUTING.md",
].join("\n");

test("E3: module id, đường dẫn available/modules, skeleton, planned", () => {
  const root = makeRepo({
    ".ai/CONTEXT_MAP.yaml": BE_MAP,
    "docs/modules/attendance.md": "# a\n",
    "docs/system/DEPLOYMENT.md": "# D\n\nĐã có nội dung\n",
    "docs/system/OTHER.md": "# O\n\n> **Status: CHƯA CÓ NỘI DUNG — không dùng làm nguồn.**\n",
    "docs/api/openapi.yaml": "openapi: 3.1.0\n",
  });
  const result = run(root, { only: ["E3"] });
  const errors = msgs(errorsOf(result));
  assert.match(errors, /module không tồn tại: ghost/);
  assert.match(errors, /docs\/modules\/nutrition.md/);
  assert.match(errors, /\.ai\/ROUTER.md/);
  const warns = msgs(warnsOf(result));
  assert.match(warns, /skeleton_only nhưng file không có header.*DEPLOYMENT/);
  assert.match(warns, /OTHER.md.*|có header skeleton nhưng không nằm/);
  assert.match(warns, /planned nhưng đã tồn tại.*docs\/api\//);
  assert.doesNotMatch(warns, /CONTRIBUTING/);
});

test("E3: thư mục planned chỉ có .gitkeep vẫn là chưa có", () => {
  const root = makeRepo({ ".ai/CONTEXT_MAP.yaml": "planned:\n  - docs/api/\n", "docs/api/.gitkeep": "" });
  assert.equal(run(root, { only: ["E3"] }).findings.length, 0);
});

const INCIDENT = (id, status) =>
  `---\nid: ${id}\ntype: bug\nmodules: [attendance]\nstatus: ${status}\ndate: 2026-10-06\nkeywords: [x]\n---\n# ${id}\n`;
const INDEX = (rows) =>
  `# Issue Index\n\n| ID | Module | Triệu chứng | Root cause | Status |\n| --- | --- | --- | --- | --- |\n${rows.join("\n")}\n`;

test("E4: incident ↔ ISSUE_INDEX 1:1, status khớp, tên file, ID chuẩn", () => {
  const ok = makeRepo({
    "docs/knowledge/ISSUE_INDEX.md": INDEX(["| BUG-261006-a | att | x | y | open |"]),
    "docs/knowledge/incidents/BUG-261006-a.md": INCIDENT("BUG-261006-a", "open"),
    "docs/knowledge/incidents/_TEMPLATE.md": INCIDENT("BUG-YYMMDD-slug", "open"),
  });
  assert.equal(run(ok, { only: ["E4"] }).findings.length, 0);

  const bad = makeRepo({
    "docs/knowledge/ISSUE_INDEX.md": INDEX(["| BUG-261006-a | att | x | y | fixed |", "| BUG-261006-orphan | att | x | y | open |"]),
    "docs/knowledge/incidents/BUG-261006-a.md": INCIDENT("BUG-261006-a", "open"),
    "docs/knowledge/incidents/wrong-name.md": INCIDENT("ENV-001", "fixed"),
    "docs/knowledge/incidents/BUG-261006-c.md": "---\nid: BUG-261006-c\n---\n",
  });
  const result = run(bad, { only: ["E4"] });
  const errors = msgs(errorsOf(result));
  assert.match(errors, /status "fixed" khác incident BUG-261006-a/);
  assert.match(errors, /BUG-261006-orphan chưa có file/);
  assert.match(errors, /tên file phải là ENV-001.md/);
  assert.match(errors, /frontmatter thiếu: type, status, date, keywords/, "mặc định không bắt buộc modules (FE/APP dùng screens)");
  assert.match(errors, /chưa có dòng trong/);
  assert.match(msgs(warnsOf(result)), /ID không theo dạng chuẩn.*ENV-001/);
});

const PLAN = (fm, body = "") => `---\n${fm}\n---\n# P\n${body}`;

test("E5: plan approved còn câu hỏi mở ⇒ lỗi; draft được phép; done phải ở completed/", () => {
  const root = makeRepo({
    "docs/plans/active/a.md": PLAN("status: approved\nbranch: main", "### Câu hỏi mở\n- [ ] chưa trả lời\n"),
    "docs/plans/active/b.md": PLAN("status: draft", "### Câu hỏi mở\n- [ ] được phép khi draft\n"),
    "docs/plans/active/c.md": PLAN("status: in-progress", "## Progress log\n"),
    "docs/plans/active/d.md": PLAN("status: done\nbranch: main"),
    "docs/plans/active/legacy.md": "# Plan cũ không frontmatter\n",
    "docs/plans/completed/e.md": PLAN("status: draft"),
    "docs/plans/completed/f.md": PLAN("status: done"),
  });
  const result = run(root, { only: ["E5"] });
  const byFile = (name) => msgs(errorsOf(result).filter((f) => f.file.endsWith(name)));
  assert.match(byFile("a.md"), /còn 1 câu hỏi mở/);
  assert.equal(byFile("/b.md"), "");
  assert.match(byFile("c.md"), /phải có `branch`/);
  assert.match(byFile("d.md"), /chuyển sang completed/);
  assert.match(byFile("legacy.md"), /thiếu frontmatter/);
  assert.match(byFile("e.md"), /phải là done\/cancelled/);
  assert.equal(byFile("f.md"), "");
});

test("E6: khối quy tắc chung và file dùng chung so với sibling (chỉ cảnh báo, cần --cross-repo)", () => {
  const ws = makeWorkspace();
  const agents = (git, scope) =>
    `# X\n## Quy tắc chung CareNest (bắt buộc)\nmở\n### Git\n${git}\n### Phạm vi\n${scope}\n## Stack\n`;
  const shared = { checkAiLayer: { sharedFiles: [".editorconfig", "scripts/**"] } };
  const be = makeRepo({ "AGENTS.md": agents("a", "BE"), ".editorconfig": "root = true\n", "scripts/x.mjs": "1" }, { workspace: ws });
  makeRepo({ "AGENTS.md": agents("b", "FE"), ".editorconfig": "root = true\r\n" }, { workspace: ws, name: "CareNest_FE" });
  const withoutFlag = run(be, { overrides: shared }).findings.filter((f) => f.code === "E6");
  assert.equal(withoutFlag.length, 0, "không bật --cross-repo thì bỏ qua E6");
  const result = run(be, { only: ["E6"], crossRepo: true, overrides: shared });
  const warns = msgs(warnsOf(result));
  assert.match(warns, /mục "Git" của khối quy tắc chung khác nhau/);
  assert.doesNotMatch(warns, /Phạm vi/);
  const files = warnsOf(result).map((f) => `${f.file}: ${f.msg}`).join("\n");
  assert.match(files, /scripts\/x.mjs: .*chỉ có ở BE/);
  assert.doesNotMatch(files, /editorconfig/, "chỉ khác CRLF thì coi là giống");
  assert.equal(errorsOf(result).length, 0);
});

test("E7: rule phải có frontmatter paths", () => {
  const root = makeRepo({
    ".claude/rules/ok.md": '---\npaths:\n  - "src/**"\n---\n# ok\n',
    ".claude/rules/bad.md": "# không frontmatter\n",
    "src/a.java": "class A {}\n",
  });
  const result = run(root, { only: ["E7"] });
  assert.deepEqual(errorsOf(result).map((f) => f.file), [".claude/rules/bad.md"]);
});

test("CLI: --only sai ⇒ exit 2; --json in JSON hợp lệ", () => {
  const bad = spawnSync(process.execPath, [SCRIPT, "--only", "E9"], { encoding: "utf8" });
  assert.equal(bad.status, 2);
  const json = spawnSync(process.execPath, [SCRIPT, "--json", "--only", "E1"], { encoding: "utf8" });
  assert.ok([0, 1].includes(json.status));
  assert.ok(Array.isArray(JSON.parse(json.stdout).findings));
});
