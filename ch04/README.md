<a id="ch4-top"></a>

# 第四章 构建面向业务不变式的自动化验收测试

按照本章后面的提示词在你自己的 Agent 上实操一遍后，你将学会用 Approved Scenarios 模式，把决策表推导出的验收测试用例落地成产品经理也能读懂的、可在 IDE 里逐条单步调试的自动化验收测试。

如何解决 Agent 生成的测试和生产代码不可信的问题？

第三章已经用决策表推导出 11 条规则、17 个验收测试用例（这里沿用第三章汇总表背后的完整测试数据变体数——11 条规则在测试数据层面派生出 17 个具体用例，细节见第三章末尾汇总表）。本章要把这些用例落地成真正可执行、人类可读、能在 IDE 里逐条单步调试的自动化验收测试。

读到这里，建议你不要只看方案对比和调试要点，先翻到本章末尾复制那段提示词给你的 Agent，让它真刀真枪走一遍"设计方案 → TDD 实现 → 产出调试指南"的完整流程，再回头看看它有没有踩中本章提到的 `@TestFactory` 那个坑——这比单纯读懂一条踩坑经验，更能让你把它记住。

> [!TIP]
> **TL;DR · 读完本章你会：**
> - 理解 Approved Scenarios 如何把"验证一段断言代码"变成"评审一份人类可读的 markdown fixture"，解决 Agent 生成的测试和代码一起不可信的问题；
> - 看到三种自动化验收测试实现方案的对比（单一通用方法扫全部 fixture / 按决策表分组的 5 个方法 / 按 12 条规则分组的 12 个方法），以及为什么最终选择 “按决策表分组的 5 个方法”；
> - 学会用单步调试去亲眼验证"结构性问题抢跑""大小写忽略"这类规则在生产代码里的真实执行路径。

