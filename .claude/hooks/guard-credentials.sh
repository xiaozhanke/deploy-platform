#!/usr/bin/env bash
# PreToolUse hook：拦截对认证 / TLS 凭据文件的写入。
# 命中则 exit 2 阻止本次工具调用，并把原因回传给 Claude（要改请人工操作）。
set -uo pipefail

# 从 stdin 的 hook 负载里取出 tool_input.file_path
payload="$(cat)"
file_path="$(printf '%s' "$payload" | node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>{try{const j=JSON.parse(s);process.stdout.write(String((j.tool_input&&j.tool_input.file_path)||""))}catch(e){process.stdout.write("")}})' 2>/dev/null)"

[ -n "$file_path" ] || exit 0

base="$(basename "$file_path")"
case "$base" in
  jwt-key.json|*.p12|*.jks|*.keystore)
    echo "⛔ 已拦截对凭据文件 ${base} 的写入：这是 JWT 签名密钥或 TLS keystore，自动改写会破坏认证 / HTTPS。" >&2
    echo "如确需修改，请人工编辑该文件，或在对话中明确说明意图后由用户手动操作。" >&2
    exit 2
    ;;
esac
exit 0
