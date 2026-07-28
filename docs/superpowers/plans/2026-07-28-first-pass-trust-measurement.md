# 可信度度量体系与第一章改稿 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为《让AI一次生成可信代码》建立五闸门可信度定义，用一次真实的单臂裸提示实验重构第一章开篇，并把度量数据回填/扩写到第三、六章。

**Architecture:** 先跑实验拿到真实数据，再写稿——书稿的每个数字都必须有可核验的命令输出作为出处。实验产物（提示词、diff、命令输出、变异报告）全部归档到本仓库 `experiments/` 下，书稿只引用不编造。第一章只做单臂（存在性证明），第六章才做双臂（比较性论断）。

**Tech Stack:** Apache Commons CSV（Java 8 target，Maven）、Codex、Superpowers skills、PIT（pitest Maven plugin）、git worktree

## Global Constraints

以下约束适用于本计划的**每一个任务**，值来自 spec，逐字照抄：

- **书稿语言**：简体中文。沿用全书骨架：**① 提出问题 → ② 引导读者实操（真实提示词供参考）→ ③ 分享实操体验（踩坑与避坑）**。
- **术语唯一**（全书统一，不得出现同义异名）：五闸门 = `G1 编译/构建`、`G2 回归绿`、`G3 规格覆盖`、`G4 变异敏感`、`G5 范围守卫`；指标 = `FPGY`（一次过闸率）、`RT`（返工轮次）、`RTC`（返工 token 占比）、`EDD`（缺陷逃逸深度）、`SCS`（规格覆盖分数）、`MS`（变异分数）、`SMS`（**规格变异分数**）、`MPKT`（每千 token 杀死变异数）。
- **A 臂标准（D2，不可放宽也不可收紧）**：允许一段写得相当不错的自然语言需求；**不允许** EARS、决策表、验收标准、`test-driven-development` skill、`requesting-code-review` / `receiving-code-review` skill、故障注入。
- **"一次生成"边界**：从提示词提交到 AI 第一次宣称"完成"为止。期间**允许** AI 自己驱动的内部循环（TDD 红绿、自查、工具调用），**不允许**任何人类的语义纠正。
- **第一章禁令（D3）**：ch01 不得出现任何双臂对比、任何 B 臂产物、任何"我们的方法更好"的宣称。
- **防剧透**：ch01 的实验需求不得触碰 `Strict Header Schema Validation Mode`、`ignoreHeaderCase`、"相邻 vs 相对顺序"这三样 ch03 的资产。
- **G1 判据严格性**：`mvn test` **不带任何特殊参数**。`-Drat.skip=true` 只允许在 G4/变异实验中使用，且必须在书稿中注明。
- **Java 8 target**：实验代码里不得使用 `Path.of` 等 Java 9+ API，用 `Paths.get`。
- **数据可核验（D6）**：书稿中每一个数字，都必须在 `experiments/` 下有对应的命令 + 原始输出文件，并在书稿中标注文件路径。
- **诚实报告**：未执行的、存疑的、失败的，一律如实标注，不得包装成成功。

## File Structure

| 路径 | 职责 |
| --- | --- |
| `experiments/README.md` | 实验目录总索引：每个实验的 ID、日期、模型 ID、产物清单 |
| `experiments/ch01-arm-a/prompt.md` | ch01 A 臂提示词原文（写死后不再改） |
| `experiments/ch01-arm-a/baseline.txt` | 改动前 `mvn test` 原始输出 |
| `experiments/ch01-arm-a/generated.diff` | 一次生成产出的完整 diff |
| `experiments/ch01-arm-a/gates/G1.txt` … `G5.md` | 五道闸门各自的命令与原始输出 |
| `experiments/ch01-arm-a/session.md` | 会话记录摘要 + 模型 ID + token 消耗 |
| `experiments/ch03-scorecard.md` | ch03 的 FPGY 记分卡原始依据 |
| `experiments/ch03-mutation/` | PIT 报告、SM-1/2/3 规格变异实测 |
| `experiments/ch06-ab/` | 第六章双臂实验全部产物 |
| `ch01/README.md` | 新增 1.2、1.3；原 1.2–1.6 顺延为 1.4–1.8 |
| `ch03/README.md` | 3.8 扩写；3.9 前补 FPGY 记分卡 |
| `ch06/README.md` | 新建，含 6.5 双臂对照实验 |

---

### Task 1: 实验环境、基线与目录骨架

**Files:**
- Create: `experiments/README.md`
- Create: `experiments/ch01-arm-a/baseline.txt`
- Create: `.gitignore`（若已存在则修改，追加 `experiments/**/workdir/`）

**Interfaces:**
- Produces: 环境变量约定 `$CSV_REPO`（commons-csv 工作副本绝对路径）、`$BOOK_REPO`（`/Users/binwu/OOR/katas/first-pass-trust`）；基线数字 `BASELINE_TESTS`、`BASELINE_FAILURES`、`BASELINE_ERRORS`，供 Task 3 的 G2 比对。

- [ ] **Step 1: 准备 commons-csv 工作副本**

```bash
export BOOK_REPO=/Users/binwu/OOR/katas/first-pass-trust
export CSV_REPO=/Users/binwu/OOR/katas/commons-csv   # 已有的本机副本，非本仓库内
mkdir -p $BOOK_REPO/experiments/ch01-arm-a
cd $CSV_REPO && git checkout -b 2026-07-28-arm-a
git rev-parse HEAD   # 实际为 66a838202d64a9b05be4e74b846619688b26cb10
```

记下这个 base SHA，Task 2 结束时要用它算 diff。

- [ ] **Step 2: 跑基线并归档原始输出**

