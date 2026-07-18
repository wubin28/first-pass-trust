# 问题驱动电子书改造 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把电子书 4 个文件（README、ch01、ch02、ch03）统一改造为"提出问题 → 引导实操（真实提示词）→ 分享体验（踩坑避坑）"骨架。

**Architecture:** 纯编辑任务，无代码。ch02/ch03 把意译提示词换成 wiki 记录里的真实提示词并重写周边分析；README/ch01 重框为问题驱动叙事。每个任务 = 编辑一个文件的一个逻辑单元 → 校验无本机路径残留 → 提交。

**Tech Stack:** Markdown。校验用 `grep`。

## Global Constraints

- 本机路径处理：所有本机绝对路径（`/Users/binwu/...`）、vault 相对路径（`my-workspace/...`、`my-knowledge-base/...`）→ 描述式方括号占位符，沿用书中现有 `[需求文档]`/`[源码目录]` 风格。
- 剔除对读者无意义的管理性句子：`请将你的完整回复以markdown格式保存到 ...md`、`当前目录为 @/Users/binwu/OOR/remote-vault-on-mbp`、纯本机环境铺垫。
- 保留对读者有意义的信息：分支名（如 `2026-07-11--13-29-e2e-tests`、`2026-07-14-tdd-new-feature`）、公开工具网址、公开文档章节名、已安装工具版本（openjdk 25.0.3、Maven 3.9.16）、commit range。
- **不动**：所有 mermaid/PlantUML 图、代码块、EARS 表、决策表、Gherkin 验收标准、方法论论述、外链、作者简介、目录结构、版权许可、FAQ。
- `20-x`（直播封面）不入书。
- 每个实操单元改完须可辨认三段：① 提出问题 ② 引导实操（真提示词）③ 分享体验。
- 每个任务结束提交，message 用中文 `docs:` 前缀，附：
  ```
  Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_01Bc4q4eKqTicaYoMJxjjRpD
  ```

## 通用校验命令

每个 ch02/ch03 任务改完，对目标文件跑：

```bash
grep -nE "/Users/binwu|my-workspace/_first-pass|my-knowledge-base|保存到.*\.md|remote-vault-on-mbp" <file>
```

预期：**无输出**（README/ch01 任务同样跑，预期无输出）。若有输出，说明有本机路径残留，须清理。

---

### Task 1: README.md — 引言 + 方法论重框为问题驱动

**Files:**
- Modify: `README.md`（`## 引言` 与 `## 本书方法论...` 两节）

**Interfaces:**
- Produces: 全书问题驱动叙事基调；ch01 沿用同一设问口吻。

- [ ] **Step 1: 读现状**

Read `README.md` 的 `## 引言` 和 `## 本书方法论：拆需求 → 出 spec → TDD 实现 → 频繁跑测试` 两节。

- [ ] **Step 2: 引言开头插入设问**

在 `## 引言` 首段前，或改写首句，使其以问题开场。示例改写（保留原有 token 不自由 + 棕地风险论述，只在开头加设问钩子）：

> 先问一个问题：**如何让 AI 在有限的 token 预算下，第一次就生成可信的代码，而不是靠反复来回试错去逼近正确答案？**
>
> 这正是本书要解决的唯一问题。国内做 AI 辅助编程的开发者，大多绕不开一个现实约束：token 不自由……（原文照旧）

保留原 `本书要解决的问题只有一个：...` 段落。

- [ ] **Step 3: 方法论节改为"问题→回答"口吻**

`## 本书方法论` 节开头加一句过渡，点明这四步是对上面那个问题的回答，如：

> 回答"如何一次生成可信代码"，本书给出的不是一句口号，而是一条可执行、可验证的四步链路：

四步列表与后文照旧不动。

- [ ] **Step 4: 校验无本机路径**

Run: `grep -nE "/Users/binwu|my-workspace/_first-pass|my-knowledge-base|保存到.*\.md|remote-vault-on-mbp" README.md`
Expected: 无输出

