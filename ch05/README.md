<a id="ch5-top"></a>

# 第五章 评测自动化验收测试确实保护了生产代码

按照本章后面的提示词在你自己的 Agent 上实操一遍后，你将学会用故障注入测试验证这些自动化验收测试是否真的在保护生产代码，而非一套形同虚设的空测试。

如何验证 Agent 生成的自动化测试确实保护了生产代码？

第四章把决策表推导出的用例实现成了逐条可调试的 Approved Scenarios 测试，全部变绿。但"测试是绿的"不等于"测试真的在保护生产代码"——它也可能是一个无论生产代码怎么改都不会变红的空测试。本章用故障注入测试来排除这种可能。

故障注入这件事，光看文字描述很难建立直觉。建议你先浏览完本章，再把末尾的提示词交给你的 Agent，让它针对自己（或你）实现的验收测试设计一轮真正的故障注入方案并跑通，亲眼看看哪些故障会让测试变红、哪些故障会像本章提到的"对照组"一样，改了却什么都没发生。

> [!TIP]
> **TL;DR · 读完本章你会：**
> - 理解故障注入测试的闭环：注入→变红→报错对应→撤销→变绿，用来证明一条测试确实在保护对应的生产代码，而非形同虚设；
> - 看清"随机机械变异"和"与保护意图语义对应的故障"之间的区别——这是本方法最容易踩的坑；
> - 看到三种实现方案的对比（源码精确文本替换 + `git checkout` 还原 / PITest 变异测试框架 / 运行期 Java Agent 字节码替换），以及为什么方案一"源码精确文本替换 + git 撤销"在语义对应、强校验、手工指南同构三项硬要求上都是最优解；
> - 亲眼见证两个反直觉的真实案例——一次因 Builder 默认值牵连 `CSVFormat` 静态初始化而被推翻重做的故障、一次连带变红集合与推演精确相等（7 个"命中侧"用例一个不多一个不少）的真实验证。

