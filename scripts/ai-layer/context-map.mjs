// E3 — kiểm .ai/CONTEXT_MAP.yaml: đường dẫn được route phải tồn tại, planned/skeleton khớp thực tế.
// Parser theo dòng (không dependency) đủ cho 3 layout BE/FE/APP: list `[a, b]`, map `{k: v}`, block `- item`.
import { existsSync, readdirSync, statSync } from "node:fs";
import { join } from "node:path";
import { crossRepoRef, isSkeleton, readText, siblingRoot, stripYamlComment } from "./lib.mjs";

const SKIP_SECTIONS = new Set(["version", "repository", "role", "external_sources", "related_repositories"]);
const FILE_LIST_SECTIONS = new Set(["available", "skeleton_only", "planned"]);

const unquote = (s) => s.trim().replace(/^["']|["']$/g, "");

// Tách value thành item: bỏ ngoặc, tách dấu phẩy, `k: v` ⇒ v; `path#8 #11` ⇒ path
function splitItems(value) {
  return value
    .replace(/[{}[\]]/g, ",")
    .split(",")
    .map((part) => unquote(part.includes(": ") ? part.slice(part.lastIndexOf(": ") + 2) : part))
    .map((item) => item.split(/\s+/)[0].replace(/#.*$/, ""))
    .filter((item) => item && item !== "null" && !/^https?:/.test(item) && !item.includes("<"));
}

function valueOf(line) {
  const block = line.match(/^\s+-\s+(.*)$/);
  if (block) return block[1];
  const quotedKey = line.match(/^\s+"[^"]*":\s*(.*)$/);
  if (quotedKey) return quotedKey[1];
  const key = line.match(/^\s*[A-Za-z_][\w-]*:\s*(.*)$/);
  return key ? key[1] : line.trim();
}

export function parseContextMap(text) {
  const entries = [];
  const moduleIds = new Set();
  let top = null;
  let sub = null;
  text.split("\n").forEach((raw, index) => {
    const line = stripYamlComment(raw);
    if (!line.trim()) return;
    const topKey = line.match(/^([A-Za-z_][\w-]*):/);
    if (topKey) {
      top = topKey[1];
      sub = null;
    } else {
      const subKey = line.match(/^ {2}([A-Za-z_][\w-]*):/);
      if (subKey) sub = subKey[1];
      if (top === "modules" && subKey) moduleIds.add(subKey[1]);
    }
    if (!top || SKIP_SECTIONS.has(top)) return;
    const section = sub === "skeleton_only" ? "skeleton_only" : top;
    for (const item of splitItems(valueOf(line))) {
      entries.push({ section, line: index + 1, item });
    }
  });
  return { entries, moduleIds };
}

const looksLikePath = (item, section) =>
  item.includes("/") || /\.[A-Za-z]{1,5}$/.test(item) || (FILE_LIST_SECTIONS.has(section) && !/\s/.test(item));

// Thư mục chỉ chứa .gitkeep vẫn tính là "chưa có"
function hasContent(abs) {
  if (!existsSync(abs)) return false;
  if (!statSync(abs).isDirectory()) return true;
  return readdirSync(abs).some((name) => name !== ".gitkeep");
}

export function resolveRef(root, config, item) {
  const ref = crossRepoRef(item);
  if (!ref) return { abs: join(root, item) };
  const base = siblingRoot(root, config, ref.repo);
  return base ? { abs: join(base, ref.rel) } : { skip: true };
}

export function plannedPaths(root, config) {
  const file = config.checkAiLayer.contextMap;
  if (!existsSync(join(root, file))) return [];
  return parseContextMap(readText(root, file))
    .entries.filter((e) => e.section === "planned")
    .map((e) => e.item.replace(/\/$/, ""));
}

export function checkContextMap(root, config, files, report) {
  const file = config.checkAiLayer.contextMap;
  if (!existsSync(join(root, file))) return report("error", file, 0, "không tìm thấy CONTEXT_MAP");
  const { entries, moduleIds } = parseContextMap(readText(root, file));
  const skeletons = new Set();

  for (const { section, line, item } of entries) {
    if (!looksLikePath(item, section)) {
      // BE: keywords → module id phải có trong `modules`
      if (section === "keywords" && moduleIds.size && !moduleIds.has(item)) {
        report("error", file, line, `keyword trỏ module không tồn tại: ${item}`);
      }
      continue;
    }
    const target = resolveRef(root, config, item);
    if (target.skip) continue;
    if (section === "planned") {
      if (hasContent(target.abs)) report("warn", file, line, `planned nhưng đã tồn tại — chuyển sang available: ${item}`);
      continue;
    }
    if (!existsSync(target.abs)) {
      report("error", file, line, `đường dẫn không tồn tại (${section}): ${item}`);
      continue;
    }
    if (section === "skeleton_only") {
      skeletons.add(item);
      if (!statSync(target.abs).isDirectory() && !isSkeleton(readText(root, item))) {
        report("warn", file, line, `skeleton_only nhưng file không có header "Status: CHƯA CÓ NỘI DUNG": ${item}`);
      }
    }
  }

  // Ngược lại: file có header skeleton mà map không khai báo
  for (const rel of files) {
    if (rel.startsWith("docs/") && rel.endsWith(".md") && !skeletons.has(rel) && isSkeleton(readText(root, rel))) {
      report("warn", rel, 1, "file có header skeleton nhưng không nằm trong skeleton_only của CONTEXT_MAP");
    }
  }
}
