# Hàm dùng chung cho git hook CareNest (POSIX sh, chạy được bằng sh của Git for Windows). ADR-0012.

cn_err() { printf '[CareNest] LỖI: %s\n' "$*" >&2; CN_ERRORS=$((CN_ERRORS + 1)); }
cn_warn() { [ "${CN_QUIET_WARN:-0}" = 1 ] || printf '[CareNest] cảnh báo: %s\n' "$*" >&2; }
CN_ERRORS=0

# Mã công việc (loại + số thứ tự) và mã Jira — AGENTS.md › Git
CN_WORK_RE='(FE|BE)-(FEAT|FIX)-[0-9]+'
CN_JIRA_RE='[A-Z][A-Z0-9]+-[0-9]+'

# Kiểm commit message theo AGENTS.md › Commit message. $1 = file chứa message.
cn_validate_msg() {
  msg=$(sed -e '/^# ------------------------ >8 ------------------------$/,$d' -e '/^#/d' "$1")
  header=$(printf '%s\n' "$msg" | sed -n '/[^[:space:]]/{p;q;}')
  [ -n "$header" ] || { cn_err "commit message rỗng"; return; }

  case "$header" in
    "Merge "* | "Revert \""*) return ;;
    "fixup! "* | "squash! "*) cn_warn "commit fixup/squash — nhớ squash trước khi push"; return ;;
  esac

  if ! printf '%s' "$header" | grep -Eq "^\[$CN_WORK_RE\] $CN_JIRA_RE: [^[:space:]]"; then
    cn_err "dòng đầu phải dạng '[<mã-công-việc>] <mã-jira>: <mô tả>' (vd. '[FE-FEAT-44] G94-181: add lesson plan form'), mã công việc ∈ FE-FEAT|BE-FEAT|FE-FIX|BE-FIX — đang là: $header"
  fi
  if printf '%s' "$header" | LC_ALL=C grep -q '[^ -~]'; then
    cn_err "dòng đầu phải tiếng Anh, chỉ ký tự ASCII"
  elif [ "${#header}" -gt 72 ]; then
    cn_err "dòng đầu dài ${#header} ký tự (tối đa 72)"
  fi
  case "$header" in *.) cn_err "dòng đầu không kết thúc bằng dấu chấm" ;; esac

  second=$(printf '%s\n' "$msg" | sed -n '2p')
  [ -z "$second" ] || cn_err "dòng 2 phải để trống (ngăn subject và body)"

  if printf '%s\n' "$msg" | grep -Eiq 'co-authored-by:.*(claude|anthropic|openai|chatgpt|copilot|codex|gemini|cursor)|generated (with|by) .*(claude|chatgpt|copilot|codex|gemini|ai)|noreply@anthropic\.com'; then
    cn_err "không ghi AI attribution (Co-Authored-By AI, 'Generated with ...')"
  fi
  if printf '%s\n' "$msg" | grep -q "$(printf '\360\237\244\226')"; then
    cn_err "không ghi AI attribution (biểu tượng robot)"
  fi
}

# Chỉ cảnh báo: nhánh hiện tại sai định dạng hoặc mã trong commit khác mã của nhánh. $1 = file chứa message.
cn_check_branch() {
  branch=$(git symbolic-ref --short -q HEAD) || return 0
  case "$branch" in
    main | dev) cn_warn "đang commit thẳng lên '$branch' — task làm trên nhánh riêng tạo từ dev"; return ;;
    release/*) return ;;
  esac
  if ! printf '%s' "$branch" | grep -Eq "^(feature/$CN_JIRA_RE-(FE|BE)-FEAT|fix/$CN_JIRA_RE-(FE|BE)-FIX)-[0-9]+-[a-z0-9]+(-[a-z0-9]+)*$"; then
    cn_warn "tên nhánh phải dạng feature|fix/<mã-jira>-<mã-công-việc>-<tên-luồng> (FEAT ⇒ feature, FIX ⇒ fix) — đang là: $branch"
    return
  fi
  # feature/G94-181-FE-FEAT-44-lesson-plan ⇒ "[FE-FEAT-44] G94-181"
  expected=$(printf '%s' "$branch" | sed -E 's#^[a-z]+/([A-Z][A-Z0-9]+-[0-9]+)-((FE|BE)-(FEAT|FIX)-[0-9]+)-.*#[\2] \1#')
  header=$(sed -e '/^# ------------------------ >8 ------------------------$/,$d' -e '/^#/d' "$1" | sed -n '/[^[:space:]]/{p;q;}')
  case "$header" in
    "$expected: "*) ;;
    "["*) cn_warn "mã trong commit khác nhánh '$branch' (mong đợi '$expected: ...') — mỗi commit chỉ thuộc task của nhánh" ;;
  esac
}
