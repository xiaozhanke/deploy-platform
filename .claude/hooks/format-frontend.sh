#!/usr/bin/env bash
# PostToolUse hook：对 deploy-web/src 下刚被编辑的前端源码跑 Prettier。
# 只用 Prettier；绝不用 eslint --fix —— 本仓库 typed-lint 会删掉「看似多余实则
# 必要」的类型断言，反而让 vue-tsc 报错（见 CLAUDE.md）。
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
WEB_DIR="$REPO_ROOT/deploy-web"
PRETTIER_BIN="$WEB_DIR/node_modules/.bin/prettier"

# 从 stdin 的 hook 负载里取出 tool_input.file_path（用项目本就具备的 node 解析 JSON）
payload="$(cat)"
file_path="$(printf '%s' "$payload" | node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>{try{const j=JSON.parse(s);process.stdout.write(String((j.tool_input&&j.tool_input.file_path)||""))}catch(e){process.stdout.write("")}})' 2>/dev/null)"

[ -n "$file_path" ] || exit 0

# 相对路径按仓库根补全成绝对路径
case "$file_path" in
  /*) ;;
  *) file_path="$REPO_ROOT/$file_path" ;;
esac

# 仅处理 deploy-web/src 下、且是前端源码后缀的文件
case "$file_path" in
  "$WEB_DIR"/src/*) ;;
  *) exit 0 ;;
esac
case "$file_path" in
  *.vue|*.ts|*.mts|*.cts|*.scss|*.css) ;;
  *) exit 0 ;;
esac

# 文件可能已被删除/移动；prettier 未安装则静默跳过
[ -f "$file_path" ] || exit 0
[ -x "$PRETTIER_BIN" ] || exit 0

# prettier 会自动向上查找 deploy-web/.prettierrc.json
"$PRETTIER_BIN" --write "$file_path" >/dev/null 2>&1 || true
exit 0
