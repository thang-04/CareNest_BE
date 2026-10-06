import assert from "node:assert/strict";
import { test } from "node:test";
import { parseIssueIndex, openQuestions } from "../ai-layer/knowledge-checks.mjs";
import { parseContextMap } from "../ai-layer/context-map.mjs";
import { sharedBlock } from "../ai-layer/cross-repo.mjs";
import {
  crossRepoRef,
  deepMerge,
  globToRegExp,
  isSkeleton,
  parseFrontmatter,
  stripYamlComment,
} from "../ai-layer/lib.mjs";

test("globToRegExp: **, *, ?, {a,b}", () => {
  assert.ok(globToRegExp("src/**").test("src/main/A.java"));
  assert.ok(globToRegExp("**/.env").test(".env"));
  assert.ok(globToRegExp("**/.env").test("docker/.env"));
  assert.ok(globToRegExp("src/main/java/com/carenest/controller/**").test("src/main/java/com/carenest/controller/x/A.java"));
  assert.ok(globToRegExp("*.{ts,tsx}").test("a.tsx"));
  assert.ok(!globToRegExp("*.{ts,tsx}").test("b/a.ts"));
  assert.ok(globToRegExp("V?__x.sql").test("V1__x.sql"));
  assert.ok(!globToRegExp(".claude/hooks/*.mjs").test(".claude/hooks/lib/a.mjs"));
});

test("parseFrontmatter: scalar, list inline, list block, comment, quote", () => {
  const fm = parseFrontmatter(
    '---\nid: BUG-1   # comment\nmodules: [a, b]\npaths:\n  - "src/**"\n  - docs/x.md\ntitle: "A # B"\n---\nbody',
  );
  assert.deepEqual(fm, { id: "BUG-1", modules: ["a", "b"], paths: ["src/**", "docs/x.md"], title: "A # B" });
  assert.equal(parseFrontmatter("# no frontmatter"), null);
  assert.equal(parseFrontmatter("---\nid: x\n"), null, "chưa đóng frontmatter");
  assert.deepEqual(parseFrontmatter("---\r\nstatus: draft\r\n---\r\n"), { status: "draft" });
});

test("stripYamlComment giữ # trong nháy và sau ký tự thường", () => {
  assert.equal(stripYamlComment('  a: [x, "docs/g.md#8 #11"]   # note'), '  a: [x, "docs/g.md#8 #11"]');
  assert.equal(stripYamlComment("a: b#c"), "a: b#c");
});

test("isSkeleton chỉ nhìn header đầu file", () => {
  assert.ok(isSkeleton("# X\n\n> **Status: CHƯA CÓ NỘI DUNG — không dùng làm nguồn.**"));
  assert.ok(!isSkeleton("# X\n\n1\n2\n3\n4\n5\nStatus: CHƯA CÓ NỘI DUNG"));
});

test("crossRepoRef", () => {
  assert.deepEqual(crossRepoRef("BE:docs/a.md"), { repo: "BE", rel: "docs/a.md" });
  assert.deepEqual(crossRepoRef("../CareNest_APP/docs/a.md"), { repo: "APP", rel: "docs/a.md" });
  assert.equal(crossRepoRef("docs/a.md"), null);
});

test("deepMerge ghép object lồng, thay array", () => {
  assert.deepEqual(deepMerge({ a: { b: 1, c: [1] } }, { a: { c: [2] } }), { a: { b: 1, c: [2] } });
});

test("parseIssueIndex: index 5 cột và 6 cột (có cột File)", () => {
  const five = "| ID | Module | Triệu chứng | Root cause | Status |\n| --- | --- | --- | --- | --- |\n| `BUG-261004-x` | att | a | b | open |";
  assert.deepEqual(parseIssueIndex(five), [{ id: "BUG-261004-x", status: "open", file: "", line: 3 }]);
  const six = "## Index\n\n| ID | Module | T | R | Status | File |\n| --- | --- | --- | --- | --- | --- |\n| ENV-001 | m | t | r | fixed | `incidents/ENV-001-a.md` |";
  assert.deepEqual(parseIssueIndex(six), [{ id: "ENV-001", status: "fixed", file: "incidents/ENV-001-a.md", line: 5 }]);
});

