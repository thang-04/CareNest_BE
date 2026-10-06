// Tiện ích dùng chung cho check-ai-layer và Claude hooks (zero-dependency, Node >= 18).
import { execFileSync } from "node:child_process";
import { existsSync, readFileSync, readdirSync, statSync } from "node:fs";
import { join, posix } from "node:path";

export const CONFIG_PATH = ".claude/hooks/harness.config.json";

export const DEFAULTS = {
  repo: "BE",
  siblings: { BE: ["../CareNest_BE-main", "../CareNest_BE"], FE: ["../CareNest_FE"], APP: ["../CareNest_APP"] },
  checkAiLayer: {
    scanGlobs: ["AGENTS.md", "CLAUDE.md", ".ai/**/*.md", ".claude/**/*.md", ".agents/**/*.md", "docs/**/*.md"],
    scanExclude: ["docs/plans/completed/**"],
    pathRoots: ["docs", ".ai"],
    strictRoots: ["docs", ".ai", ".claude", ".agents", "src", "scripts", ".githooks"],
    contextMap: ".ai/CONTEXT_MAP.yaml",
    issueIndex: "docs/knowledge/ISSUE_INDEX.md",
    incidentsDir: "docs/knowledge/incidents",
    incidentRequired: ["id", "type", "status", "date", "keywords"],
    incidentIdPattern: "^(BUG|CASE|ENV)-\\d{6}-[a-z0-9-]+$",
    plansDir: "docs/plans",
    agentsBlock: { heading: "Quy tắc chung CareNest (bắt buộc)", exclude: ["Hỏi trước khi làm", "Phạm vi"] },
    sharedFiles: [],
  },
  // Phần dưới dùng cho Claude hooks (.claude/hooks); repo ghi đè trong harness.config.json
  nonCodeGlobs: ["docs/**", ".ai/**", ".claude/**", ".agents/**", ".githooks/**", "scripts/**", "**/*.md", ".git*", ".editorconfig"],
  memoryGlobs: ["docs/knowledge/**", "docs/modules/**", "docs/business/**", "docs/features/**"],
  aiLayerGlobs: ["AGENTS.md", "CLAUDE.md", ".ai/**", ".claude/**", ".agents/**", "docs/**"],
  lanes: { smallMaxFiles: 2, mediumMaxFiles: 8, riskGlobs: [] },
  verify: { activeWhen: [], globs: ["src/**"], display: "node scripts/verify.mjs" },
  guard: {
    denyRead: ["**/.env", "**/.env.*", "**/*.pem", "**/*.key", "**/*.p12", "**/*.pfx", "**/*.jks", "**/id_rsa*", "**/id_ed25519*"],
    denyReadExcept: ["**/.env.example", "**/.env.sample", "**/.env.template"],
    immutableGlobs: [],
    dependencyGlobs: [],
    contractGlobs: [],
    highRisk: [],
    harnessGlobs: [".claude/settings*.json", ".claude/hooks/**", ".githooks/**", "scripts/**"],
  },
  plans: { dir: "docs/plans", gateStatuses: ["approved", "in-progress"] },
};

// Chuẩn hóa EOL để so sánh nội dung giữa Windows (CRLF) và LF
export const normalizeEol = (text) => text.replace(/\r\n?/g, "\n");

export function readText(root, rel) {
  return normalizeEol(readFileSync(join(root, rel), "utf8"));
}

function isObject(value) {
  return value && typeof value === "object" && !Array.isArray(value);
}

export function deepMerge(base, extra) {
  const out = { ...base };
  for (const [key, value] of Object.entries(extra ?? {})) {
    out[key] = isObject(value) && isObject(base[key]) ? deepMerge(base[key], value) : value;
  }
  return out;
}

// Config lỗi ⇒ dùng mặc định (fail-open) nhưng báo lại để người sửa biết
export function loadConfig(root) {
  const file = join(root, CONFIG_PATH);
  if (!existsSync(file)) return { config: DEFAULTS, error: null };
  try {
    return { config: deepMerge(DEFAULTS, JSON.parse(readFileSync(file, "utf8"))), error: null };
  } catch (err) {
    return { config: DEFAULTS, error: `${CONFIG_PATH}: ${err.message}` };
  }
}

// Glob → RegExp neo từ gốc repo: `**/` = 0..n thư mục, `*` = trong 1 segment, `{a,b}`, `?`
export function globToRegExp(glob) {
  let re = "";
  for (let i = 0; i < glob.length; i++) {
    const c = glob[i];
    if (c === "*" && glob[i + 1] === "*") {
      if (glob[i + 2] === "/") {
        re += "(?:.*/)?";
        i += 2;
      } else {
        re += ".*";
        i += 1;
      }
    } else if (c === "*") re += "[^/]*";
    else if (c === "?") re += "[^/]";
    else if (c === "{") {
      const end = glob.indexOf("}", i);
      re += `(?:${glob.slice(i + 1, end).split(",").map(escapeRe).join("|")})`;
      i = end;
    } else re += escapeRe(c);
  }
  return new RegExp(`^${re}$`);
}

const escapeRe = (s) => s.replace(/[.+^$()|[\]\\]/g, "\\$&");

export function matchAny(globs, rel) {
  return globs.some((g) => globToRegExp(g).test(rel));
}

