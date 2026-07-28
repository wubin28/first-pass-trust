# 实验产物目录

本书所有数字都出自这里的原始命令输出。书稿只引用，不编造。

| 实验 ID | 日期 | 模型 ID | 用途 | 产物 |
| --- | --- | --- | --- | --- |
| `ch01-arm-a` | 2026-07-28 | 待填 | 第一章单臂裸提示解剖 | `ch01-arm-a/` |
| `ch03-scorecard` | 待填 | — | 第三章 FPGY 记分卡依据 | `ch03-scorecard.md` |
| `ch03-mutation` | 待填 | — | MS / SMS / MPKT 实测 | `ch03-mutation/` |
| `ch06-ab` | 待填 | 待填 | 第六章双臂对照实验 | `ch06-ab/` |

## 约定

- commons-csv 工作副本在本仓库**之外**（见下方各实验的环境事实），不入库。`.gitignore` 另行排除 `experiments/**/workdir/`，以防将来有人在实验目录内建副本。
- 每个实验目录下必须有：`prompt.md`（提示词原文）、`baseline.txt`（改动前基线）、`generated.diff`（产出 diff）、`session.md`（模型 ID 与 token 消耗）。
- "一次生成"边界：从提示词提交到 AI 第一次宣称完成为止，期间允许 AI 自驱动的内部循环（TDD 红绿、自查、工具调用），不允许任何人类的语义纠正。

## ch01-arm-a 环境事实

- 工作副本：`/Users/binwu/OOR/katas/commons-csv`
- 分支：`2026-07-28-arm-a`
- base SHA：`66a838202d64a9b05be4e74b846619688b26cb10`
- 基线：`Tests run: 924, Failures: 0, Errors: 0, Skipped: 11`（原始输出见 `ch01-arm-a/baseline.txt`）
- A 臂环境洁净性核查（2026-07-28）：工作树干净；顶层无 `AGENTS.md`、`CLAUDE.md`、`.claude/`、`.codex/`、`.agents/`、`e2e/`、`docs/superpowers/` —— 无任何 harness 痕迹会泄漏进 A 臂会话。

> **这个 base SHA 与第三章 commit range `66a83820..ef286e5b` 的起点相同**，因此第一章 A 臂与第三章 B 臂建立在同一个 upstream 基线上，两章数据直接可比。
