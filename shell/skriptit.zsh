# Convenience functions and completions for the skriptit bookmark workflow.
# Source this file from .zshrc after putting skriptit's bin/ directory on
# PATH, and after compinit has run (the completion block needs compdef).

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

if (( $+functions[compdef] )); then
  # Ask the command being completed ($service) for its groups and commands, so
  # a private build that calls dispatch! with extra groups gets those completed
  # without touching this file.
  _skriptit() {
    local -a matches

    if (( CURRENT == 2 )); then
      matches=(help ${(f)"$($service autocomplete 2>/dev/null)"})
      _describe 'command group' matches
    elif (( CURRENT == 3 )); then
      if [[ "$words[2]" == help ]]; then
        matches=(${(f)"$($service autocomplete 2>/dev/null)"})
        _describe 'command group' matches
      else
        matches=(${(f)"$($service autocomplete "$words[2]" 2>/dev/null)"})
        _describe "$words[2] command" matches
      fi
    elif (( CURRENT == 4 )); then
      # Only the first argument names an existing bookmark: `rename` takes a
      # new, not-yet-existing key as its second.
      case "$words[3]" in
        read|remove|rename)
          matches=(${(f)"$($service "$words[2]" autocomplete entries 2>/dev/null)"})
          _describe "$words[2] bookmark" matches
          ;;
      esac
    elif (( CURRENT == 5 )) && [[ "$words[2]" == fileb && "$words[3]" == save ]]; then
      _files
    fi
  }
  compdef _skriptit skriptit s

  # $service is the helper here rather than a CLI, so these ask skriptit for
  # the bookmark names directly.
  _skriptit_bookmarks() {
    (( CURRENT == 2 )) || return 0

    local -a matches
    local group=fileb
    [[ "$service" == sgo ]] && group=dirb

    matches=(${(f)"$(command skriptit "$group" autocomplete entries 2>/dev/null)"})
    _describe "$group bookmark" matches
  }
  compdef _skriptit_bookmarks sgo svim sless scode
fi
