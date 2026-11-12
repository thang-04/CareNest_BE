#!/usr/bin/env node
// Sinh bảng tra endpoint → controller từ BE docs/api/openapi.yaml: mỗi endpoint 1 dòng ngắn để grep,
// thay cho việc đọc openapi.yaml (~19k dòng). BE ghi docs/api/API_INDEX.md; FE/APP giữ ảnh chụp
// docs/integration/BE_API_INDEX.md để tra được cả khi máy không có repo BE.
// Dùng:  node scripts/ai-layer/api-index.mjs            (ghi)
//        node scripts/ai-layer/api-index.mjs --check    (không ghi; lệch ⇒ exit 1)
//        node scripts/ai-layer/api-index.mjs --from ../CareNest_BE   (FE/APP: chỉ rõ repo BE)
import { execFileSync } from "node:child_process";
import { existsSync, mkdirSync, writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { listFiles, loadConfig, readText, siblingRoot } from "./lib.mjs";

const OPENAPI = "docs/api/openapi.yaml";
const OUT = { BE: "docs/api/API_INDEX.md", FE: "docs/integration/BE_API_INDEX.md", APP: "docs/integration/BE_API_INDEX.md" };
const SOURCE_LINE = /^Nguồn: .*$/m;

// YAML do springdoc sinh: path thụt 2, method thụt 4, operationId/tags thụt 6. Bỏ tiền tố /api như baseURL của client.
export function parseOpenapi(text) {
  const operations = [];
  let inPaths = false;
  let path = null;
  let op = null;
  let inTags = false;
  for (const line of text.split("\n")) {
    if (/^\S/.test(line)) {
      inPaths = /^paths:/.test(line);
      continue;
    }
    if (!inPaths) continue;
    const pathLine = line.match(/^ {2}(\/\S*):\s*$/);
    if (pathLine) {
      path = pathLine[1].replace(/^\/api(?=\/|$)/, "") || "/";
      continue;
    }
    const method = line.match(/^ {4}(get|post|put|patch|delete):\s*$/);
    if (method) {
      op = { method: method[1].toUpperCase(), path, operationId: "", tag: "" };
      operations.push(op);
      inTags = false;
      continue;
    }
    if (!op) continue;
    const id = line.match(/^ {6}operationId:\s*(\S+)/);
    if (id) op.operationId = id[1];
    // Phần tử danh sách của `tags:` cũng thụt 6 (`      - account-controller`) nên xét trước key
    const item = line.match(/^ {6}- (\S+)/);
    if (item) {
      if (inTags && !op.tag) op.tag = item[1];
    } else if (/^ {6}\S/.test(line)) inTags = /^ {6}tags:\s*$/.test(line);
  }
  return operations.sort((a, b) => a.path.localeCompare(b.path) || a.method.localeCompare(b.method));
}

// Tag mặc định của springdoc là tên controller dạng kebab: account-controller ⇒ AccountController
const controllerClass = (tag) =>
  tag
    .split("-")
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join("");

export function renderIndex(operations, controllerFiles) {
  const byClass = new Map(controllerFiles.map((file) => [file.split("/").pop().replace(/\.java$/, ""), file]));
  const rows = operations.map((op) => {
    const file = byClass.get(controllerClass(op.tag));
    // springdoc thêm hậu tố _1, _2 khi trùng tên method giữa các controller
    const method = op.operationId.replace(/_\d+$/, "");
    return `${op.method} ${op.path} · ${file ?? `(không tìm thấy controller cho tag ${op.tag || "trống"})`}${method ? `#${method}` : ""}`;
  });
  return [
    "# Bảng tra API CareNest — endpoint → controller",
    "",
    "File sinh tự động từ `BE:docs/api/openapi.yaml`, không sửa tay. Sinh lại: `node scripts/ai-layer/api-index.mjs`.",
    "",
    "- Cách dùng: grep đoạn path (vd. `grep \"meal-counts\"`) để lấy file controller và tên method; không đọc cả file này hay `openapi.yaml`.",
    "- Path đã bỏ tiền tố `/api` (trùng baseURL của Web/App). File controller tính từ gốc repo `CareNest_BE`.",
    "- Request/response chi tiết: đọc method trong controller và DTO nó dùng; lỗi theo `BE:docs/contracts/ERROR_CONTRACT.md`.",
    "",
    "Nguồn: repo CareNest_BE (bản đang làm việc)",
    "",
    `Tổng: ${rows.length} endpoint.`,
    "",
    "```text",
    ...rows,
    "```",
    "",
  ].join("\n");
}

function sourceLabel(beRoot) {
  try {
    const out = execFileSync("git", ["log", "-1", "--format=%h %ad", "--date=short", "--", OPENAPI], { cwd: beRoot, encoding: "utf8", stdio: ["ignore", "pipe", "ignore"] });
    return out.trim() ? `Nguồn: CareNest_BE ${OPENAPI} tại commit ${out.trim()}` : null;
  } catch {
    return null;
  }
}

export function buildIndex(beRoot, { snapshot = false } = {}) {
  const controllers = listFiles(beRoot)
    .map((f) => f.split("\\").join("/"))
    .filter((f) => /^src\/main\/java\/.*Controller\.java$/.test(f));
  const text = renderIndex(parseOpenapi(readText(beRoot, OPENAPI)), controllers);
  const label = snapshot ? sourceLabel(beRoot) : null;
  return label ? text.replace(SOURCE_LINE, label) : text;
}

// BE giữ ảnh chụp bảng "file Web gọi endpoint nào" (FE sinh bằng check-api-contract.mjs --consumers) để tra tác động khi đổi API
const FE_CALLS = { from: "docs/integration/FE_API_CALLS.md", to: "docs/system/FE_API_CALLS.md" };
function pullFeCalls(root, config) {
  const fe = siblingRoot(root, config, "FE");
  if (!fe || !existsSync(join(fe, FE_CALLS.from))) return console.log(`api-index: không có repo FE cạnh bên — giữ ${FE_CALLS.to} đang có`);
  const next = readText(fe, FE_CALLS.from);
  if (!existsSync(join(root, FE_CALLS.to)) || readText(root, FE_CALLS.to) !== next) writeFileSync(join(root, FE_CALLS.to), next);
  console.log(`api-index: ${FE_CALLS.to} — chụp từ FE (${next.match(/^Tổng: (\d+)/m)?.[1] ?? "?"} lời gọi)`);
}

// So nội dung bỏ qua dòng "Nguồn:" (ảnh chụp ghi commit BE, bản BE thì không)
const body = (text) => text.replace(SOURCE_LINE, "");

function main(argv) {
  const check = argv.includes("--check");
  const from = argv.includes("--from") ? argv[argv.indexOf("--from") + 1] : null;
  const root = resolve(dirname(fileURLToPath(import.meta.url)), "..", "..");
  const { config } = loadConfig(root);
  const beRoot = from ? resolve(root, from) : siblingRoot(root, config, "BE");
  const out = OUT[config.repo] ?? OUT.FE;

  if (!beRoot || !existsSync(join(beRoot, OPENAPI))) {
    const msg = `api-index: không thấy repo BE cạnh bên (${OPENAPI}) — dùng ảnh chụp ${out} đang có`;
    if (check) console.log(`${msg}; bỏ qua kiểm tra`);
    else console.error(`${msg}. Chỉ rõ: --from <đường dẫn repo BE>`);
    return check ? 0 : 2;
  }

  const next = buildIndex(beRoot, { snapshot: config.repo !== "BE" });
  const current = existsSync(join(root, out)) ? readText(root, out) : "";
  const count = next.match(/^Tổng: (\d+)/m)?.[1];
  if (check) {
    if (body(current) === body(next)) {
      console.log(`api-index: ${out} khớp openapi (${count} endpoint)`);
      return 0;
    }
    console.log(`api-index: ${out} lệch so với ${OPENAPI} của BE ⇒ chạy: node scripts/ai-layer/api-index.mjs`);
    return 1;
  }
  if (body(current) !== body(next)) {
    mkdirSync(dirname(join(root, out)), { recursive: true });
    writeFileSync(join(root, out), next);
  }
  console.log(`api-index: ${out} — ${count} endpoint${body(current) === body(next) ? " (không đổi)" : ""}`);
  if (config.repo === "BE") pullFeCalls(root, config);
  return 0;
}

if (import.meta.url === pathToFileURL(process.argv[1] ?? "").href) {
  try {
    process.exitCode = main(process.argv.slice(2));
  } catch (err) {
    console.error(`api-index crash: ${err.stack ?? err}`);
    process.exitCode = 2;
  }
}