```bash
cd $CSV_REPO && mvn test 2>&1 | tee $BOOK_REPO/experiments/ch01-arm-a/baseline.txt
echo "exit=${PIPESTATUS[0]}" >> $BOOK_REPO/experiments/ch01-arm-a/baseline.txt
```

期望：退出码 0。从输出末尾的 `Tests run: X, Failures: Y, Errors: Z, Skipped: W` 记下 X/Y/Z/W。

**若退出码非 0**：说明 upstream 本身构建就不干净。此时**停下来**，把失败原因记进 `baseline.txt` 顶部，并在书稿里注明——一个不干净的基线会污染 G1/G2 的全部结论。

- [ ] **Step 3: 写实验目录总索引**

创建 `experiments/README.md`：

```markdown
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
- "一次生成"边界：从提示词提交到 AI 第一次宣称完成为止，期间允许 AI 自驱动的内部循环，不允许任何人类的语义纠正。
```

- [ ] **Step 4: 排除工作副本入库**

在 `$BOOK_REPO/.gitignore` 追加一行：

```
experiments/**/workdir/
```

- [ ] **Step 5: 验证**

```bash
cd $BOOK_REPO && git status --short
```

期望：只看到 `experiments/README.md`、`experiments/ch01-arm-a/baseline.txt`、`.gitignore` 三项，**看不到** `workdir/` 下的任何文件。

- [ ] **Step 6: Commit**

```bash
cd $BOOK_REPO
git add .gitignore experiments/README.md experiments/ch01-arm-a/baseline.txt
git commit -m "chore: 建立实验产物目录与 ch01 A 臂基线"
```

---

### Task 2: 执行 ch01 A 臂一次生成

**Files:**
- Create: `experiments/ch01-arm-a/prompt.md`
- Create: `experiments/ch01-arm-a/generated.diff`
- Create: `experiments/ch01-arm-a/session.md`

**Interfaces:**
- Consumes: Task 1 的 `$CSV_REPO`、base SHA、基线数字
- Produces: `generated.diff`（Task 3 的 G5 要逐行读它）、`session.md` 里的模型 ID 与 token 消耗（Task 3 的 MPKT、Task 4 的正文都要引用）

- [ ] **Step 1: 把 A 臂提示词写死**

创建 `experiments/ch01-arm-a/prompt.md`。**这段文字一旦写下就不再修改**——它会原文出现在书里，让读者自行判断这个对照是否公平。

````markdown
# ch01 A 臂提示词（写死于 2026-07-28，此后不再修改）

符合 D2 标准：一段写得相当不错的自然语言需求。不含 EARS、决策表、验收标准；
不调用任何 skill；不要求 TDD；不做 code review；不做故障注入。

---

我在维护 Apache Commons CSV（Java 8 target，Maven 项目）。现在要加一个新能力：
当解析 CSV 时，某条记录的字段数与表头列数不一致，应该抛出异常，并且异常信息里
要带上出问题的行号，方便用户定位。

背景：目前 commons-csv 遇到字段数不匹配时不会报错，用户拿到 CSVRecord 之后才
发现字段少了或多了，此时位置信息已经丢失，排查很痛苦。尤其是处理几万行的导出
文件时，用户需要知道到底是第几行出的问题。

要求：

1. 这个行为要可开关，默认关闭，保持向后兼容——现有用户升级后行为不能变。
2. 异常信息要包含行号、期望字段数、实际字段数。
3. 请补充相应的自动化测试。
4. 遵循项目现有的代码风格与 API 设计惯例。

请实现它。
````

- [ ] **Step 2: 确认工作树干净，然后一次生成**

```bash
cd $CSV_REPO && git status --short
```

期望：无输出。有输出则先处理干净再继续。

然后开一个**全新的 Codex 会话**（不带本书任何 AGENTS.md / skill 上下文），把 `prompt.md` 中 `---` 以下的正文原样粘贴进去，让它跑到宣称完成为止。

**期间不得做任何人类语义纠正**——不追问、不纠错、不提示边界。这是 D2 和"一次生成"边界的核心，破了这条整个 ch01 就废了。

- [ ] **Step 3: 归档 diff**

```bash
cd $CSV_REPO
git add -A
git diff --cached > $BOOK_REPO/experiments/ch01-arm-a/generated.diff
git commit -m "ch01 arm-a: 一次生成产出（未经任何人工纠正）"
git rev-parse HEAD
```

记下这个 HEAD SHA。

- [ ] **Step 4: 归档会话元数据**

创建 `experiments/ch01-arm-a/session.md`：

```markdown
# ch01 A 臂会话记录

- 日期：2026-07-28
- 模型 ID：<填实际模型 ID>
- 工具：Codex，全新会话，无 AGENTS.md、无 skill
- base SHA：<Task 1 Step 1 的 SHA>
- HEAD SHA：<Step 3 的 SHA>
- 人类语义纠正轮次：0（这是"一次生成"的定义要求）
- token 消耗：输入 <X> / 输出 <Y> / 合计 <Z>
- AI 自驱动的内部循环次数：<N>（工具调用、自查、重跑测试等，不计为人类纠正）

## AI 宣称完成时说了什么

> <原样粘贴 AI 的完成宣言，尤其是它对测试情况的自述>
```

最后一栏是刻意设计：Task 3 会用五闸门去核对这段自述，**"AI 说测试都过了" vs "五闸门实测"的落差本身就是 1.2 节最有冲击力的素材**。

- [ ] **Step 5: 更新实验索引**

把 `experiments/README.md` 表格里 `ch01-arm-a` 那行的"模型 ID"填上实际值。

- [ ] **Step 6: Commit**

```bash
cd $BOOK_REPO
git add experiments/
git commit -m "experiment: ch01 A 臂一次生成产出归档"
```

---

