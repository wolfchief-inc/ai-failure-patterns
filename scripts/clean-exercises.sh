#!/usr/bin/env bash
#
# exercises/ 配下に演習で生成された成果物（src/ の Java コードや target/ のビルド出力）を
# 安全に掃除する。判定はシンプルに一つだけ:
#
#   Git が追跡しているファイルは絶対に消さない。消すのは未追跡ファイルだけ。
#
# これで requirements.md（追跡済み）は常に守られる。掃除後は空になった src/・target/
# などの空ディレクトリも取り除く（exercise ディレクトリ本体は残す）。
#
# 使い方:
#   scripts/clean-exercises.sh            # dry-run。何を消すか一覧するだけで削除しない
#   scripts/clean-exercises.sh --force    # 実際に削除する
#   scripts/clean-exercises.sh -h         # ヘルプ

set -euo pipefail

usage() {
  sed -n '2,20p' "$0" | sed 's/^# \{0,1\}//'
  exit 0
}

DRY_RUN=1
for arg in "$@"; do
  case "$arg" in
    -f|--force) DRY_RUN=0 ;;
    -h|--help)  usage ;;
    *) echo "error: 不明な引数: $arg" >&2; exit 2 ;;
  esac
done

# リポジトリのルートで動く。exercises/ が無ければ何もしない。
REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"
if [[ ! -d exercises ]]; then
  echo "error: exercises/ が見つかりません（$REPO_ROOT）" >&2
  exit 1
fi

# exercises/ 配下の全ファイルを走査し、Git 未追跡のものだけを削除対象に集める。
targets=()
while IFS= read -r -d '' f; do
  # 追跡済みなら残す（--error-unmatch は追跡外だと非ゼロ終了）
  if git ls-files --error-unmatch -- "$f" >/dev/null 2>&1; then
    continue
  fi
  # 念のための二重ガード: requirements.md と .gitkeep は絶対に消さない
  case "$(basename "$f")" in
    requirements.md|.gitkeep) continue ;;
  esac
  targets+=("$f")
done < <(find exercises -type f -print0)

if [[ ${#targets[@]} -eq 0 ]]; then
  echo "掃除対象なし。exercises/ はすでにクリーンです。"
else
  echo "削除対象の未追跡ファイル (${#targets[@]} 件):"
  printf '  %s\n' "${targets[@]}"
  if [[ $DRY_RUN -eq 1 ]]; then
    echo
    echo "これは dry-run です。実際に削除するには --force を付けてください。"
    exit 0
  fi
  printf '%s\0' "${targets[@]}" | xargs -0 rm -f
  echo "削除しました (${#targets[@]} 件)。"
fi

# 空になったディレクトリを取り除く（exercises 本体と exercise 各ディレクトリは残す）。
if [[ $DRY_RUN -eq 0 ]]; then
  find exercises -mindepth 2 -type d -empty -delete
fi
