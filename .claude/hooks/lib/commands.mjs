// Phân loại lệnh Bash/PowerShell theo quy tắc git chung của CareNest (AGENTS.md › Git) + chặn đọc secret.
// Heuristic theo token: đủ cho lệnh agent thường gõ; git hook là lưới thứ hai.

// Tách segment theo && || ; | newline & — tôn trọng nháy đơn/kép
export function segments(command) {
  const out = [];
  let cur = "";
  let quote = null;
  for (let i = 0; i < command.length; i++) {
    const c = command[i];
    if (quote) {
      if (c === quote) quote = null;
      cur += c;
    } else if (c === '"' || c === "'") {
      quote = c;
      cur += c;
    } else if (c === ";" || c === "\n" || c === "|" || c === "&") {
      if (cur.trim()) out.push(cur.trim());
      cur = "";
      if ((c === "|" || c === "&") && command[i + 1] === c) i++;
    } else cur += c;
  }
  if (cur.trim()) out.push(cur.trim());
  return out;
}

export function tokens(segment) {
  const out = [];
  for (const m of segment.matchAll(/"([^"]*)"|'([^']*)'|(\S+)/g)) out.push(m[1] ?? m[2] ?? m[3]);
  // Bỏ tiền tố không đổi ý nghĩa lệnh: VAR=x, sudo, command, env, time
  while (out.length && (/^[A-Za-z_][\w]*=/.test(out[0]) || ["sudo", "command", "env", "time"].includes(out[0]))) out.shift();
  return out;
}

const exeName = (t) => (t ?? "").split(/[\\/]/).pop().replace(/\.exe$/i, "").toLowerCase();

const ENV_FILE = /^\.env(\.(?!example$|sample$|template$)[\w.-]+)?$/;
const KEY_FILE = /\.(pem|key|p12|pfx|jks)$|^id_(rsa|ed25519)/;
const READERS = new Set(
  "cat type more less head tail bat nl od xxd strings base64 get-content gc select-string sls findstr grep rg awk sed cut sort diff cp copy mv copy-item scp".split(" "),
);

const isSecretPath = (t) => {
  const base = t.replace(/[<>]/g, "").split(/[\\/]/).pop();
  return ENV_FILE.test(base) || KEY_FILE.test(base);
};

// Bỏ global option của git (`-C path`, `-c k=v`, ...) để lấy subcommand
function gitParts(toks) {
  let i = 1;
  const configs = [];
  while (i < toks.length && toks[i].startsWith("-")) {
    if (toks[i] === "-C" || toks[i] === "-c") {
      if (toks[i] === "-c") configs.push(toks[i + 1] ?? "");
      i += 2;
    } else i++;
  }
  return { sub: toks[i], args: toks.slice(i + 1), configs };
}

// `commit -nm "x"`: cụm cờ ngắn chứa n là --no-verify; token sau cụm kết thúc bằng m/F/c/C là giá trị
function commitSkipsHooks(args) {
  for (let i = 0; i < args.length; i++) {
    const a = args[i];
    if (/^-[a-zA-Z]+$/.test(a)) {
      if (a.includes("n")) return true;
      if (/[mFcC]$/.test(a)) i++;
    }
  }
  return false;
}

const BRANCH_QUERY = /^-(a|r|l|v|vv|d|D|m|M|c|C)$|^--(list|all|remotes|show-current|contains|merged|no-merged|delete|move|copy|format|sort|points-at)/;

function classifyGit(toks) {
  const { sub, args, configs } = gitParts(toks);
  const reasons = { deny: [], ask: [] };
  if (args.includes("--no-verify") || (sub === "commit" && commitSkipsHooks(args))) reasons.deny.push("không bỏ qua hook (`--no-verify`/`-n`)");
  if (configs.some((c) => /^core\.hooksPath=/i.test(c))) reasons.deny.push("không đổi core.hooksPath để lách hook");
  const has = (...flags) => args.some((a) => flags.includes(a));
  switch (sub) {
    case "commit":
      reasons.ask.push("git commit cần user cho phép rõ trong tin nhắn hiện tại");
      break;
    case "push":
      reasons.ask.push(has("--force", "-f", "--force-with-lease", "--delete") || args.some((a) => a.startsWith("+")) ? "push --force/--delete là lệnh phá hủy" : "git push cần user cho phép");
      break;
    case "checkout":
      if (has("-b", "-B")) reasons.ask.push("tạo branch cần user cho phép");
      if (has("--") || has(".")) reasons.ask.push("checkout -- / . hủy thay đổi chưa commit");
      break;
    case "switch":
      if (has("-c", "-C", "--create")) reasons.ask.push("tạo branch cần user cho phép");
      break;
    case "branch":
      if (has("-D", "--delete") && has("--force")) reasons.ask.push("xóa branch cưỡng bức");
      else if (has("-D")) reasons.ask.push("branch -D là lệnh phá hủy");
      else if (args.some((a) => !a.startsWith("-")) && !args.some((a) => BRANCH_QUERY.test(a))) reasons.ask.push("tạo branch cần user cho phép");
      break;
    case "worktree":
      if (args[0] === "add") reasons.ask.push("tạo worktree/branch cần user cho phép");
      break;
    case "reset":
      if (has("--hard")) reasons.ask.push("reset --hard hủy thay đổi");
      break;
    case "rebase":
    case "filter-branch":
      reasons.ask.push(`${sub} viết lại lịch sử`);
      break;
    case "clean":
      if (args.some((a) => /^-[a-zA-Z]*f/.test(a) || a === "--force")) reasons.ask.push("clean -f xóa file untracked");
      break;
    case "restore":
      if (!has("--staged") || has("--worktree", "-W")) reasons.ask.push("restore hủy thay đổi trong working tree");
      break;
    case "stash":
      if (["drop", "clear"].includes(args[0])) reasons.ask.push(`stash ${args[0]} mất dữ liệu`);
      break;
    case "update-ref":
      if (has("-d")) reasons.ask.push("update-ref -d xóa ref");
      break;
    case "config":
      if (args.some((a) => /^core\.hooksPath$/i.test(a))) reasons.ask.push("đổi/bỏ core.hooksPath");
      break;
  }
  return reasons;
}

// Kết quả xấu nhất của mọi segment: {decision: "deny"|"ask"|null, reasons: [...]}
export function classify(command) {
  const deny = [];
  const ask = [];
  for (const seg of segments(String(command ?? ""))) {
    const toks = tokens(seg);
    const exe = exeName(toks[0]);
    if (exe === "git") {
      const r = classifyGit(toks);
      deny.push(...r.deny);
      ask.push(...r.ask);
    } else if (exe === "gh" && toks[1] === "pr" && ["create", "merge", "close"].includes(toks[2])) {
      ask.push(`gh pr ${toks[2]} cần user cho phép`);
    }
    const redirectsSecret = /<\s*\S*\.env\b(?!\.example)/.test(seg);
    if ((READERS.has(exe) && toks.slice(1).some(isSecretPath)) || redirectsSecret) {
      deny.push("không đọc .env/khóa bí mật — dùng .env.example; cần giá trị thì user tự kiểm tra");
    }
  }
  if (deny.length) return { decision: "deny", reasons: [...new Set(deny)] };
  if (ask.length) return { decision: "ask", reasons: [...new Set(ask)] };
  return { decision: null, reasons: [] };
}