- [ ] **Step 5: 提交**

```bash
git add README.md
git commit -m "docs: 将 README 引言与方法论重框为问题驱动叙事"
```

---

### Task 2: ch01/README.md — 1.1–1.6 设问开场 + 指向后续实操

**Files:**
- Modify: `ch01/README.md`（每节开头 + 节尾过渡句）

**Interfaces:**
- Consumes: Task 1 的设问口吻。
- Produces: 读者从 ch01 被引导到 ch02–ch05 的实操。

- [ ] **Step 1: 读现状**

Read `ch01/README.md` 全文（6 节 + 3 张 mermaid 图）。

- [ ] **Step 2: 每节开头加设问**

为 1.1–1.6 每节在首段前加一句问题钩子（不改后续论述、不改图）。建议设问：
- 1.1 → "为什么国内 token 不自由的开发者，最怕的不是 AI 不会写代码，而是它'写得看着对'？"
- 1.2 → "有没有一条可复用的链路，能把'信任'从主观判断变成可验证的工程流程？"
- 1.3 → "在棕地项目上新增功能，第一步到底该先做什么？"
- 1.4 → "修缺陷和写新功能，做法上最大的区别在哪一步？"
- 1.5 → "代码功能是对的、只是结构烂，动手前必须先有什么？"
- 1.6 → "方法论怎么落到一个真实、体量适中的棕地项目上？"

- [ ] **Step 3: 每节尾加实操指向**

在 1.3/1.4/1.5 节尾（已有"本书第 X 章会……"句的，强化为动作指向），确保每节明确告诉读者"具体提示词与踩坑体验见第 X 章"。不硬塞提示词。示例：1.3 节尾保留并补一句"——第三章会把这一步用到的每一条真实提示词和踩过的坑全部摊开"。

- [ ] **Step 4: 校验**

Run: `grep -nE "/Users/binwu|my-workspace/_first-pass|my-knowledge-base|保存到.*\.md|remote-vault-on-mbp" ch01/README.md`
Expected: 无输出

- [ ] **Step 5: 提交**

```bash
git add ch01/README.md
git commit -m "docs: ch01 各节改为设问开场并指向后续章节实操"
```

---

### Task 3: ch02 关卡一 — 换 e2e 设计真实提示词（源：07-0）

**Files:**
- Modify: `ch02/README.md`（`### 关卡一` 的提示词引用块 + 周边说明）

**Interfaces:**
- Produces: ch02 占位符命名基准 `[commons-csv 源码目录（已切到 2026-07-11--13-29-e2e-tests 分支）]`。

- [ ] **Step 1: 读现状 + 源记录**

Read `ch02/README.md` 关卡一（约 79–120 行）。真实提示词见 wiki `ch02/07-0`。

- [ ] **Step 2: 换成清洗后的真实提示词**

把关卡一引用块替换为（去掉"保存到.md"、本机路径→占位符，保留分支名与工具版本）：

> 请阅读 commons-csv 的 user guide，特别留意"Parsing an Excel CSV File"一节，然后参考 [commons-csv 源码目录（已切到 `2026-07-11--13-29-e2e-tests` 分支）]，根据 user guide 中"Parsing an Excel CSV File"的内容，为我设计一个端到端测试：用该源码构建的 jar 包，用 `java` 命令执行一个带 `main()` 方法的 class，解析一个用 macOS 版 Excel 手工创建、至少含"Last Name"和"First Name"两列、至少 3 行记录的 CSV 文件，并打印输出到 console。
> 1）因为涉及把源码构建为 jar 包，是否需要在 home 目录下新建端到端测试目录？还是可以在源码根目录下创建一个 `e2e/` 测试目录来完成？请分析两种方案的利弊，按便利性推荐一种并说明理由。
> 2）请完整提供从把源码构建为 jar 包，到最后用 `java` 命令运行端到端测试的全过程步骤和命令。我已安装 openjdk 25.0.3 和 Apache Maven 3.9.16。