test("openQuestions đếm `- [ ]` trong mục Câu hỏi mở", () => {
  const plan = "## Làm rõ\n### Câu hỏi mở\n- [x] a\n- [ ] b\n- [ ] c\n## AC\n- [ ] không tính";
  assert.equal(openQuestions(plan), 2);
  assert.equal(openQuestions("## Không có mục"), 0);
});

test("parseContextMap: 3 layout BE/FE/APP", () => {
  const be = [
    "keywords:",
    '  "điểm danh|attendance": [attendance]   # ghi chú',
    "tech_keywords:",
    '  "apicode": [docs/contracts/E.md, "docs/guide.md#8 #11"]',
    "modules:",
    "  attendance: {card: docs/modules/attendance.md, feature: att, rules: [ATT], adr: [0005]}",
    "available:",
    "  build_and_run: [README.md, pom.xml, Dockerfile]",
    "  skeleton_only:   # x",
    "    - docs/system/DEPLOYMENT.md",
    "planned:",
    "  - docs/api/                       # chưa có",
  ].join("\n");
  const parsed = parseContextMap(be);
  assert.deepEqual([...parsed.moduleIds], ["attendance"]);
  const by = (section) => parsed.entries.filter((e) => e.section === section).map((e) => e.item);
  assert.deepEqual(by("keywords"), ["attendance"]);
  assert.deepEqual(by("tech_keywords"), ["docs/contracts/E.md", "docs/guide.md"]);
  assert.ok(by("modules").includes("docs/modules/attendance.md"));
  assert.deepEqual(by("available"), ["README.md", "pom.xml", "Dockerfile"]);
  assert.deepEqual(by("skeleton_only"), ["docs/system/DEPLOYMENT.md"]);
  assert.deepEqual(by("planned"), ["docs/api/"]);

  const fe = [
    "keywords:",
    '  "điểm danh":',
    "    {be_module_card: ../CareNest_BE/docs/modules/a.md, fe_docs: [docs/f.md, docs/r.md]}",
    "available:",
    "  skills: [.agents/skills/a/SKILL.md,",
    "           .claude/skills/a/SKILL.md]",
    "skeleton_only:",
    "  - docs/architecture/X.md   # dùng được",
  ].join("\n");
  const feParsed = parseContextMap(fe);
  const feBy = (section) => feParsed.entries.filter((e) => e.section === section).map((e) => e.item);
  assert.deepEqual(feBy("keywords"), ["../CareNest_BE/docs/modules/a.md", "docs/f.md", "docs/r.md"]);
  assert.deepEqual(feBy("available"), [".agents/skills/a/SKILL.md", ".claude/skills/a/SKILL.md"]);
  assert.deepEqual(feBy("skeleton_only"), ["docs/architecture/X.md"]);

  const app = '  "x":\n    {app_docs: [docs/a.md], be_module_card: [BE:docs/modules/a.md]}';
  assert.deepEqual(
    parseContextMap(`keywords:\n${app}`).entries.map((e) => e.item),
    ["docs/a.md", "BE:docs/modules/a.md"],
  );
});

test("sharedBlock bỏ mục được phép khác và chuẩn hóa khoảng trắng", () => {
  const text = "# A\n## Quy tắc chung\nmở đầu\n### Git\na  b\n### Phạm vi\nriêng\n## Khác\nx";
  const block = sharedBlock(text, { heading: "Quy tắc chung", exclude: ["Phạm vi"] });
  assert.deepEqual([...block.entries()], [["(mở đầu)", "mở đầu"], ["Git", "a b"]]);
});
