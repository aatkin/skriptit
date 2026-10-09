# Changelog

Notable changes to skriptit, newest first.

## 2026-10-09

### Added

- An unknown command group or command now suggests the closest name:
  `skriptit dirv` answers "Did you mean `dirb`?". A name within two edits
  qualifies, as does one the typed word starts with (`dias-dev` suggests
  `dias`), so private groups get suggestions too.

## 2026-09-28

### Added

- Bash shell integration in `shell/skriptit.bash`, the counterpart of
  `shell/skriptit.zsh`. Source it from `.bashrc` for the same `s`, `ss`,
  `sgo`, `svim`, `sless`, and `scode` helpers.
- Bash tab completion for command groups, commands, and bookmark names. Like
  the zsh completion, it asks the command being completed, so a private build
  behind a redefined `s` gets its own groups completed. Bookmark names are
  shell-escaped, so a name containing spaces completes as one argument.
- README setup instructions for bash.
