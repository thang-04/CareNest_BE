#!/usr/bin/env node
// Kiểm tính nhất quán của lớp tài liệu AI (ADR-0012). Exit: 0 ok · 1 có lỗi · 2 lỗi dùng/crash.
// Dùng: node scripts/check-ai-layer.mjs [--quiet] [--json] [--cross-repo] [--only E1,E3]
import { dirname, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { checkContextMap, plannedPaths } from "./ai-layer/context-map.mjs";
import { checkCrossRepo } from "./ai-layer/cross-repo.mjs";
import { checkLinks, checkRules, checkSkillsMirror } from "./ai-layer/docs-checks.mjs";
import { checkKnowledge, checkPlans } from "./ai-layer/knowledge-checks.mjs";
import { deepMerge, listFiles, loadConfig } from "./ai-layer/lib.mjs";

const CHECKS = ["E1", "E2", "E3", "E4", "E5", "E6", "E7"];

// `overrides`: ghép đè lên config của repo (dùng trong test hoặc khi kiểm repo khác)
export function runChecks({ root, only = CHECKS.filter((c) => c !== "E6"), crossRepo = false, overrides } = {}) {
  const loaded = loadConfig(root);
  const config = overrides ? deepMerge(loaded.config, overrides) : loaded.config;
  const error = loaded.error;
  const files = listFiles(root);
  const findings = [];
  const run = (code, fn) => {
    if (!only.includes(code) || (code === "E6" && !crossRepo)) return;
    try {
      fn((level, file, line, msg) => findings.push({ code, level, file, line, msg }));
    } catch (err) {
      findings.push({ code, level: "error", file: "-", line: 0, msg: `check lỗi nội bộ: ${err.message}` });
    }
  };
  if (error) findings.push({ code: "CFG", level: "warn", file: "-", line: 0, msg: `config lỗi, dùng mặc định: ${error}` });
  run("E1", (report) => checkSkillsMirror(root, files, report));
  run("E2", (report) => checkLinks(root, config, files, plannedPaths(root, config), report));
  run("E3", (report) => checkContextMap(root, config, files, report));
  run("E4", (report) => checkKnowledge(root, config, files, report));
  run("E5", (report) => checkPlans(root, config, files, report));
  run("E6", (report) => checkCrossRepo(root, config, files, report));
  run("E7", (report) => checkRules(root, files, report));
  return { repo: config.repo, findings, errors: findings.filter((f) => f.level === "error").length };
}

export function formatSummary({ repo, findings }, only, crossRepo) {
  const parts = CHECKS.map((code) => {
    if (!only.includes(code) || (code === "E6" && !crossRepo)) return `${code} bỏ qua`;
    const n = findings.filter((f) => f.code === code).length;
    return `${code} ${n ? n : "ok"}`;
  });
  const errors = findings.filter((f) => f.level === "error").length;
  return `check-ai-layer (${repo}): ${errors} lỗi, ${findings.length - errors} cảnh báo · ${parts.join(" · ")}`;
}

function main(argv) {
  const flag = (name) => argv.includes(name);
  const onlyIndex = argv.indexOf("--only");
  const only = onlyIndex >= 0 ? (argv[onlyIndex + 1] ?? "").split(",").map((s) => s.trim().toUpperCase()) : CHECKS;
  if (only.some((c) => !CHECKS.includes(c))) {
    console.error(`--only nhận: ${CHECKS.join(",")}`);
    return 2;
  }
  const crossRepo = flag("--cross-repo");
  const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
  const result = runChecks({ root, only, crossRepo });
  if (flag("--json")) {
    console.log(JSON.stringify(result, null, 2));
  } else {
    for (const f of result.findings) {
      if (flag("--quiet") && f.level !== "error") continue;
      console.log(`[${f.code}] ${f.level === "error" ? "ERROR" : "WARN "} ${f.file}${f.line ? `:${f.line}` : ""}  ${f.msg}`);
    }
    if (!flag("--quiet") || result.errors) console.log(formatSummary(result, only, crossRepo));
  }
  return result.errors ? 1 : 0;
}

if (import.meta.url === pathToFileURL(process.argv[1] ?? "").href) {
  try {
    process.exitCode = main(process.argv.slice(2));
  } catch (err) {
    console.error(`check-ai-layer crash: ${err.stack ?? err}`);
    process.exitCode = 2;
  }
}
