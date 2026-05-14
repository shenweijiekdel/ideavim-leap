# vim-leap

Full port of [leap.nvim](https://github.com/ggandor/leap.nvim) for IdeaVim.

**English** | [中文](./README_CN.md)

## Usage

Type a 2-character pattern — labels appear next to matches. Press a label to jump.

After `{char1}`, labels are already visible and **final** — they do not change when you type `{char2}`. By the time you decide which second character to type, the label for your target is already in front of you.

When the closest match is unlabeled (autojump), the cursor jumps there automatically. The remaining labels are safe to ignore — they won't conflict with any normal command and disappear on the next keypress.

## Features

- **2-char search** — `s{char1}{char2}` forward, `S{char1}{char2}` backward
- **Stable-label preview** — labels assigned per `{char2}` group after the first keystroke; never reassigned
- **Safe-label autojump** — jumps to the nearest match when all remaining matches fit the safe-label set
- **Label groups** — `Space` / `Backspace` cycles groups when matches exceed available labels; inactive groups show `·`
- **Traversal** — after autojump, `Enter` / `Backspace` steps through remaining matches
- **Repeat** — `Enter` at the start repeats the last search; `{char}<Enter>` jumps to the nearest `{char}` pair
- **Flit (f/F/t/T)** — single-char labelled motions (port of [flit.nvim](https://github.com/ggandor/flit.nvim)); `;` / `,` repeat
- **Till motions** — land one character before/after the match, like Vim's `t`/`T`
- **Equivalence classes** — `Space` matches any whitespace by default; fully configurable
- **Remote operation** — jump to a target, operate, return to origin
- **PSI structural selection** — labels PSI ancestor nodes; press a label to visually select that range
- **Multi-split search** — searches all visible editor splits simultaneously

## Quick setup (~/.ideavimrc)

```vim
" Sneak-style (recommended)
nmap s  <Action>(leap.forward)
nmap S  <Action>(leap.backward)
xmap s  <Action>(leap.forward)
xmap S  <Action>(leap.backward)

" Bidirectional (targets ordered by Euclidean distance from cursor)
"nmap s  <Action>(leap.anywhere)
"xmap s  <Action>(leap.anywhere)

" Flit — labelled f/F/t/T
nmap f  <Action>(leap.flit_f)
nmap F  <Action>(leap.flit_f_backward)
xmap f  <Action>(leap.flit_f)
xmap F  <Action>(leap.flit_f_backward)
nmap t  <Action>(leap.flit_t)
nmap T  <Action>(leap.flit_t_backward)
xmap t  <Action>(leap.flit_t)
xmap T  <Action>(leap.flit_t_backward)
nmap ;  <Action>(leap.flit_repeat)
nmap ,  <Action>(leap.flit_repeat_backward)

" PSI treesitter structural selection
nmap gs <Action>(leap.treesitter)
xmap gs <Action>(leap.treesitter)

" Till motions — z/Z conflict with fold; bind to a free key if desired:
"   nmap <key>  <Action>(leap.forward_till)
"   nmap <key>  <Action>(leap.backward_till)

" Remote leap — r conflicts with replace-char; bind to a free key if desired:
"   nmap <key>  <Action>(leap.remote)
```

## All actions

| Action ID | Description |
|---|---|
| `leap.forward` | Forward 2-char leap from cursor |
| `leap.backward` | Backward 2-char leap from cursor |
| `leap.anywhere` | Bidirectional leap in current window; targets sorted by Euclidean distance |
| `leap.forward_till` | Forward leap, land one char before the match |
| `leap.backward_till` | Backward leap, land one char after the match |
| `leap.flit_f` | Flit-style f: 1-char forward jump with labels |
| `leap.flit_f_backward` | Flit-style F: 1-char backward jump with labels |
| `leap.flit_t` | Flit-style t: 1-char forward till with labels |
| `leap.flit_t_backward` | Flit-style T: 1-char backward till with labels |
| `leap.flit_repeat` | Repeat last flit jump forward (`;`) |
| `leap.flit_repeat_backward` | Repeat last flit jump backward (`,`) |
| `leap.treesitter` | Label PSI ancestors; press label to select structural range |
| `leap.remote` | Jump to target, execute motion, return to origin |

## Key behavior during a session

| Key | Phase | Action |
|---|---|---|
| `<Esc>` | any | Cancel and exit |
| char1 | CHAR1 | Start search, show preview |
| char2 | CHAR2 | Filter matches, autojump or show labels |
| `<Backspace>` | CHAR2 | Erase char1, back to CHAR1 |
| `<Enter>` | CHAR1 | Repeat last search |
| `<Enter>` | CHAR2 | Shortcut: jump to nearest char1-only match |
| `<Enter>` | SELECT (after autojump) | Start traversal forward |
| `<Enter>` | SELECT (no autojump) | Jump to nearest labeled target |
| `<Enter>` | TRAVERSE | Advance to next match |
| `<Backspace>` | SELECT (after autojump) | Start traversal backward |
| `<Backspace>` | SELECT (label groups) | Previous label group |
| `<Backspace>` | TRAVERSE | Step back to previous match |
| `Space` | SELECT | Next label group |
| label char | SELECT | Jump to the labeled target |
| any other key | SELECT (after autojump) | Accept position, re-feed key |
| any other key | TRAVERSE | Exit traversal, re-feed key |

## Configuration

`Settings → Tools → vim-leap`

| Option | Default | Description |
|---|---|---|
| Labels | `sfnjklh…` | Characters used for jump labels |
| Safe labels | `sfnut/SFNLHMUGTZ?` | Autojump is triggered when all remaining matches fit this set |
| Equivalence classes | `" \t\r\n"` | Characters treated as interchangeable during search |
| Max traversal targets | `10` | Number of upcoming matches highlighted in traversal mode |
| Show char1 preview | ✓ | Highlight potential matches after first character |
| Search across splits | ✓ | Search all visible editor splits simultaneously |
| Always case-sensitive | ✗ | Force case-sensitive for all searches |
| Label / match / nearest colors | leap.nvim defaults | Customizable color pickers |

## Development

```bash
./gradlew buildPlugin   # produces build/distributions/vim-leap-*.zip
./gradlew runIde        # launch sandbox IDEA with the plugin loaded
```

Requires **IntelliJ IDEA 2023.1+** and **IdeaVim** installed.

## Acknowledgements

Inspired by and faithfully porting [leap.nvim](https://github.com/ggandor/leap.nvim) by [@ggandor](https://github.com/ggandor).
This is an independent reimplementation for the IntelliJ Platform + IdeaVim.
No source code from the original project was copied or translated.

## License

[MIT](./LICENSE)
