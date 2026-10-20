<a id="ch3-top"></a>

# 第三章 设计面向验收测试的 Spec

按照本章后面的提示词在你自己的 Agent 上实操一遍后，你将学会用 [ADR](https://adr.github.io/)（Architecture Decision Record）裁决需求里没讲清楚的开放设计问题，再用决策表测试法系统性地推导出一份不遗漏、不重复的验收测试用例清单。

在让 Agent 针对新需求生成生产代码之前，如何判断 Agent 生成的验收测试用例能全面覆盖新需求的业务且没有遗漏和重复？

第二章的影响分析已经确定了改动会落在哪些类，也留下了 6 个开放设计问题没有裁决。本章要做两件事：先用轻量的 ADR 把这些开放问题逐一裁决，再用 [Decision Table Testing](https://www.virtuosoqa.com/post/decision-table-testing)（决策表测试法）把裁决结果系统性地推导成一份不遗漏、不重复的验收测试用例清单。

如果你想真正检验自己有没有吃透决策表的推导逻辑，不妨先快速浏览一遍本章，再把本章末尾的提示词复制给你的 Agent，让它独立跑一遍同样的 ADR 裁决和决策表推导，然后拿它的产出和本章逐条对比——出现规则数、测试用例数对不上的地方很正常，此时需要用你的判断力来分析和取舍，当然如果遇到困难，也可以求助于 Agent。

> [!TIP]
> **TL;DR · 读完本章你会：**
> - 看到第二章留下的 6 个开放设计问题，如何用一份轻量 ADR 逐条裁决、每条决策都能追溯到具体代码证据；
> - 学会决策表测试法的七步推导流程：识别条件 → 定义取值 → 计算规则数 → 识别动作 → 填充 → 化简 → 转为测试用例；
> - 看到真实案例——`required headers` 需求最终被拆成 3 张小表、11 条规则、21 个验收测试用例；
> - 理解为什么"条件之间会互相影响"（比如大小写敏感性依赖另一个条件）正是决策表要系统处理的典型场景，而非简单的独立二元判断。

<a id="ch3-toc"></a>
**本章目录**：[3.1 用决策表推导验收测试](#ch3-1) · [3.2 ADR 决策记录](#ch3-2) · [3.3 决策表](#ch3-3) · [3.4 动手练习提示词](#ch3-hands-on)

<a id="ch3-1"></a>

## 3.1 用 Decision Table 推导验收测试以便不遗漏不重复

<a id="ch3-def"></a>

#### 定义

决策表测试法是一种把"条件（condition，影响系统行为的输入变量）× 条件取值 × 动作（action，系统对应的响应）"组织成表格、再系统性枚举所有有意义组合的测试设计方法。表的每一列是一条规则（rule），对应一种条件取值组合和它应该触发的动作；表格本身既是设计工具，也是可以直接转成测试用例的产出物。根据条件取值是否限定为二元（True/False），决策表可以分成有限决策表（Limited）、扩展决策表（Extended，条件可以有多个取值）等不同形态；条件之间存在依赖/门控关系时，则适合用规则型决策表（Rule Based）来组织，同一张大表拆成多张互相关联的小表，避免条件数一多就出现规则数爆炸。标准的推导流程是七步：识别所有条件、定义条件取值、计算规则数、识别所有动作、填充表格、尽可能化简（用"don't care"标记对结果无影响的条件）、把每条规则转成测试用例。

<a id="ch3-value"></a>

#### 价值

决策表测试法把"验收测试覆盖全不全"这件事，从"我觉得应该测这几种情况"的主观列举，变成一套可追溯、可复核的系统性推导——每一条测试用例都能回答"它在验证哪条规则、为什么这条规则需要被验证"。对于用 Agent 生成验收测试的场景，这一点格外关键：Agent 很擅长"照着一个例子生成几个相似的变体"，但不一定天然具备"这批测试用例有没有漏掉某种条件组合"的判断力，而决策表恰好能把这种判断力显式地落到纸面上。

<a id="ch3-risk"></a>

#### 没有它的危害

没有系统性的推导工具时，验收测试用例的设计很容易变成"想到哪测到哪"——覆盖了几个直觉上"明显"的场景，却漏掉了"两个条件叠加才会触发"的边界组合（比如本章要处理的"大小写差异"和"必需列缺失"同时发生时该怎么判定）。更隐蔽的风险是**重复**：没有意识到两个看起来不同的测试用例，其实在验证同一条规则，白白增加维护成本却没有换来新的覆盖率。

<a id="ch3-strength"></a>

#### 独特优势

决策表测试法天然适合"条件之间会互相影响"的业务规则场景——比如"大小写是否生效"这个结果依赖于"是否存在大小写差异"这另一个条件，这正是决策表要系统处理的典型情形，而并非简单的独立二元判断。它还强制你在"化简"这一步显式写出"为什么这个条件在这里不重要"，这个动作本身就是对系统行为理解程度的一次检验——写不出理由，往往说明理解还不到位。

<a id="ch3-weakness"></a>

#### 主要劣势

条件数一多，笛卡尔积会指数级增长（10 个二元条件就是 1024 条规则），没有拆分成多张小表、没有识别条件间的依赖关系去化简，表格会变得难以创建、审查和维护；它也更适合离散、分类的输入，面对连续区间的输入（时间、价格）需要先用等价类划分把区间收敛成有限的类别，再套用决策表。

<a id="ch3-fit"></a>

#### 适用场景

业务规则涉及多个会互相影响的离散条件、且需要"完全可追溯"的验收测试覆盖率证明（比如本章这种"条件之间存在门控关系"的真实场景）；需要把一批容易靠直觉遗漏或重复的测试场景，变成可以让产品经理、测试、开发三方共同评审的结构化产出物。

<a id="ch3-nofit"></a>

#### 不适用场景

条件本身是连续区间且没有先做等价类划分；或者需求本身只有一两个互相独立的二元开关，用决策表反而比直接列举测试用例更繁琐。

<a id="ch3-2"></a>

## 3.2 开放设计问题决策记录（ADR，Architecture Decision Record）

> 下面并非 Agent 产出文档的全文，而是我把 3.4 节的"动手练习提示词"发给 OpenCode V2 搭配 GLM 5.3 之后，真实发生的问答过程与产出复盘。

### 过程：Agent 问了什么，我怎么答的

Round 0，Agent 没有先列一堆假设就急着提问，而是先做了一次事实核查：确认 `./commons-csv` 已就位且 HEAD 与上一章两份上游文档的基线一致、工作区干净，再通读两份上游文档。随后它列出 A1~A10 共 10 条默认假设（比如"产出语言为中文""验收测试用例只写结构化文字描述、不写可编译代码""只产出三份 Markdown，不碰生产代码"）和 M1~M5 共 5 条缺失信息，指出"跳过先裁决开放设计问题、先核查代码事实这两步，凭直觉画一张笛卡尔积大表"是最常见的错误，然后只问了 Round 0 唯一的问题：**ADR 里这 7 个开放设计问题，到底该由谁拍板？**它给了三个选项——(a) 推荐制（它给决策+证据+影响，我确认或改写）、(b) 拍板制（我亲自逐条决定，它只摆证据）、(c) 授权制（全部交给它裁决，我只在最终文档上验收）——并推荐选 (a)，理由是这正贴合追问协议"发掘事实是它的职责、决策权在我"的分工。

**我没有采纳它的推荐**：它建议 (a)，我选了更彻底的 (c)——基于它的最佳判断一次性裁决全部 7 个问题，我只在最终产出的文档上做评审。我同时答了它另外提的 M1~M5：少而精即可，不设用例数量硬指标；前沿清空后一次性交付两份文档，不用先交 ADR 等我确认；"安全网回归"（比如 `get("nope")` 晚失败不回归、全量 1003 例零破坏）不属于本次验收范围，因为这次只做设计、不改生产代码；对话记录里，较长的初始提示词写摘要即可，我之后较短的回复逐字记录。选 (c) 而非它推荐的 (a)，是因为上一章的影响分析文档已经把这 7 个问题的权衡摆得很清楚，我没必要把同一轮权衡在这一章再重新走一遍——让 Agent 基于证据自己裁决、我只验收结果，对我来说效率更高。

答完 Q0 和 M1~M5 之后，Agent 在同一轮里直接宣布"前沿清空"：因为 (c) 授权制把 7 个 ADR 开放问题、决策表类型、条件取舍、变体派生全部划给了它的裁决权，不再有任何前提未定的问题需要等我答复。它随即一次性走完第一步代码核查、第二步 ADR、第三步决策表，在同一轮里把两份文档都交给我——整个过程只用了 Round 0 和 Round 1 两轮，没有再出现像上一章那样逐题交锋的中间轮次。

### 产出：ADR 文档里写了什么

Agent 给我产出的 ADR 文档（`ch03-2-adr.md`）一共裁决了 10 条决策（D1~D10），每条都附"决策 + 基于哪条代码核查事实的理由 + 会影响决策表哪个条件/动作"：缺必需列抛受检的 `CSVException`（消息模板 `Missing required header(s) [x] in [实际表头]`），配置矛盾（设了必需列却没启用任何表头模式）抛非受检的 `IllegalArgumentException`（D1）；大小写/空白匹配语义完全复用既有的 `headerMap`——跟随 `ignoreHeaderCase`、不 trim、不忽略空白（D2）；与现有结构性表头错误（空列名、重复列名）的交互顺序上，结构错误绝对优先（D3）；写方向（`CSVPrinter`）完全不校验（D4）；自动探测和手工指定两种表头模式下，必需列校验统一只在解析期 `createHeaders()` 末尾做单点校验，不在构建期提前查（D5）；设了必需列却没启用任何表头模式时，构建期就 fail-fast 抛错（D6）；`null`/空数组都归一化为"未设置"，名单自身重复不报错但报缺失时去重保序，名单里的空白名字不做特判（D7）；空文件配合自动探测时不特判，统一按"缺全部列"处理（D8）；手工指定模式下，必需列对照的是手工名单本身而非文件首行内容（D9）；以及 `trim`、`ignoreSurroundingSpaces`、`skipHeaderRecord`、`maxRows` 等既有方言参数跟必需列校验零交互、不引入任何新规则（D10）。

### 可信怎么实现的

这份 ADR 的可信度，在我看来同样靠一件具体的事可以拿来对照检查：**每一条决策都能指回某条实地核查到的代码事实**（E1~E15），而非"我觉得应该这样"——比如缺列抛 `CSVException`（D1）追溯到既有异常体系里"声明自身的错误用非受检异常、输入数据不满足声明用受检异常"这条既有分界线（E10），大小写匹配复用 `headerMap`（D2）追溯到 `ignoreHeaderCase` 靠 `TreeMap(CASE_INSENSITIVE_ORDER)` 还是 `LinkedHashMap` 生效的实现细节（E6）。这种"每一条决策都能被追问、都经得起追问"的结构，比文档本身读起来顺不顺更能说明它值不值得信。

### 是否一把过

是，但"一把过"的含义跟常见情形不太一样：这次没有发生"Agent 推荐、我否决某一条具体设计决策"的交锋，因为我在 Q0 就选择了把全部 7 个开放问题的裁决权交给 Agent——真正被我否决的其实是它对**协作模式本身**的推荐（它建议 (a) 推荐制，我选了更彻底的 (c) 授权制）。从那之后，10 条决策全部由 Agent 自主裁决、一次性产出，没有任何一步被我打回重做。

<a id="ch3-3"></a>

## 3.3 决策表

> 同样不贴全文，我先讲清楚产出的分量和可信度在哪，具体表格留给你自己跑一遍提示词时亲眼看。

### 过程：从"前沿清空"直接推进到七步法，没有新的澄清问题

3.2 的 ADR 和这一节的决策表，其实是 Agent 在同一轮回复里一次性产出的——因为我在 Q0 选了 (c) 授权制，前沿在那一刻就已经清空，Agent 没有分两轮交付，而是把第一步代码核查、第二步 ADR、第三步决策表七步法连着走完，才停下来等我评审。七步法里最容易被跳过的一步——**挑决策表类型并给出理由**——它也没有省：把 Limited / Extended / Condition Action / Switch 四种都排除了（条件是多值的、条件之间存在门控关系、并非简单的一对一映射、没有单一主控条件），选择了 Rule Based（规则型）决策表，并按**生命周期阶段**拆成三张表——构建期决策点（Table A）、解析期决策点（Table B）、打印期决策点（Table C），还画了一张流程图说明三张表之间"谁是谁的前提"。这三张表怎么拆、哪个是哪个的前提，全部由 Agent 依据 ADR 已经定下的决策（尤其是 D5"解析期单点校验"和 D4"写方向不校验"）自行推导，不需要我再做任何取舍。

#### 表间关系

本特性在**构建期**和**解析/打印期**各有一个判断，解析与打印又是同一个 `CSVFormat` 的两个互斥使用方向。故拆成三张表：

```
构建期（CSVFormat.Builder.get() → validate()）
  └─ 表 A：build 期决策点            [3 条规则]
        ├─ 必需列已设置 × 未启用任何表头模式 → 抛「配置矛盾」错，表 B/表 C 均不可达
        └─ build 通过 ──────────────┬──────────────┐
                                    ▼              ▼
          解析期（CSVParser → createHeaders()）     打印期（CSVPrinter 构造/打印）
          表 B：parse 期决策点  [7 条规则]            表 C：print 期决策点  [1 条规则]
          （"读"方向：required 生效）                  （"写"方向：required 恒不生效）
```

- **表 A 与表 B/表 C 的关系**：表 A 是表 B、表 C 共同的**前提**——只有 build 期通过的配置才能到达 parse 或 print；表 A 的"配置矛盾"分支一旦触发，后两张表都不可达。
- **表 B 与表 C 的关系**：**互斥独立**——同一个 `CSVFormat` 要么用于读、要么用于写，required 只在读方向生效（ADR D4），两张表互不依赖，是并列的两个决策点。

### 产出：39 条规则化简到 11 条、派生出 21 个验收测试用例

真正让我觉得体现出"决策表测试法"价值的是化简这一步（产出 `ch03-3-decision-tables.md`）。如果不做任何化简，三张表理论上一共要穷举 39 个条件组合（表 A：3 个表头模式 × 2 个必需列设置状态 = 6；表 B：2 个必需列状态 × 2 个结构错误存在性 × 4 种差异形态 × 2 个 `ignoreHeaderCase` 取值 = 32；表 C：1）；Agent 通过识别"门控关系"（比如必需列未设置时，差异形态和大小写配置根本无意义；存在结构性表头错误时，必需列相关条件全部失效）和"don't-care"（比如差异形态是"无差异"或"仅空白"时，`ignoreHeaderCase` 取哪个值结果都一样），把表 A 的 6 个组合压缩到 **3 条规则**、表 B 的 32 个组合压缩到 **7 条规则**、表 C 保持 **1 条规则**，三表合计 **11 条规则**，并给我写清楚了对每一个被标记为"don't-care"的格子、每一个被剔除的"不可能组合"的具体理由——并非机械地标个"–"就算了。最后从这 11 条规则派生出 **21 个验收测试用例**（表 A 5 个、表 B 15 个、表 C 1 个），除了覆盖核心判定逻辑，还专门设计了几个"反向验证"变体——比如用必需列全部命中的表头去触发结构性错误（证明结构错误仍然优先）、手工指定表头模式下让文件首行"看似满足却不该被采信"（证明对照的是手工名单而非文件内容）——这些恰恰是我自己手工列用例时最容易漏掉的部分。

### 可信怎么实现的

这份决策表的可信度，在我看来同样靠一件具体的事可以拿来对照检查：**每一条"排除"或"折叠"都写了为什么**——比如 `DuplicateHeaderMode` 的三个取值没有被单独列成条件，而是归入"结构性表头错误存在性"这一个条件的取值来源，理由写得很具体：细粒度语义是现状行为、已被特征测试钉住，列为独立条件只会让规则数按 2^n 膨胀却不增加任何区分度；`trim`、`ignoreSurroundingSpaces` 也被排除，理由是它们作用于表头 key 的**形成阶段**，发生在校验之前、与校验零交互。这种"每一步化简都能被追问、都经得起追问"的结构，而并非只是表格本身看起来工整，才是它值不值得信的真正依据。

### 是否一把过

是。3.2 的 ADR 和这一节的决策表是在同一轮回复里连着产出的，Agent 没有在决策表阶段引入任何需要我重新决策的新问题——类型选择、拆表方式、化简策略全部是它依据 ADR 已有决策自主推导出来的，整个七步法走完也没有发生推翻重做。


<a id="ch3-hands-on"></a>

## 3.4 动手练习提示词

为方便实操，这里把本章 3.1～3.3 节的“裁决开放设计问题（ADR）”和“推导决策表”相应的提示词合并成一份完整的提示词，让你一次性走完"代码核查 → ADR → 决策表七步法"的完整链路。

使用前请把下面三处占位符替换成你自己的实际信息：

- `<WORK_DIRECTORY>`：你在 Agent 里设置的当前工作目录
- `<DIALOGS>.md（可以使用./ch03-1-dialogs.md）`：保存你和 Agent 之间对话记录的相对路径，已备 Agent 因为 LLM 服务暂时不可用等种种原因中断时能切换到其他 Agent 继续
- `<SUBDOMAINS_DOC_PATH>（即上一章的产出./ch02-2-2-subdomains.md）`：子域分析文档的相对路径
- `<IMPACT_ANALYSIS_DOC_PATH>（即上一章的产出./ch02-2-3-impact-analysis.md）`：影响分析文档的相对路径
- `<COMMONS_CSV_REPO_PATH>（即上一章使用的./commons-csv）`：commons-csv 代码库在本机的路径（推荐和上一章保持一致，即当前工作目录下的 `./commons-csv` 子目录）
- `<ADR_DOC_PATH>（可以使用./ch03-2-adr.md）`：架构决策记录（ADR）文档的相对路径
- `<DECISION_TABLE_DOC_PATH>（可以使用./ch03-3-decision-tables.md）`：决策表文档的相对路径

推荐你在上一章为实操创建的目录中继续本章的实操。

<details>
<summary>📋 点击展开/折叠完整提示词（可直接复制给 Agent）</summary>

提示词开始，请从这里往下全部复制

````
当前目录为

```
<WORK_DIRECTORY>
```

。请按照下面的要求完成任务，并在你每一次停下来等待我回复你的澄清问题时，将上一次追加前你我之间的完整对话（包括下面的Round 0）以markdown格式追加到

```
<DIALOGS>.md（可以使用./ch03-1-dialogs.md）
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

任务：用决策表测试法为 commons-csv 的 `required headers` 新需求设计验收测试用例

你是一名资深测试架构师，要为 Apache Commons CSV 代码库里一个尚未实现的新需求，设计验收测试用例。你要用**决策表测试法**（Decision Table Testing）来保证测试用例推导过程清晰、全面、不遗漏，而并非凭直觉随手列几个例子。

本提示词末尾的"附录 A：决策表测试法指南"包含你需要的全部方法论知识，**不要去网上搜索其他资料，只依据附录 A 和你自己对代码库的实地核查来完成任务**。

## 背景资料

- 子域分析文档：
```
<SUBDOMAINS_DOC_PATH>（即上一章的产出./ch02-2-2-subdomains.md）
```
（请阅读该文档以了解 CSVFormat / CSVParser / CSVRecord / Lexer 等类在这个代码库里分别承担什么职责，哪些是"核心业务逻辑"，哪些是"支撑性配置"）

- 影响分析文档：
```
<IMPACT_ANALYSIS_DOC_PATH>（即上一章的产出./ch02-2-3-impact-analysis.md）
```
（请阅读该文档以了解这次新需求会牵动哪些类、哪些类不受影响）

## 代码库

本机路径：

```
<COMMONS_CSV_REPO_PATH>（即上一章使用的./commons-csv）
```

把仓库克隆到你当前的工作目录下，然后在克隆下来的副本里做下面的"代码核查"。

**重要：你只能做调研和设计，不能修改/实现这个需求的源码**（即不要去改 `CSVFormat.java`、`CSVParser.java` 等任何生产代码文件）。你唯一要产出的是两份 Markdown 设计文档（见下文"产出要求"）。

## 需求描述

> 我在维护 Apache Commons CSV。我们对账时，上游 CSV 文件应当包含若干固定列（比如 `date`、`amount`、`currency`）。我想给 `CSVFormat.Builder` 加一个新方法 `setRequiredHeaders(String...)`：解析 CSV 时，一旦发现表头缺少这些列，就在读到第一条记录之前抛出一个清楚说明"缺了哪些列"的错误，而不用等业务代码调用 `record.get("currency")` 时才报一个语言含糊的"Mapping for currency not found"式的错误。

这个需求目前**完全没有在源码里实现**（`requiredHeaders`、`setRequiredHeaders` 这两个名字在代码库里还不存在）。

## 第一步：代码核查（在写任何文档之前必须先做）

在你克隆后的上述代码库的本地副本里，至少核查以下事实，并在后续文档里引用你核查到的具体证据（类名、方法名、真实的异常消息文本），而并非凭空假设：

1. `CSVParser.java` 里 `createHeaders()` 方法（或等价的构建表头映射的方法）现在是怎么处理"空列名"和"重复列名"这两类表头结构问题的——抛什么类型的异常？异常消息长什么样？检查发生在循环的什么位置（逐列检查还是全部读完才检查）？
2. `CSVFormat.java` 里 `validate()`（或等价的构造期自检方法）现在校验哪些内容？这些校验是否都不依赖"真实输入数据"，只依赖"声明本身的形状"？
3. `ignoreHeaderCase` 这个配置项，在构建"列名 → 列序号"映射表时是怎么生效的（具体看用的是什么 `Map` 实现、什么比较器）？
4. 表头到底有几种配置模式（例如"自动从首行解析"vs"手工指定表头数组"），这几种模式最终是否会汇入同一段处理逻辑？
5. 和重复表头相关的枚举/配置（如果存在）有哪几个取值？各自的语义是什么？
6. 现有的异常体系里，是否存在"受检异常"和"非受检异常"两类，分别用在什么场景？

把这些核查结果当作你接下来所有设计决策的**事实依据**——你后面写的每一条决策理由，都应该能够追溯到这一步核查到的具体代码行为，而并非"我觉得应该这样"。

## 第二步：先处理需求里没有讲清楚的开放设计问题（产出 ADR 文档 1）

这类"需求挂在一个类的 API 上，但真正的业务规则该不该落在别的类里、该不该与现有某个配置项交互"的问题，往往有好几个关键细节是需求原文没有明说的。在动笔设计决策表之前，你需要先自己识别并裁决这些开放问题（**务必优先**点对点回答上述“影响分析文档”`<IMPACT_ANALYSIS_DOC_PATH>（即上一章的产出./ch02-2-3-impact-analysis.md）`列出的“开放设计问题”），形式上做成一份轻量的 ADR（Architecture Decision Record，架构决策记录）。

**至少要覆盖以下问题**（如果你在第一步核查代码时发现了需求原文没提到、但确实需要裁决的额外问题，也要一并补充进来，不要漏掉）：

1. 异常类型：复用现有的异常类型（保持风格一致），还是新增一个专门的异常类型（让调用方可以精确 `catch`）？两者各有什么代价？
2. 大小写敏感性：必需列的名字比较，要不要尊重 `ignoreHeaderCase` 这个已有配置？
3. 与现有"重复列名""空列名"校验的交互顺序：如果同一次解析里，既有结构性表头错误，又缺必需列，应该先报哪个错？为什么？
4. 对称性问题：如果有人用同一份配置去"写" CSV（而并非"读"），要不要也校验一下要写的数据包含这些必需列？
5. 表头的"自动解析"模式和"手工指定"模式下，必需列校验的时机和行为是否应该一致？
6. 配置本身的自洽性：如果调用方设置了必需列，却完全没有启用任何表头模式，这种"自相矛盾"的配置应该在什么时候被发现（构造配置对象时，还是等到真正解析数据时）？
7. 空值/未设置语义：必需列这个新配置项，传 `null` 或空数组应该是什么含义？会不会和代码库里其他配置项的"空数组"特殊含义（如果第一步核查发现有的话）产生混淆？

对每一个问题：
- 给出一个**具体、可执行**的决策（不能是"看情况"这种模糊回答）；
- 用第一步核查到的**具体代码事实**作为理由支撑这个决策（引用真实的方法名、类名、异常消息文本）；
- 如果这条决策会影响后面决策表的"条件"或"动作"，要点明是哪一条。

**产出文件**：把这份 ADR 保存为当前工作目录下的

```
<ADR_DOC_PATH>（可以使用./ch03-2-adr.md）
```

（Markdown 格式），结构建议：
- 开头一段核查证据清单（对应上面"第一步"的发现）
- 每条决策单独一个二级标题，包含"决策"和"理由"两部分
- 结尾一张"决策记录汇总表"（问题 / 决策 / 依据的证据）

## 第三步：用决策表测试法设计验收测试用例（产出 Decision Table 文档 2）

基于 `<SUBDOMAINS_DOC_PATH>（即上一章的产出./ch02-2-2-subdomains.md）` 中列出的各个类的职责，严格按照附录 A 里"How to Create a Decision Table: Step by Step"的 7 个步骤来做，不要跳步，每一步都要把结果和理由写出来：

1. **先选定决策表类型，并给出理由**。一共有 5 种类型（Limited / Extended / Condition Action / Switch / Rule Based）。你要对照你的任务的真实复杂度（条件数量、条件之间是否存在互相影响/门控关系）来挑选，并在文档里明确说："因为……所以我选……"，不能跳过这一步直接开始画表。
2. **Step 1：识别所有条件**——条件要能追溯到第一步的代码核查结果和第二步的 ADR 决策，不能是含糊的描述。同时要明确写出"哪些看起来像条件、但其实不影响结果、因此被我排除掉了"的维度，并说明排除理由（这一点很重要，是判断你有没有真正理解系统行为、还是只是在堆砌组合的关键标志）。
3. **Step 2：定义每个条件的具体取值**——每个取值都要精确、可测试（例如"缺 1 列"和"缺全部列"算同一个取值还是不同取值，要讲清楚判断依据），不能是"差不多""大概"这种模糊说法。
4. **Step 3：计算规则数**——写出理论上的笛卡尔积是多少，再写出利用条件之间的依赖/门控关系消除掉多少"不可能"或"无意义"的组合之后，实际剩下多少条规则，每一步化简都要讲理由。
5. **Step 4：识别所有动作**——包括正常路径（成功）和异常路径（各种报错），每个异常动作要写清楚具体的异常类型和消息格式（可以参考你在 ADR 里定下来的格式）。
6. **Step 5：填充决策表**——正式画出表格（用 Markdown 表格语法），每一列是一条规则，行是条件和动作。**如果你发现一张表装不下全部逻辑（条件太多、或者有些条件只在特定的决策点才有意义），可以拆成多张表**，但必须在文档里清楚说明这几张表之间的关系（比如：表 A 的某个结果是表 B 成立的前提条件；或者表 A、表 B 是同一个判断逻辑在不同输入组合下的两个独立决策点）。
7. **Step 6：尽可能化简**——用"don't care"（用"–"表示）标记那些在某条规则下不影响结果的条件，并对**每一个**"–"都写一句话理由（不能只是机械地标，要让人看出"为什么这个条件在这里真的不重要"）。同时检查表里是否存在逻辑上不可能出现的组合，如果有，要剔除并说明理由。
8. **Step 7：把每条规则转成一个验收测试用例**——用结构化的文字/表格描述（前置条件 / 测试数据 / 预期结果），**不需要写可编译运行的代码骨架**。如果同一条规则下有几种不同的具体测试数据值得分别测一下（比如"缺 1 列"和"缺全部列"是同一个判断逻辑，但消息内容不同，值得各写一个测试用例），可以在同一条规则下派生出多个测试用例变体，并说明它们是变体而并非新规则。

**产出文件**：把这份决策表和测试用例保存为当前工作目录下的

```
<DECISION_TABLE_DOC_PATH>（可以使用./ch03-3-decision-tables.md）
```
（Markdown 格式），结构建议：
- 开头一段：决策表类型选择与理由，以及（如果拆成多张表）各表之间关系的说明图/说明文字
- Step 1 到 Step 7 依次成节，每节标注对应的 Step 编号
- 结尾一张汇总表：每张决策表产出了几条规则、派生了几个测试用例、对应 ADR 里的哪条决策

## 质量标准（自检清单，交付前检查一遍）

- [ ] 每一条设计决策、每一个决策表条件，都能在文档里找到"这是根据代码库里哪个具体事实/哪条 ADR 决策得出的"的说明，并非凭空猜测。
- [ ] 没有把互相独立、对结果没有交互影响的维度（比如两种等价的"允许"配置取值）硬塞成决策表的新条件，导致规则数不必要地膨胀。
- [ ] 没有把一个条件的"取值集合"写得含糊不清（例如"大小写差异较大"这种无法直接变成测试数据的说法）。
- [ ] 每一条规则都对应一个真实可达的程序状态，不存在"理论组合但实际不可能发生"却没有被剔除或说明的规则。
- [ ] 第 7 步产出的测试用例，任何一个拿去跟同事复述，对方都能明确说出"这个测试用例在验证哪条业务规则、输入什么、期望输出什么"，不需要再追问你。
- [ ] 两份产出文件（`adr.md` 和 `decision-table.md`）都是独立可读的 Markdown 文档，不依赖你在对话里说过的话才能看懂。

# 附录 A：决策表测试法指南（节选自 "What Is Decision Table Testing? Types and Examples"）

## Key Components of a Decision Table（决策表的四个核心组成部分）

### 1. Conditions（条件）

Conditions are the input variables that influence the system's behavior. Each condition represents a factor that the system evaluates when making a decision. In a loan approval system, conditions might include credit score, annual income, and employment status. In an e-commerce checkout, conditions might include payment method, shipping address validity, and coupon code applicability.

Each condition has a defined set of possible values. For binary conditions, these are typically True or False (sometimes represented as Y or N). For multi-valued conditions, these might be categories like High, Medium, or Low, or specific ranges like "income above $50,000" versus "income below $50,000."

### 2. Actions（动作）

Actions are the system's expected responses when a particular combination of conditions is met. Actions represent what the system does, not what it evaluates. In the loan approval example, actions might include "approve loan," "reject loan," or "refer to manual underwriting." In the e-commerce example, actions might include "process payment," "display error message," or "apply discount."

### 3. Rules（规则）

Rules are the columns of the decision table. Each rule defines one specific combination of condition values and the corresponding action or actions the system should execute. If a decision table has three binary conditions, the table will have up to eight rules (2^3), with each rule representing a unique test case.

### 4. Condition Alternatives and Action Entries（条件取值与动作条目）

Condition alternatives are the specific values assigned to each condition within a rule. Action entries indicate which actions are triggered (or not triggered) for each rule. Together, they populate the body of the table and define the complete decision logic under test.

## Types of Decision Tables（决策表的五种类型）

### 1. Limited Decision Table（有限决策表）

A limited decision table restricts condition values to binary states: True/False or Yes/No. This is the simplest form and works well for systems with straightforward, independent conditions.

典型例子：User Authentication System（用户名有效性 × 密码有效性 → 四种组合，四个测试用例）。

### 2. Extended Decision Table（扩展决策表）

An extended decision table allows conditions to have multiple values beyond binary. This is essential for systems where inputs are categorical or range-based.

典型例子：Insurance Policy Underwriting（年龄分段 × 理赔历史 → 不同的核保结论和保费档位）。

### 3. Condition Action Table（条件-动作表）

A condition action table maps each condition directly to a specific outcome, making it particularly useful when the relationship between inputs and outputs is clear and one to one.

典型例子：E-commerce Discount Engine。

### 4. Switch Table（开关/分支表）

A switch table applies when a single controlling condition determines the outcome through multiple branches. It simplifies decision logic into a lookup structure.

典型例子：Shipping Method Selection（按运输方式这一个主控条件分支）。

### 5. Rule Based Decision Table（规则型决策表）

Rule based decision tables combine multiple interacting conditions to handle complex, layered business logic. They are the most comprehensive form and are commonly used in financial services, healthcare, and insurance systems where regulatory compliance demands complete traceability.

典型例子：Healthcare Patient Triage System——症状严重程度和既往病史会共同决定"是否立即收治"和"是否触发专科会诊提醒"这两个动作，每条路径都要能被显式定义和测试。

## How to Create a Decision Table: Step by Step（七步法）

### Step 1: Identify All Conditions

Start by listing every input condition that influences the system's decision. Review requirements documents, user stories, and business rules to ensure completeness. For an enterprise loan origination system, conditions might include credit score range, debt to income ratio, employment verification status, and loan amount requested.

### Step 2: Define Condition Values

For each condition, define the complete set of possible values. Binary conditions have two values. Multi-valued conditions might have three, four, or more. Be precise: "credit score above 700" is testable; "good credit score" is not.

### Step 3: Calculate the Number of Rules

For a limited decision table with binary conditions, the number of rules equals 2^n, where n is the number of conditions. For extended tables, multiply the number of values for each condition. Three conditions with 2, 3, and 2 values respectively produce 2 × 3 × 2 = 12 rules.

### Step 4: Identify All Actions

List every possible action the system can take. Include both positive outcomes (approve, process, route) and negative outcomes (reject, display error, escalate). Include compound actions where the system performs multiple responses for a single rule.

### Step 5: Populate the Table

Fill in the condition alternatives for each rule, ensuring every unique combination is represented. Then assign the correct action or actions for each rule based on the business logic.

### Step 6: Simplify Where Possible

Some conditions may be irrelevant for certain rules. If the outcome is the same regardless of a condition's value, that condition can be marked as "don't care" (often represented with a dash) for those rules. This reduces redundancy without sacrificing coverage.

### Step 7: Convert Rules to Test Cases

Each column (rule) becomes one test case. Define the specific test data, expected results, and preconditions for each. This traceability from business rule to test case is one of decision table testing's greatest strengths.

## Limitations of Decision Table Testing（局限性，设计时要主动规避）

### 1. Exponential growth with many conditions

A system with 10 binary conditions produces 1,024 rules. With multi-valued conditions, the number grows even faster. Large tables become difficult to create, review, and maintain without tool support or optimization techniques.

### 2. Weak fit for continuous or range based data

Decision tables work best with discrete, categorical inputs. Conditions involving continuous ranges (temperature, price, time) require equivalence partitioning or boundary value analysis to reduce values to testable categories before a decision table can be applied.

### 3. Assumption of condition independence

Standard decision tables assume conditions are independent. When the value of one condition constrains the possible values of another (for example, if "account type = savings" limits "overdraft eligibility" to "no"), the table may include invalid combinations that must be identified and removed.

### 4. Maintenance overhead in fast changing systems

When business rules change frequently, decision tables require constant updates. This overhead can become a bottleneck, particularly for large enterprise systems with hundreds of decision points.

## Best Practices for Enterprise Decision Table Testing（最佳实践）

### 1. Start with the business rules, not the UI

Decision tables should be derived from documented business rules and requirements, not from observing the application's interface. This ensures the table tests intended behavior, not incidental behavior.

### 2. Apply equivalence partitioning before building the table

For conditions with continuous ranges, use equivalence partitioning to reduce the range to representative categories. Then build the table using those categories. This prevents the table from growing unmanageably large while maintaining meaningful coverage.

### 3. Always test boundary conditions

After building the decision table, supplement it with boundary value analysis for any condition that involves a range. If a condition threshold is "income above $50,000," test at $49,999, $50,000, and $50,001. Boundaries are where defects cluster.

### 4. Eliminate impossible combinations

Review the completed table for rules that represent logically impossible combinations of conditions. Remove or mark them to avoid wasting test execution time on invalid scenarios.

### 5. Automate with data driven testing

Convert the decision table into a data source (CSV, database, or API) and feed it into an automation platform that supports parameterized execution. This transforms the table from a planning document into an executable test suite.

## 补充：常见问答（FAQ 节选）

**How many test cases does a decision table produce?**
The number of test cases equals the number of rules (columns) in the table. For a limited decision table with n binary conditions, this is 2^n. For extended tables, multiply the number of possible values for each condition. Three conditions with 2, 3, and 4 values respectively produce 2 × 3 × 4 = 24 test cases.

**What is the difference between a limited and extended decision table?**
A limited decision table restricts all conditions to binary values (Yes/No or True/False), producing a fixed number of rules calculated as 2^n where n is the number of conditions. An extended decision table allows conditions to have multiple values (such as High, Medium, Low), which increases the number of rules but provides more granular coverage of complex business logic.

**How does decision table testing relate to equivalence partitioning and boundary value analysis?**
These techniques are complementary. Equivalence partitioning reduces continuous input ranges into representative categories, which are then used as condition values in the decision table. Boundary value analysis supplements the table by testing the edges of those categories, where defects are most likely to occur. Used together, they produce comprehensive coverage that is both efficient and thorough.
````

提示词结束，以上内容请整段复制给 Agent。

</details>

跑完之后，数一数你的 Agent 推导出多少条规则、多少个测试用例，和本章的 11 条规则、21 个测试用例对比——如果数字差异很大，回头看看是否漏掉了某个条件之间的依赖关系，或者把本该合并的等价类拆成了多条规则。

验收测试用例清单推导出来了，但它们目前还只是文档里的表格，离"能跑起来、能保护代码"还差一步。第四章会把这份清单落地成真正可执行的自动化验收测试——用 Approved Scenarios 模式，让每条用例对应一份产品经理也能读懂的 markdown fixture，再配合 TDD 的红绿重构节奏，把生产代码补齐到能让这些测试全部通过，这是"构建"这一步要交付的东西。

---

⬅️ 上一章：[第二章 理解代码所承载的复杂业务逻辑](../ch02/README.md#ch2-top) ｜ ➡️ 下一章：[第四章 构建面向业务不变式的自动化验收测试](../ch04/README.md#ch4-top)
