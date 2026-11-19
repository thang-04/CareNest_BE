// E6 (chỉ cảnh báo, bật bằng --cross-repo) — khối "Quy tắc chung" và file harness dùng chung phải giống nhau ở 3 repo
import { createHash } from "node:crypto";
import { existsSync } from "node:fs";
import { join } from "node:path";
import { listFiles, matchAny, readText, siblingRoot } from "./lib.mjs";

export const INTRO = "(mở đầu)";

// Cắt khối chung thành: phần trước (gồm dòng heading), các mục `### ` giữ nguyên văn, phần sau (từ `## ` kế tiếp)
export function splitBlock(text, heading) {
  const lines = text.split("\n");
  const start = lines.findIndex((l) => l.replace(/^#+\s*/, "").trim() === heading);
  if (start < 0) return null;
  let end = start + 1;
  while (end < lines.length && !/^## /.test(lines[end])) end++;
  const sections = [{ name: INTRO, lines: [] }];
  for (const line of lines.slice(start + 1, end)) {
    const sub = line.match(/^### (.+)$/);
    if (sub) sections.push({ name: sub[1].trim(), lines: [line] });
    else sections.at(-1).lines.push(line);
  }
  return { before: lines.slice(0, start + 1), sections, after: lines.slice(end) };
}

// Nội dung từng mục của khối chung (đã gộp khoảng trắng), bỏ các mục được phép khác nhau giữa repo
export function sharedBlock(text, { heading, exclude }) {
  const block = splitBlock(text, heading);
  if (!block) return null;
  const sections = new Map();
  for (const { name, lines } of block.sections) {
    const body = name === INTRO ? lines : lines.slice(1);
    sections.set(name, body.join("\n").replace(/\s+/g, " ").trim());
  }
  for (const key of exclude) sections.delete(key);
  return sections;
}

// Khối chung của `target` lấy theo `source`; mục trong `exclude` giữ bản của target. Thiếu khối ⇒ null
export function mergeSharedBlock(sourceText, targetText, { heading, exclude }) {
  const source = splitBlock(sourceText, heading);
  const target = splitBlock(targetText, heading);
  if (!source || !target) return null;
  const own = new Map(target.sections.map((s) => [s.name, s.lines]));
  const merged = source.sections.flatMap((s) => (exclude.includes(s.name) && own.has(s.name) ? own.get(s.name) : s.lines));
  return [...target.before, ...merged, ...target.after].join("\n");
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
