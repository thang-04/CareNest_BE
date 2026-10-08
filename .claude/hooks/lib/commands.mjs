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

// Bỏ global option của git (`-C path`, `-c k=v`, ...) để lấy subcommand; `-C` ⇒ lệnh chạy ở repo khác
function gitParts(toks) {
  let i = 1;
  const configs = [];
  let otherRepo = false;
  while (i < toks.length && toks[i].startsWith("-")) {
    if (toks[i] === "-C" || toks[i] === "-c") {
      if (toks[i] === "-c") configs.push(toks[i + 1] ?? "");
      else otherRepo = true;
      i += 2;
    } else i++;
  }
  return { sub: toks[i], args: toks.slice(i + 1), configs, otherRepo };
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

// Mã task (AGENTS.md › Git): commit/PR `[<mã-công-việc>] <mã-jira>: <mô tả>`, nhánh `feature|fix/<mã-jira>-<mã-công-việc>-<tên-luồng>`
const JIRA = "[A-Z][A-Z0-9]+-\\d+";
const TASK_HEADER = new RegExp(`^\\[((?:FE|BE)-(?:FEAT|FIX)-\\d+)\\] (${JIRA}): \\S`);
const TASK_BRANCH = new RegExp(`^(?:feature/(${JIRA})-((?:FE|BE)-FEAT-\\d+)|fix/(${JIRA})-((?:FE|BE)-FIX-\\d+))-[a-z0-9]+(?:-[a-z0-9]+)*$`);
const EXEMPT_HEADER = /^(Merge |Revert "|fixup! |squash! )/;
const FORMAT = '"[<mã-công-việc>] <mã-jira>: <mô tả>"';
const ASK_TASK = 'chưa có mã task ⇒ hỏi user "Thay đổi này thuộc task Jira nào (mã Jira + mã công việc, vd. G94-181 / FE-FEAT-44)?" rồi chờ trả lời, không tự đoán';

// "[FE-FEAT-44] G94-181" lấy từ tên nhánh task; nhánh khác ⇒ null
export function branchTask(branch) {
  const m = TASK_BRANCH.exec(branch ?? "");
  return m ? `[${m[2] ?? m[4]}] ${m[1] ?? m[3]}` : null;
}

// Giá trị cờ: `--long x`, `--long=x`, `-s x`, cụm `-as x`; không có ⇒ null
function flagValue(args, long, short) {
  for (let i = 0; i < args.length; i++) {
    const a = args[i];
    if (a === long || a === short || (/^-[a-zA-Z]+$/.test(a) && a.endsWith(short.slice(1)))) return args[i + 1] ?? "";
    if (a.startsWith(`${long}=`)) return a.slice(long.length + 1);
  }
  return null;
}

// Dòng đầu message; heredoc `"$(cat <<'EOF' ... EOF)"` thì lấy phần thân
function firstLine(message) {
  const heredoc = /^\$\(cat <<-?\s*['"]?(\w+)['"]?\n([\s\S]*?)\n\s*\1\s*\)$/.exec(message.trim());
  return (heredoc ? heredoc[2] : message).split("\n").map((l) => l.trim()).find(Boolean) ?? "";
}

// Agent không tự đoán mã task: sai định dạng/lệch nhánh ⇒ deny (agent phải hỏi user); hợp lệ ⇒ ask kèm mã để user soát
function commitReasons(args, { branch, readFile }) {
  const task = branchTask(branch);
  const source = task ? `nhánh thuộc task ${task}` : ASK_TASK;
  const where = ["main", "dev"].includes(branch) ? `; đang ở "${branch}" — task làm trên nhánh riêng tạo từ dev, xác nhận user muốn commit ở đây` : "";
  let message = flagValue(args, "--message", "-m");
  if (message === null) {
    const file = flagValue(args, "--file", "-F");
    message = file && readFile ? readFile(file) : null;
  }
  if (message === null) return { ask: [`git commit cần user cho phép rõ; message phải dạng ${FORMAT} — ${source}${where}`] };
  const header = firstLine(message);
  if (EXEMPT_HEADER.test(header)) return { ask: [`git commit cần user cho phép rõ trong tin nhắn hiện tại${where}`] };
  const m = TASK_HEADER.exec(header);
  if (!m) return { deny: [`commit message phải dạng ${FORMAT} (AGENTS.md › Commit message), đang là "${header}" — ${source}`] };
  const used = `[${m[1]}] ${m[2]}`;
  if (task && used !== task) return { deny: [`mã ${used} khác task của nhánh (${task}) — mỗi commit chỉ thuộc task của nhánh; hỏi user trước khi commit`] };
  if (task) return { ask: [`git commit cần user cho phép rõ — task ${used} (khớp tên nhánh)`] };
  return { ask: [`git commit cần user cho phép rõ — mã ${used} không có trong tên nhánh "${branch || "?"}": chỉ commit khi user đã cung cấp đúng mã này${where}`] };
}

// PR nhánh task gộp vào dev (mặc định GitHub là main), chỉ release/* gộp vào main; tiêu đề cùng định dạng commit
function prCreateReasons(args, { branch }) {
  const head = flagValue(args, "--head", "-H") ?? branch ?? "";
  const base = flagValue(args, "--base", "-B");
  const title = flagValue(args, "--title", "-t");
  const task = branchTask(head);
  const deny = [];
  if (!head.startsWith("release/") && (base ?? "main") === "main") deny.push("PR của nhánh task gộp vào dev — thêm `--base dev`; chỉ release/* được gộp vào main");
  if (title !== null && !head.startsWith("release/")) {
    const m = TASK_HEADER.exec(title.trim());
    if (!m) deny.push(`tiêu đề PR phải dạng ${FORMAT}, đang là "${title.trim()}" — ${task ? `nhánh thuộc task ${task}` : ASK_TASK}`);
    else if (task && `[${m[1]}] ${m[2]}` !== task) deny.push(`tiêu đề PR mang mã khác task của nhánh (${task}) — hỏi user`);
  }
  if (deny.length) return { deny };
  return { ask: ["gh pr create cần user cho phép; mô tả PR gồm link task Jira, các thay đổi, phần kiểm thử đã làm"] };
}

// Đích push là main (refspec main/…:main, hoặc push nhánh hiện tại khi đang ở main)
function pushesMain(args, branch) {
  const refspecs = args.filter((a) => !a.startsWith("-")).slice(1);
  const dest = (r) => r.replace(/^\+/, "").split(":").pop().replace(/^refs\/heads\//, "");
  return refspecs.some((r) => dest(r) === "main") || (branch === "main" && (refspecs.length === 0 || refspecs.includes("HEAD")));
}

function classifyGit(toks, repoEnv) {
  const { sub, args, configs, otherRepo } = gitParts(toks);
  // Nhánh/đường dẫn của repo hiện tại không áp cho `git -C <repo khác>`
  const env = otherRepo ? {} : repoEnv;
  const reasons = { deny: [], ask: [] };
  if (args.includes("--no-verify") || (sub === "commit" && commitSkipsHooks(args))) reasons.deny.push("không bỏ qua hook (`--no-verify`/`-n`)");
  if (configs.some((c) => /^core\.hooksPath=/i.test(c))) reasons.deny.push("không đổi core.hooksPath để lách hook");
  const has = (...flags) => args.some((a) => flags.includes(a));
  switch (sub) {
    case "commit": {
      const r = commitReasons(args, env);
      reasons.deny.push(...(r.deny ?? []));
      reasons.ask.push(...(r.ask ?? []));
      break;
    }
    case "push":
      reasons.ask.push(has("--force", "-f", "--force-with-lease", "--delete") || args.some((a) => a.startsWith("+")) ? "push --force/--delete là lệnh phá hủy" : "git push cần user cho phép");
      if (pushesMain(args, env.branch)) reasons.ask.push("cấm push trực tiếp lên main (AGENTS.md › Git) — chỉ làm khi user cho phép ngoại lệ rõ");
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
// env: {branch: nhánh hiện tại, readFile(path): nội dung file message của `commit -F` hoặc null}
export function classify(command, env = {}) {
  const deny = [];
  const ask = [];
  for (const seg of segments(String(command ?? ""))) {
    const toks = tokens(seg);
    const exe = exeName(toks[0]);
    if (exe === "git") {
      const r = classifyGit(toks, env);
      deny.push(...r.deny);
      ask.push(...r.ask);
    } else if (exe === "gh" && toks[1] === "pr" && toks[2] === "create") {
      const r = prCreateReasons(toks.slice(3), env);
      deny.push(...(r.deny ?? []));
      ask.push(...(r.ask ?? []));
    } else if (exe === "gh" && toks[1] === "pr" && ["merge", "close"].includes(toks[2])) {
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