### Task 3: 五闸门逐关实测

**Files:**
- Create: `experiments/ch01-arm-a/gates/G1.txt`
- Create: `experiments/ch01-arm-a/gates/G2.txt`
- Create: `experiments/ch01-arm-a/gates/G3.md`
- Create: `experiments/ch01-arm-a/gates/G4.txt`
- Create: `experiments/ch01-arm-a/gates/G5.md`
- Create: `experiments/ch01-arm-a/gates/summary.md`

**Interfaces:**
- Consumes: Task 1 的基线数字、Task 2 的 `generated.diff` 与 `session.md`
- Produces: `summary.md` 中的 `FPGY`、破关位置、以及 G3/G4/G5 的具体破口描述——Task 4 的 1.2 节正文全部基于它

**闸门是单调收紧序列：遇到第一个不通过就停，后面的关不用测。** 但 G1 若因构建配置（而非代码逻辑）失败，仍需继续测 G2–G5 并在 summary 中注明"G1 因构建配置破关，后续闸门为补充观测，不计入 FPGY"。

- [ ] **Step 1: G1 编译/构建**

```bash
cd $CSV_REPO && mvn test 2>&1 | tee $BOOK_REPO/experiments/ch01-arm-a/gates/G1.txt
echo "exit=${PIPESTATUS[0]}" >> $BOOK_REPO/experiments/ch01-arm-a/gates/G1.txt
```

判据：**不带任何特殊参数**退出码为 0 则 G1 通过。

- [ ] **Step 2: G2 回归绿**

从 Step 1 的输出中提取 `Tests run: X, Failures: Y, Errors: Z, Skipped: W`，写入 `gates/G2.txt`：

```
基线：Tests run: <BASELINE_TESTS>, Failures: <BASELINE_FAILURES>, Errors: <BASELINE_ERRORS>, Skipped: <BASELINE_SKIPPED>
本次：Tests run: <X>, Failures: <Y>, Errors: <Z>, Skipped: <W>
判据：Failures 与 Errors 均为 0，且 Tests run 不低于基线
结论：<通过 / 不通过，及原因>
```

- [ ] **Step 3: G3 规格覆盖——手工逐条列举**

**这一关不用 Agent 判**（spec 第 8 节风险表：ch01 的 G3 改为人工逐条列举，避免引入唯一的 inferential 判据）。

创建 `gates/G3.md`，把需求拆成规则，逐条填表。前三条来自提示词明文，后四条是提示词**没说、AI 必须自己做主**的边界：

```markdown
# G3 规格覆盖实测

判据：每条规则都有**自动化直接覆盖**（第 1 档）。分档：
1 = 自动化直接覆盖｜2 = 仅间接｜3 = 仅手工验证｜4 = 未覆盖

| # | 规则 | 出处 | AI 实际实现成什么 | 直接覆盖它的测试类#方法 | 档位 |
| --- | --- | --- | --- | --- | --- |
| R1 | 字段数与表头数不一致时抛异常 | 提示词明文 | | | |
| R2 | 异常信息含行号、期望字段数、实际字段数 | 提示词明文 | | | |
| R3 | 可开关，默认关闭，向后兼容 | 提示词明文 | | | |
| R4 | **空行算不算一条记录** | 提示词未提，AI 自行决定 | | | |
| R5 | **文件末尾无换行的那一行算不算** | 提示词未提，AI 自行决定 | | | |
| R6 | **quoted 字段内含换行时，"行号"指物理行还是逻辑记录号** | 提示词未提，AI 自行决定 | | | |
| R7 | **行号从 0 起还是 1 起、含不含表头行** | 提示词未提，AI 自行决定 | | | |

SCS = (1×第1档数 + 0.5×第2档 + 0.25×第3档 + 0×第4档) / 7 = <计算值>

结论：<通过 / 不通过。不通过时列出所有非第 1 档的行>
```

填表方法：对每条规则，在生成的测试文件里用 `grep` 找断言，判断它是否**直接**断言了这条规则。R4–R7 的填法是先读实现代码看 AI 事实上选了哪个语义，再找有没有测试锁住这个选择。

- [ ] **Step 4: G4 变异敏感**

挑一条 G3 判为第 1 档的规则，把它对应的生产代码判断注释掉，看测试红不红。

```bash
cd $CSV_REPO && git status --short   # 必须为空，否则停止
# 手工编辑：把字段数校验那一行/那个条件注释掉，只改这一处
mvn -Drat.skip=true test 2>&1 | tee $BOOK_REPO/experiments/ch01-arm-a/gates/G4.txt
echo "exit=${PIPESTATUS[0]}" >> $BOOK_REPO/experiments/ch01-arm-a/gates/G4.txt
git checkout -- <被改的文件>          # 精确还原
git status --short                    # 必须为空
mvn -Drat.skip=true test              # 必须恢复为绿
```

判据：注释掉之后**至少一个测试变红**，且失败原因是业务语义（断言不满足），不是编译失败、测试发现失败或超时。编译失败不算成功的变异。

在 `G4.txt` 顶部补一段说明：注入了什么、期望哪个测试红、实际结果、是否已完整还原。

**安全约束**：全程禁用 `git reset --hard`；每次注入前确认 `git status --short` 为空；用完立即还原并验证。

- [ ] **Step 5: G5 范围守卫**

创建 `gates/G5.md`，逐行读 `generated.diff`，找 AI 自作主张多做的事：

```markdown
# G5 范围守卫实测

需求隐含的范围：一个开关 + 一处校验 + 一个带行号的异常 + 相应测试。

| 新增/修改的公开 API 或行为 | 需求要求了吗 | 判定 |
| --- | --- | --- |
| | | |

判据：无任何超出上述隐含范围的新增公开 API 或行为变更。
结论：<通过 / 不通过，并列出超范围项>
```

