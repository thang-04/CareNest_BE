// State theo session: event log, baseline lúc bắt đầu, phần thay đổi của session, fingerprint, làn S/M/L.
import { execFileSync } from "node:child_process";
import { createHash } from "node:crypto";
import { appendFileSync, existsSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { matchAny, parseFrontmatter, readText } from "../../../scripts/ai-layer/lib.mjs";
import { git, readJson, tryGit, writeJson } from "./hook-io.mjs";

const eventsFile = (ctx) => join(ctx.stateDir, `${ctx.sid}.events.jsonl`);
const baselineFile = (ctx) => join(ctx.stateDir, `${ctx.sid}.baseline.json`);

// Append-only để nhiều hook chạy song song không ghi đè nhau
export function appendEvent(ctx, event) {
  appendFileSync(eventsFile(ctx), `${JSON.stringify({ at: Date.now(), ...event })}\n`);
}

export function readEvents(ctx) {
  if (!existsSync(eventsFile(ctx))) return [];
  return readFileSync(eventsFile(ctx), "utf8")
    .split("\n")
    .filter(Boolean)
    .map((line) => {
      try {
        return JSON.parse(line);
      } catch {
        return null;
      }
    })
    .filter(Boolean);
}

export const hasEvent = (events, kind, key) => events.some((e) => e.kind === kind && e.key === key);

// File đang thay đổi so với HEAD (kể cả untracked) ⇒ {path: blobHash | "D"}; parse `-z` giữ path Unicode
export function dirtyMap(root) {
  let raw = "";
  try {
    raw = git(["status", "--porcelain=v1", "-z", "-uall"], root);
  } catch {
    return {};
  }
  const parts = raw.split("\0");
  const paths = [];
  for (let i = 0; i < parts.length; i++) {
    const entry = parts[i];
    if (entry.length < 4) continue;
    paths.push(entry.slice(3));
    if (entry[0] === "R" || entry[0] === "C") i++;
  }
  const limited = paths.slice(0, 5000);
  const existing = limited.filter((p) => existsSync(join(root, p)));
  const hashes = existing.length ? hashObjects(root, existing) : [];
  const map = Object.fromEntries(limited.map((p) => [p, "D"]));
  existing.forEach((p, i) => (map[p] = hashes[i] ?? "?"));
  return map;
}

// Qua stdin để không vượt giới hạn độ dài dòng lệnh Windows
function hashObjects(root, files) {
  try {
    return execFileSync("git", ["hash-object", "--stdin-paths"], {
      cwd: root,
      input: files.join("\n"),
      encoding: "utf8",
      stdio: ["pipe", "pipe", "ignore"],
      timeout: 10000,
    })
      .trim()
      .split("\n");
  } catch {
    return [];
  }
}

// Baseline = WIP có từ trước session; chỉ ghi lần đầu để resume/compact giữ nguyên mốc
export function ensureBaseline(ctx) {
  if (!existsSync(baselineFile(ctx))) writeJson(baselineFile(ctx), dirtyMap(ctx.root));
}

// File session này đã đổi = file sửa qua tool ∪ file khác baseline (bắt cả sửa qua Bash)
export function sessionDelta(ctx, events) {
  const edited = events.filter((e) => e.kind === "edit" && e.path).map((e) => e.path);
  const baseline = readJson(baselineFile(ctx), null);
  if (!baseline) return [...new Set(edited)];
  const now = dirtyMap(ctx.root);
  const changed = Object.keys(now).filter((p) => now[p] !== baseline[p]);
  const reverted = Object.keys(baseline).filter((p) => !(p in now));
  return [...new Set([...edited, ...changed, ...reverted])];
}

// Fingerprint của mã nguồn cần verify: HEAD + nội dung file đang đổi trong verify.globs
export function sourceFingerprint(ctx) {
  const head = tryGit(["rev-parse", "HEAD"], ctx.root, "none");
  const dirty = dirtyMap(ctx.root);
  const relevant = Object.keys(dirty)
    .filter((p) => matchAny(ctx.config.verify.globs, p))
    .sort()
    .map((p) => `${p}:${dirty[p]}`);
  return createHash("sha1").update([head, ...relevant].join("\n")).digest("hex").slice(0, 16);
}

export const verifyActive = (ctx) => ctx.config.verify.activeWhen.some((f) => existsSync(join(ctx.root, f)));

// Làn thực tế từ diff: vùng rủi ro hoặc >8 file ⇒ L; >2 file ⇒ M; còn lại S
export function laneOf(paths, config) {
  const source = paths.filter((p) => !matchAny(config.nonCodeGlobs, p));
  if (paths.some((p) => matchAny(config.lanes.riskGlobs, p)) || source.length > config.lanes.mediumMaxFiles) return "L";
  return source.length > config.lanes.smallMaxFiles ? "M" : "S";
}

// Plan active cùng branch (dùng cho plan gate + orient)
export function activePlans(ctx) {
  const dir = join(ctx.root, ctx.config.plans.dir, "active");
  if (!existsSync(dir)) return [];
  return tryGit(["ls-files", "-co", "--exclude-standard", `${ctx.config.plans.dir}/active`], ctx.root)
    .split("\n")
    .filter((f) => f.endsWith(".md"))
    .map((file) => {
      const text = readText(ctx.root, file);
      const fm = parseFrontmatter(text) ?? {};
      const logs = [...text.matchAll(/^### (\d{4}-\d{2}-\d{2}.*)$/gm)].map((m) => m[1]);
      return { file, status: fm.status, branch: fm.branch, lastLog: logs.at(-1) };
    });
}

export function approvedPlanFor(ctx, branch) {
  return activePlans(ctx).find((p) => p.branch === branch && ctx.config.plans.gateStatuses.includes(p.status));
}
