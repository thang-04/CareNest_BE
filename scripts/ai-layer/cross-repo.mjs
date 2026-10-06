// E6 (chỉ cảnh báo, bật bằng --cross-repo) — khối "Quy tắc chung" và file harness dùng chung phải giống nhau ở 3 repo
import { createHash } from "node:crypto";
import { existsSync } from "node:fs";
import { join } from "node:path";
import { listFiles, matchAny, readText, siblingRoot } from "./lib.mjs";

// Tách khối chung theo heading `### `, bỏ các mục được phép khác nhau giữa repo
export function sharedBlock(text, { heading, exclude }) {
  const lines = text.split("\n");
  const start = lines.findIndex((l) => l.replace(/^#+\s*/, "").trim() === heading);
  if (start < 0) return null;
  const sections = new Map();
  let name = "(mở đầu)";
  let body = [];
  const flush = () => sections.set(name, body.join("\n").replace(/\s+/g, " ").trim());
  for (let i = start + 1; i < lines.length && !/^## /.test(lines[i]); i++) {
    const sub = lines[i].match(/^### (.+)$/);
    if (sub) {
      flush();
      name = sub[1].trim();
      body = [];
    } else body.push(lines[i]);
  }
  flush();
  for (const key of exclude) sections.delete(key);
  return sections;
}

const hash = (text) => createHash("sha1").update(text).digest("hex");

export function checkCrossRepo(root, config, files, report) {
  const { agentsBlock, sharedFiles } = config.checkAiLayer;
  const mine = existsSync(join(root, "AGENTS.md")) ? sharedBlock(readText(root, "AGENTS.md"), agentsBlock) : null;
  const myShared = files.filter((f) => matchAny(sharedFiles, f));

  for (const repo of ["BE", "FE", "APP"]) {
    if (repo === config.repo) continue;
    const other = siblingRoot(root, config, repo);
    if (!other) continue;
    const label = `${repo} (${other})`;

    if (mine && existsSync(join(other, "AGENTS.md"))) {
      const theirs = sharedBlock(readText(other, "AGENTS.md"), agentsBlock);
      if (!theirs) report("warn", "AGENTS.md", 0, `${label}: không tìm thấy khối "${agentsBlock.heading}"`);
      else {
        for (const name of new Set([...mine.keys(), ...theirs.keys()])) {
          if (mine.get(name) !== theirs.get(name)) report("warn", "AGENTS.md", 0, `${label}: mục "${name}" của khối quy tắc chung khác nhau`);
        }
      }
    }

    const otherShared = new Set(listFiles(other).filter((f) => matchAny(sharedFiles, f)));
    for (const rel of new Set([...myShared, ...otherShared])) {
      const here = myShared.includes(rel);
      if (!here || !otherShared.has(rel)) {
        report("warn", rel, 0, `${label}: file dùng chung chỉ có ở ${here ? config.repo : repo}`);
      } else if (hash(readText(root, rel)) !== hash(readText(other, rel))) {
        report("warn", rel, 0, `${label}: file dùng chung khác nội dung`);
      }
    }
  }
}