- [ ] **Step 3: 对齐周边分析**

确认关卡一提示词前的引导句仍成立（"整理后发给 Codex 的提示词是"），三段结构完整：① 开头点出问题"e2e 测试该建在哪" ② 提示词 ③ 后续 RAT/依赖踩坑体验。若分析引用了旧措辞，微调匹配。

- [ ] **Step 4: 校验**

Run: `grep -nE "/Users/binwu|my-workspace/_first-pass|my-knowledge-base|保存到.*\.md|remote-vault-on-mbp" ch02/README.md`
Expected: 无输出（后续关卡尚未改，可能仍有——本任务只需确认关卡一区段无残留；全文净空在 Task 7 末尾统一确认）

- [ ] **Step 5: 提交**

```bash
git add ch02/README.md
git commit -m "docs: ch02 关卡一换用 e2e 设计真实提示词"
```

---

### Task 4: ch02 关卡二 — 换 BOM 报错根因真实提示词（源：07-2）

**Files:**
- Modify: `ch02/README.md`（`### 关卡二` 提示词引用块）

- [ ] **Step 1: 读现状 + 源记录**

Read 关卡二（约 122–153 行）。源：wiki `ch02/07-2`。

- [ ] **Step 2: 换清洗后提示词**

替换为（保留两段命令输出对比，因它们是提示词教学实质；本机路径→占位符）：

> 请阅读 [e2e 目录下的 `ParseExcelCsvMain.java`]，分析在 [e2e 测试目录] 下运行下面针对 `Employees-by-excel.csv` 的命令报错的根因，并解释为何针对 `Employees.csv` 的同样命令不报错，然后提供解决方案。
>
> 针对 `Employees.csv` 的命令正常输出四行姓名；针对 `Employees-by-excel.csv` 的同样命令抛出：
> ```
> Exception in thread "main" java.lang.IllegalArgumentException: Mapping for Last Name not found, expected one of [Last Name, First Name, Department]
> ```

- [ ] **Step 3: 对齐三段**

确认：① 问题"换 Excel 真实导出文件同样代码就报错了" ② 提示词 ③ BOM/`xxd`/`U+FEFF` 踩坑体验（保留）。

- [ ] **Step 4: 提交**

```bash
git add ch02/README.md
git commit -m "docs: ch02 关卡二换用 BOM 报错根因真实提示词"
```

---

### Task 5: ch02 关卡三 — 换编译依赖缺失真实提示词（源：07-4）

**Files:**
- Modify: `ch02/README.md`（`### 关卡三` 提示词引用块）

- [ ] **Step 1: 读现状 + 源记录**

Read 关卡三（约 155–170 行）。源：wiki `ch02/07-4`。注意：关卡三现状可能无独立引用块（现文以叙述带过），需补一个提示词引用块以形成三段。

- [ ] **Step 2: 插入/替换清洗后提示词**

在关卡三报错描述后加引用块：

> 请阅读 [e2e 目录下的 `ParseExcelCsvMain.java`]，分析在 [e2e 测试目录] 下运行下面命令报错的根因，并提供解决方案：
> ```
> javac -cp ../target/commons-csv-1.14.2-SNAPSHOT.jar ParseExcelCsvMain.java
> ParseExcelCsvMain.java:10: error: package org.apache.commons.io.input does not exist
> import org.apache.commons.io.input.BOMInputStream;
> ...
> ```

- [ ] **Step 3: 对齐三段**

① 问题"改完代码编译又报新错" ② 提示词 ③ `mvn dependency:build-classpath` 避坑（保留）。

- [ ] **Step 4: 提交**

```bash
git add ch02/README.md
git commit -m "docs: ch02 关卡三换用编译依赖缺失真实提示词"
```

---