注意这里的教学点：A 臂的需求**没有"明确不做"清单**（那是 B 臂 spec 才有的东西），所以 G5 只能对着"隐含范围"判——这本身就是 A 臂的一个结构性弱点，Task 4 写稿时要点破。

- [ ] **Step 6: 汇总**

创建 `gates/summary.md`：

```markdown
# ch01 A 臂五闸门实测汇总

| 闸门 | 判据 | 实测 | 结论 | 证据文件 |
| --- | --- | --- | --- | --- |
| G1 编译/构建 | `mvn test` 无特殊参数退出码 0 | | | `gates/G1.txt` |
| G2 回归绿 | Failures=0 Errors=0 且不低于基线 | | | `gates/G2.txt` |
| G3 规格覆盖 | 每条规则第 1 档 | SCS=<值> | | `gates/G3.md` |
| G4 变异敏感 | 注入后至少一测变红且原因是业务语义 | | | `gates/G4.txt` |
| G5 范围守卫 | 无超范围公开 API/行为 | | | `gates/G5.md` |

**FPGY = 连续通过闸门数 / 5 = <值>**

破关位置：<第几关>
破关表现（一句话，供 1.2 节引用）：<例如"全量测试 X 个全绿，但 R4/R6/R7 三条边界语义 AI 自己做了主，没有任何测试锁住它们">

AI 自述 vs 实测落差：<对照 session.md 里 AI 的完成宣言>
```

- [ ] **Step 7: 验证归档完整**

```bash
ls $BOOK_REPO/experiments/ch01-arm-a/gates/
```

期望：`G1.txt G2.txt G3.md G4.txt G5.md summary.md` 六个文件齐全，且每个都已填入实测值，无占位符。

```bash
grep -rn "待填\|<值>\|<填" $BOOK_REPO/experiments/ch01-arm-a/gates/
```

期望：无输出。

- [ ] **Step 8: Commit**

```bash
cd $BOOK_REPO
git add experiments/ch01-arm-a/gates/
git commit -m "experiment: ch01 A 臂五闸门实测结果"
```

---

### Task 4: 写第一章新 1.2 节「一次裸提示生成的完整解剖」

**Files:**
- Modify: `ch01/README.md`（在 1.1 节之后、原 1.2 节之前插入）

**Interfaces:**
- Consumes: `experiments/ch01-arm-a/` 全部产物，尤其 `gates/summary.md`、`prompt.md`、`session.md`
- Produces: 五闸门定义表（ch03 Task 6、ch06 Task 8 都引用它）、指向第六章 6.5 的欠条

- [ ] **Step 1: 写节首问题与实操引导**

在 `ch01/README.md` 的 1.1 节末尾之后插入 `## 1.2 一次裸提示生成的完整解剖：它到底在第几关破`。

开篇问题（一句话，独立成段）：

> 一段写得挺好的需求，交给 AI 一次生成，测试全绿了——这就算可信了吗？

然后交代实验设置：需求是什么、为什么选它（三条硬标准：零 commons-csv 背景可懂、含反直觉边界、不与后面三章重叠）、A 臂标准是什么（允许写得好的自然语言需求，不允许 EARS/决策表/TDD/review）。

- [ ] **Step 2: 原文贴出 A 臂提示词**

把 `experiments/ch01-arm-a/prompt.md` 中 `---` 以下的正文，用引用块原样贴进书里，并写明：

> 这段提示词在实验开始前就写死了，此后一个字没改。之所以原文贴出来，是因为这一节全部结论都建立在"这是一个公平的对照"之上——公不公平，请读者自己判断。

- [ ] **Step 3: 写四个反直觉边界**

在提示词之后、实测之前，先把 R4–R7 四个边界摆出来，让读者**在看结果之前**自己意识到这段需求有洞：

> 这段需求读起来很完整。但它有四件事没说：
>
> - 空行算不算一条记录？
> - 文件末尾没有换行的那一行算不算？
> - quoted 字段里含换行时，"行号"指物理行还是逻辑记录号？
> - 行号从 0 起还是从 1 起？含不含表头那一行？
>
> 这四个问题，AI 必须给出答案才能写出代码。它会问你吗？还是会替你做主？

- [ ] **Step 4: 逐关呈现实测**

按 G1 → G5 顺序，每关一小段，格式统一：**判据一句话 → 实际命令 → 原始输出（截取关键行）→ 结论**。数据全部取自 `gates/*.txt`，并标注证据文件路径。

G3 那一关是本节重心，把 `gates/G3.md` 的表格搬进来（可精简列），让读者亲眼看到哪几行是空的。

G4 那一关要强调：注释掉一行，测试**照样绿**（或红），这个结果比任何覆盖率数字都直观。

G5 那一关要点破 A 臂的结构性弱点：需求里没有"明确不做"清单，所以范围只能靠猜。

- [ ] **Step 5: 写 AI 自述 vs 实测的落差**

把 `session.md` 里 AI 的完成宣言原样贴出，与 `summary.md` 的五闸门结果并排。这是本节冲击力最强的地方。

- [ ] **Step 6: 交付五闸门定义**

小标题「把'可信'定义死」，贴出五闸门表格（严格使用 Global Constraints 里的术语），并写明两条配套约定：**单调收紧**（遇到第一个不通过即停）与**"一次生成"的边界**（允许 AI 自驱动内部循环，不允许人类语义纠正）。边界那条要说清理由：harness 的两大目标之一就是提供自纠正回路，把 AI 的自纠正算成"多轮试错"等于把 harness 一半功效算成成本。

- [ ] **Step 7: 写证据边界欠条**

节尾原样写入（数字按 `summary.md` 实测替换）：