<a id="ch5-toc"></a>
**本章目录**：[5.1 故障注入验证方法](#ch5-1) · [5.2 故障注入测试实现方案](#ch5-2) · [5.3 单步调试 + 故障注入](#ch5-3) · [5.4 动手练习提示词](#ch5-hands-on)

<a id="ch5-1"></a>

## 5.1 用故障注入验证测试没有形同虚设

<a id="ch5-def"></a>

#### 定义

故障注入测试（Fault Injection Testing）是在生产代码里**人为、有针对性地**引入一个错误（故障），观察依赖这段代码的自动化测试是否会因此运行失败，并验证这次失败的报错信息确实与该测试本应保护的那段代码行为相关；然后撤销这个人为引入的错误，再次运行该测试，确认它恢复变绿。如果"注入→变红→报错对应→撤销→变绿"这个闭环完整成立，就证明这个测试确实在保护对应的生产代码，而并非一个形同虚设的空测试。这个方法广泛用于鲁棒性测试、压力测试等场景，按注入时机可以分为编译期注入（直接修改源代码，比如代码变异）和运行期注入（通过触发器在运行时扰动系统），本章用的是编译期的代码修改方式。

<a id="ch5-pitfall"></a>

#### 最容易踩的坑

把"故障注入"做成随机的、机械的代码变异（比如随手把 `&&` 改成 `||`，或者把条件判断直接改成 `true`/`false`），而并非针对"这个测试到底想保护什么行为"去精心设计一个语义对应的故障。机械变异只能笼统证明"测试能检测到某种变化"，回答不了"这个特定测试是否确实在保护这一行具体逻辑"这个问题——本章设计的每一处故障，都要能讲清楚"这个故障对应测试的哪一条保护意图"。

<a id="ch5-value"></a>

#### 价值

第四章的 Approved Scenarios 测试已经验证了"实现符合预期行为"，但没有验证"测试本身有没有检测能力"——这是两个不同的问题。故障注入测试直接回答第二个问题：如果我故意让生产代码出错，测试会不会发现？对用 Agent 生成测试的场景尤其重要，因为 Agent 完全有可能生成一段"看起来在断言、但断言条件写反了、永远为真"的测试，肉眼读代码很难发现，但故障注入能立刻把它暴露出来。

<a id="ch5-risk"></a>

#### 没有它的危害

没有这一步验证，一套测试套件全绿，给人的错觉是"代码受到了完整保护"，但其中可能混有若干"假阳性"测试——它们从第一天起就不会因为任何生产代码的退化而失败。这类测试直到真正的缺陷流入生产环境、而测试却毫无反应时，才会暴露出"形同虚设"的真相，而那时的调查成本远高于现在验证一遍。

<a id="ch5-strength"></a>

#### 独特优势

相比"读代码审查测试质量"，故障注入给出的是**可复现的实证结果**——变红、变绿都是可以重复执行、任何人都能亲眼验证的事实，而并非"我觉得这个测试写得不错"的主观判断；它还能顺带产出一份"手工调试资料"——按同一份故障清单，在 IDE 里亲手改一行代码、看测试变红、读懂报错、改回来看测试变绿，这个过程本身就是加深理解生产代码逻辑的好办法（5.3 节会展开）。

<a id="ch5-weakness"></a>

#### 主要劣势

故障的"语义设计"工作量主要落在人（或指导 Agent 精心设计）身上，并非工具自动生成的——这恰恰保证了"故障与测试保护意图的语义对应"，但也意味着不能指望一个现成工具无脑跑一遍就拿到有意义的结果。

<a id="ch5-fit"></a>

#### 适用场景

一批验收测试已经实现完成、全部变绿，需要验证它们是否真的在保护对应的生产代码，而非形同虚设；尤其适合作为一次性的学习/验证工具，帮助理解测试与生产代码之间真实的因果关系。

<a id="ch5-nofit"></a>

#### 不适用场景

如果目标是对整个代码库做无差别的大范围变异覆盖率扫描（而并非针对少数几个已知测试逐一验证保护意图），更适合用成熟的变异测试框架。

<a id="ch5-2"></a>

## 5.2 故障注入测试实现方案

> 下面不再是一份完整的方案设计文档全文，而是我把本章"动手练习提示词"发给 Agent（OpenCode V2 搭配 GLM 5.3）之后，真实跑完一轮的过程复盘——Agent 问了什么、我怎么答的、哪里我本来想错了、最后产出了什么。

**过程：Agent 问了哪些关键问题，我是如何回答的**

Agent 在动手设计方案之前，先做了一轮事实核查：确认 `./commons-csv` 当前 HEAD 为 `9a1af060`（第四章的生产实现、测试与 fixture 已经提交入库，工作区干净）；逐行用 `grep -n` 核对调试指南里列出的断点（`CSVFormat.java:888` 归一化、`:2851-2852` D6 矛盾检查、`CSVParser.java:687` 命中判定、`:692` 异常消息组装等），确认行号和真实源码完全一致，没有漂移；确认被保护对象在位——`RequiredHeadersApprovedScenariosTest`（3 个参数化入口方法）+ `RequiredHeadersScenario`（fixture 执行器）+ 22 份 `.approved.md`（表 A 6 份、表 B 15 份、表 C 1 份）；检查 `pom.xml`，确认仓库里没有集成任何变异测试框架（如 PITest）。

随后它提出的唯一一个 **Q0** 是全局性的："依次对每个测试"这句话到底要的是 22 份 fixture 逐一闭环，还是按约 6-8 个生产代码故障点做代表性闭环？它指出一个我没想清楚的地方：22 份 fixture 的生产代码落点其实只有约 6 处，同一落点的不同 fixture 往往保护的是同一行代码的不同语义侧面（比如"缺失名单按声明序"和"名单去重"就是同一处代码的两个不同故障）。我同意了它的建议——选 22 份 fixture 全量逐一闭环，理由是只有这样才能让"每一个测试都在保护对应代码"被逐条验证，而并非抽样验证；而且这类"同落点不同语义侧面"的故障恰好最能演示"语义对应"和"机械变异"的区别，正是本章要讲的核心命题。

第一轮追问里确定了几项基础决策：用途定位是一次性教学演示（不进 CI、不留作长期回归资产）；三个方案按"故障怎么注入、怎么撤销的技术路线"互相区分；自动化校验必须达到"指定 fixture 的失败信息必须包含期望片段"这个强校验标准；实现语言和隔离机制我都交给 Agent 自行判断——它最后选了 Python 3 单文件脚本（本机自带、处理 JSON 配方和 XML 报告都是一等公民），隔离机制选了"方法级运行 + 按 surefire XML 里确定性的索引顺序定位单个 fixture"。

另一个决定了整个设计走向的问题是**"连带变红集合"要不要纳入精确核对**。Agent 指出，有些故障（比如拆掉某个 null 判断）会让一大片用例一起变红，这恰恰是区分"语义对应故障"和"机械变异"最强的证据——语义故障的连带红面是可以提前推演出来的，机械变异的连带红面是随机的。所以它建议：每条配方都要声明一份"预期变红清单"，脚本跑完后核对实际变红集合**恰好等于**预期清单，多一个或少一个都算闭环失败。我采纳了这个建议。

**哪些假设是错的，怎么纠正的**：方案设计阶段的决策都是一轮确定的，没有反复。但进入实现阶段后，第一轮全量跑完是 19/22，有三条配方和实测结果不符，而且全部出在表 A 的 getter 相关断言上。其中两条（`TC-A1a-empty`、`TC-A1a-null`）故障本身设计对了、连带面推演也对了，只是我们预判"失败会发生在 getter 的 `assertNull` 断言"，但实测发现失败发生得更早——归一化逻辑被拆掉后，未归一的名单在 `Builder.get()` 内部的 D6 矛盾检查那一步就先被拦截了，这一步只需要把期望报错片段改对。但 `TC-A1` 那一条真的是故障点设计错了：第一版把故障打在 Builder 字段默认值上（把默认值从 `null` 改成 `new String[0]`），结果实测直接炸出 `ExceptionInInitializerError`——因为 `CSVFormat` 的静态构造字段也要走一遍 Builder，D6 检查在类初始化阶段就把这个非 null 默认值当成配置矛盾拒绝了，表 A 的 6 份 fixture 全部变红。这个连带面和"从未调用过 setter"这个 TC-A1 本该保护的意图完全无关，Agent 判定这是一个不合格的语义故障，如实记录了这次翻车，改打 `getRequiredHeaders()` 里 null 分支的那一行，连带面缩小为 3 份同样断言"getter 应返回 null"的 fixture。第二轮全量跑完，22/22 全部闭环成功。

**这些问题给了我什么启发**：这次返工意外揪出了一个我事先完全没想到的代码事实——`CSVFormat` 的 Builder 默认值不只是"没人调用 setter 时的默认表现"，它还参与了 `CSVFormat` 自身的静态初始化，任何非 null 默认值都会在类加载阶段就被 D6 拦下来。如果没有先把"预期变红集合"精确声明出来再去对实测结果，这种"故障打对了地方、但炸出来的连带面和保护意图根本不相关"的情况很容易被误判成"反正测试变红了就算成功"。

**产出：生成了哪几份文档，关键内容是什么**

问题前沿清空后，Agent 给我产出了两份文档：

- **故障注入测试的3个方案**（`ch05-2-solutions-for-fault-injection.md`）：方案一"源码精确文本替换 + `git checkout` 撤销"、方案二"PITest 变异测试框架"（字节码算子级变异）、方案三"运行期 Java Agent 字节码替换"（premain + `ClassFileTransformer`，切换故障靠系统属性，不碰源码）。三者共享同一份"故障字典"（每条配方记录生产代码位置、注入前/后代码片段、期望失败片段、预期变红集合、语义对应理由）和同一套运行协议（跑方法级测试→解析 surefire XML 拿到实际变红集合→和预期集合精确比对→撤销→确认复绿）。Agent 最终推荐**方案一**，理由逐条对应之前锁定的决策——比如强校验和连带集合精确核对这两条，只有方案一（和方案三）能在 surefire XML 的 per-fixture 粒度上落地，方案二的 PITest 报告是方法级聚合，压根没有"单个 fixture 的失败消息"这个概念。
- **故障注入指南（`ch05-3-fault-injection-guide.md`）**：按 22 份 fixture 逐条组织，分三组对应三个入口方法——表 A 组（build 期决策点，6 条）、表 B 组（parse 期决策点，15 条）、表 C 组（print 期决策点，1 条）。每条列出对应的生产代码类名+行号（附重新定位用的 `grep -n` 命令，因为注入会让行号漂移）、注入前代码、注入后代码、实测报错信息，以及预期和实测的连带变红清单。

**生成了哪些测试代码和生产代码**：这一阶段没有改动任何生产代码（`CSVFormat.java`/`CSVParser.java`/`CSVPrinter.java` 保持不变），而是在代码库里新增了一套独立的故障注入工具链：`faults.json`（22 条故障配方，单一事实源，含注入前/后精确代码片段、期望报错片段、预期变红集合、语义对应理由，270 行）、`run_faults.py`（Python 3 stdlib 驱动器：preflight 检查工作区干净→精确文本注入→方法级 `mvn test` 运行→解析 surefire XML→三重断言[目标 fixture 变红/失败消息含期望片段/实际变红集合精确等于预期集合]→`try/finally` 保证 `git checkout` 撤销→变绿验证→末尾全量回归比对基线，231 行），两份文件共约 501 行；`pom.xml` 只新增一行 RAT 豁免（照第四章先例）。完整代码已公开在 [commons-csv-by-opencode-v2-with-glm-5.3](https://github.com/wubin28/commons-csv-by-opencode-v2-with-glm-5.3) 仓库的 [`6abf0a6e` 这次提交](https://github.com/wubin28/commons-csv-by-opencode-v2-with-glm-5.3/commit/6abf0a6e2ccd7cac37a64a6c6a580f0ec1247b9a) 里。

**这些文档和代码的质量是如何实现可信的**：22 份 fixture 全部实测跑通了"注入→变红→撤销→复绿"的完整闭环；更关键的是，每次变红时产生的报错信息都要和预先声明的"期望片段"精确匹配，而并非看到"测试失败了"就算数——而且实际变红的 fixture 集合必须和预先推演出的"预期变红集合"**精确相等**，多红、少红都算闭环失败。比如 `TC-B3`（命中判定复用 `headerMap`）这个故障会精确打红 7 个"命中侧"用例（`TC-B3a`/`B4`/`B5`/`B6`/`B7a`/`B7b` 加自己），而本来就缺列的 `TC-B7c`/`B7d`/`B7e`/`B7f` 在同一次运行里保持绿，实测和推演完全吻合；`TC-B1`、`TC-B7f` 则是全表最窄的两处故障，连带面只有自己。这给我的启发是：一份"可信"的故障注入报告，衡量标准并非"故障点够不够多"，而是"每一次变红，是否都能说清楚它对应哪一行生产代码、精确影响到哪几条测试"。

**是不是一把过，有没有返工**：方案设计阶段是一把过的——三个方案的结构、推荐理由都是一轮产出。实现阶段确实返工过一次：第一轮全量跑完 19/22，前面提到的 `TC-A1` 故障点被推翻重做，另外两条配方修正了期望报错片段。Agent 没有把这次翻车藏起来，而是在最终的手工指南里专门留了一节"修正记录"，如实写明两次全量运行的结果、具体哪三条配方不符、原因是什么、怎么改的。第二轮全量跑完 22/22 全部闭环成功，末尾全量回归 `Tests run: 1025, Failures: 0, Errors: 0, Skipped: 11`，和第四章的基线一致。

<a id="ch5-3"></a>

## 5.3 用单步调试和故障注入的方法理解 approved scenarios 测试方法

> 同样，这里我也没有把生成的调试指南全文搬过来，而是复盘这份指南实际交付时长什么样、为什么这样组织。

Agent 给我产出的"故障注入指南"按 22 份 fixture 逐一组织——这是 5.2 节 Q0 决策的直接结果：我选的是"22 份全量逐一闭环"而并非按生产代码落点归并成几个代表性故障点，目的是让"每一个测试都在保护对应代码"被逐条验证，而并非抽样验证。指南分三组对应三个入口方法：表 A 组（`tableA_buildPhase`，build 期决策点，6 条）、表 B 组（`tableB_parsePhase`，parse 期决策点，15 条）、表 C 组（`tableC_printPhase`，print 期决策点，1 条）。每一条列出这段逻辑所在的类名和行号（附重新定位用的 `grep -n` 命令，因为注入会让行号漂移）、注入故障前的代码、注入故障后的代码、为什么这样设计（这条故障和该 fixture 保护意图的语义对应说明）、注入后实际跑出来的报错信息，以及预期和实测的连带变红清单。

这样组织的好处是：如果你在自己的 IDE 里跟着指南把某一行代码临时改成"故障版"，打上断点运行对应入口方法，你能直接对照指南上写的"预期报错信息"和"预期哪些 fixture 会变红"，来验证自己是不是真的理解了这段代码在保护什么——而并非盯着一大段不知道该往哪里下断点的生产代码无从下手。这份指南本质上是前面两章"子域分析→决策表→Approved Scenarios 测试"这条链路的最后一次复核：如果某条验收测试被设计出来却从来没在故障注入时真正变红，那就说明它其实没有在保护任何行为，前面几章辛苦建立起来的"可信"链条就会在这里露出破绽——而我这次真实运行的结果是，22 份 fixture 全部在故障注入下如预期般变红（连带变红集合也和推演精确相等）、报错信息也和预期一致，没有出现"形同虚设"的测试。


<a id="ch5-hands-on"></a>

## 5.4 动手练习提示词

为方便动手实操，这里把本章 5.2～5.3 节的“设计故障注入方案”和“产出手工操作指南”相应的提示词合并成一份完整的提示词。该提示词会先让 Agent 做事实核查，再设计 3 个方案并推荐一个，最终产出的单份文档本身就同时兼顾"自动化脚本设计"和"手工调试资料"两个用途。

使用前请把下面所有占位符替换成你自己的实际信息（如果某个占位符暂时没有明确答案，可以保留原样，让 Agent 自己判断并在文档里说明它做出的假设）：

- `<WORK_DIRECTORY>`：你在 Agent 里设置的当前工作目录
- `<DIALOGS>.md（可以使用./ch05-1-dialogs.md）`：保存你和 Agent 之间对话记录的文件路径，已备 Agent 因为 LLM 服务暂时不可用等种种原因中断时能切换到其他 Agent 继续
- `<COMMONS_CSV_REPO_PATH>（即上一章使用的./commons-csv）`：commons-csv 代码库在本机的路径（推荐和上一章保持一致，即当前工作目录下的 `./commons-csv` 子目录）
- `<DEBUG_GUIDE_FOR_APPROVED_SCENARIOS_DOC_PATH>（即上一章的产出ch04-3-debug-guide-for-approved-scenarios.md））`：上一章动手练习中你自己产出的调试指南文档路径（里面应包含各测试对应的生产代码类名+行号+断点）
- `<DECISION_TABLE_DOC_PATH>（即第 3 章的产出ch03-3-decision-tables.md）`：要做故障注入测试的验收测试范围参见第 3 章的 decision table 文档的“Step 7：把每条规则转成验收测试用例”
- `<SOLUTIONS_FOR_FAULT_INJECTION_DOC_PATH>（可以使用./ch05-2-solutions-for-fault-injection.md）`：设计的 3 个故障注入方案及推荐方案的最终产出文档路径（里面应包含每个方案的主要内容、优势、劣势、适用场景、不适用场景，以及推荐方案的推荐理由）
- `<FAULT_INJECTION_GUIDE_DOC_PATH>（可以使用./ch05-3-fault-injection-guide.md）`：最终产出的手工故障注入指南文档路径（里面应包含每条故障对应的生产代码类名+行号+注入前代码+注入后代码+注入后运行该测试时期望看到的报错信息）
- `<USAGE_SCOPE>`：这套故障注入设施的用途定位。示例："一次性验证/学习工具，不需要长期留在代码库里、不需要进 CI"
- `<DIFFERENTIATION_DIMENSION>`：希望 3 个方案按什么维度区分（不填则由 Agent 自行建议并说明理由）。示例："按故障注入的实现机制区分"
- `<IMPLEMENTATION_LANGUAGE>`：自动化脚本的实现语言偏好。示例："本机是 macOS iTerm2 zsh，优先 shell 脚本方案"
- `<VALIDATION_STRICTNESS>`：自动化校验"测试失败信息是否与生产代码行为相关"这一步的严格程度。示例："精确子串匹配：预先写好每个测试期望的异常消息关键片段，脚本跑完后用这个片段校验"

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
<DIALOGS>.md（可以使用./ch05-1-dialogs.md）
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

你是一名资深软件测试工程师。你的任务分两个阶段：**阶段 A** 产出一份方案设计文档并给出推荐；**阶段 B**（在方案确定后）按推荐方案完成 fault injection testing 实现，并产出一份手工故障注入指南文档。如果你是在非交互式、单轮执行的环境里运行（无法等待人工确认再继续），**直接采用你自己推荐的方案继续阶段 B**，不要中途停下来等待批准。

### 阶段 A：方案设计

请为我设计 3 个"故障注入测试"（Fault Injection Testing）方案，目标代码库和背景信息如下：

- **代码库路径**：

```
<COMMONS_CSV_REPO_PATH>（即上一章使用的./commons-csv）
```

（本机 macOS，终端环境为 iTerm2 zsh）

- **要保护的验收测试所对应的“Step 7：把每条规则转成验收测试用例”所在的 decision table 文档**：

```
<DECISION_TABLE_DOC_PATH>（即第 3 章的产出ch03-3-decision-tables.md）
```

- **这些测试对应生产代码的调试指南**（里面列出了每个测试命中的生产代码类名、行号、断点、变量）：

```
<DEBUG_GUIDE_FOR_APPROVED_SCENARIOS_DOC_PATH>（即上一章的产出ch04-3-debug-guide-for-approved-scenarios.md））
```

- **这套设施的用途定位**：

```
<USAGE_SCOPE>
```

（如果留空，请你自己提出一个合理的区分维度并说明理由，然后在问题前沿里征询我的意见）

- **方案区分维度偏好**：

```
<DIFFERENTIATION_DIMENSION>
```

（如果留空，请你自己提出一个合理的区分维度并说明理由，然后在问题前沿里征询我的意见）

- **自动化脚本实现语言偏好**：

```
<IMPLEMENTATION_LANGUAGE>
```

（如果留空，请结合代码库的构建工具自行判断并说明理由，然后在问题前沿里征询我的意见）

- **自动化校验严格程度偏好**：

```
<VALIDATION_STRICTNESS>
```

（如果留空，请你自己判断并说明理由，然后在问题前沿里征询我的意见）

#### 背景知识：什么是故障注入测试

你可能不熟悉"故障注入测试"这个方法，本提示词末尾的"附录"里已经附上了完整的参考资料，请先阅读附录内容，再开始设计方案。核心要点提前说明：

> 故障注入测试的核心目的，是在生产代码里**人为、有针对性地**引入一个错误（故障），让某个自动化测试因此运行失败，并验证这个失败的报错信息确实与该测试本应保护的那一段生产代码行为相关；然后撤销这个人为引入的错误，再次运行该测试，观察它恢复变绿。如果整个"变红→报错信息对应→变绿"的闭环都成立，就证明这个测试确实在保护对应的生产代码，而并非形同虚设的空测试。

**这个方法最容易踩的坑**：把"故障注入"做成随机的、机械的代码变异（比如随手把 `&&` 改成 `||`，或者把 `>` 改成 `>=`），而并非针对"这个测试到底想保护什么行为"去精心设计一个语义对应的故障。机械变异只能笼统证明"测试能检测到某种变化"，回答不了"这个特定测试是否确实在保护这一行具体逻辑"这个问题。你设计的 3 个方案都应该围绕"故障要与测试的保护意图语义对应"这个原则展开，而并非推荐纯随机变异工具。

#### 在设计方案之前，你必须先做的事实核查

不要凭空假设代码库的现状，请先：

1. 读一遍上述调试指南，了解每个测试对应的生产代码位置、断点、变量。
2. 实际打开上述代码库下对应的生产代码文件，核实 debugging guide 里提到的类名、行号、代码逻辑目前是否仍然准确（代码可能已经变动，guide 里的行号可能已经漂移）。如果你没有直接读取本地文件系统的能力，请在文档里明确声明"以下内容基于 debugging guide 原文，未做本地代码核实，正式实现前请用 `grep -n` 重新核实行号"，不要假装自己核实过。
3. 检查代码库的构建配置文件（如 Java 项目的 `pom.xml`/`build.gradle`），确认是否已经集成了某种变异测试框架（如 PITest）。这个事实会影响你在"方案二"里描述的优劣势（如果已经集成了，那么"方案二：引入变异测试框架"的实现成本会大幅降低，需要如实调整该方案的优劣势描述）。

把你的事实核查结果写在文档最前面的"背景与已确认的前提"小节里，再开始设计 3 个方案。

#### 任务要求

请设计 **3 个方案**，用于在上述代码库里，针对上述 decision table 文档的“Step 7：把每条规则转成验收测试用例”所对应的验收测试做故障注入测试，需要同时满足两个用途：

1. **自动化脚本**：依次对每个测试，在其对应生产代码里注入一个与该测试保护意图语义对应的故障，让该测试运行失败；验证失败时的报错信息确实与该测试所保护的生产代码行为相关；然后撤销故障，重新运行该测试，确认它变绿。这个"注入→验证变红→撤销→验证变绿"的闭环要能做成可重复执行的自动化脚本。
2. **手工调试资料**：为每一个测试提供一份人类可读的"故障注入信息"，包含：生产代码的类名+行号、注入前代码示例、注入后代码示例、注入后运行该测试时期望看到的报错信息。这份资料是给我在 VSCode 里用 debug 单步执行的方式手工注入故障、观察测试失败用的，目的是加深我对相关生产代码逻辑的理解。

3 个方案应该按上述方案区分维度偏好互相区分（即每个方案代表一种不同的"故障怎么注入、怎么撤销"的技术路线），而并非互相之间只是"故障点多少""脚本拆分方式"这类表面差异。

#### 输出格式（严格遵守，这是让文档质量达标的关键）

请把 3 个备选方案以及你的一个推荐方案及其推荐理由的最终产出保存为一份 markdown 文件，路径为：

```
<SOLUTIONS_FOR_FAULT_INJECTION_DOC_PATH>（可以使用./ch05-2-solutions-for-fault-injection.md）
```

。文件结构必须是：每个方案一节（主要内容/优势/至少3条/劣势/至少2条/适用场景/不适用场景），最后一节"推荐方案"逐条对应"背景与已确认的前提"里的具体决策给出理由。

#### 方案写作质量要求

- 每个方案的"优势"和"劣势"必须具体到这个方案自己的实现细节上（比如提到会不会改动构建配置文件、会不会引入新依赖、撤销故障的具体手段是什么、报错信息的可读性如何），不要写"简单""复杂""灵活""不灵活"这类没有信息量的形容词而不给出原因。
- "适用场景"和"不适用场景"必须是互补的、具体的条件判断，而并非同一句话正反说两遍。
- 3 个方案的"优势/劣势"之间要能相互印证——如果方案一的优势是"不改构建配置文件"，那么其他把这一点作为劣势的方案就应该明确提到"需要改动构建配置文件"，保持前后一致，不要自相矛盾。
- 推荐方案的理由要逐条对应"背景与已确认的前提"里的具体决策，不要写泛泛而谈的总结性理由。
- 全文使用中文，专有名词（类名、方法名、工具名）保留英文原文。
- 不要在方案设计阶段直接动手实现（不要去改生产代码、不要创建除了这份 markdown 文档之外的任何文件）。文档写完之后，等待我确认选哪个方案，再进入实现阶段。

### 阶段 B：实现与产出手工故障注入指南

方案确定后，请按推荐方案实现自动化脚本，并产出手工故障注入指南（

```
<FAULT_INJECTION_GUIDE_DOC_PATH>（可以使用./ch05-3-fault-injection-guide.md）
```

）——每条包含生产代码的类名+行号、注入前代码、注入后代码、注入后运行该测试时期望看到的报错信息。如果某个故障注入后测试依然是绿的，请如实说明"为什么这个故障不会让测试变红"，不要隐瞒或者悄悄换一个更容易触发失败的故障。

# 附录：Fault Injection Testing（故障注入测试）方法参考资料

> 以下内容摘自 GeeksforGeeks 词条《Fault Injection Testing - Software Engineering》，供你在不具备外部检索能力的情况下直接参考，不需要再去网上查找。

Fault injection is a technique used in software engineering to test the resilience of a software system. The idea is to intentionally introduce errors or faults into the system to see how it reacts and to identify potential weaknesses. This can be achieved in several ways, such as:

1. **Hardware faults:** This involves physically altering hardware components to induce faults.
2. **Software faults:** This involves intentionally introducing errors into the code, such as incorrect data or incorrect logic.
3. **Network faults:** This involves simulating network conditions, such as latency, packet loss, and congestion, to see how the system reacts.

## What is Fault Injection Testing?

**Fault Injection** is a technique for enhancing the **testing quality** by involving intentional faults in the software. Fault injection is often used in **Stress Testing**, and it is considered an important part of developing robust software. The broadcast of a fault through to a noticeable failure follows a well-defined cycle. During execution, a fault can cause an error that is not a valid state within a system boundary.

The same error can cause further errors within the system boundary, hence each new error acts as a fault, and it may propagate to the system boundary and be observable. When an error state is observed at the system boundary, that is called a failure.

The Fault Injection process follows a **Fault-Error-Failure Cycle**, which includes these steps:

1. **Fault:** Deliberate errors are introduced into the code, either during compile-time or run-time.
2. **Error:** These faults cause the software to act incorrectly, leading to unexpected behavior.
3. **Failure:** Eventually, the errors cause the software to fail, such as a service crash or system outage.

This cycle helps identify weaknesses in the system and improves its design for better performance and resilience.

## Types of Fault Injection Testing

Fault injection can be categorized into two types based on software implementation:

### 1. Compile-time fault injection

Compile-time fault injection is a fault injection technique in which source code is modified to inject imitated faults into a system. Two methods are used to implement faults at compile time:

- **Code Modification:** Mutation testing is used to change existing lines of code so that there may exist faults. Code mutation produces faults that are similar to the faults unintentionally made by programmers.

  **Example:**

  ```
  Original Code:
  int main()
  {
    int a = 10;
    while ( a > 0 )
    {
      cout << "GFG";
      a = a - 1;
    }
    return 0;
  }
  ```

  ```
  Modified Code:
  int main()
  {
    int a = 10;
    while ( a > 0 )
    {
      cout << "GFG";
      a = a + 1; // '-' is changed to '+'
    }
    return 0;
  }
  ```

  Now it can be observed that the value of `a` will increase and the `while` loop will never terminate, so the program will go into an infinite loop.

- **Code Insertion:** A second method of code mutation is code insertion fault injection, which adds code instead of modifying existing code. This is basically done by the use of anxiety functions, which are simple functions that take an existing value and change it via some logic into another value.

  **Example:**

  ```
  Original Code:
  int main()
  {
    int a = 10;
    while ( a > 0 )
    {
      cout << "GFG";
      a = a - 1;
    }
    return 0;
  }
  ```

  ```
  Modified Code:
  int main()
  {
    int a = 10;
    while ( a > 0 )
    {
      cout << "GFG";
      a = a - 1;
      a++; // Additional code
    }
    return 0;
  }
  ```

  Now it can be observed that the value of `a` will be fixed and the `while` loop will never terminate, so the program will go into an infinite loop.

### 2. Run-time fault injection

Run-time fault injection technique uses a software trigger to inject a fault into a running software system. Faults can be injected via a number of physical methods, and triggers can be implemented in different ways. Software triggers used in run-time fault injection:

```
1. Time Based Triggers
2. Interrupt Based Triggers
```

3 methods are used to inject faults at run-time:

1. **Corrupting memory space:** This method involves corrupting main memory and processor registers.
2. **System call interposition:** This method is related to fault imitation from operating system kernel interfaces to executing system software. This is done by intercepting operating system calls made by user-level software and injecting faults into them.
3. **Network level:** This method is related to the corrupting, loss, or reordering of network packets at the network interface.

**Fault Injection in Different Software Testing:**

- **Robustness Testing** - In robustness testing, fault injection is used.
- **Stress Testing** - Fault injection is also used in stress testing.

## Advantages of Fault Injection

1. **Improved resilience:** By testing the system's resilience to faults, the software development team can identify potential weaknesses and make improvements to ensure the system is more robust.
2. **Increased reliability:** By intentionally introducing faults, the software development team can identify and resolve potential issues before they occur in production.
3. **Improved debugging:** By intentionally introducing faults, the software development team can more easily identify and debug issues, as the cause of the problem is known.

## Disadvantages of Fault Injection

1. **Increased complexity:** Fault injection can add complexity to the software development process, making it more difficult for new developers to understand and contribute.
2. **Increased cost:** Fault injection can be an expensive process, as additional resources may be needed to simulate faults and monitor the system's behavior.
3. **Time-consuming:** Fault injection can take significant time and effort, as the software development team must carefully plan and execute the tests.

## When to Use Fault Injection in Software Testing?

Fault Injection is particularly useful when testing software systems that rely on external services, third-party APIs, or are deployed across multiple platforms. It helps assess how the system handles issues or disruptions in these external dependencies.

It is also valuable during the early stages of the software development process (SDLC) to identify potential failure points in a controlled environment, before the software is released to production.

## Fault Injection Tools

There are several tools available to automate Fault Injection testing. Some of the widely used ones include:

- **Xception**
- **beStorm**
- **Holodeck**
- **Grid-FIT**
- **Orchestra**
- **ExhaustiF**
- **The Mu Service Analyzer**

(这些现成工具大多面向硬件级/网络级/大型分布式系统的故障注入，不一定适合你当前要处理的"单元测试级、代码行级"故障注入场景——设计方案时请不要简单照搬这个工具列表，而是围绕"代码库 + 自动化测试"这个具体场景来设计。)

## Conclusion

Software Fault Injection is an important testing method that helps evaluate how software handles unexpected errors, ensuring it's robust and reliable. It's especially useful for complex systems that depend on external services or run across multiple platforms. By injecting faults in a controlled environment, developers can see how the software reacts to stress and fix any vulnerabilities before the software is released.

While Fault Injection is valuable, it requires careful planning since it can disrupt normal operations and needs changes to the source code. Despite these challenges, it offers important insights into the software's reliability, making it a powerful tool to improve its quality and performance.
````

提示词结束，以上内容请整段复制给 Agent。

</details>

亲手跑一遍产出的手工操作指南，尤其是那个"全局牵连"和那个"对照组"的例子——比起把方法论当作抽象概念来记忆，亲眼看到一个 null 检查牵连全部测试、或者一个测试确实测不出某种改动，会让你对"这套测试到底在保护什么"建立起远比读文字描述更扎实的理解。下一章会回顾这套"理解→设计→构建→评测"四元素方法论的完整闭环，并给出迁移到你自己项目的行动清单。

故障注入的闭环跑完，"这些测试真的在保护生产代码"这件事就有了可复现的证据，而非一句主观判断。第六章会回头串联这四章的完整链路，把"理解 → 设计 → 构建 → 评测"沉淀成一份可以直接搬到你自己棕地项目上的行动清单，这是把方法论从这一个案例里"拔出来"、变成你自己日常工作习惯的最后一步。

---

⬅️ 上一章：[第四章 构建面向业务不变式的自动化验收测试](../ch04/README.md#ch4-top) ｜ ➡️ 下一章：[第六章 总结"让 Agent 一次生成可信代码"工程范式](../ch06/README.md#ch6-top)