### Task 6: ch02 关卡四 — 换 PlantUML 转义报错真实提示词（源：08-2）

**Files:**
- Modify: `ch02/README.md`（`### 关卡四` 提示词引用块）

- [ ] **Step 1: 读现状 + 源记录**

Read 关卡四（约 172–203 行）。源：wiki `ch02/08-2`。

- [ ] **Step 2: 换清洗后提示词**

替换 C4 图生成/报错相关提示词为（vault 文档路径→占位符）：

> 请阅读 [C4 图相关的代码理解文档]，特别留意其中"C4 Model Dynamic Diagram（PlantUML）"一节和下方"我的实际输出"里的报错截图，然后分析在 plantuml.com 在线工具中复制粘贴上述 C4 dynamic diagram 脚本后、报该截图中错误的根因，并给出解决方案。

（注：关卡四还有一个"生成 C4 图"的提示词，现状在 ch02 line 176 已是清洗形式，保留即可；本步只换"修复 unquoted 报错"这一处。）

- [ ] **Step 3: 对齐三段**

① 问题"图贴到 plantuml.com 就报错" ② 提示词 ③ 预处理器不支持反斜杠转义、改单引号避坑（保留）。

- [ ] **Step 4: 提交**

```bash
git add ch02/README.md
git commit -m "docs: ch02 关卡四换用 PlantUML 转义报错真实提示词"
```

---

### Task 7: ch02 关卡五 + 2.4 — 换设计追问真实提示词并全文净空校验（源：09-0、10-0）

**Files:**
- Modify: `ch02/README.md`（`### 关卡五` 两个提示词 + `## 2.4` 模板回抽）

- [ ] **Step 1: 读现状 + 源记录**

Read 关卡五（约 205–213 行）与 2.4（约 215–246 行）。源：wiki `ch02/09-0`（Builder）、`ch02/10-0`（for 语法）。

- [ ] **Step 2: 换 Builder 追问提示词**

为关卡五第一个追问加/换引用块：

> 请查看 [e2e 测试的代码理解文档]，特别注意其中"关键调用时序"里"CSVFormat → CSVParser 构造"这一步，然后先解释 Builder 设计模式的定义、价值、没有它的危害、独特优势、劣势和适用场景，再结合 [e2e 目录下的 `ParseExcelCsvMain.java`] 解释为何这里用了 Builder 模式？`CSVFormat` 是如何调用 `CSVParser` 构造函数的？

- [ ] **Step 3: 换 for 语法追问提示词**

为第二个追问加/换引用块：

> 请查看 [e2e 目录下的 `ParseExcelCsvMain.java`]，解释其中第 31 行的 for 循环与其上一行 `.parse(in)) {` 之间是什么关系？这在 Java 里是什么写法？从 JDK 多少版本开始支持？

- [ ] **Step 4: 回抽 2.4 模板**

确认 2.4 四个模板与新真实提示词一致（模板四"追问设计细节"应能覆盖 Step 2/3 的问法：定义+价值+危害+优势+劣势+适用场景+结合具体源码）。现状模板已接近，仅核对措辞。

- [ ] **Step 5: 全文净空校验**

Run: `grep -nE "/Users/binwu|my-workspace/_first-pass|my-knowledge-base|保存到.*\.md|remote-vault-on-mbp" ch02/README.md`
Expected: 无输出

- [ ] **Step 6: 提交**

```bash
git add ch02/README.md
git commit -m "docs: ch02 关卡五换用设计追问真实提示词并回抽 2.4 模板"
```

---

### Task 8: ch03 3.1 — 换 e2e 视角需求真实提示词（源：13-0）

**Files:**
- Modify: `ch03/README.md`（`### 3.1.2` 提示词引用块，约 55–62 行）

- [ ] **Step 1: 读现状 + 源记录**

Read 3.1（约 25–123 行）。源：wiki `ch03/13-0`。

- [ ] **Step 2: 换清洗后提示词**