> 这一节只证明了"一次生成会在第 N 道闸门破"，没有证明"用了本书的方法就不会破"。后者需要一个对照实验，本书在第六章 6.5 节完整给出：同一批需求、两臂、独立盲评，数据与提示词全部公开。

- [ ] **Step 8: 自查禁令**

```bash
grep -n "B 臂\|对照组\|我们的方法更好\|Strict Header Schema\|ignoreHeaderCase\|相邻" ch01/README.md
```

期望：无输出。有输出说明违反了 ch01 禁令或防剧透约束，必须删改。

- [ ] **Step 9: Commit**

```bash
git add ch01/README.md
git commit -m "docs(ch01): 新增 1.2 一次裸提示生成的完整解剖与五闸门定义"
```

---

### Task 5: 写新 1.3 节 + 全书节号顺延与交叉引用修正

**Files:**
- Modify: `ch01/README.md`（新增 1.3；原 1.2–1.6 改号为 1.4–1.8）
- Modify: `README.md`（目录）
- Modify: `ch02/README.md`、`ch03/README.md`（交叉引用）

**Interfaces:**
- Consumes: Task 4 的五闸门定义
- Produces: 稳定的章节编号，后续任务引用 `1.4` 指方法论、`1.8` 指贯穿案例

- [ ] **Step 1: 写 1.3 闸门破口预告表**

在 1.2 之后插入 `## 1.3 本书后面会撞上的四道闸门破口`。

一段引子：这四行都是本书后面章节**真实发生**的，不是假设。然后贴表：

```markdown
| 现象 | 破在 | 详见 |
| --- | --- | --- |
| 全量测试卡在构建关口，需要加特殊参数才能跑绿 | G1 | 3.6.1 |
| 四个故事全绿、五个 e2e 输出全对，评审结论仍是"不可合并" | G3 | 3.7.3 |
| 决策表白纸黑字五列之一，从头到尾没人测过 | G3 | 3.7.3 |
| 漂亮的 `records=4` 正向断言，在变异测试下几乎测不到任何东西 | G4 | 3.8.3 |
```

**只报现象，不展开原因**——展开就剧透了 ch03。收尾一句话，把读者推向 1.4 的方法论。

- [ ] **Step 2: 逐条核对表格出处**

```bash
grep -n "UNAPPROVED\|Request changes\|R4\|变异敏感\|records=4" ch03/README.md
```

四行的每一行都要能在 ch03 找到原文出处。找不到的行删掉或改写。ch04、ch05 写完后按同格式各补 1–2 行（在本任务中先不补）。

- [ ] **Step 3: 顺延原有节号**

`ch01/README.md` 中：`## 1.2 一条方法论` → `## 1.4 一条方法论`；`## 1.3 场景一` → `## 1.5 场景一`；`## 1.4 场景二` → `## 1.6 场景二`；`## 1.5 场景三` → `## 1.7 场景三`；`## 1.6 本书的实操锚点` → `## 1.8 本书的实操锚点`。

**从后往前改**（先改 1.6→1.8，最后改 1.2→1.4），避免改完 1.2→1.4 后与原有的 1.4 撞号。

- [ ] **Step 4: 更新根 README 目录**

`README.md` 第一章目录改为八条：1.1 棕地困境 / 1.2 一次裸提示生成的完整解剖 / 1.3 本书后面会撞上的四道闸门破口 / 1.4 一条方法论 / 1.5–1.7 三个场景 / 1.8 实操锚点。

- [ ] **Step 5: 修正全书交叉引用**

```bash
grep -rn "1\.1 节\|1\.2 节\|1\.3 节\|1\.4 节\|1\.5 节\|1\.6 节" README.md ch01/README.md ch02/README.md ch03/README.md
```

逐条判断：指方法论四步的（原 1.2）改为 `1.4 节`；指棕地风险的（1.1）不变；指三场景的按 +2 改。已知至少 `ch01/README.md` 的 1.8 节里有"本书第 1.1 节所说的棕地风险"（不变），`ch03/README.md` 中引用方法论的地方需要检查。

- [ ] **Step 6: 验证无残留错号**

```bash
grep -n "^## 1\." ch01/README.md
```

期望：恰好八行，依次为 1.1 到 1.8，无重号无跳号。

```bash
grep -rn "1\.2 节.*方法论\|1\.6 节.*锚点" README.md ch0*/README.md
```

期望：无输出（旧编号的语义引用已全部改完）。

- [ ] **Step 7: Commit**

```bash
git add README.md ch01/README.md ch02/README.md ch03/README.md
git commit -m "docs(ch01): 新增 1.3 闸门破口预告表，原 1.2-1.6 顺延为 1.4-1.8 并修正交叉引用"
```

---

### Task 6: 第三章 FPGY 记分卡回填

**Files:**
- Create: `experiments/ch03-scorecard.md`
- Modify: `ch03/README.md`（3.9 节之前插入）

**Interfaces:**
- Consumes: Task 4 的五闸门定义、ch03 已有的实操记录
- Produces: FPGY 记分卡格式，ch04/ch05/ch06 复用

- [ ] **Step 1: 从 ch03 正文回填原始依据**

创建 `experiments/ch03-scorecard.md`，逐格标注出处：

