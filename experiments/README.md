# 实验产物目录

本书所有数字都出自这里的原始命令输出。书稿只引用，不编造。

| 实验 ID | 日期 | 模型 ID | 用途 | 产物 |
| --- | --- | --- | --- | --- |
| `ch01-arm-a` | 2026-07-28 | 待填 | 第一章单臂裸提示解剖 | `ch01-arm-a/` |
| `ch03-scorecard` | 待填 | — | 第三章 FPGY 记分卡依据 | `ch03-scorecard.md` |
| `ch03-mutation` | 待填 | — | MS / SMS / MPKT 实测 | `ch03-mutation/` |
| `ch06-ab` | 待填 | 待填 | 第六章双臂对照实验 | `ch06-ab/` |

## 约定

- `workdir/` 是各实验的 commons-csv 工作副本，已在 `.gitignore` 中排除，不入库。
- 每个实验目录下必须有：`prompt.md`（提示词原文）、`baseline.txt`（改动前基线）、`generated.diff`（产出 diff）、`session.md`（模型 ID 与 token 消耗）。
- "一次生成"边界：从提示词提交到 AI 第一次宣称完成为止，期间允许 AI 自驱动的内部循环（TDD 红绿、自查、工具调用），不允许任何人类的语义纠正。

## ch01-arm-a 环境事实

- 工作副本：`experiments/ch01-arm-a/workdir/`（upstream `https://github.com/apache/commons-csv.git`）
- 分支：`2026-07-28-ch01-arm-a`
- base SHA：`85345a302dff477278349fbeddc25073b1dc866a`
- clone 日期：2026-07-28