替换 3.1.2 引用块为（保留三条规则+五条不做的粘贴意图，vault 路径→占位符）：

> 请阅读本书 README（特别留意"目录"中的"第三章"）以及第二章（特别留意其中的端到端测试）。我打算在第三章用 EARS + TDD 开发新需求 `Strict Header Schema Validation Mode`，其描述如下：[粘贴需求描述、三条规则 `requiredHeaders`/`allowUnknownHeaders`/`enforceHeaderOrder`，以及五条"明确不做"]。
>
> 请问：这些新需求该如何添加到第二章"读取 Excel 导出 CSV"的端到端测试中，以便我将来测试？并能方便 commons-csv 用户在使用框架前就了解这些规则？是把规则写进文档给用户看，还是有其他更方便的途径让用户知道？请提供 3 个解决方案及其优劣势供我选择，最后推荐一个并说明理由。

- [ ] **Step 3: 对齐三段**

① 问题"第一个问题不是怎么实现，而是用户怎么发现"（现状 3.1.2 标题已是）② 提示词 ③ 三方案/四层发现路径体验（保留）。

- [ ] **Step 4: 提交**

```bash
git add ch03/README.md
git commit -m "docs: ch03 3.1 换用 e2e 视角需求真实提示词"
```

---

### Task 9: ch03 3.3 — 换纵向拆分真实提示词（源：13-2）

**Files:**
- Modify: `ch03/README.md`（`### 3.3.2` 提示词引用块，约 288–297 行）

- [ ] **Step 1: 读现状 + 源记录**

Read 3.3（约 260–335 行）。源：wiki `ch03/13-2`。

- [ ] **Step 2: 换清洗后提示词**

替换 3.3.2 引用块为：

> `$superpowers:brainstorming`
>
> 请阅读 e2e 测试视角下的新需求描述 [需求文档]，以及 e2e 测试视角下的新需求测试思路 [方案文档中"公开 `CSVHeaderSchema`，由 `CSVFormat.Builder` 显式接入（推荐）"一节]。
>
> 请帮我把这个新需求做**纵向拆分（vertical slicing）**[参考：拆分用户故事的指南]，拆成若干个 user story [参考：user story 的定义]，以便小步迭代完成整个新需求。请提供 3 个纵向拆分方案及其优劣势供我选择，最后推荐一个并说明推荐理由。每个方案都要用 "as a ..., I want to ..., so that ..." 格式给出 user story，并为每个 story 配一个**揭示用户价值**的标题。

- [ ] **Step 3: 对齐三段**

① 问题"为什么不能按值对象/builder/parser/文档拆"（3.3.1 已是）② 提示词 ③ 三方案+"能让低价值工作被延后"体验（保留）。

- [ ] **Step 4: 提交**

```bash
git add ch03/README.md
git commit -m "docs: ch03 3.3 换用纵向拆分真实提示词"
```

---

### Task 10: ch03 3.4 — 换"六份产出"生成真实提示词（源：13-4）

**Files:**
- Modify: `ch03/README.md`（`### 3.4.1` 提示词引用块，约 350–365 行）

- [ ] **Step 1: 读现状 + 源记录**

Read 3.4（约 337–460 行）。源：wiki `ch03/13-4`。

- [ ] **Step 2: 换清洗后提示词**

替换 3.4.1 引用块为：

