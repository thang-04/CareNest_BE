# Hàm dùng chung cho git hook CareNest (POSIX sh, chạy được bằng sh của Git for Windows). ADR-0012.

cn_err() { printf '[CareNest] LỖI: %s\n' "$*" >&2; CN_ERRORS=$((CN_ERRORS + 1)); }
cn_warn() { [ "${CN_QUIET_WARN:-0}" = 1 ] || printf '[CareNest] cảnh báo: %s\n' "$*" >&2; }
CN_ERRORS=0

# Kiểm commit message theo AGENTS.md › Commit message. $1 = file chứa message.
cn_validate_msg() {
  msg=$(sed -e '/^# ------------------------ >8 ------------------------$/,$d' -e '/^#/d' "$1")
  header=$(printf '%s\n' "$msg" | sed -n '/[^[:space:]]/{p;q;}')
  [ -n "$header" ] || { cn_err "commit message rỗng"; return; }

  case "$header" in
    "Merge "* | "Revert \""*) return ;;
    "fixup! "* | "squash! "*) cn_warn "commit fixup/squash — nhớ squash trước khi push"; return ;;
  esac

  if ! printf '%s' "$header" | grep -Eq '^(feat|fix|refactor|test|docs|chore|build|ci)(\([a-z0-9][a-z0-9._/-]*\))?!?: [^[:space:]]'; then
    cn_err "dòng đầu phải dạng '<type>(<scope>): <subject>', type ∈ feat|fix|refactor|test|docs|chore|build|ci — đang là: $header"
  fi
  if printf '%s' "$header" | LC_ALL=C grep -q '[^ -~]'; then
    cn_err "dòng đầu phải tiếng Anh, chỉ ký tự ASCII"
  elif [ "${#header}" -gt 72 ]; then
    cn_err "dòng đầu dài ${#header} ký tự (tối đa 72)"
  fi
  case "$header" in *.) cn_err "dòng đầu không kết thúc bằng dấu chấm" ;; esac

  second=$(printf '%s\n' "$msg" | sed -n '2p')
  [ -z "$second" ] || cn_err "dòng 2 phải để trống (ngăn subject và body)"

  refs=$(printf '%s\n' "$msg" | grep -c '^Refs:')
  if [ "$refs" -gt 1 ]; then
    cn_err "chỉ 1 dòng 'Refs: <KEY>' mỗi commit (nhiều task ⇒ tách commit)"
  elif [ "$refs" -eq 1 ]; then
    printf '%s\n' "$msg" | grep -Eq '^Refs: [A-Z][A-Z0-9]+-[0-9]+$' || cn_err "footer phải dạng 'Refs: CN-123'"
  else
    cn_warn "thiếu footer 'Refs: <Jira key>' — bỏ qua nếu user xác nhận không có task"
  fi

  if printf '%s\n' "$msg" | grep -Eiq 'co-authored-by:.*(claude|anthropic|openai|chatgpt|copilot|codex|gemini|cursor)|generated (with|by) .*(claude|chatgpt|copilot|codex|gemini|ai)|noreply@anthropic\.com'; then
    cn_err "không ghi AI attribution (Co-Authored-By AI, 'Generated with ...')"
  fi
  if printf '%s\n' "$msg" | grep -q "$(printf '\360\237\244\226')"; then
    cn_err "không ghi AI attribution (biểu tượng robot)"
  fi
}
