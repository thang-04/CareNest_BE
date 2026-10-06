// E4 engineering memory (incident ↔ ISSUE_INDEX) · E5 plan (frontmatter, câu hỏi mở)
import { existsSync } from "node:fs";
import { basename, join } from "node:path";
import { parseFrontmatter, readText } from "./lib.mjs";

const clean = (cell) => cell.replace(/`/g, "").replace(/\[([^\]]*)\]\([^)]*\)/g, "$1").trim();

// Đọc bảng đầu tiên có cột ID + Status; tìm cột theo tên để chịu được index 5 hoặc 6 cột
export function parseIssueIndex(text) {
  const lines = text.split("\n");
  for (let i = 0; i < lines.length; i++) {
    const header = lines[i].split("|").map((c) => clean(c).toLowerCase());
    const idCol = header.indexOf("id");
    const statusCol = header.indexOf("status");
    if (idCol < 0 || statusCol < 0) continue;
    const fileCol = header.indexOf("file");
    const rows = [];
    for (let j = i + 2; j < lines.length && lines[j].trim().startsWith("|"); j++) {
      const cells = lines[j].split("|").map(clean);
      if (!cells[idCol]) continue;
      rows.push({ id: cells[idCol], status: cells[statusCol], file: fileCol >= 0 ? cells[fileCol] : "", line: j + 1 });
    }
    return rows;
  }
  return [];
}

export function checkKnowledge(root, config, files, report) {
  const { issueIndex, incidentsDir, incidentRequired, incidentIdPattern } = config.checkAiLayer;
  if (!existsSync(join(root, issueIndex))) return;
  const rows = parseIssueIndex(readText(root, issueIndex));
  const byId = new Map(rows.map((r) => [r.id, r]));
  const idRe = new RegExp(incidentIdPattern);
  const seen = new Set();

  const incidents = files.filter(
    (f) => f.startsWith(`${incidentsDir}/`) && f.endsWith(".md") && basename(f) !== "_TEMPLATE.md",
  );
  for (const file of incidents) {
    const fm = parseFrontmatter(readText(root, file));
    if (!fm) {
      report("error", file, 1, "thiếu frontmatter incident");
      continue;
    }
    const missing = incidentRequired.filter((k) => fm[k] === undefined || fm[k] === "" || (Array.isArray(fm[k]) && !fm[k].length));
    if (missing.length) report("error", file, 1, `frontmatter thiếu: ${missing.join(", ")}`);
    const id = fm.id;
    if (!id) continue;
    seen.add(id);
    const name = basename(file, ".md");
    if (name !== id && !name.startsWith(`${id}-`)) report("error", file, 1, `tên file phải là ${id}.md`);
    if (!idRe.test(id)) report("warn", file, 1, `ID không theo dạng chuẩn (${incidentIdPattern}): ${id}`);
    const row = byId.get(id);
    if (!row) report("error", file, 1, `chưa có dòng trong ${issueIndex}`);
    else if (row.status !== fm.status) {
      report("error", issueIndex, row.line, `status "${row.status}" khác incident ${id} ("${fm.status}")`);
    }
  }
  for (const row of rows) {
    if (!seen.has(row.id)) report("error", issueIndex, row.line, `${row.id} chưa có file trong ${incidentsDir}/`);
    if (row.file && !existsSync(join(root, incidentsDir, row.file.replace(/^incidents\//, "")))) {
      report("error", issueIndex, row.line, `cột File trỏ file không tồn tại: ${row.file}`);
    }
  }
}

const ACTIVE = new Set(["draft", "approved", "in-progress", "blocked"]);
const GATED = new Set(["approved", "in-progress"]);
const FINISHED = new Set(["done", "cancelled"]);

// Số câu chưa trả lời trong mục "Câu hỏi mở" (tới heading kế tiếp)
export function openQuestions(text) {
  const lines = text.split("\n");
  const start = lines.findIndex((l) => /^#{2,4}\s+Câu hỏi mở/.test(l));
  if (start < 0) return 0;
  let count = 0;
  for (let i = start + 1; i < lines.length && !/^#{1,4}\s/.test(lines[i]); i++) {
    if (/^\s*-\s+\[ \]/.test(lines[i])) count++;
  }
  return count;
}

export function checkPlans(root, config, files, report) {
  const dir = config.checkAiLayer.plansDir;
  for (const file of files.filter((f) => f.startsWith(`${dir}/active/`) && f.endsWith(".md"))) {
    const text = readText(root, file);
    const fm = parseFrontmatter(text);
    if (!fm) {
      report("error", file, 1, "plan thiếu frontmatter (xem docs/plans/_TEMPLATE.md)");
      continue;
    }
    if (!ACTIVE.has(fm.status)) {
      report("error", file, 1, `status "${fm.status}" không hợp lệ ở active/ (done/cancelled ⇒ chuyển sang completed/)`);
    }
    if (GATED.has(fm.status) && !fm.branch) report("error", file, 1, "plan approved/in-progress phải có `branch`");
    const open = openQuestions(text);
    if (GATED.has(fm.status) && open) {
      report("error", file, 1, `plan ${fm.status} còn ${open} câu hỏi mở chưa trả lời`);
    }
    if (fm.status === "in-progress" && !/^## Progress log/m.test(text)) report("warn", file, 1, "thiếu mục Progress log");
  }
  for (const file of files.filter((f) => f.startsWith(`${dir}/completed/`) && f.endsWith(".md"))) {
    const fm = parseFrontmatter(readText(root, file));
    if (!fm) report("warn", file, 1, "plan cũ không có frontmatter");
    else if (!FINISHED.has(fm.status)) report("error", file, 1, `plan ở completed/ phải là done/cancelled, đang là "${fm.status}"`);
  }
}
