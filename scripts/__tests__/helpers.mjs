// Dựng repo tạm (không cần git) cho test check-ai-layer / hook
import { mkdirSync, mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";

export function makeWorkspace() {
  return mkdtempSync(join(tmpdir(), "carenest-harness-"));
}

export function writeFiles(root, files) {
  for (const [rel, content] of Object.entries(files)) {
    const abs = join(root, rel);
    mkdirSync(dirname(abs), { recursive: true });
    writeFileSync(abs, content);
  }
  return root;
}

// Repo trong workspace (vd. ws/CareNest_FE) để test tham chiếu sibling
export function makeRepo(files, { workspace = makeWorkspace(), name = "CareNest_BE" } = {}) {
  const root = join(workspace, name);
  mkdirSync(root, { recursive: true });
  return writeFiles(root, files);
}

export const errorsOf = (result, code) =>
  result.findings.filter((f) => f.level === "error" && (!code || f.code === code));
export const warnsOf = (result, code) =>
  result.findings.filter((f) => f.level === "warn" && (!code || f.code === code));