```markdown
# ch03 FPGY 记分卡原始依据

| 阶段 | G1 | G2 | G3 | G4 | G5 | FPGY | RT | RTC | EDD |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Story 1–4 首次生成 | ✗ | — | — | — | — | 0/5 | <回填> | <回填> | 3 |
| receiving-code-review 之后 | ✓ | ✓ | ✓ | ✓ | ✓ | 5/5 | — | — | — |

## 逐格出处

- G1 首次生成 ✗：3.6.1 与 3.7.3，`mvn test` 因 RAT 报 `Unexpected count for UNAPPROVED … Count: 9` 在测试执行前失败。G1 判据要求不带特殊参数，故判 ✗。
- G3 修复后 ✓：3.7.4，需求—证据矩阵的"仅手工 e2e"与"未覆盖"两类均已转为自动化直接覆盖（AC-S1-H1/AC-S2-H1/AC-S2-S1/AC-S3-H1/AC-S3-S1 + R4）。
- G4 修复后 ✓（3/8 直测）：3.8.2 三次语义变异全部被杀死；映射表另 5 行如实标注"未独立执行"，见 3.8.3。**书稿中必须标注 3/8 这个分数，不得只写 ✓。**
- EDD = 3：`ignoreHeaderCase` 那个 Critical 在 G3（需求—证据矩阵评审）被抓住。若 3.5 节的人工评审没被跳过，EDD 会是 0。
- RT：从首次生成到五闸门全过之间，人类发起的语义纠正轮次。已知至少含 `receiving-code-review` 一轮。**回填方法**：数 3.6→3.8 之间人类主动发起的、带语义纠正意图的提示词条数（TDD 各故事的实现提示词不算纠正，`requesting-code-review` 算观测不算纠正，`receiving-code-review` 算 1 轮）。
- RTC = 返工消耗 token / 总消耗 token。**回填方法**：把 3.7 `requesting-code-review` 与 3.7.4 `receiving-code-review` 两轮的 token 计入分子，3.4 生成 spec + 3.6 四次 TDD 实现计入分母的其余部分。受 prompt cache 影响，**报区间不报点值**，并注明是否命中缓存。这个数直接回答 3.4.4 节那笔账。
```

**关于 ch04、ch05**：两章尚未写完，本任务只交付 ch03 的记分卡与格式。ch04、ch05 完稿时各按同格式补一张，并在 1.3 节的破口预告表中各补 1–2 行（Task 5 Step 2 已说明）。

- [ ] **Step 2: 把记分卡写进书稿**

在 `ch03/README.md` 的 `## 3.9` 之前插入小节 `### 这一章的 FPGY 记分卡`，贴表 + 三段说明：

1. 首次生成 FPGY = 0/5，破在 G1（构建关口）——**而且是被一个与业务逻辑毫无关系的 License 检查挡住的**。
2. 走完全套 harness 之后是 5/5，但 G4 那格要诚实标成 3/8 直测。
3. EDD = 3 这个数的含义：这个 Critical 本可以在 G0（spec 人工评审）就被抓住，代价差一个数量级——回指 3.5.2。

- [ ] **Step 3: 校验术语一致**

```bash
grep -n "FPGY\|EDD\|RT\b\|G1\|G2\|G3\|G4\|G5" ch03/README.md
```

期望：所有术语拼写与 Global Constraints 一致，无"闸门1""门禁"等异名。

- [ ] **Step 4: Commit**

```bash
git add experiments/ch03-scorecard.md ch03/README.md
git commit -m "docs(ch03): 回填 FPGY 记分卡与逐格出处"
```

---

### Task 7: 第三章 3.8 扩写——MS / SMS / MPKT 与规格变异实测

**Files:**
- Create: `experiments/ch03-mutation/pit-config.md`
- Create: `experiments/ch03-mutation/pit-report.txt`
- Create: `experiments/ch03-mutation/spec-mutants.md`
- Modify: `ch03/README.md`（3.8 节新增 3.8.4、3.8.5）

**Interfaces:**
- Consumes: ch03 的 commons-csv 工作副本（分支 `2026-07-14-tdd-new-feature`）
- Produces: `MS`、`SMS`、`MPKT` 三个实测值，Task 8 的双臂实验复用同一套 PIT 配置

- [ ] **Step 1: 接入 PIT 并限定变异范围**

在 ch03 的工作副本 `pom.xml` 中加入 pitest 插件，**变异算子只用默认集**（不自选，避免调参嫌疑），目标类限定：

```xml
<targetClasses>
  <param>org.apache.commons.csv.CSVParser</param>
  <param>org.apache.commons.csv.CSVHeaderSchema</param>
  <param>org.apache.commons.csv.CSVFormat*</param>
</targetClasses>
```

把完整配置片段与限定理由（避免全库变异烧光 CPU）写入 `experiments/ch03-mutation/pit-config.md`。

- [ ] **Step 2: 跑 PIT 并归档**

```bash
cd <ch03 工作副本> && mvn -Drat.skip=true org.pitest:pitest-maven:mutationCoverage 2>&1 \
  | tee $BOOK_REPO/experiments/ch03-mutation/pit-report.txt
```

从报告末尾读出 `Generated N mutations Killed M (X%)`，即 `MS`。

- [ ] **Step 3: 人工剔除等价变异体**

在 `pit-report.txt` 之后追加一节，逐个列出存活变异体，判定是否等价（语义等价、不可能被杀死），并给出**剔除规则**：

```markdown
## 等价变异体剔除

规则：仅当变异后的程序与原程序在所有合法输入下行为完全相同时，才判为等价并剔除。
判定需给出一句话理由，理由不成立的一律保留为"存活"。

| 变异体位置 | 变异内容 | 判定 | 理由 |
| --- | --- | --- | --- |

剔除后：MS = 杀死数 / (总数 - 等价数) = <值>
```

- [ ] **Step 4: 执行三个规格变异（SMS）**

创建 `experiments/ch03-mutation/spec-mutants.md`。做法：不改代码，改 spec 条款，然后问"现有测试套件能否区分这两份 spec"。操作上等价于——按变异后的 spec 重写实现，看有没有测试变红。