// Danh sách file (tracked + untracked không bị ignore); không phải git repo ⇒ tự duyệt thư mục
export function listFiles(root) {
  try {
    const out = execFileSync("git", ["ls-files", "-co", "--exclude-standard", "-z"], {
      cwd: root,
      encoding: "utf8",
      stdio: ["ignore", "pipe", "ignore"],
      maxBuffer: 64 * 1024 * 1024,
    });
    return out.split("\0").filter((p) => p && existsSync(join(root, p)));
  } catch {
    return walk(root, "");
  }
}

// File cố ý không commit (vd. .claude/settings.local.json) vẫn là tham chiếu hợp lệ
export function isGitIgnored(root, rel) {
  try {
    execFileSync("git", ["check-ignore", "-q", "--no-index", rel], { cwd: root, stdio: "ignore" });
    return true;
  } catch {
    return false;
  }
}

// Tự bật git hook của repo (core.hooksPath=.githooks) để không ai phải nhớ chạy tay.
// Đã trỏ chỗ khác (husky, hook riêng) ⇒ không ghi đè. Trả về: absent | ok | enabled | other | error
export function ensureGitHooks(root) {
  if (!existsSync(join(root, ".githooks"))) return "absent";
  const run = (args) => execFileSync("git", args, { cwd: root, encoding: "utf8", stdio: ["ignore", "pipe", "ignore"] }).trim();
  let current = "";
  try {
    current = run(["config", "--get", "core.hooksPath"]);
  } catch {
    // chưa đặt: git trả exit 1
  }
  if (current === ".githooks") return "ok";
  if (current) return "other";
  try {
    run(["config", "core.hooksPath", ".githooks"]);
    return "enabled";
  } catch {
    return "error";
  }
}

const SKIP_DIRS = new Set([".git", "node_modules", "target", "build", "dist", ".idea"]);

function walk(root, rel) {
  const files = [];
  for (const name of readdirSync(join(root, rel))) {
    const child = rel ? `${rel}/${name}` : name;
    if (statSync(join(root, child)).isDirectory()) {
      if (!SKIP_DIRS.has(name)) files.push(...walk(root, child));
    } else files.push(child);
  }
  return files;
}

// Bỏ comment YAML ` # ...` nằm ngoài dấu nháy
export function stripYamlComment(line) {
  let quote = null;
  for (let i = 0; i < line.length; i++) {
    const c = line[i];
    if (quote) {
      if (c === quote) quote = null;
    } else if (c === '"' || c === "'") quote = c;
    else if (c === "#" && (i === 0 || /\s/.test(line[i - 1]))) return line.slice(0, i).trimEnd();
  }
  return line;
}

const unquote = (s) => s.trim().replace(/^["']|["']$/g, "");

function parseScalar(raw) {
  const value = raw.trim();
  if (value.startsWith("[") && value.endsWith("]")) {
    const inner = value.slice(1, -1).trim();
    return inner ? inner.split(",").map(unquote).filter(Boolean) : [];
  }
  return unquote(value);
}

// Frontmatter YAML tối giản: `key: value`, `key: [a, b]`, `key:` + các dòng `  - item`
export function parseFrontmatter(text) {
  const lines = normalizeEol(text).split("\n");
  if (lines[0].trim() !== "---") return null;
  const data = {};
  let listKey = null;
  for (let i = 1; i < lines.length; i++) {
    const line = stripYamlComment(lines[i]);
    if (line.trim() === "---") return data;
    const item = line.match(/^\s+-\s+(.*)$/);
    if (item && listKey) {
      data[listKey].push(unquote(item[1]));
      continue;
    }
    const kv = line.match(/^([A-Za-z_][\w-]*):\s*(.*)$/);
    if (!kv) continue;
    if (kv[2] === "") {
      data[kv[1]] = [];
      listKey = kv[1];
    } else {
      data[kv[1]] = parseScalar(kv[2]);
      listKey = null;
    }
  }
  return null;
}

// Header "Status: CHƯA CÓ NỘI DUNG" trong vài dòng đầu = file skeleton, không dùng làm nguồn
export function isSkeleton(text) {
  return /Status:\s*CHƯA CÓ NỘI DUNG/.test(normalizeEol(text).split("\n").slice(0, 6).join("\n"));
}

// Repo sibling đầu tiên tồn tại (BE trỏ về chính nó khi đang ở repo BE)
export function siblingRoot(root, config, name) {
  if (name === config.repo) return root;
  for (const candidate of config.siblings?.[name] ?? []) {
    const abs = join(root, candidate);
    if (existsSync(abs)) return abs;
  }
  return null;
}

// Đường dẫn trỏ sang repo khác: `BE:x`, `../CareNest_FE/x` ⇒ {repo, rel}
export function crossRepoRef(token) {
  const prefixed = token.match(/^(BE|FE|APP):(.+)$/);
  if (prefixed) return { repo: prefixed[1], rel: prefixed[2] };
  const sibling = token.match(/^(?:\.\.\/)+CareNest_(BE|FE|APP)(?:-main)?\/(.*)$/);
  if (sibling) return { repo: sibling[1], rel: sibling[2] };
  return null;
}

export const toPosix = (p) => p.split("\\").join("/");
export const joinRel = (...parts) => posix.normalize(parts.join("/"));
