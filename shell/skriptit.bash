# Convenience functions and completions for the skriptit bookmark workflow.
# Bash counterpart of skriptit.zsh. Source this file from .bashrc after
# putting skriptit's bin/ directory on PATH.

s() {
  command skriptit "$@"
}

ss() {
  command skriptit dirb save "$@"
}

sgo() {
  if (( $# != 1 )); then
    printf '%s\n' 'usage: sgo <directory-bookmark>' >&2
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
    printf '%s\n' "usage: s${opener} <file-bookmark> [${opener}-arguments]" >&2
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

# Offer each line of stdin that starts with the word being completed. Lines
# are whole candidates, shell-escaped the way they appear on the command line,
# so a bookmark name containing spaces completes as a single argument.
_skriptit_reply() {
  local cur="$1" line quoted
  COMPREPLY=()
  while IFS= read -r line; do
    [[ -n $line ]] || continue
    printf -v quoted '%q' "$line"
    [[ $quoted == "$cur"* ]] && COMPREPLY+=("$quoted")
  done
}

if type complete >/dev/null 2>&1; then
  # Ask the command being completed (${COMP_WORDS[0]}) for its groups and
  # commands, so a private build that calls dispatch! with extra groups and
  # redefines s gets those completed without touching this file.
  _skriptit() {
    local service="${COMP_WORDS[0]}"
    local cur="${COMP_WORDS[COMP_CWORD]}"

    if (( COMP_CWORD == 1 )); then
      _skriptit_reply "$cur" < <(printf '%s\n' help; "$service" autocomplete 2>/dev/null)
    elif (( COMP_CWORD == 2 )); then
      if [[ ${COMP_WORDS[1]} == help ]]; then
        _skriptit_reply "$cur" < <("$service" autocomplete 2>/dev/null)
      else
        _skriptit_reply "$cur" < <("$service" autocomplete "${COMP_WORDS[1]}" 2>/dev/null)
      fi
    elif (( COMP_CWORD == 3 )); then
      # Only the first argument names an existing bookmark: `rename` takes a
      # new, not-yet-existing key as its second.
      case "${COMP_WORDS[2]}" in
        read|remove|rename)
          _skriptit_reply "$cur" < <("$service" "${COMP_WORDS[1]}" autocomplete entries 2>/dev/null)
          ;;
      esac
    elif (( COMP_CWORD == 4 )) && [[ ${COMP_WORDS[1]} == fileb && ${COMP_WORDS[2]} == save ]]; then
      # Fall back to readline's own filename completion.
      compopt -o default
      COMPREPLY=()
    fi
  }
  complete -F _skriptit skriptit s

  # The helper is the command here rather than a CLI, so these ask skriptit
  # for the bookmark names directly.
  _skriptit_bookmarks() {
    COMPREPLY=()
    (( COMP_CWORD == 1 )) || return 0

    local group=fileb
    [[ ${COMP_WORDS[0]} == sgo ]] && group=dirb

    _skriptit_reply "${COMP_WORDS[COMP_CWORD]}" < <(command skriptit "$group" autocomplete entries 2>/dev/null)
  }
  complete -F _skriptit_bookmarks sgo svim sless scode
fi