```markdown
# 规格变异（SMS）实测

| ID | 原文 | 变异后 | 期望 | 实测 | 结论 |
| --- | --- | --- | --- | --- | --- |
| SM-1 | 声明列须保持**相对顺序** | 声明列须**相邻** | ≥1 测试变红 | | |
| SM-2 | 在**任何记录暴露前**失败 | 在**首次迭代时**失败 | ≥1 测试变红 | | |
| SM-3 | 精确名称匹配 | 大小写不敏感匹配 | ≥1 测试变红 | | |

SMS = 被杀死的 spec 变异数 / 3 = <值>

## SM-3 的两次测量（本节最有价值的数据点）

- 在 3.7 节修复**之前**（commit <base SHA>）：SM-3 <存活/杀死>
- 在 3.7 节修复**之后**（commit <HEAD SHA>）：SM-3 <存活/杀死>
```

SM-3 必须在**修复前后各测一次**——这把"3.5 节跳过 spec 评审的代价"从叙事变成一个数字。

安全约束同 3.8：每次变异前 `git status --short` 必须为空，用完立即精确还原并验证，禁用 `git reset --hard`。

- [ ] **Step 5: 计算 MPKT**

```
MPKT = 杀死的变异数 / 消耗的千 token
```

token 数取 ch03 全流程的实际消耗。**报区间而非点值**，并注明是否命中 prompt cache（spec 第 8 节风险表）。

- [ ] **Step 6: 写 3.8.4、3.8.5 两节**

`### 3.8.4 从手工故障注入到自动变异测试：MS 与 MPKT`——把 3.8.2 那三次手工注入定位为"自动变异测试的手工版"，给出 PIT 实测 MS，并放在两个行业锚点旁边：Stryker 默认 high 阈值 80%、已有案例"85% 行覆盖率但变异杀死率 57.3%"。然后给出 MPKT 及其含义：它把"可信"和"token 预算"压进同一个数。

`### 3.8.5 规格变异分数（SMS）：连规格本身也要被变异`——先讲清 SMS 是什么、为什么代码变异不够（代码变异测的是"测试能否发现实现改坏了"，SMS 测的是"测试能否发现规格改了"）；贴 SM-1/2/3 表；重点展开 SM-3 修复前后的两次测量。收尾点明：Birgitta 在文末留了一个公开问题——缺一个类似 code coverage / mutation testing 之于测试的、用来评估 harness 覆盖度与质量的方法。SMS 是本书对这个问题的一个回答。

- [ ] **Step 7: 验证工作树干净**

```bash
cd <ch03 工作副本> && git status --short && git diff --check
```

期望：均无输出。所有变异必须已完整还原。

```bash
cd <ch03 工作副本> && mvn -Drat.skip=true test
```

期望：与 3.8.2 记录的基线完全一致（944 tests, 0 failures, 0 errors, 11 skipped）。

- [ ] **Step 8: Commit**

```bash
cd $BOOK_REPO
git add experiments/ch03-mutation/ ch03/README.md
git commit -m "docs(ch03): 3.8 扩写 MS/SMS/MPKT，新增规格变异分数实测"
```

---

### Task 8: 第六章双臂对照实验执行

**Files:**
- Create: `experiments/ch06-ab/design.md`
- Create: `experiments/ch06-ab/judge-calibration.md`
- Create: `experiments/ch06-ab/arm-a/`、`experiments/ch06-ab/arm-b/`（各 3 需求 × 3 次）
- Create: `experiments/ch06-ab/results.md`

**Interfaces:**
- Consumes: Task 3 的 ch01 A 臂产物（作为 n=1 补充样本）、Task 7 的 PIT 配置
- Produces: `results.md` 中的 SCS 均值与变异系数，Task 9 写稿全部基于它

**这是本计划唯一真正烧 token 的任务。** 开始前确认预算。

- [ ] **Step 1: 固化实验设计**

创建 `experiments/ch06-ab/design.md`，写死六条纪律（开跑后不得修改）：

```markdown
# 第六章双臂对照实验设计

## 两臂
- A 臂：按 D2 标准的自然语言需求，直接喂 Codex，一次生成
- B 臂：拆需求 → EARS + 决策表 + 验收标准 → test-driven-development → 全量回归
       → requesting-code-review → 故障注入

## 六条纪律（缺一条实验作废）
1. 物理隔离：两臂各自一个 git worktree、各自一个全新会话，绝不同会话
2. 随机顺序：先跑哪臂由掷硬币决定，结果记录在案
3. 盲评：第三个从未参与生成的 Agent 会话；两份 diff 匿名化为"实现甲/实现乙"；
   评审提示词复用 3.7.1 那段（需求—证据矩阵 + 四档判定）
4. 判分器标定：先喂一个已知含 Critical 的版本，抓不到则判分器不合格，换提示词重来
5. 需求文本防泄漏：法官拿到的必须是**原始需求**，不能是 B 臂产出的 EARS 规格
6. 同日同模型：两臂同一天、同一模型版本跑完，记录模型 ID

## 样本
3 个需求（新功能 / 缺陷 / 技术债）× 每臂重复 3 次 = 18 次生成。
另有 ch01 那次单臂解剖作为**第 4 个需求的 A 臂样本（n=1，无 B 臂）**单独列出，
**不参与方差计算**，只用于回指开篇。表中必须显式标注它是补充样本。

## 主结局指标（只此一个）
SCS = (1×直接自动化覆盖 + 0.5×仅间接 + 0.25×仅手工e2e + 0×未覆盖) / 规格条目总数

## 次要指标
盲评 Critical/Important 条数、MS、总 token 消耗（含返工）、RT
```

- [ ] **Step 2: 标定判分器**

