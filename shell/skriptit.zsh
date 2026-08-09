# Convenience functions for the bookmark-oriented skriptit workflow.
# Source this file from .zshrc after putting skriptit's bin/ directory on PATH.

s() {
  command skriptit "$@"
}

ss() {
  command skriptit dirb save "$@"
}

sgo() {
  if (( $# != 1 )); then
    print -u2 'usage: sgo <directory-bookmark>'
    return 2
  fi

  local destination
  destination="$(command skriptit dirb read "$1")" || return
  builtin cd -- "$destination"
}

_skriptit_open_file() {
  local opener="$1"
  shift

  if (( $# < 1 )); then
    print -u2 "usage: s${opener} <file-bookmark> [${opener}-arguments]"
    return 2
  fi

  local bookmark="$1"
  shift
  local file_path
  file_path="$(command skriptit fileb read "$bookmark")" || return
  command "$opener" "$@" "$file_path"
}

svim() {
  _skriptit_open_file vim "$@"
}

sless() {
  _skriptit_open_file less "$@"
}

scode() {
  _skriptit_open_file code "$@"
}
