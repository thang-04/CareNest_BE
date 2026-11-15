#!/usr/bin/env node
// In bản tác động của việc sửa một hay nhiều file: file nào dùng nó, lan tới màn hình/route (Web), endpoint + file Web gọi (BE),
// feature (App), và ai phụ trách. Dùng trước khi sửa vùng dùng chung hoặc module của người khác (AGENTS.md › Chế độ làm việc).
// Dùng: node scripts/ai-layer/impact.mjs <file> [file...]
// Đồ thị dựng từ câu lệnh import (tĩnh và động); tham chiếu không qua import (Java cùng package, class CSS dùng chung) không thấy.
import { existsSync } from "node:fs";
import { dirname, posix, relative, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { listFiles, loadConfig, matchAny, readText, toPosix } from "./lib.mjs";

const MAX_LISTED = 6; // số file liệt kê mỗi nhóm; phần còn lại chỉ đếm
const MAX_GROUPS = 10; // số nhóm in chi tiết

// Cách đọc import và nhận biết "màn hình" theo repo
const PROFILES = {
  FE: {
    source: /^src\/.*\.(js|jsx|css)$/,
    imports: (text) => [...text.matchAll(/(?:from\s+|import\s*\(\s*|import\s+|@import\s+(?:url\()?)['"]([^'"]+)['"]/g)].map((m) => m[1]),
    resolve(spec, from, has) {
      const base = spec.startsWith("@/") ? `src/${spec.slice(2)}` : spec.startsWith(".") ? posix.join(posix.dirname(from), spec) : null;
      return base && [base, `${base}.js`, `${base}.jsx`, `${base}/index.js`, `${base}/index.jsx`].find(has);
    },
    screen: /^src\/pages\//,
    group: (file) => file.split("/")[2]?.replace(/\.jsx?$/, "") ?? "pages",
    screenLabel: "màn (page)",
  },
  BE: {
    source: /^src\/main\/java\/.*\.java$/,
    imports: (text) => [...text.matchAll(/^import\s+(?:static\s+)?([\w.]+?)(?:\.[A-Z_]+|\.\*)?;/gm)].map((m) => m[1]),
    resolve: (spec, _from, has) => [`src/main/java/${spec.split(".").join("/")}.java`].find(has),
    screen: /Controller\.java$/,
    group: (file) => file.split("/").at(-2),
    screenLabel: "controller",
  },
  APP: {
    source: /^lib\/.*\.dart$/,
    imports: (text) => [...text.matchAll(/^(?:import|export|part)\s+['"]([^'"]+)['"]/gm)].map((m) => m[1]),
    resolve(spec, from, has) {
      const base = spec.startsWith("package:carenest_app/") ? `lib/${spec.slice("package:carenest_app/".length)}` : spec.includes(":") ? null : posix.join(posix.dirname(from), spec);
      return base && [base].find(has);
    },
    screen: /^lib\/features\/[^/]+\/presentation\//,
    group: (file) => file.split("/")[2],
    screenLabel: "file giao diện (presentation)",
  },
};

// importers.get(file) = các file import trực tiếp `file`
export function buildImporters(root, profile, files) {
  const sources = files.filter((f) => profile.source.test(f));
  const set = new Set(sources);
  const importers = new Map();
  for (const file of sources) {
    for (const spec of profile.imports(readText(root, file))) {
      const target = profile.resolve(spec, file, (p) => set.has(p));
      if (!target || target === file) continue;
      if (!importers.has(target)) importers.set(target, new Set());
      importers.get(target).add(file);
    }
  }
  return importers;
}

// Mọi file phụ thuộc (trực tiếp hoặc gián tiếp) vào các file đích
export function dependents(importers, targets) {
  const seen = new Set(targets);
  const queue = [...targets];
  while (queue.length) for (const next of importers.get(queue.shift()) ?? []) if (!seen.has(next)) queue.push(next) && seen.add(next);
  for (const t of targets) seen.delete(t);
  return seen;
}

// Web: page ⇒ các `path` khai trong src/routes/AppRoutes.jsx
export function feRoutes(routesText) {
  const fileOf = new Map(); // tên component ⇒ file page
  for (const m of routesText.matchAll(/const\s+(\w+)\s*=[^;]*?import\(\s*['"]@\/(pages\/[^'"]+)['"]/g)) fileOf.set(m[1], `src/${m[2]}`);
  for (const m of routesText.matchAll(/import\s+(\w+)\s+from\s+['"]@\/(pages\/[^'"]+)['"]/g)) fileOf.set(m[1], `src/${m[2]}`);
  const routes = new Map(); // file page (không đuôi) ⇒ [path]
  for (const line of routesText.split("\n")) {
    if (!line.includes("<Route")) continue;
    const path = line.match(/path="([^"]*)"/)?.[1] ?? (/\bindex\b/.test(line) ? "(index)" : null);
    if (path === null) continue;
    for (const m of line.matchAll(/<(\w+)[\s/>]/g)) {
      const file = fileOf.get(m[1]);
      if (file) routes.set(file, [...(routes.get(file) ?? []), path]);
    }
  }
  return routes;
}

const stripExt = (file) => file.replace(/\.(jsx?|dart|java)$/, "");

function ownerOf(config, file) {
  return Object.entries(config.ownership.modules).find(([glob]) => matchAny([glob], file))?.[1] ?? null;
}

function listGroup(files, decorate = (f) => f) {
  const shown = files.slice(0, MAX_LISTED).map(decorate);
  return shown.join(", ") + (files.length > MAX_LISTED ? `, … (+${files.length - MAX_LISTED})` : "");
}

// BE: endpoint của controller và file Web gọi endpoint đó (từ 2 bảng sinh tự động)
function beEndpoints(root, controllers) {
  const norm = (p) => p.replace(/\{[^}]+\}/g, "{x}");
  const index = existsSync(resolve(root, "docs/api/API_INDEX.md")) ? readText(root, "docs/api/API_INDEX.md").split("\n") : [];
  const calls = existsSync(resolve(root, "docs/system/FE_API_CALLS.md")) ? readText(root, "docs/system/FE_API_CALLS.md").split("\n") : [];
  const out = [];
  for (const line of index) {
    const row = line.match(/^(GET|POST|PUT|PATCH|DELETE) (\S+) · (\S+?)#/);
    if (!row || !controllers.has(row[3])) continue;
    const key = `${row[1]} ${norm(row[2])} · `;
    const callers = calls.filter((c) => c.startsWith(key)).map((c) => c.slice(key.length).split(" ")[0]);
    out.push({ endpoint: `${row[1]} ${row[2]}`, callers });
  }
  return out;
}

export function impactReport(root, targets) {
  const { config } = loadConfig(root);
  const profile = PROFILES[config.repo] ?? PROFILES.FE;
  const files = listFiles(root).map(toPosix);
  const importers = buildImporters(root, profile, files);
  const lines = [];

  for (const target of targets) {
    lines.push(`## ${target}`);
    if (!files.includes(target)) {
      lines.push("- Không có trong repo (file mới hoặc sai đường dẫn) ⇒ chưa có gì phụ thuộc.");
      continue;
    }
    const shared = matchAny(config.guard.sharedGlobs, target);
    lines.push(`- Vùng dùng chung: ${shared ? "CÓ" : "không"} · Phụ trách: ${ownerOf(config, target) ?? "chưa khai (ownership trong harness.config.json)"}`);
    const direct = [...(importers.get(target) ?? [])].sort();
    const all = [...dependents(importers, [target])].sort();
    lines.push(`- Dùng trực tiếp: ${direct.length} file${direct.length ? ` — ${listGroup(direct)}` : ""}`);

    const screens = all.filter((f) => profile.screen.test(f));
    if (profile.screen.test(target)) screens.unshift(target);
    const groups = new Map();
    for (const f of screens) groups.set(profile.group(f), [...(groups.get(profile.group(f)) ?? []), f]);
    lines.push(`- Lan tới ${screens.length} ${profile.screenLabel}, ${groups.size} nhóm (qua ${all.length} file phụ thuộc):`);

    const routes = config.repo === "FE" && files.includes("src/routes/AppRoutes.jsx") ? feRoutes(readText(root, "src/routes/AppRoutes.jsx")) : new Map();
    const decorate = (f) => {
      const paths = routes.get(stripExt(f));
      return (f.split("/").slice(3).join("/") || f.split("/").pop()) + (paths ? ` [route ${[...new Set(paths)].join(", ")}]` : "");
    };
    const ranked = [...groups].sort((a, b) => b[1].length - a[1].length);
    if (ranked.length > MAX_GROUPS) lines.push(`  - … và ${ranked.length - MAX_GROUPS} nhóm khác: ${ranked.slice(MAX_GROUPS).map(([g, l]) => `${g} (${l.length})`).join(", ")}`);
    for (const [group, list] of ranked.slice(0, MAX_GROUPS)) {
      const owners = [...new Set(list.map((f) => ownerOf(config, f)).filter(Boolean))];
      lines.push(`  - ${group} (${list.length})${owners.length ? ` · phụ trách: ${owners.join(", ")}` : ""}: ${listGroup(list, config.repo === "FE" ? decorate : (f) => f.split("/").pop())}`);
    }

    if (config.repo === "BE") {
      const endpoints = beEndpoints(root, new Set(screens));
      const callers = [...new Set(endpoints.flatMap((e) => e.callers))].sort();
      lines.push(`- Endpoint bị ảnh hưởng: ${endpoints.length}${endpoints.length ? ` — ${listGroup(endpoints.map((e) => e.endpoint))}` : ""}`);
      lines.push(`- File Web đang gọi các endpoint đó: ${callers.length}${callers.length ? ` — ${listGroup(callers)}` : ""}`);
    }
    if (target.endsWith(".css")) lines.push("- CSS: class trong file này áp lên mọi màn đã nạp nó; đổi tên/đổi kiểu class ⇒ grep tên class trong `src/` trước khi sửa.");
  }
  lines.push("", "Bản tác động cần nêu thêm: thay đổi gì · luồng người dùng đổi thế nào · 2–3 phương án + đề xuất · cách kiểm.");
  return lines.join("\n");
}

function main(argv) {
  const root = resolve(dirname(fileURLToPath(import.meta.url)), "..", "..");
  const targets = argv.filter((a) => !a.startsWith("--")).map((a) => toPosix(relative(root, resolve(process.cwd(), a))));
  if (!targets.length) {
    console.error("Dùng: node scripts/ai-layer/impact.mjs <file> [file...]");
    return 2;
  }
  console.log(impactReport(root, targets));
  return 0;
}

if (import.meta.url === pathToFileURL(process.argv[1] ?? "").href) {
  try {
    process.exitCode = main(process.argv.slice(2));
  } catch (err) {
    console.error(`impact crash: ${err.stack ?? err}`);
    process.exitCode = 2;
  }
}