取 ch03 中 `ignoreHeaderCase` 修复**之前**的那个 commit（已知含 1 条 Critical），匿名化后喂给盲评 Agent。

判据：法官必须报出那条 Critical。报不出则判分器不合格，调整评审提示词后重新标定，直到通过。全过程记入 `judge-calibration.md`，**包括失败的那几次**。

- [ ] **Step 3: 掷硬币定顺序并记录**

```bash
echo $((RANDOM % 2))   # 0 = 先跑 A 臂，1 = 先跑 B 臂
```

结果写进 `design.md`。

- [ ] **Step 4: 跑 18 次生成**

每次生成一个独立 worktree、一个全新会话。每次归档：`prompt.md`、`generated.diff`、`session.md`（模型 ID、token、RT）。

目录命名：`arm-a/req1-run1/`、`arm-a/req1-run2/` …… `arm-b/req3-run3/`。

- [ ] **Step 5: 盲评 18 份产出**

diff 匿名化后交给标定过的法官，逐份产出需求—证据矩阵与四档判定。**法官不得知道哪份属于哪臂。**

匿名化检查（提交给法官前必跑）：

```bash
grep -rn "EARS\|决策表\|superpowers\|test-driven-development\|AGENTS.md" <待评审的 diff>
```

有输出说明 B 臂身份会泄漏给法官——必须先剥离这些痕迹，或改为只提交源码 diff 不提交文档 diff。

- [ ] **Step 6: 算 SCS 与变异系数**

创建 `results.md`：

```markdown
| 需求 | 臂 | run1 SCS | run2 SCS | run3 SCS | 均值 | 标准差 | 变异系数 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 需求1 新功能 | A | | | | | | |
| 需求1 新功能 | B | | | | | | |
| …… | | | | | | | |
| **ch01 补充样本（n=1，不参与方差）** | A | | — | — | — | — | — |
```

主报**变异系数**（标准差/均值），不报显著性检验（n 不足）。

- [ ] **Step 7: 验证纪律未被破坏**

逐条核对 design.md 六条纪律，在 `results.md` 末尾写一份「纪律遵守自查」，任何一条打了折扣都如实写明及其对结论的影响。

- [ ] **Step 8: Commit**

```bash
cd $BOOK_REPO
git add experiments/ch06-ab/
git commit -m "experiment: 第六章双臂对照实验全部产物"
```

---

### Task 9: 写第六章 6.5 节

**Files:**
- Create: `ch06/README.md`（若已存在则 Modify）
- Modify: `README.md`（目录补 6.5）

**Interfaces:**
- Consumes: Task 8 的 `results.md`、`design.md`、`judge-calibration.md`；Task 4 的五闸门定义；Task 6 的记分卡格式

- [ ] **Step 1: 写 6.5 节主体**

`## 6.5 我们做了一个对照实验`，结构：

1. **问题**：全书讲到这里，凭什么说这套方法真的更可信？——回指 1.2 节那张欠条。
2. **设计**：两臂定义、六条纪律，逐条说明**为什么**需要它（物理隔离防上下文污染、随机顺序防"第二次更懂需求"、盲评防作者偏好、标定防判分器失灵、防泄漏防法官识别身份、同日同模型防漂移）。
3. **判分器标定**：包括失败的那几次——`judge-calibration.md` 里的失败记录要写进书。
4. **结果**：SCS 表 + 变异系数。**主结论落在方差上**："如果 A 臂三次结果差异巨大而 B 臂三次高度一致，harness 降低方差本身就是一个强结论"——对 token 不自由的读者，"方差小 = 可预算"比"均值高"更有价值。
5. **ch01 那次的回指**：作为 n=1 补充样本单列，标注它不参与方差计算，用它把开篇和结尾接上。
6. **诚实的边界**：n 不足以做显著性检验；作者即实验者这一威胁，缓解手段是 A 臂提示词原文公开。

- [ ] **Step 2: 写威胁效度小节**

`### 6.5.x 这个实验不能证明什么`，逐条列出 spec 第 8 节风险表的六项及其缓解，并给出仓库中原始数据的路径（`experiments/ch06-ab/`）。

**这一节不能省。** 全书教的就是"证据 vs 主观判断"，自己的实验不划边界会当场自打耳光。

- [ ] **Step 3: 更新根 README 目录**

第六章目录下补 `6.5 我们做了一个对照实验`。

- [ ] **Step 4: 全书术语与数字终检**

```bash
grep -rn "可信\|更可信" ch0*/README.md README.md | grep -v "G1\|G2\|G3\|G4\|G5\|FPGY\|五闸门"
```

逐条检查：每一处"可信/更可信"是否有五闸门定义支撑，或紧邻处有定义指引。无支撑的表述改写或补引用。

```bash
grep -rn "待填\|TODO\|TBD\|<值>" ch0*/README.md README.md experiments/
```

期望：无输出。

- [ ] **Step 5: Commit**

```bash
git add ch06/README.md README.md
git commit -m "docs(ch06): 新增 6.5 双臂对照实验与威胁效度说明"
```

---

## 任务依赖

```
Task 1 ──> Task 2 ──> Task 3 ──> Task 4 ──> Task 5
                        │                      │
                        └──────────────────────┴──> Task 8 ──> Task 9
Task 6 ──> Task 7 ─────────────────────────────────────┘
```

- Task 6、Task 7 只依赖已有的 ch03 数据，可与 Task 1–5 并行。
- Task 8 依赖 Task 3（ch01 补充样本）与 Task 7（PIT 配置复用）。
- Task 8 是唯一大额 token 支出，开始前应确认预算；若预算不足，可先交付 Task 1–7（ch01 改稿 + ch03 扩写已能独立成立），并把 1.2 节欠条中的"第六章 6.5 节"改为"本书后续版本"。