> `$superpowers:brainstorming`
>
> 请阅读：
> - e2e 测试视角下的新需求描述 [需求文档]；
> - e2e 测试视角下的新需求测试思路 [方案文档中"公开 `CSVHeaderSchema`，由 `CSVFormat.Builder` 显式接入（推荐）"一节]；
> - 拆分后的用户故事 [拆分文档中"方案一：按可独立验证的规则变化切分（推荐）"一节]。
>
> 然后为**每个** user story 生成：1）英文标题（揭示用户价值）；2）"as a ..., I want to ..., so that ..." 格式的英文 user story 正文；3）中英对照的 EARS [参考：EARS 的定义与五种句型]；4）决策表 [参考：决策表测试的定义]；5）中英对照的 happy path 和 sad path 验收标准 [参考：验收标准的定义]；6）两段中英对照提示词，分别用 `superpowers:test-driven-development` 和 `superpowers:requesting-code-review` 实现该 story 并完成代码评审，以便复制粘贴给 Codex。要求：TDD 提示词中必须要求遵循上述 EARS、决策表、验收标准；并确保 [源码目录] 下所有自动化测试在新需求实现前后都运行成功。

- [ ] **Step 3: 对齐三段 + 保留决策表/Gherkin 样本**

① 问题"一段提示词，六份产出" ② 提示词 ③ Story 4 决策表/验收标准样本 + "省 token 经济学"（全部保留，D6 不动图表）。

- [ ] **Step 4: 提交**

```bash
git add ch03/README.md
git commit -m "docs: ch03 3.4 换用六份产出生成真实提示词"
```

---

### Task 11: ch03 3.6 — 换逐个实现 Story 的真实 TDD 提示词（源：13-4 内 TDD 提示词 / 13-6、14-0、15-0、16-0 输出）

**Files:**
- Modify: `ch03/README.md`（3.6 节，需先 Read offset≥497 定位）

- [ ] **Step 1: 读现状 + 源记录**

Read `ch03/README.md` offset 497 起，定位 `## 3.6`。3.4 已展示 Story 4 的 TDD 提示词（约 426–434 行）；3.6 应展示"请只实现其中的 Story N"这类逐个实现提示词与 Codex 输出体验。源：wiki `ch03/13-6`（story1）、`14-0`（story2）、`15-0`（story3）、`16-0`（story4）为 Codex 输出记录。

- [ ] **Step 2: 换/核对提示词**

若 3.6 内嵌 TDD 提示词，替换本机路径为 `[规格文档]`/`[源码目录]`，去掉"保存到.md"。确认与 3.4.3 展示的五段骨架一致，不重复整段（引用 3.4 即可）。保留各 Story 的 Codex 输出/踩坑体验叙述。

- [ ] **Step 3: 对齐三段**

① 问题"怎么逐个把四个 Story 落地" ② 逐个实现提示词 ③ 每个 Story 的 RED-GREEN 实操体验。

- [ ] **Step 4: 提交**

```bash
git add ch03/README.md
git commit -m "docs: ch03 3.6 换用逐个实现 Story 的真实 TDD 提示词"
```

---

### Task 12: ch03 3.7 — 换请求+接收 Code Review 真实提示词（源：17-1、18-1）

**Files:**
- Modify: `ch03/README.md`（3.7 节）

**Interfaces:**
- Consumes: 3.4 生成的规格文档（评审基准）。

- [ ] **Step 1: 读现状 + 源记录**

Read 3.7 节。源：wiki `ch03/17-1`（请求评审，含中文版提示词）、`ch03/18-1`（接收评审，仅英文，按书中中英对照惯例译为中文嵌入）。

- [ ] **Step 2: 换请求评审提示词（用 17-1 中文版清洗）**

替换为（本机路径→占位符，去掉"保存到.md"）：

