# vim-leap

[leap.nvim](https://github.com/ggandor/leap.nvim) 的完整 IdeaVim 移植版。

[English](./README.md) | **中文**

## 用法

输入 2 个字符 —— 匹配位置旁边出现 label，按 label 跳转。

输入 `{char1}` 后，label 已经可见且**最终确定** —— 输入 `{char2}` 时不会重新分配。当你决定输入哪个第二字符时，目标的 label 已经摆在那里了。

当最近的匹配没有 label（自动跳转），光标直接跳过去。剩余的 label 可以安全忽略 —— 不会与任何常规命令冲突，下次按键时自动消失。

## 特性

- **双字符跳转** — `s{char1}{char2}` 向前，`S{char1}{char2}` 向后
- **稳定 label 预览** — 第一次按键后按 `{char2}` 分组分配 label，之后不再重新计算
- **安全 label 自动跳转** — 剩余匹配能被安全 label 集合覆盖时自动跳到最近匹配
- **Label 分组** — 匹配数超过 label 数时 `Space` / `Backspace` 切组；非当前组显示 `·`
- **遍历模式** — 自动跳转后 `Enter` / `Backspace` 逐步步进剩余匹配
- **重复搜索** — 开始时按 `Enter` 重复上次搜索；`{char}<Enter>` 跳到最近的同字符对
- **Flit（f/F/t/T）** — 单字符带 label 的跳转（移植自 [flit.nvim](https://github.com/ggandor/flit.nvim)）；`;` / `,` 重复
- **Till 模式** — 落点在匹配前/后一字符，类似 Vim 的 `t`/`T`
- **等价字符类** — 默认空格匹配任意空白字符；可自定义
- **远程操作** — 跳到目标、执行操作、返回原位
- **PSI 结构化选择** — 标注 PSI 祖先节点，按 label 可视化选中对应范围
- **多分屏搜索** — 同时搜索所有可见编辑器分屏

## 快速配置（~/.ideavimrc）

```vim
" Sneak 风格（推荐）
nmap s  <Action>(leap.forward)
nmap S  <Action>(leap.backward)
xmap s  <Action>(leap.forward)
xmap S  <Action>(leap.backward)

" 双向搜索（按与光标的欧氏距离排序）
"nmap s  <Action>(leap.anywhere)
"xmap s  <Action>(leap.anywhere)

" Flit — 带 label 的 f/F/t/T
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

" PSI 结构化选择
nmap gs <Action>(leap.treesitter)
xmap gs <Action>(leap.treesitter)

" Till 模式 — z/Z 与折叠冲突，可绑定到空闲按键：
"   nmap <key>  <Action>(leap.forward_till)
"   nmap <key>  <Action>(leap.backward_till)

" 远程 leap — r 与 replace-char 冲突，可绑定到空闲按键：
"   nmap <key>  <Action>(leap.remote)
```

## 全部 Action

| Action ID | 说明 |
|---|---|
| `leap.forward` | 从光标向前双字符跳转 |
| `leap.backward` | 从光标向后双字符跳转 |
| `leap.anywhere` | 当前窗口双向跳转，按欧氏距离排序 |
| `leap.forward_till` | 向前跳转，落在匹配前一字符 |
| `leap.backward_till` | 向后跳转，落在匹配后一字符 |
| `leap.flit_f` | Flit 风格 f：单字符向前跳转，显示 label |
| `leap.flit_f_backward` | Flit 风格 F：单字符向后跳转，显示 label |
| `leap.flit_t` | Flit 风格 t：单字符向前 till，显示 label |
| `leap.flit_t_backward` | Flit 风格 T：单字符向后 till，显示 label |
| `leap.flit_repeat` | 同方向重复上次 flit 跳转（`;`） |
| `leap.flit_repeat_backward` | 反方向重复上次 flit 跳转（`,`） |
| `leap.treesitter` | 标注 PSI 祖先节点，按 label 选中结构范围 |
| `leap.remote` | 跳到目标，执行 motion，返回原位 |

## 会话期间按键行为

| 按键 | 阶段 | 动作 |
|---|---|---|
| `<Esc>` | 任意 | 取消并退出 |
| char1 | CHAR1 | 开始搜索，显示预览（含稳定 label） |
| char2 | CHAR2 | 过滤匹配，自动跳转或显示 label |
| `<Backspace>` | CHAR2 | 清除 char1，返回 CHAR1 |
| `<Enter>` | CHAR1 | 重复上次搜索 |
| `<Enter>` | CHAR2 | 快捷：跳到最近的单字符匹配 |
| `<Enter>` | SELECT（自动跳转后） | 开始向前遍历 |
| `<Enter>` | SELECT（无自动跳转） | 跳到最近的带 label 目标 |
| `<Enter>` | TRAVERSE | 前进到下一个匹配 |
| `<Backspace>` | SELECT（自动跳转后） | 开始向后遍历 |
| `<Backspace>` | SELECT（label 分组） | 上一组 label |
| `<Backspace>` | TRAVERSE | 退回到上一个匹配 |
| `Space` | SELECT | 下一组 label |
| label 字符 | SELECT | 跳到对应目标 |
| 其他任意键 | SELECT（自动跳转后） | 接受当前位置，将该键重新传入编辑器 |
| 其他任意键 | TRAVERSE | 退出遍历，将该键重新传入编辑器 |

## 配置项

`Settings → Tools → vim-leap`

| 选项 | 默认值 | 说明 |
|---|---|---|
| Labels | `sfnjklh…` | 跳转 label 使用的字符集 |
| Safe labels | `sfnut/SFNLHMUGTZ?` | 匹配数不超过该集合大小时触发自动跳转 |
| Equivalence classes | `" \t\r\n"` | 搜索时视为等价的字符 |
| Max traversal targets | `10` | 遍历模式下高亮显示的前向匹配数量 |
| Show char1 preview | ✓ | 输入第一个字符后显示稳定 label 预览 |
| Search across splits | ✓ | 同时搜索所有可见的编辑器分屏 |
| Always case-sensitive | ✗ | 强制区分大小写 |
| Label / match / nearest colors | leap.nvim 默认值 | 可自定义颜色 |

## 开发

```bash
./gradlew buildPlugin   # 输出到 build/distributions/vim-leap-*.zip
./gradlew runIde        # 启动带插件的沙箱 IDEA
```

需要 **IntelliJ IDEA 2023.1+** 并已安装 **IdeaVim**。

## 致谢

受 [@ggandor](https://github.com/ggandor) 的 [leap.nvim](https://github.com/ggandor/leap.nvim) 启发，忠实移植到 IntelliJ Platform + IdeaVim。
本项目为独立重新实现，未复制或翻译原项目任何源代码。

## 许可证

[MIT](./LICENSE)