<a id="ch4-toc"></a>
**本章目录**：[4.1 Approved Scenarios 方法](#ch4-1) · [4.2 自动化验收测试实现方案](#ch4-2) · [4.3 用单步调试理解测试](#ch4-3) · [4.4 动手练习提示词](#ch4-hands-on)

<a id="ch4-1"></a>

## 4.1 用 Approved Scenarios 方法解决 Agent 生成的代码不可信的问题

<a id="ch4-def"></a>

#### 定义

Approved Scenarios 是一种把"测试和代码一起生成、一起不可信"这个问题，转化成"评审一份人类可读的 fixture 文件"的测试模式。它的核心做法是：为每个验收测试用例设计一份近似自然语言的 markdown 固定文件（approval file），这份文件同时包含输入数据和预期输出，格式贴合问题领域、便于肉眼扫描比对；测试执行逻辑只需要被验证一次——验证通过之后，新增测试用例就只是新增一份结构化的 markdown 文件，而非审查一段可能和生产代码一样不可信的断言代码。这种模式和 Gherkin（行为驱动开发常用的 Given-When-Then 语法）有相似之处，但更能贴合具体问题领域的呈现方式，这份额外的定制成本通常是值得的。

<a id="ch4-value"></a>

#### 价值

用 Agent 生成测试和生产代码的最大风险，是"测试和实现一起生成、一起生成错"——如果 Agent 把断言写成了"验证实现确实是这样做的"而非"验证实现应该这样做"，测试再绿也没有意义，而这种错误很难通过肉眼读一段 JUnit 断言代码发现。Approved Scenarios 把"这个测试在验证什么"从一段断言代码,变成一份不需要懂 Java 就能看懂的 markdown 文件——产品经理、测试、开发三方都能参与评审，而且评审的是同一份文件，并非"代码里的断言"和"文档里的描述"两份可能走偏的东西。

<a id="ch4-risk"></a>

#### 没有它的危害

没有这个模式时，review 一批 Agent 生成的测试，往往只能靠读断言代码——而断言代码的可读性天然比自然语言差，容易出现"测试看起来覆盖了这个场景，但断言写错了方向，永远不会失败"这种假阳性测试，直到真正的缺陷发生时才被发现测试形同虚设（这正是第五章要专门验证的问题）。

<a id="ch4-strength"></a>

#### 独特优势

fixture 文件既是"评审材料"又是"测试真正执行的输入"，这保证了"文档描述的行为"和"测试真正验证的行为"永远是同一份东西，不会出现代码注释和实现脱节那种"文档撒谎"的经典问题。新增测试用例在绝大多数情况下只需要新增一份 markdown 文件，不需要改 Java 代码，这让"新增验收测试"这件事的门槛大大降低，也让 Agent 生成新 fixture 的产出更容易被人工评审。

<a id="ch4-weakness"></a>

#### 主要劣势

需要为具体问题领域设计一套清晰的 markdown DSL（领域特定语言），这个设计和解析成本是一次性的，但如果需求形态跨度很大（比如本次既要表达"构造失败"又要表达"解析成功+记录内容"），DSL 的复杂度会上升；此外，如果测试入口组织不当（比如用不被 IDE 稳定支持的 JUnit5 特性），哪怕 fixture 本身设计得再好，也可能在"逐条单步调试"这个诉求上踩坑——4.2 节会具体展开这个教训。

<a id="ch4-fit"></a>

#### 适用场景

问题本身有一种直观、容易肉眼扫描验证的呈现方式（比如本次的"配置 + CSV 文本 + 预期异常/成功结果"），且用 Agent 批量生成测试、需要让非工程背景的人也能参与评审；特别适合"验收测试设计已经完成（比如第三章的决策表），只需要把设计落地成可执行测试"这个阶段。

<a id="ch4-nofit"></a>

#### 不适用场景

问题的输入输出没有直观的文本化表示（比如高度依赖二进制格式、图形渲染结果），或者测试用例数量极少、不需要批量评审机制。

<a id="ch4-2"></a>

## 4.2 自动化验收测试实现方案

> 下面展示的是作者本人真实跑过、本人验证过的一次会话内容的提炼，并非虚构的"典范问答"。

**过程：Agent 先问清楚"达标线"，再才敢动代码**

这份提示词要求 Agent 分两阶段交付：先出方案设计文档，再按推荐方案做 TDD 实现。Agent 没有直接动手设计方案，而是先做了一次 Round 0 假设体检，并抓住一个关键矛盾抛给我：我在提示词里说"我在维护 Apache Commons CSV"，这听起来像要一份能合并进上游的改动；但当前目录名和 `01-*`/`02-*`/`03-*` 这种递进式命名，又更像一套自用的"可调试"演练产物。这两种理解会直接决定它要不要为 `japicmp` 二进制兼容检查、`@since` 标注、RELEASE-NOTES、以及这个仓库高到 99% 的 JaCoCo 覆盖率门禁投入额外成本。它给了三个选项——"上游 PR 级""Kata 级（推荐）""只出测试制品不改生产代码"——并建议选"Kata 级"。我同意了它的建议：既要真改生产代码、真跑通 `mvn verify`，又不必为了凑上游合并门槛而稀释"可单步调试"这个核心目标。这一步给我的启发是：**同一份需求描述，在"真实身份"和"演练场景"之间是有歧义的，Agent 愿意先把这个歧义摆出来、而并非悄悄选一种理解往下走**，省掉了后面可能因为理解错位而整体推翻重来的风险。

**过程：我纠正了它一个想当然的默认假设**

Agent 在 Round 0 里列出的默认假设之一是"既有的热身阶段特征测试（`HeaderLookupTest`/`HeaderResolutionTest`/`HeaderValidationTest`）必须继续通过"。这个假设本身没错，但没说清楚：如果新增生产代码逻辑和这些旧测试的断言冲突了，该改哪一边？我把这一条明确纠正为：**只能修改那些特征测试让它们变绿，绝不能为了迁就旧测试而改动新的生产代码逻辑**——因为特征测试只是用来"固定现有行为"的辅助产物，并非本次需求的验收标准，不能让它反过来约束新需求该怎么实现。这条纠正后来确实发挥了作用：最终交付时生产代码的改动没有打破任何一个既有测试（见下文"是否一把过"），但如果没有这条事先说清楚的优先级，一旦真的冲突，Agent 很可能会选择一条更"保守"但错误的路——改生产代码去迎合旧测试。这件事给我的启发是：**"哪些约束是硬底线、哪些只是辅助性的参考"，必须在动手之前讲清楚优先级，而不能指望 Agent 自己猜对方向**。

**过程：决策表文档里的"约 27 个用例"是低估的，实际数清楚是 37 条**

Round 0 阶段 Agent 先按决策表文档的汇总表估算出"12 条规则 / 约 27 个用例"作为默认假设。但进入设计细节后，当我要求明确"验收测试用例"的粒度到底是"决策表里的一行"还是"每一个变体/每一次调用"时，Agent 按"每个变体算一条"重新从第三章的 Step 7 逐行展开核对，得到的真实数字是 **37 条**（后来又按我同意的建议再增补 3 条边界场景，最终落地 **40 条**）。这并非我纠正它，而是它自己在被我要求"把粒度定义清楚"之后，老老实实重新数了一遍、发现了自己最初估算的偏差,并坦白"约 27 是粗粒度估算，并非精确值"。这件事给我的启发是：**只要明确要求"按最小可执行单元逐条数清楚，而并非按文档里的汇总行数估算"，Agent 是会主动纠正自己的估算误差的**——但这个要求需要我主动提出来，它不会自己察觉"汇总行数"和"实际用例数"之间可能存在落差。

**过程：先出两个样板，确认格式后再批量生成，避免返工**

在批量生成 40 份 fixture 之前，Agent 主动提出先交付 2 个样板（一个失败用例、一个成功用例）供我确认 DSL 格式——字段命名、占位符写法、`## Expected` 断言方式是否认可，认可了才批量铺开剩下的 38 份。我认可了这个节奏。这给我的启发是：**对于"一次性要生成大批量结构相似产出"的任务，先用一两个样本对齐格式再批量铺开，是比"全量生成后再统一挑错"更省成本的返工防御手段**，尤其是在 token 预算有限的场景下，这种"小样本确认"几乎是免费的保险。

**产出：最终交付了什么**

问题前沿清空后，Agent 产出了两份文档和一套完整的测试/生产代码：

- **方案设计文档**（`ch04-1-solutions.md`）：对比了三个候选方案（单一通用方法扫全部 fixture / 按决策表分组的 5 个方法 / 按 12 条规则分组的 12 个方法），真实差异点都落在"测试入口怎么组织"这一个维度上——三个方案都遵守同一条硬性技术决定：**禁止用 JUnit 5 的 `@TestFactory` + `DynamicTest` 做"每个 fixture 一个独立测试入口"**，因为 VSCode 的 Java 测试扩展对动态测试在 Testing 面板里的支持长期有已知缺陷（面板经常不展开子节点，即便展开了单独调试也可能报错）；正确做法是用 `@ParameterizedTest(name = "{0}") + @MethodSource`，并用 `Named.of(displayName, fixture)` 包装每个 fixture。最终推荐并采纳了"按决策表分组的 5 个方法"（Table C / Table A / Table B / Interaction / Config）——顶层即业务结构，比"1 个方法扫全部"更容易按组定位失败用例，又比"按 12 条规则拆 12 个方法"样板更少。
- **调试指南文档**（`ch04-2-debug-guide.md`）：按 40 条验收用例逐一给出断点位置（类名+真实行号+该行代码）、要观察的变量、以及这个变量为什么能验证决策表里对应的那条规则，并附一张行号对照表。
- **生产代码**（真实修改，而非只给补丁建议）：`CSVFormat.java` 新增 `requiredHeaders` 字段及其 getter/setter/`equals`/`hashCode`/构造器处理；`CSVParser.java` 在 `createHeaders()` 方法末尾、`return new Headers(...)` 之前新增唯一的解析期校验点。这与 2.4 节的影响分析和第三章 ADR 决策 6/7（在 `CSVFormat` 构造期先做配置自洽性校验、缺列报错放在解析期）完全吻合。
- **测试代码**：`RequiredHeadersApprovalTest`（1 个类、5 个 `@ParameterizedTest` 方法）+ `ApprovalFixture`/`ApprovalFixtureParser`/`ApprovalFixtures`/`ApprovalScenarioExecutor`/`ApprovalFixtureRewriter` 这套解析器-执行器框架，以及按 table-c / table-a / table-b / interaction / config 五个子目录存放的 **40 份 `*.approved.md` fixture 文件**。生产代码和测试代码现已公开在 [`commons-csv-by-opencode-v2-with-deepseek-v4.1-flash-2026-10-11-with-warming-up-debug`](https://github.com/wubin28/commons-csv-by-opencode-v2-with-deepseek-v4.1-flash-2026-10-11-with-warming-up-debug) 仓库的提交 [`e7e92431`](https://github.com/wubin28/commons-csv-by-opencode-v2-with-deepseek-v4.1-flash-2026-10-11-with-warming-up-debug/commit/e7e92431c2be395a3b0bf20287a958117d6eb273) 里，感兴趣可以直接去看完整 diff。

**可信怎么实现的**

这份交付的可信度并非靠"看起来很完整"，而是靠几件具体的事：① 每份 fixture 既是"评审材料"又是测试真正读取并执行的唯一输入，不存在"文档说一套、代码断言另一套"的脱节风险；② 决策表文档里关于默认值的文字描述（比如"`duplicateHeaderMode` 默认 `DISALLOW`"）被 Agent 主动去源码核实后发现不准确——真实的公开构造入口 `CSVFormat.DEFAULT.builder()` 默认是 `ALLOW_ALL`——这个差异没有被悄悄抹掉，而是在对应 fixture 里用一行 `Note:` 记录下来，供人工评审知悉，决策表文档本身保持不动；③ 实现严格走了 TDD 红绿节奏并留有实测证据：先把 40 份 fixture 和测试桩都铺好、生产校验逻辑暂时注释掉，跑出"40 个测试、11 个失败"的红（恰好等于解析期校验相关的用例数），再恢复校验逻辑，跑出全绿；④ 全量回归 `mvn test` 从 1004 个测试变成 1043 个（新增 39 个，原有测试一个没少），`mvn verify` 保持 `BUILD SUCCESS`，JaCoCo 覆盖率门禁也全部满足。这几件事合在一起，让"测试和代码一起生成、一起不可信"这个风险，变成了一份可以被人逐条审阅、逐条单步调试验证的产出。

**是否一把过**

基本是一把过，但有一次关键的"先确认格式再批量生产"的止损动作：方案设计阶段的问答推进得很顺——我对 Agent 提出的绝大多数细节问题（测试入口粒度、目录命名、DSL 字段设计、异常消息断言严格度、regen 开关语义等）都是直接同意它的建议，没有发生"推翻重做"。真正值得记录的返工防御，是 Agent 没有等到 40 份 fixture 全部生成完才让我评审，而是先出 2 个样板确认格式——这意味着即便格式有问题，返工成本也只是改 2 份样板，而并非改 40 份。实现阶段按既定节奏一次性走完"全量铺 fixture → 跑红 → 实现到绿"，最终 `mvn test`/`mvn verify` 一次性全绿，没有出现"改完以后发现基础设计错了、要推翻重来"的情况。

<a id="ch4-3"></a>

## 4.3 用单步调试的方法理解 approved scenarios 测试方法

实现完成后，打开 IDE 的 Testing 面板，会发现测试方法；**展开前请先对它整体 Run 一次**——JUnit 5 的测试实例是运行时才计算出来的，面板需要先跑一次才能展开出每个用例的独立子节点。跑完一次后，面板会展开为若干个独立的子节点，每个节点右键都有单独的"Debug Test"。

**一个重要的共性说明**：因为所有测试共享同一套生产代码路径（`CSVFormat` 的配置自洽性校验和 `CSVParser.createHeaders()`），不同用例命中的其实是*同一组*断点位置。区分度来自于**你每次只 Debug 一个测试节点**——断点命中时变量里的值就是那一个用例的数据，不会和其他用例混在一起。这是推荐方案的权衡：用"调试入口独立"替代"断点位置独立"。

调试指南文档按全部 40 条用例逐一给出了断点位置、要观察的变量、以及这个变量为什么能验证决策表里对应的那条规则，并在末尾附了一张行号对照表，方便直接在 IDE 里定位。单步调试的价值不只是"确认测试会不会过"，更重要的是通过亲眼看到变量在断点处的真实取值，把第二、三章抽象推导出的业务规则和生产代码里具体的执行路径对应起来——这也是本书反复强调的"理解代码所承载的复杂业务逻辑"在实现阶段的延续，而并非一次性的任务。建议你完成自己的动手练习后，用同样的方法逐一核实自己实现版本里的真实断点和行号，而并非照抄上面提到的任何细节——这些细节会随你自己的实现而变化。

<a id="ch4-hands-on"></a>

## 4.4 动手练习提示词

为方便动手实操，这里把本章 4.1～4.3 节的“实现 Approved Scenarios 测试”和“产出调试指南”相应的提示词合并成一份完整的提示词。该提示词所安排的事项分两个阶段：阶段 A 先设计 3 个方案并推荐一个，阶段 B 按推荐方案完成 TDD 实现并产出调试指南。

使用前请把下面的占位符替换成你自己的实际信息（若某个占位符你暂时没有答案，可以保留原样，提示词里已经写了兜底规则）：

- `<CURRENT_DIRECTORY>`：你在 Agent 里设置的当前工作目录
- `<DIALOGS>.md`：保存你和 Agent 之间对话记录的文件路径，已备 Agent 因为 LLM 服务暂时不可用等种种原因中断时能切换到其他 Agent 继续
- `<COMMONS_CSV_REPO_PATH>` ：你要分析的代码库源码目录路径，可以从 `https://github.com/apache/commons-csv` 克隆到本地
- `<DECISION_TABLE_DOC_PATH>` ： 决策表文档的相对路径
- `<SOLUTIONS_DOC_PATH>` ： 方案设计文档的相对路径
- `<DEBUG_GUIDE_DOC_PATH>` ： 调试指南文档的相对路径

推荐你在上一章为实操创建的目录中继续本章的实操。

<details>
<summary>📋 点击展开/折叠完整提示词（可直接复制给 Agent）</summary>

提示词开始，请从这里往下全部复制

````
当前目录为

```
<CURRENT_DIRECTORY>
```

。请按照下面的要求完成任务，并在你每一次停下来等待我回复你的澄清问题时，将上一次追加前你我之间的完整对话（包括下面的Round 0）以markdown格式追加到

```
<DIALOGS>.md
```

文件（如果这个文件不存在，就新建）末尾，以便我在回复你的问题前仔细阅读。下面是要求：

## 1. Round 0（假设体检）

在开始设计树之前，先执行 Round 0（假设体检）。

1.1 列出我下面要求里没有明说、但你已默认成立的假设（逐条编号，形如：A1、A2、A3……）；
1.2 列出你还缺哪些关键信息，并说明每条信息会如何改变你的方案（逐条编号，形如：M1、M2、M3……）；
1.3 指出人们处理这类问题时最常犯的一个错误；
1.4 只提一个最关键的问题 —— 这个问题要能帮你搞清我的真实目标和具体处境，而并非最后给我一份谁都能套用的通用建议。这个问题的格式如下：

```
❓ **Q0** - **<问题标题>**: <问题正文，可能包含多个段落及选项>

➡️ <你的建议答案>
```

## 2. 推进设计树

Round 0 只问上述这一个问题，不要提前问别的。我回答后，你再按追问的常规轮次（frontier / 编号问题 / 推荐答案）推进设计树，直到前沿清空。

## 3. 追问

按下面追问的要求追问我的诉求：

---
名称: 追问
描述: 针对某项计划、决策或想法，对用户进行深入且严密的追问。适用于用户希望压力测试其思路，或使用了任何触发"追问"模式的关键词句时。
---

对用户进行持续、深入的追问，直到双方达成共识。将此过程构建为一棵**决策树**：每一项决策都会衍生出后续相关的决策分支。

按**轮次**推进这棵树的构建。**前沿（frontier）**指的是那些前提条件已确定的决策——即你可以**立即**提问、而无需猜测尚未获知答案的问题。在每一轮中，提出"前沿"上的所有问题：为每个问题编号并给出你的建议答案。随后等待用户回答，再进入下一轮。

每个问题的格式应如下所示：

```
❓ **Q1** - **<问题标题>**: <问题正文，可能包含多个段落及选项>

➡️ <你的建议答案>
```

用户在每一轮的回答都会重塑决策树：已确定的决策会将"前沿"向外推进，并解锁那些依赖于这些决策的问题。重新计算"前沿"并进行下一轮提问。如果某个问题的答案依赖于本轮中尚未确定的另一个问题，则该问题应归入**后续**轮次，而非当前轮次。

发掘**事实**是你的职责，绝非用户的职责。当"前沿"上的某个问题需要获取环境（如文件系统、工具等）中的事实信息时，应指派子代理（sub-agent，如果有的话）去查找；切勿向用户询问你自己就能查到的信息。不要因此停滞不前：正在进行的探索属于尚未确定的前提条件，因此只有依赖于该探索结果的后续问题才需要等待子代理的报告；你可以立即提出"前沿"上的其他问题。**决策**权在于用户：将每一项决策提交给用户，并等待其回应。

## 4. 我的诉求

你是一名资深 Java 测试工程师，精通 TDD（测试驱动开发）和 JUnit 5。你的任务分两个阶段：**阶段 A** 产出一份方案设计文档并给出推荐；**阶段 B**（在方案确定后）按推荐方案完成 TDD 实现，并产出一份 VSCode 单步调试指南文档。如果你是在非交互式、单轮执行的环境里运行（无法等待人工确认再继续），**直接采用你自己推荐的方案继续阶段 B**，不要中途停下来等待批准。

### 背景与约束

- 目标代码库：

```
<COMMONS_CSV_REPO_PATH>
```

这是一个标准 Maven 项目（Apache Commons CSV），`pom.xml` 已引入 `org.junit.jupiter:junit-jupiter`（JUnit 5）。**不要凭空假设代码库里任何类的字段/方法/默认值——必须实际打开源码文件核实**，具体要核实什么见下面「阶段 A 的必做勘查」。
- 本次要实现的需求原文：

> 我在维护 Apache Commons CSV。我们对账时，上游 CSV 应当包含若干固定列。我想给 `CSVFormat.Builder` 加一个 `setRequiredHeaders(String...)`：解析时一旦发现表头缺少这些列，就在读第一条记录之前抛出一个清楚说明缺了哪些列的错误，而不用等业务代码调用 `record.get("currency")` 时才报 `Mapping for currency not found`。

- 验收测试用例的**设计**已经由决策表方法完成，不需要你重新设计测试用例本身，你的任务是把它们**转化为可执行代码**。这些用例在

```
<DECISION_TABLE_DOC_PATH>
```

文档的"Step 7：将规则转换为测试用例"章节里，通常以"前置条件 / 测试数据 / 预期结果"的结构逐条列出。

- 实现方法必须是 **Approved Scenarios**（完整定义见本提示词末尾附录，务必先读附录再开始设计）：每个验收测试用例对应一份人类可读的 markdown fixture 文件，fixture 同时是"产品经理评审材料"和"测试真正读取并执行的唯一输入"——不能是两份互相脱节、手工保持同步的东西。
- 实现方法必须是 **TDD**（红/绿/重构）：每加入一条新的验收测试，先确认它在当前代码下会失败（红），再在生产代码里做最小改动让它通过（绿），必要时重构。
- 最终代码要能在 **VSCode** 里用 Testing 面板逐条单步调试：每个验收测试用例必须能在 VSCode 左侧 Testing 面板里展开为**独立的、可单独右键 "Debug Test" 的子节点**，不能是"全部用例共享 1 个不可拆分的测试入口"。

### ⚠️ 必须遵守的关键技术决定（来自真实踩坑经验，不要重新试错）

1. **禁止用 JUnit 5 的 `@TestFactory` + `DynamicTest` 来实现"每个 fixture 一个独立测试入口"**。原因：VSCode 的 `vscjava.vscode-java-test` 扩展对 `DynamicTest` 在 Testing 侧边栏里的支持长期存在已知缺陷——动态测试经常不会展开出子节点（面板只显示"No test results yet."），即便展开了，单独点击某个子节点调试也可能报错（这是微软官方仓库 `microsoft/vscode-java-test` 的 issue #1282「Dynamic tests should not be runnable from test explorer」和 issue #1643 记录的真实问题，长期未修复）。
2. **正确做法是用 `@ParameterizedTest(name = "{0}") + @MethodSource`，并用 `org.junit.jupiter.api.Named.of(displayName, fixture)` 包装每个 fixture**，让每个 fixture 变成一次独立的、有可读名字的 parameterized 调用。`@ParameterizedTest` 单个 invocation 的"单独重跑/调试"能力在该扩展的 0.35.0 版本就已经正式支持，是远比 `DynamicTest` 更可靠的选择。如果环境允许，检查一下本机 `vscjava.vscode-java-test` 的版本（扩展目录一般在 `~/.vscode/extensions/vscjava.vscode-java-test-*`），确认不低于 0.35.0。
3. **JUnit 5 的测试实例（无论 `@ParameterizedTest` 还是 `@TestFactory`）都是运行时才计算出来的**，VSCode Testing 面板必须先对该测试方法整体 Run 一次，才会展开出各个子节点——这是 JUnit 5 本身的机制，并非 bug，实现完成后要在调试指南文档里提醒用户这一步。
4. **不要相信决策表文档里关于"默认值"的文字描述，一定要去源码核实**。真实案例：某个决策表文档写"duplicateHeaderMode 默认 DISALLOW"，但实际源码里唯一公开可用的构造入口（`CSVFormat.DEFAULT.builder()` 这一类"预定义格式 + builder()"的入口）会显式把这个字段设成 `ALLOW_ALL`，文档的描述和代码的真实默认值不一致。遇到类似情况：以源码为准，在对应的 fixture 文件里用一行 `Note:` 向评审者说明这个差异，不要悄悄改掉决策表文档本身（那是已批准的设计产出）。
5. **如果 fixture 里会显式指定表头（并非"自动从首行解析"模式），CSV 测试数据里不要重复写一遍表头行**，除非你同时显式设置了"跳过表头行"的开关。这是因为"显式指定表头"和"自动从首行解析表头"在很多 CSV 解析库里是两种不同的代码路径，前者默认不会自动消费数据里的第一行，如果你把表头又抄了一遍到数据区，它会被解析成一条多余的数据记录，导致记录数断言多算了一条。写每个 fixture 前，先想清楚这一条测试数据用的是哪种表头模式。
6. **如果目标代码库有 Apache RAT 一类的许可证头检查插件**，你新增的 `*.approved.md` fixture 文件（纯 markdown 测试数据，并非可授权源码）会被判定为"未批准"而导致构建失败。检查 `pom.xml` 里是否已经为其他测试数据文件开过白名单（通常是 `<inputExcludes>` 配置块），照着已有写法给你的新目录加一条通配符豁免。

### 阶段 A：设计 3 个方案并推荐一个

#### A1. 必做的环境/代码勘查（不要跳过，不要假设）

1. 确认 `<COMMONS_CSV_REPO_PATH>` 是否已经是一个可用的 git 工作副本；如果需要先 clone，执行 clone。
2. 读 `<DECISION_TABLE_DOC_PATH>` 的"Step 7"章节，数清楚总共有多少条验收测试用例，记下每条用例的前置条件/测试数据/预期结果。如果该章节之前还有"决策表类型选择""条件/规则/动作"等章节，通读一遍以理解每条用例背后的业务规则。
3. 在代码库里搜索与上述需求原文相关的现有字段、方法、校验逻辑——即使这个功能"尚未实现"，也要找到**最合理的插入点**（比如现有的构造期校验方法、现有的解析期表头处理方法），并记录下具体的类名和当前行号。
4. 搜索代码库里 `src/test/resources` 一类目录下是否已经存在类似"approved/golden file"的测试模式先例；如果有，优先复用已有约定；如果没有，说明这是全新引入。
5. 检查本机开发环境（如果你有权限执行命令）：IDE 的 Java 调试/测试相关扩展是否已安装，版本是多少。
6. 把以上勘查到的**事实**（并非假设）写成文档开头的"前置事实"小节。

#### A2. 设计 3 个方案

三个方案的**真实差异点**应该放在"测试入口如何组织"这个维度上（例如：单一通用 DSL + 单一测试方法 vs 按决策表分组的多个测试方法 vs 每条用例独立可调试入口），而并非在"是否采用 Approved Scenarios"这个已经定好的大前提上重复横向对比——那个大前提是本提示词已经替你决定好的，不需要再作为方案差异点。每个方案要写清楚：主要内容、优势、劣势、适用场景、不适用场景；并给出最终推荐方案和理由，再附上"CI/CD 如何运行这些测试"和"新增测试用例时该如何操作"两节说明。

挑选推荐方案时，优先权重给"VSCode 单步调试体验"这个维度（如果用户的原始诉求里反复强调了这一点），但要用上面「必须遵守的关键技术决定」里第 1、2 条的结论来实现它——也就是说，"调试体验最好的方案"在具体落地时要用 `@ParameterizedTest + Named`，不要用 `@TestFactory + DynamicTest`。

把这份方案设计文档保存为 

```
<SOLUTIONS_DOC_PATH>
```

### 阶段 B：按推荐方案实现

1. **TDD 实现生产代码**：逐条按决策表的用例在生产代码里加最小改动，让测试由红变绿。每处改动前先读现有代码的组织方式（字段声明顺序、方法分工），让新增代码风格与现有代码一致，不要引入新的代码风格。
2. **设计并写出 fixture 的 markdown DSL**：至少要能表达"输入配置""CSV 原始数据（如适用）""预期结果（成功/失败，失败时的异常类型与消息，成功时要验证哪些字段）"。为"未设置/留空/显式置空"等需要区分的场景设计清晰的占位符写法（比如用 `(none)`、`(empty)`、`(auto)`、`(default)` 这类带括号的词，或 `<NULL>`、`<BLANK>` 这类带尖括号的特殊标记），并在文档里说明每个占位符的含义。
3. **写出 fixture 解析器和执行器**：解析器只管"读懂 markdown 结构"，执行器只管"把解析出来的数据变成真实的 API 调用并断言"，两者分开，任何一方都不应该因为对方的实现细节而被迫改动。
4. **写出测试入口类**：用 `@ParameterizedTest(name = "{0}") + @MethodSource`，`@MethodSource` 方法扫描 fixture 目录（建议按决策表分组用子目录），对每个 fixture 文件用 `Named.of(displayName, fixture)` 包装后传入。
5. **全量回归验证**：跑一次完整的构建/测试命令（比如 `mvn verify` 或该项目的等价命令），确认新增的全部用例通过，且没有破坏任何已有测试。如果构建失败（比如许可证检查插件报错），按「必须遵守的关键技术决定」第 6 条处理，不要用 `--no-verify`/跳过检查之类的手段绕过。
6. **产出调试指南文档**（

```
<DEBUG_GUIDE_DOC_PATH>
```

），结构参考：打开仓库文件夹后 Testing 面板怎么用、每一条验收测试用例一节（断点：类名+行号+这一行代码内容；观察变量：变量名+预期值+为什么能验证对应规则）、测试执行逻辑本身的 1~3 个关键断点、最后附一张"行号对照表"。

每条用例的断点必须给出**真实存在**的类名和行号（在你实际写完生产代码之后，用 grep 或者直接打开文件数行号来核实，不要凑数或估算），并且要说清楚"观察这个变量能验证决策表里的哪条规则"——调试指南的核心价值是帮助读者通过单步执行理解代码逻辑，并非单纯罗列断点坐标。

### 两份文档的质量标准（务必自查）

- 每一个判断、每一个"默认值是什么""应该在哪里插入校验逻辑"之类的结论，都必须能追溯到你在阶段 A1 实际核查的事实或阶段 B 实际跑通的测试结果，不能是"通常应该是……"这种猜测性表述。
- 方案对比部分，"优势/劣势/适用场景/不适用场景"四项都要填，不能有空泛的套话（比如不能只写"维护性更好"而不说清楚好在哪、对谁而言）。
- 调试指南里的每一条断点说明，读者应该能不看生产代码原文就理解"命中这里意味着程序正在检查什么"。
- 两份文档之间要能相互印证：调试指南里提到的"为什么选这个技术方案"，应该能在方案设计文档的"推荐理由"里找到对应说法，不能自相矛盾。

## 附录：Approved Scenarios 模式完整说明

> 以下内容完整摘自 `approved-scenarios.md`（Augmented Coding Patterns 文档集），逐字保留，供实现时直接参考。

### Problem

Generating both tests and code with the AI and not checking is risky, but the AI is also prone to generating lots of tests quickly. Reviewing many AI-generated tests quickly becomes impractical, especially when assertions are complex.

### Pattern

Design tests around approval files that combine input and expected output in a domain-specific easy-to-validate format. This is a special case of the Constrained Tests pattern.

Validate the test execution logic once. After that, adding new test cases only requires reviewing fixtures.

Structure each approval file to contain:
- Input data (context, parameters, state)
- Expected output (results, side effects, API calls)
- Format adapted to your problem domain for easy scanning

The test runner reads fixtures, executes code, and regenerates approval files. Validation becomes a simple diff review.

This pattern works best for problems that have an intuitive visual representation that is straightforward to check, but can also be used for checking call sequences.

### Example

The pattern adapts to different domains:

**Testing a multi-step process with external service calls:**

Create fixtures like `checkout-with-discount.approved.md`:
```markdown
## Input
User: premium_member
Cart: [{product_id: "laptop-123", quantity: 1}, {product_id: "mouse-456", quantity: 1}]
Discount code: SAVE20

## Service Calls
POST /inventory/reserve
  {"items": [{product_id: "laptop-123", quantity: 1}, {product_id: "mouse-456", quantity: 1}]}
Response: 200 {"reservation_id": "res_789"}

GET /pricing/calculate
  {"items": [{product_id: "laptop-123", quantity: 1}, {product_id: "mouse-456", quantity: 1}], "user": "premium_member"}
Response: 200 {"subtotal": 1250, "discount": 250, "total": 1000}

POST /payment/process
  {"amount": 1000, "reservation_id": "res_789"}
Response: 200 {"transaction_id": "txn_abc"}

## Output
Order: confirmed
Total: $1000
Email sent: order_confirmation
```

Single test reads all `.approved.md` files, executes flows, regenerates files with actual results. Review is scanning markdown diffs, not reading assertion code.

**Testing visual algorithms:**

Create fixtures like `game-of-life-glider.approved.md`:
```markdown
## Input
......
..#...
...#..
.###..
......

## Result
......
......
.#.#..
..##..
..#...
```

Test reads all game-of-life fixtures, computes next generation, verifies output matches. Adding new test cases is drawing ASCII patterns - trivially easy to validate correctness by eye.

**Testing refactorings:**

Create fixture pairs like `inline-variable.input.ts` and `inline-variable.approved.ts`:

This example uses two separate files. One for the input and one for the expected output. The header contains the command that generates the approved output.

Input file:
```typescript
/**
 * @description Inline variable with multiple usages
 * @command refakts inline-variable "[<CURRENT_FILE> 8:18-8:21]"
 */

function processData(x: number, y: number): number {
    const sum = x + y;
    const result = sum * 2 + sum;
    return result;
}
```

Expected output file:
```typescript
/**
 * @description Inline variable with multiple usages
 * @command refakts inline-variable "[<CURRENT_FILE> 8:18-8:21]"
 */

function processData(x: number, y: number): number {
  const result = (x + y) * 2 + (x + y);
  return result;
}
```

### Note

This pattern has similarities to Gherkin but better adapts to the specific domain, making the extra indirection worthwhile.
````

提示词结束，以上内容请整段复制给 Agent。

</details>

跑完之后，自己在 IDE 里按产出的调试指南走一遍，特别关注"结构性问题抢跑"这一类用例——亲眼看到"必需列检查那个断点永远不会命中"，比读十遍文字描述都更有说服力；也把你的 Agent 推荐的方案和本章 4.2.3 节的方案3对比，看看它有没有踩中"用 `@TestFactory` 实现独立调试入口"这个真实的坑。下一章会用故障注入的方法，进一步验证这些测试是否真的在保护对应的生产代码，而非形同虚设。

验收测试已经变绿、也能逐条单步调试了——但"绿"不等于"真的在保护代码"。第五章会用故障注入测试，故意在生产代码里埋几个与测试保护意图语义对应的故障，亲眼验证这些测试是否真的会因此变红，排除"形同虚设"的可能，这是"评测"这一步要交付的证据。

---

⬅️ 上一章：[第三章 设计面向验收测试的 Spec](../ch03/README.md#ch3-top) ｜ ➡️ 下一章：[第五章 评测自动化验收测试确实保护了生产代码](../ch05/README.md#ch5-top)