> 请使用并遵循 `$superpowers:requesting-code-review`，对 [源码目录] 中 Story 1–4 的合并实现进行评审。将 [规格文档] 作为行为规格：评审前必须阅读共同约束及 Story 1–4 的全部 EARS 需求、决策表行和中英对照验收标准。
>
> 确定四个 Story 中第一个提交之前的 base SHA 与当前 HEAD SHA。按该 skill 要求派遣独立评审代理，检查累计真实 diff、当前源码、当前测试和测试接线；不得把实现摘要或历史实施记录当作证据。为每一条决策表规则和验收标准建立"需求—证据"矩阵，每行写出直接覆盖它的自动化测试类名/方法名，另行写出每个可运行手工 e2e 入口、fixture、参数和预期结果，并分类为：(1) 自动化直接覆盖、(2) 仅手工 e2e、(3) 仅间接、(4) 未覆盖；手工 e2e 不等于自动化覆盖，对类别 2–4 给出最小整改方案。
>
> 评审重点是四个决策表和验收标准是否被完整测试覆盖，尤其验证：公开不可变 `CSVHeaderSchema` 与 `CSVFormat.Builder#setHeaderSchema`；未配置 schema 的旧行为；parser 创建期间、任何 `CSVRecord` 暴露前失败；针对缺失/未知/顺序违规 header 的稳定断言而非整段报错；BOM 输入链；成功读取四条记录；以及 Story 4 五种组合。**若实现把"只要求声明列相对顺序"错误地做成"必须相邻"，应判定为不合格。** 同时完成常规代码评审，运行受影响测试和完整 Maven 测试套件，按 Critical/Important/Minor 报告发现。

- [ ] **Step 3: 换接收评审提示词（用 18-1 译中清洗）**

替换/加入接收评审提示词（核心保留：只修列出项、每处改动走 TDD、每修一项跑全量 `mvn test` 并记录命令与结果、脏工作树/手工 e2e 禁令）：

> 请在 [源码目录] 内工作。调用并遵循 `$superpowers:receiving-code-review` 处理指定评审发现，每处代码或测试改动都调用并遵循 `$superpowers:test-driven-development`。先完整读 [评审报告]（需求—证据矩阵）与 [规格文档]（共同约束、EARS、决策表、验收标准）。把规格文档当行为规格，把评审报告当权威范围清单，**只修列出项**，不扩范围。
>
> 必修范围：1）修复 `setIgnoreHeaderCase(true)` 下 header 顺序校验的 Critical 缺陷——校验须用解析到的物理表头序列而非 `headerMap.keySet()`；2）把矩阵中"仅手工 e2e"的每一行转成 `src/test` 下可运行自动化测试（复用 BOM 输入链与 fixture），覆盖 AC-S1-H1/AC-S2-H1/AC-S2-S1/AC-S3-H1/AC-S3-S1；3）修复"未覆盖"行，尤其补 Story 4 R4 直接自动化覆盖。
>
> TDD 与验证：每处改生产代码前先写最小失败测试并观察预期 RED，再最小实现；每完成一项跑 [源码目录] 下完整自动化测试套件（`mvn test`），记录命令与结果；全量失败则先按规格分析、最小修正，绝不为凑绿削弱正确断言。不得运行手工 e2e。

- [ ] **Step 4: 对齐三段**

① 问题"实现完就能合并了吗——谁来证明测试真覆盖了规格" ② 请求+接收两段提示词 ③ 评审发现（Critical `ignoreHeaderCase` 缺陷等）+ 接收整改体验。

- [ ] **Step 5: 提交**

```bash
git add ch03/README.md
git commit -m "docs: ch03 3.7 换用请求与接收 Code Review 真实提示词"
```

---

### Task 13: ch03 3.8 + 3.9 — 换故障注入真实提示词并回抽 3.9 模板（源：19-1）

**Files:**
- Modify: `ch03/README.md`（3.8 节 + 3.9 产出物模板）

- [ ] **Step 1: 读现状 + 源记录**

Read 3.8、3.9 节。源：wiki `ch03/19-1`（故障注入，仅英文，译中清洗嵌入）。

- [ ] **Step 2: 换故障注入提示词**

替换为（保留 commit range 与分支名，本机路径→占位符）：

