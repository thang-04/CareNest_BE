import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { test } from "node:test";
import { mergeSharedBlock } from "../ai-layer/cross-repo.mjs";
import { syncShared } from "../ai-layer/sync-shared.mjs";
import { makeRepo, makeWorkspace } from "./helpers.mjs";

const block = { heading: "Quy tắc chung CareNest (bắt buộc)", exclude: ["Phạm vi"] };
const agents = (intro, git, scope) =>
  ["# Repo", "", "## Riêng", "giữ nguyên", "", "## Quy tắc chung CareNest (bắt buộc)", "", intro, "", "### Git", git, "", "### Phạm vi", scope, "", "## Sau", "cuối"].join(
    "\n",
  );

test("mergeSharedBlock: lấy mục chung theo nguồn, giữ mục riêng và phần ngoài khối của đích", () => {
  const merged = mergeSharedBlock(agents("mở đầu mới", "git mới", "phạm vi BE"), agents("mở đầu cũ", "git cũ", "phạm vi FE"), block);
  assert.equal(merged, agents("mở đầu mới", "git mới", "phạm vi FE"));
  assert.equal(mergeSharedBlock("# không có khối", agents("a", "b", "c"), block), null);
});

test("syncShared: chép file chung lệch/thiếu, không đụng file riêng, --check không ghi", () => {
  const workspace = makeWorkspace();
  const config = JSON.stringify({ checkAiLayer: { sharedFiles: [".editorconfig", "scripts/ai-layer/**"], agentsBlock: block } });
  const be = makeRepo(
    {
      ".claude/hooks/harness.config.json": config,
      ".editorconfig": "root = true\n",
      "scripts/ai-layer/lib.mjs": "export const v = 2;\n",
      "scripts/verify.mjs": "// BE\n",
      "AGENTS.md": agents("mở đầu", "git mới", "phạm vi BE"),
    },
    { workspace, name: "CareNest_BE" },
  );
  const fe = makeRepo(
    {
      ".editorconfig": "root = true\r\n",
      "scripts/ai-layer/lib.mjs": "export const v = 1;\n",
      "scripts/ai-layer/old.mjs": "// cũ\n",
      "scripts/verify.mjs": "// FE\n",
      "AGENTS.md": agents("mở đầu", "git cũ", "phạm vi FE"),
    },
    { workspace, name: "CareNest_FE" },
  );

  const dry = syncShared({ source: be, target: fe, write: false });
  assert.deepEqual(dry.map((c) => c.rel).sort(), ["AGENTS.md", "scripts/ai-layer/lib.mjs", "scripts/ai-layer/old.mjs"]);
  assert.equal(readFileSync(join(fe, "scripts/ai-layer/lib.mjs"), "utf8"), "export const v = 1;\n", "--check không ghi");

  syncShared({ source: be, target: fe });
  assert.equal(readFileSync(join(fe, "scripts/ai-layer/lib.mjs"), "utf8"), "export const v = 2;\n");
  assert.equal(readFileSync(join(fe, "scripts/verify.mjs"), "utf8"), "// FE\n", "file theo repo giữ nguyên");
  assert.equal(readFileSync(join(fe, "AGENTS.md"), "utf8"), agents("mở đầu", "git mới", "phạm vi FE"));
  assert.ok(existsSync(join(fe, "scripts/ai-layer/old.mjs")), "file chỉ có ở đích không tự xóa");
  // Chỉ còn file thừa ở đích; khác EOL không tính là lệch
  assert.deepEqual(
    syncShared({ source: be, target: fe, write: false }).map((c) => c.rel),
    ["scripts/ai-layer/old.mjs"],
  );
});
