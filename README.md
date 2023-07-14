# skriptit
Personal CLI utilities written with [babashka](https://github.com/babashka/babashka)

Example `.zshrc`

```sh
# s(kriptit)
## "s config"
## "s config code"
s() {
    bb --config $BB_SCRIPTS/bb.edn $@
}

# autocomplete all skriptit tasks on shell startup
_s_tasks() {
    local matches=(`s tasks |tail -n +3 |cut -f1 -d ' '`)
    compadd -a matches
}
compdef _s_tasks s

# directory saving/changing utility, inspired by similarly named bash utility
## "s dirb save docs" (or use alias to save typing)
## "go docs"
alias ss='s dirb save'
go() {
    cd $(s dirb "$1")
}

# autocomplete all dirb entries on shell startup
_s_dirb() {
    local matches=(`s dirb autocomplete |cut -f1 -d ' '`)
    compadd -a matches
}
compdef _s_dirb go
```