> 请对 [源码目录]（分支 `2026-07-14-tdd-new-feature`，commit range `66a83820..ef286e5b`）中新增的自动化测试做严格的故障注入测试，证明每个新增自动化测试的断言真正检验了目标生产业务逻辑，而非假阳性通过。调用并遵循相关 superpowers skill，结果异常时用 `superpowers:systematic-debugging`，宣称成功前用 `superpowers:verification-before-completion`。**不得使用 `git reset --hard`、`git checkout --` 等破坏性命令。**
>
> 安全前置：只在源码目录内做源码检查、临时注入和 Maven 执行；确认分支与 HEAD 正确；改动前先跑 `git status --short`，**有任何输出立即停止并报告，不得 stash/丢弃/覆盖既有改动**；每次注入都必须是本机、最小、可逆的编辑，用完立即用最小反向补丁还原并用 `git diff --check`、`git status --short` 验证。
>
> 建立测试清单与基线：识别该 commit range 新增的全部测试源文件与自动化测试（排除手工 e2e）；注入前先跑 `mvn -Drat.skip=true test` 记录基线；阅读 [规格文档] 中 Story 1–4 的共同约束、决策表和验收标准。
>
> 为每个新增自动化测试建"覆盖映射表"（story 号+AC 标识、决策表条件/动作、测试类#方法+断言描述、对应生产类/方法、一个具体故障注入方案——选该测试断言应能抓到的最小业务逻辑缺陷，如绕过 schema 附加、漏掉必填 header 校验分支、允许未知 header、关闭声明列顺序比较、把校验移到记录暴露之后、删掉异常里的缺失/未知/违规 header 细节；不注入与被测行为无关的表面故障）。
>
> 逐个测试执行注入：注入前确认干净→记录原始位置与最小可逆补丁→只注入这一个故障到生产代码→用最窄可靠 Maven 命令（通常 `mvn -Dtest=类#方法 test`）跑对应测试→验证它因预期语义原因失败（编译/发现/超时/无关失败都不算成功注入）→立即用反向补丁还原并验证干净→重跑该测试确认通过。当前用例未完全还原且重新通过前，不得进入下一个。全部还原后再跑一次 `mvn -Drat.skip=true test`，与基线比对；工作树须与初始状态完全一致。

- [ ] **Step 3: 回抽 3.9 模板**

确认 3.9"行动清单和提示词模板"覆盖本章新真实提示词的模式（给边界→3方案→选择；EARS+决策表+AC+提示词一次生成；TDD 五段骨架；请求/接收评审；故障注入）。本机路径残留清零。

- [ ] **Step 4: 对齐三段**

① 问题"测试都绿了，怎么知道不是假阳性" ② 故障注入提示词 ③ 注入-还原执行体验。

- [ ] **Step 5: ch03 全文净空校验**

Run: `grep -nE "/Users/binwu|my-workspace/_first-pass|my-knowledge-base|保存到.*\.md|remote-vault-on-mbp" ch03/README.md`
Expected: 无输出

- [ ] **Step 6: 提交**

```bash
git add ch03/README.md
git commit -m "docs: ch03 3.8 换用故障注入真实提示词并回抽 3.9 模板"
```

---

## Self-Review 结果

- **Spec 覆盖**：D1（4 文件）→ Task 1–13 全覆盖；D2（真提示词替换）→ Task 3–13；D3（描述式占位符）→ Global Constraints + 每任务 Step；D4（README/ch01 重框不塞提示词）→ Task 1、2；D5（重写周边分析）→ 每任务"对齐三段"步骤。
- **占位符一致**：ch02 用 `[commons-csv 源码目录（...分支）]`/`[e2e 测试目录]`/`[ParseExcelCsvMain.java]`/`[代码理解文档]`；ch03 用 `[需求文档]`/`[方案文档...一节]`/`[拆分文档...一节]`/`[规格文档]`/`[源码目录]`/`[评审报告]`——全书统一。
- **无占位符残留**：每任务嵌入了清洗后的真实提示词全文，非 "TBD"。
- **Task 11 依赖**：3.6 现状未读全（文件 >1000 行），已在 Step 1 显式要求 Read offset≥497 定位后再改，避免盲改。
