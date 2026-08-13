# skriptit

Small directory and file bookmark commands for
[babashka](https://babashka.org/):

- `dirb` saves and resolves directory bookmarks.
- `fileb` saves and resolves file bookmarks.

## Setup

Install babashka, clone this repository, and add two lines to `.zshrc`:

```zsh
export PATH="$HOME/src/skriptit/bin:$PATH"
source "$HOME/src/skriptit/shell/skriptit.zsh"   # after compinit
```

`bin/skriptit` is the only executable, and `shell/skriptit.zsh` holds the
interactive helpers and their tab completions. The completions register only
when `compinit` has already run, which is why the file is sourced late.

## Usage

```sh
skriptit help
skriptit help dirb

skriptit dirb save work
skriptit dirb read work

skriptit fileb save notes ~/notes.md
skriptit fileb read notes
```

Both bookmark types support `list`, `read`, `save`, `remove`, `rename`,
`validate`, `fix`, and `autocomplete`.

The low-level commands only print paths and update bookmark databases. The
stateful, interactive operations are zsh functions layered on top:

- `s <group> <command> ...` — short form of `skriptit`.
- `ss <name>` — save the current directory as a `dirb` bookmark.
- `sgo <name>` — change the current shell to a bookmarked directory.
- `svim <name> [args]` — open a bookmarked file with Vim.
- `sless <name> [args]` — read a bookmarked file with less.
- `scode <name> [args]` — open a bookmarked file with VS Code.

```console
$ cd ~/work/customer-portal
$ ss portal
$ cd /tmp
$ sgo portal

$ skriptit fileb save zshrc ~/.zshrc
$ svim zshrc
$ sless zshrc --chop-long-lines
```

`sgo` must be a shell function because an external process cannot change its
parent shell's directory. The file openers stay shell functions for the same
division of labor: `skriptit fileb read` supplies the path, the shell decides
which application and arguments to use.

## Local data

Bookmarks are machine-local state, never stored in the Git checkout. The
database directory is the first of:

1. `$SKRIPTIT_DATA_DIR`
2. `$XDG_DATA_HOME/skriptit`
3. `$HOME/.local/share/skriptit`

`dirb.edn` and `fileb.edn` there are plain EDN maps of bookmark name to
absolute path, readable and editable by hand.

## Private commands

The CLI is a function over a vector of `[prefix namespace]` pairs, so a
private checkout extends it with ordinary code — no hooks in this repository.
Given a sibling checkout layout:

```text
private/
├── bb.edn
└── src/my/
    ├── main.clj
    └── git.clj
skriptit/
```

`private/bb.edn` puts both source trees on the classpath (paths resolve
relative to the bb.edn):

```edn
{:paths ["src" "../skriptit/src"]}
```

`private/src/my/main.clj` passes an extended vector to the public dispatcher:

```clojure
(ns my.main
  (:require [skriptit.cli :as cli]))

(def groups
  (conj cli/default-groups ["git" 'my.git]))

(defn -main [& args]
  (let [status (cli/dispatch! args groups)]
    (when-not (zero? status)
      (System/exit status))))
```

Command namespaces are plain functions tagged with metadata:

```clojure
(ns my.git)

(defn status!
  "Show the current Git status."
  {:skriptit/cmd "status"}
  []
  (println "private implementation"))
```

Point the `s` function at the private build and everything follows —
`s git status` runs, `s help` lists the group, and tab completion picks up
the private commands because the completion functions query the command
being completed:

```zsh
# in .zshrc, below the shell/skriptit.zsh source line
s() {
  bb --config "$HOME/src/private/bb.edn" -m my.main "$@"
}
```

The order matters: `shell/skriptit.zsh` defines `s` itself, so a private
definition placed above the `source` line is overwritten and `s git status`
answers `Unknown command group: git`.

Duplicate group prefixes are rejected instead of silently shadowing a public
command, and so are `help` and `autocomplete`: the dispatcher answers those
itself, so a group behind one would be listed but never reachable.

## Development

```sh
bb test
clj-kondo --lint src test
```
