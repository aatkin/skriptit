# skriptit

Small directory and file bookmark commands for
[babashka](https://babashka.org/).

`skriptit` deliberately contains only two public command groups:

- `dirb` saves and resolves directory bookmarks.
- `fileb` saves and resolves file bookmarks.

Private commands can be added from another checkout through an extension
manifest. No private source or personal bookmark data needs to be copied into
this repository.

## Requirements and setup

Install babashka, clone this repository, and put `bin` on `PATH`:

```sh
git clone https://github.com/YOUR-NAME/skriptit.git ~/src/skriptit
export PATH="$HOME/src/skriptit/bin:$PATH"
```

Then run:

```sh
skriptit help
skriptit help dirb

skriptit dirb save work
skriptit dirb read work

skriptit fileb save notes ~/notes.md
skriptit fileb read notes
```

`skriptit` is the only executable. Every command is reached as
`skriptit <group> <command>`, so a new command group needs no new entry in
`bin`.

## Recommended shell workflow

The low-level commands print paths and update bookmark databases. Small shell
functions can build the stateful, interactive operations on top: changing the
current shell's directory or opening a file in a chosen program.

The repository packages the recommended zsh helpers. Source them after putting
`bin` on `PATH`:

```zsh
source "$HOME/src/skriptit/shell/skriptit.zsh"
```

This provides:

- `s <group> <command> ...` — short form of `skriptit`.
- `ss <name>` — save the current directory as a `dirb` bookmark.
- `sgo <name>` — change the current shell to a bookmarked directory.
- `svim <name> [args]` — open a bookmarked file with Vim.
- `sless <name> [args]` — read a bookmarked file with less.
- `scode <name> [args]` — open a bookmarked file with VS Code.

For example:

```console
$ cd ~/work/customer-portal
$ ss portal
$ cd /tmp
$ sgo portal

$ skriptit fileb save zshrc ~/.zshrc
$ svim zshrc
$ sless zshrc --chop-long-lines
$ scode zshrc --reuse-window
```

`sgo` must be a shell function because an external process cannot change its
parent shell's directory. The file-opening helpers intentionally remain shell
functions too: `skriptit fileb read` supplies the path, while the shell decides
which application and application-specific arguments to use.

## Zsh completion

The repository includes completions for `skriptit` and `s` in
`completions/_skriptit`, and for the `sgo`, `svim`, `sless`, and `scode`
helpers in `completions/_skriptit_shell`. mise installs both idempotently:

```sh
mise run setup-completions
exec zsh
```

oh-my-zsh is optional. The installer selects the destination in this order:

1. `$SKRIPTIT_ZSH_COMPLETIONS_DIR`, when explicitly set.
2. `$ZSH/completions` or `~/.oh-my-zsh/completions`, when oh-my-zsh exists.
3. `${XDG_DATA_HOME:-$HOME/.local/share}/zsh/site-functions` otherwise.

For the third option, add the directory to `$fpath` before `compinit` in
`.zshrc`:

```zsh
fpath=("${XDG_DATA_HOME:-$HOME/.local/share}/zsh/site-functions" $fpath)
autoload -Uz compinit
compinit
```

For a custom destination, likewise ensure it is on `$fpath` before `compinit`.

The completions call the same `autocomplete` commands the CLI exposes, so they
cover command groups from private extensions, bookmark names, and file paths
for `skriptit fileb save` without needing to be updated alongside them.

## Local data

Bookmarks are machine-local state and are never stored in the Git checkout.
The database directory is selected in this order:

1. `$SKRIPTIT_DATA_DIR`
2. `$XDG_DATA_HOME/skriptit`
3. `$HOME/.local/share/skriptit`

`dirb.edn` and `fileb.edn` are EDN maps of bookmark name to absolute path, so
they stay readable and editable by hand.

Both bookmark types support `list`, `read`, `save`, `remove`, `rename`,
`validate`, `fix`, `merge-defaults`, and `autocomplete`. Run
`skriptit help fileb` or `skriptit help dirb` for the exact interface.

Defaults can remain in a private repository and be merged explicitly:

```edn
{:dirb {"bootstrap" "$HOME/bootstrap"}
 :fileb {"hosts" "$HOME/.ssh/config"}}
```

```sh
skriptit dirb merge-defaults ~/bootstrap/private/skriptit/defaults.edn
skriptit fileb merge-defaults ~/bootstrap/private/skriptit/defaults.edn
```

## Private command namespaces

Set `$SKRIPTIT_EXTENSIONS` to a private EDN manifest:

```edn
{:paths ["src"]
 :commands [["git" my-skriptit.git]
            ["gh" my-skriptit.github]]}
```

Paths are relative to the manifest. Each registered namespace exposes ordinary
functions marked with the same metadata as the public commands:

```clojure
(ns my-skriptit.git)

(defn status!
  "Show the current Git status."
  {:skriptit/cmd "status"}
  []
  (println "private implementation"))
```

The commands then appear in `skriptit help` and run as
`skriptit git status`. A private namespace may depend on public APIs, but public
namespaces never depend on private code. Duplicate group prefixes are rejected
instead of silently overriding public commands.

## Development

```sh
bb test
clj-kondo --lint src test
mise run setup-completions
```
