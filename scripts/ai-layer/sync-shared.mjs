#!/usr/bin/env node
// Đồng bộ harness dùng chung giữa 3 repo. BE là nguồn: sửa file chung ở BE rồi chép sang FE/APP.
// Dùng:  node scripts/ai-layer/sync-shared.mjs --from ../CareNest_BE      (chạy trong FE/APP: lấy từ BE)
//        node scripts/ai-layer/sync-shared.mjs --to ../CareNest_FE        (chạy trong BE: chép sang FE)
//        node scripts/ai-layer/sync-shared.mjs --check [--from|--to ...]  (không ghi; lệch ⇒ exit 1)
// --check không kèm đường dẫn: FE/APP so với BE sibling; BE so với mọi sibling FE/APP đang có.
import { copyFileSync, existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { mergeSharedBlock } from "./cross-repo.mjs";
import { listFiles, loadConfig, matchAny, normalizeEol, readText, siblingRoot } from "./lib.mjs";

const same = (a, b) => normalizeEol(readFileSync(a, "utf8")) === normalizeEol(readFileSync(b, "utf8"));

// Chép file chung và khối "Quy tắc chung" của AGENTS.md từ source sang target. write=false ⇒ chỉ liệt kê.
// Danh sách file chung lấy theo config của source (nguồn quyết định cái gì là chung).
export function syncShared({ source, target, write = true }) {
  const { sharedFiles, agentsBlock } = loadConfig(source).config.checkAiLayer;
  const shared = (root) => listFiles(root).filter((f) => matchAny(sharedFiles, f));
  const changes = [];

  const fromSource = shared(source);
  for (const rel of fromSource) {
    const dest = join(target, rel);
    if (existsSync(dest) && same(join(source, rel), dest)) continue;
    changes.push({ rel, kind: existsSync(dest) ? "khác nội dung" : "thiếu ở đích" });
    if (!write) continue;
    mkdirSync(dirname(dest), { recursive: true });
    copyFileSync(join(source, rel), dest);
  }
  // File chung chỉ có ở đích: không tự xóa, để người chạy quyết định
  for (const rel of shared(target)) {
    if (!fromSource.includes(rel)) changes.push({ rel, kind: "chỉ có ở đích (không tự xóa)", manual: true });
  }

  if (existsSync(join(source, "AGENTS.md")) && existsSync(join(target, "AGENTS.md"))) {
    const current = readText(target, "AGENTS.md");
    const merged = mergeSharedBlock(readText(source, "AGENTS.md"), current, agentsBlock);
    if (merged === null) changes.push({ rel: "AGENTS.md", kind: `không tìm thấy khối "${agentsBlock.heading}"`, manual: true });
    else if (merged !== current) {
      changes.push({ rel: "AGENTS.md", kind: "khối quy tắc chung khác" });
      if (write) writeFileSync(join(target, "AGENTS.md"), merged);
    }
  }
  return changes;
}

function main(argv) {
  const value = (name) => (argv.includes(name) ? argv[argv.indexOf(name) + 1] : null);
  const check = argv.includes("--check");
  const root = resolve(dirname(fileURLToPath(import.meta.url)), "..", "..");
  const { config } = loadConfig(root);
  const from = value("--from");
  const to = value("--to");
  if ((from && to) || (argv.includes("--from") && !from) || (argv.includes("--to") && !to)) {
    console.error("Dùng: sync-shared.mjs [--check] (--from <repo nguồn> | --to <repo đích>)");
    return 2;
  }

  let pairs = [];
  if (from) pairs = [{ source: resolve(root, from), target: root }];
  else if (to) pairs = [{ source: root, target: resolve(root, to) }];
  else if (!check) {
    console.error("Cần --from <repo nguồn> hoặc --to <repo đích> (ghi file phải chỉ rõ repo).");
    return 2;
  } else if (config.repo === "BE") {
    pairs = ["FE", "APP"].map((name) => siblingRoot(root, config, name)).filter(Boolean).map((target) => ({ source: root, target }));
  } else {
    const source = siblingRoot(root, config, "BE");
    if (source) pairs = [{ source, target: root }];
  }
  if (!pairs.length) {
    console.log("sync-shared: không có repo sibling để so — bỏ qua");
    return 0;
  }

  let total = 0;
  for (const { source, target } of pairs) {
    if (!existsSync(source) || !existsSync(target)) {
      console.error(`sync-shared: không thấy thư mục ${existsSync(source) ? target : source}`);
      return 2;
    }
    const changes = syncShared({ source, target, write: !check });
    total += changes.length;
    for (const c of changes) console.log(`${check || c.manual ? "LỆCH " : "CHÉP "} ${c.rel}  (${c.kind})`);
    console.log(`sync-shared: ${source} → ${target}: ${changes.length} file ${check ? "lệch" : "đã xử lý"}`);
  }
  if (check && total) console.log("Sửa: sửa file chung ở BE, rồi trong FE/APP chạy `node scripts/ai-layer/sync-shared.mjs --from ../CareNest_BE`.");
  return check && total ? 1 : 0;
}

if (import.meta.url === pathToFileURL(process.argv[1] ?? "").href) {
  try {
    process.exitCode = main(process.argv.slice(2));
  } catch (err) {
    console.error(`sync-shared crash: ${err.stack ?? err}`);
    process.exitCode = 2;
  }
}
