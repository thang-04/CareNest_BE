// E1 mirror skills · E2 link/đường dẫn trong markdown · E7 frontmatter `paths` của rules
import { existsSync, statSync } from "node:fs";
import { dirname, join } from "node:path";
import { crossRepoRef, globToRegExp, isGitIgnored, matchAny, parseFrontmatter, readText, siblingRoot } from "./lib.mjs";

// E1 — `.agents/skills` phải là bản sao y hệt `.claude/skills` (Codex đọc bản .agents)
export function checkSkillsMirror(root, files, report) {
  const pick = (prefix) =>
    new Map(files.filter((f) => f.startsWith(prefix)).map((f) => [f.slice(prefix.length), f]));
  const claude = pick(".claude/skills/");
  const agents = pick(".agents/skills/");
  for (const [rel, file] of claude) {
    if (!agents.has(rel)) report("error", file, 0, `thiếu bản mirror .agents/skills/${rel}`);
    else if (readText(root, file) !== readText(root, agents.get(rel))) {
      report("error", file, 0, `khác nội dung với .agents/skills/${rel}`);
    }
    const name = parseFrontmatter(readText(root, file))?.name;
    if (rel.endsWith("SKILL.md") && name && name !== rel.split("/")[0]) {
      report("warn", file, 1, `frontmatter name "${name}" khác tên thư mục`);
    }
  }
  for (const [rel, file] of agents) {
    if (!claude.has(rel)) report("error", file, 0, `không có bản gốc .claude/skills/${rel}`);
  }
}

// Token trong backtick đáng kiểm: có "/", không khoảng trắng/placeholder/glob
const CANDIDATE = /^[^\s<>*{}()$=,;"'|\\]+$/;

function isCandidate(token) {
  return token.includes("/") && CANDIDATE.test(token) && !/^[/@-]/.test(token) && !token.includes("...");
}

function resolveToken(root, config, file, token) {
  const ref = crossRepoRef(token);
  if (ref) {
    const base = siblingRoot(root, config, ref.repo);
    return base ? (existsSync(join(base, ref.rel)) ? "ok" : "missing") : "skip";
  }
  const clean = token.replace(/#.*$/, "").replace(/:\d+(-\d+)?$/, "");
  const bases = [dirname(file)];
  for (let dir = dirname(file); dir !== "." && dir; dir = dirname(dir)) bases.push(dirname(dir));
  bases.push(".", ...config.checkAiLayer.pathRoots);
  if (bases.some((b) => existsSync(join(root, b, clean)))) return "ok";
  // Có ở repo sibling (vd. README_AI mô tả FE/APP) ⇒ không tính lỗi
  for (const repo of ["BE", "FE", "APP"]) {
    const base = repo === config.repo ? null : siblingRoot(root, config, repo);
    if (base && existsSync(join(base, clean))) return "ok";
  }
  return isGitIgnored(root, clean) ? "ok" : "missing";
}

// Chỉ báo lỗi khi token rõ ràng là đường dẫn trong repo (segment đầu là thư mục gốc đã biết)
function isStrict(config, token) {
  const clean = token.replace(/#.*$/, "");
  const first = clean.split("/")[0];
  return config.checkAiLayer.strictRoots.includes(first) && (/\.[A-Za-z]{1,5}$/.test(clean) || clean.endsWith("/"));
}

export function checkLinks(root, config, files, planned, report) {
  const { scanGlobs, scanExclude } = config.checkAiLayer;
  const targets = files.filter((f) => matchAny(scanGlobs, f) && !matchAny(scanExclude, f));
  for (const file of targets) {
    let inFence = false;
    readText(root, file)
      .split("\n")
      .forEach((line, index) => {
        if (/^\s*```/.test(line)) inFence = !inFence;
        if (inFence) return;
        for (const m of line.matchAll(/\[[^\]]*\]\(([^)\s]+)\)/g)) {
          const target = m[1].replace(/#.*$/, "");
          if (!target || /^[a-z]+:/i.test(target)) continue;
          if (!existsSync(join(root, dirname(file), target))) {
            report("error", file, index + 1, `link không tồn tại: ${m[1]}`);
          }
        }
        for (const m of line.matchAll(/`([^`\n]+)`/g)) {
          const token = m[1].trim();
          if (!isCandidate(token)) continue;
          const crossRef = crossRepoRef(token);
          if (!crossRef && !isStrict(config, token)) continue;
          if (resolveToken(root, config, file, token) !== "missing") continue;
          const norm = token.replace(/\/$/, "");
          const isPlanned = planned.some((p) => norm === p || norm.startsWith(`${p}/`));
          report(isPlanned ? "warn" : "error", file, index + 1, `${isPlanned ? "đường dẫn planned, chưa tồn tại" : "đường dẫn không tồn tại"}: ${token}`);
        }
      });
  }
}

// E7 — rule path-scoped phải có `paths` hợp lệ, nếu không Claude nạp rule cho mọi file (tốn token)
export function checkRules(root, files, report) {
  for (const file of files.filter((f) => /^\.claude\/rules\/[^/]+\.md$/.test(f))) {
    const fm = parseFrontmatter(readText(root, file));
    const paths = fm?.paths;
    if (!Array.isArray(paths) || paths.length === 0) {
      report("error", file, 1, "thiếu frontmatter `paths` (danh sách glob)");
      continue;
    }
    for (const glob of paths) {
      let re;
      try {
        re = globToRegExp(glob);
      } catch {
        report("error", file, 1, `glob không hợp lệ: ${glob}`);
        continue;
      }
      const base = glob.split(/[*?{]/)[0].replace(/\/[^/]*$/, "");
      const baseExists = base && existsSync(join(root, base)) && statSync(join(root, base)).isDirectory();
      if (baseExists && !files.some((f) => re.test(f))) report("warn", file, 1, `glob không khớp file nào: ${glob}`);
    }
  }
}
