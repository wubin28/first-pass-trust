<a id="ch5-top"></a>

# 第五章 评测自动化验收测试确实保护了生产代码

如何验证 Agent 生成的自动化测试确实保护了生产代码？

第四章把决策表推导出的用例实现成了逐条可调试的 Approved Scenarios 测试，全部变绿。但"测试是绿的"不等于"测试真的在保护生产代码"——它也可能是一个无论生产代码怎么改都不会变红的空测试。本章用故障注入测试来排除这种可能。

故障注入这件事，光看文字描述很难建立直觉。建议你先浏览完本章，再把末尾的提示词交给你的 Agent，让它针对自己（或你）实现的验收测试设计一轮真正的故障注入方案并跑通，亲眼看看哪些故障会让测试变红、哪些故障会像本章提到的"对照组"一样，改了却什么都没发生。

> [!TIP]
> **TL;DR · 读完本章你会：**
> - 理解故障注入测试的闭环：注入→变红→报错对应→撤销→变绿，用来证明一条测试确实在保护对应的生产代码，而非形同虚设；
> - 看清"随机机械变异"和"与保护意图语义对应的故障"之间的区别——这是本方法最容易踩的坑；
> - 看到三种实现方案的对比（git 文本精准替换 / PITest 变异测试 / 自制 Java 故障注入器），以及为什么一次性场景下 git 文本替换反而是最优解；
> - 亲眼见证两个反直觉的真实案例——一个"牵一发而动全身"的 null 检查、一个"诚实的对照组"（故障没让测试变红，但这恰恰揭示了测试的真实保护边界）。

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

如果目标是对整个代码库做无差别的大范围变异覆盖率扫描（而并非针对少数几个已知测试逐一验证保护意图），更适合用成熟的变异测试框架，见 5.2.2。

<a id="ch5-2"></a>

## 5.2 故障注入测试实现方案

> [!NOTE]
> 从这里到本章"动手练习提示词"之间所展示的内容，就是作者在添加新功能之前“评测自动化验收测试确实保护了生产代码”时与 Claude Code 搭配 Sonnet 5 用"假设体检 + 追问"提示词共创出来的结果。你可以先快速浏览，然后复制本章后半部分的[动手练习提示词](#ch5-hands-on)给你的 Agent ，自己动手跑一遍，再回来和本章上述所展示的内容对比复盘，看看自己的产出有什么差异、这些差异是否合理。

> [!IMPORTANT]
> 上述本章所展示的内容，都经过作者本人验证。由于大模型的不确定性，你把本章末尾的"动手练习提示词"发给自己常用的 Agent 和大模型组合后，会看到不完全相同的产出——这是很正常的现象。所以上述展示的内容，仅供你参考，而并非唯一答案。条条大路通罗马，解决问题的答案会有很多种。只要你理解了这份经过评审的参考结果背后的底层逻辑，再带着这份理解去评判你的 Agent 产出是否做到了"让 Agent 一次生成可信代码"，就已经能在亲身实践中有所收获。

### 5.2.1 方案1（选用）：git 文本精准替换且用 shell 脚本编排

为每个测试维护一份纯文本"故障清单"：测试显示名 / 生产代码文件 / 原始代码片段（而非硬编码行号，避免行号漂移导致误伤）/ 注入后的故障代码片段 / 期望测试失败时输出中必须出现的异常消息关键子串。自动化脚本逐条执行：按原始代码片段做精确文本替换 → 编译 → 单独跑该测试（预期失败）→ 校验报错信息里是否包含期望的关键子串 → `git checkout` 撤销改动 → 重新跑该测试确认变绿 → 记录"红→绿"结果。同一份故障清单，天然也是人类可读的手工调试资料的数据来源。

- **优势**：实现成本最低，几十到上百行 shell 代码即可覆盖全部测试；不引入任何新依赖、不改动构建配置文件；`git checkout` 作为撤销手段简单可靠，不会有残留污染；一份清单同时服务"自动化脚本"和"手工调试资料"两个交付物，内容天然一致。
- **劣势**：精确文本替换对跨多行的复杂改动不够优雅，容易因缩进/空格差异匹配失败，需要仔细设计每条替换的锚点；每个故障点都要重新编译，故障点数量多时累计有一定耗时；故障的"语义设计"工作量落在人身上——但这恰恰保证了语义对应。
- **适用场景**：一次性、本机验证，故障点数量可控，且故障清单可以逐条手工精心设计、语义对应关系明确。
- **不适用场景**：若未来故障点扩展到成百上千个，或需要对整个代码库做无差别的大范围自动变异扫描。

### 5.2.2 方案2：引入 PITest 变异测试框架

在构建配置里新增变异测试插件，限定目标类和目标测试，自动对目标类做字节码级变异（条件反转、常量替换、返回值变异等），对每个变异体跑测试套件，产出标准化的"杀死/存活"矩阵报告。

- **优势**：业界标准变异测试工具，自动化程度最高，不需要人工为每个测试逐一设计故障；产出标准化、可视化的报告；一次配置，理论上可重复用于任意代码区域。
- **劣势**：需要修改构建配置文件引入新插件依赖，与"一次性验证、用完即可丢弃不留痕迹"的定位有冲突；变异是字节码级"机械变异"，并非刻意针对"这个测试的保护意图"设计的语义故障，可能需要额外筛选才能找到对应某个具体测试的那个变异体；默认不提供"这个变异体对应哪一个测试用例"的清晰一对一映射；无法直接复用于手工调试场景——操作在字节码层面，不产出"注入前/后源码行示例"这种人类可读呈现，等于要维护两套粒度不同的故障描述。
- **适用场景**：目标是对相关代码做大范围、无遗漏的变异覆盖率扫描，且不要求"故障-测试"语义一一对应。
- **不适用场景**：诉求明确要求"故障与测试保护意图语义对应"+"同时服务手工调试"；一次性、小范围场景下，引入新框架的配置成本相对收益不划算。

### 5.2.3 方案3：自制 Java 故障注入器

写一个独立的小型程序，用配置文件描述每个故障点（文件路径、锚点字符串、原始代码、故障代码、期望断言片段），程序读取生产代码、按锚点定位替换、写回文件，再调用构建工具解析输出判断红绿状态，最后自己保存的原始内容做还原（不依赖 git，自成一体）。

- **优势**：结构化的字符串/文件处理比 shell 更健壮，不容易因 shell 转义、特殊字符出错；配置驱动的故障清单可以额外写一个小程序自动渲染成手工调试文档，避免人工同时维护两份内容；比 shell 更容易做复杂、结构化的校验与报告生成逻辑。
- **劣势**：实现成本明显高于方案1，需要新写一个独立程序，对"一次性、学习/验证用途"而言，工具本身的开发投入可能超过它要验证的东西；需要额外决定这个工具放在项目的什么位置、怎么编译运行；排查这个注入器自身的问题比直接读一个 shell 脚本成本更高。
- **适用场景**：预期未来还会把故障注入扩展到当前需求之外的其他功能模块，值得一次性投入做成可配置、可复用的工具。
- **不适用场景**：范围明确限定在少数测试、一次性用途，方案3的"可配置复用性"优势用不上。

**推荐方案1**，理由：与"一次性用途 / 按实现机制区分 / 不留痕迹"的定位逐条对应；实现体量与任务精确匹配，不过度设计（不像方案3要先造工具），也不过度依赖外部框架（不像方案2要改构建配置、还要额外解读变异报告）；同一份故障清单天然同时服务自动化脚本和手工调试资料两个交付物，保证两者内容一致；`git checkout` 作为撤销手段简单可靠。

<a id="ch5-3"></a>

## 5.3 用单步调试和故障注入的方法理解 approved scenarios 测试方法

把故障注入和第四章的单步调试结合起来，是加深理解生产代码逻辑最直接的办法：先用断点观察变量，再手工改一行代码、重跑测试看它变红、读懂报错信息，再改回来看它变绿——这正是故障注入测试的核心循环。下面挑几个有代表性的例子展示这个循环，并特别说明一种诚实但容易被误解的情况。

**典型循环（以"缺 1 个必需列"对应的测试为例）**
1. 打开生产代码，找到"把缺失列加入 `missingHeaders`"这一行，手工改成一个永远不会触发的条件（比如在判断后面加一个 `&& false`）。
2. 重跑对应的测试，观察它从绿变红：报错变成"本该抛出异常却什么都没抛出"。
3. 读懂这条报错，确认它确实在说"这一行逻辑被我屏蔽后，本该失败的场景意外成功了"。
4. `git checkout` 还原代码，重跑测试，确认变绿。

**一个"全局牵连"的例子，值得亲手改一遍**：如果把 `CSVFormat.validate()` 里对 `requiredHeaders` 做的 `null` 检查去掉（从"先判断非空再判断长度"改成"直接判断长度"），会发现**全部测试一起变红**，而不只是某一个测试——根因是这个 `null` 检查不仅保护它自己对应的那条规则，还保护着所有测试都依赖的一个静态默认配置对象的初始化。这类"看似局部的 null 检查、实际牵一发而动全身"的发现，正是亲手做一遍故障注入才能获得的直觉，光读代码很难建立。

**一种诚实的"对照组"情况：算不上错漏**：有些故障注入下去，测试依然是绿的——这谈不上故障清单设计有问题，恰恰相反，它准确地揭示了这个测试的真实保护边界。比如把"必需列是空数组还是未设置"这两种情况在代码里做的区分去掉（去掉一个纯粹的性能优化式短路判断），某些测试仍然会通过，因为该测试真正断言的只是"结果一致"，压根不关心"内部走了哪条分支"。遇到这种情况，正确的做法是在手工调试资料里如实记录"为什么这个故障不会让测试变红"，不要悄悄换一个更容易触发失败的故障来掩盖这个发现——这种诚实的记录本身就是在帮你准确理解这个测试真正能检测到什么、不能检测到什么,这正是故障注入测试最有价值的产出之一。

<a id="ch5-hands-on"></a>

## 5.4 动手练习提示词

为方便动手实操，这里把本章 5.1～5.3 节的“设计故障注入方案”和“产出手工操作指南”相应的提示词合并成一份完整的提示词。该提示词会先让 Agent 做事实核查，再设计 3 个方案并推荐一个，最终产出的单份文档本身就同时兼顾"自动化脚本设计"和"手工调试资料"两个用途。

使用前请把下面所有占位符替换成你自己的实际信息（如果某个占位符暂时没有明确答案，可以保留原样，让 Agent 自己判断并在文档里说明它做出的假设）：

- `<CODEBASE_PATH>`：目标代码库在本机的绝对路径。示例：`/Users/xxx/work/commons-csv-workcopy`
- `<DEBUGGING_GUIDE_PATH>`：第四章动手练习中你自己产出的调试指南文档路径（里面应包含各测试对应的生产代码类名+行号+断点）。示例：`/path/to/debugging-guide.md`
- `<ACCEPTANCE_TESTS_DESCRIPTION>`：要做故障注入测试的验收测试范围说明：数量、测试类全名、所在目录。示例："11 个验收测试，测试类为 `org.apache.commons.csv.requiredheaders.RequiredHeadersApprovedScenariosTest`，fixture 在 `src/test/resources/.../approved-scenarios/`"
- `<USAGE_SCOPE>`：这套故障注入设施的用途定位。示例："一次性验证/学习工具，不需要长期留在代码库里、不需要进 CI"
- `<DIFFERENTIATION_DIMENSION>`：希望 3 个方案按什么维度区分（不填则由 Agent 自行建议并说明理由）。示例："按故障注入的实现机制区分"
- `<IMPLEMENTATION_LANGUAGE>`：自动化脚本的实现语言偏好。示例："本机是 macOS iTerm2 zsh，优先 shell 脚本方案"
- `<VALIDATION_STRICTNESS>`：自动化校验"测试失败信息是否与生产代码行为相关"这一步的严格程度。示例："精确子串匹配：预先写好每个测试期望的异常消息关键片段，脚本跑完后用这个片段校验"
- `<OUTPUT_PATH>`：生成文档要保存到的路径。示例：`/path/to/solutions.md`

<details>
<summary>📋 点击展开/折叠完整提示词（可直接复制给 Agent）</summary>

提示词开始，请从这里往下全部复制

````
你是一名资深软件测试工程师。请为我设计 3 个"故障注入测试"（Fault Injection Testing）方案，目标代码库和背景信息如下：

- **代码库路径**：`<CODEBASE_PATH>`（本机 macOS，终端环境为 iTerm2 zsh）
- **要保护的验收测试**：`<ACCEPTANCE_TESTS_DESCRIPTION>`
- **这些测试对应生产代码的调试指南**（里面列出了每个测试命中的生产代码类名、行号、断点、变量）：`<DEBUGGING_GUIDE_PATH>`
- **这套设施的用途定位**：`<USAGE_SCOPE>`
- **方案区分维度偏好**：`<DIFFERENTIATION_DIMENSION>`（如果留空，请你自己提出一个合理的区分维度并说明理由）
- **自动化脚本实现语言偏好**：`<IMPLEMENTATION_LANGUAGE>`（如果留空，请结合代码库的构建工具自行判断）
- **自动化校验严格程度偏好**：`<VALIDATION_STRICTNESS>`（如果留空，请你自己判断并说明理由）

## 背景知识：什么是故障注入测试

你可能不熟悉"故障注入测试"这个方法，本提示词末尾的"附录"里已经附上了完整的参考资料，请先阅读附录内容，再开始设计方案。核心要点提前说明：

> 故障注入测试的核心目的，是在生产代码里**人为、有针对性地**引入一个错误（故障），让某个自动化测试因此运行失败，并验证这个失败的报错信息确实与该测试本应保护的那一段生产代码行为相关；然后撤销这个人为引入的错误，再次运行该测试，观察它恢复变绿。如果整个"变红→报错信息对应→变绿"的闭环都成立，就证明这个测试确实在保护对应的生产代码，而并非形同虚设的空测试。

**这个方法最容易踩的坑**：把"故障注入"做成随机的、机械的代码变异（比如随手把 `&&` 改成 `||`，或者把 `>` 改成 `>=`），而并非针对"这个测试到底想保护什么行为"去精心设计一个语义对应的故障。机械变异只能笼统证明"测试能检测到某种变化"，回答不了"这个特定测试是否确实在保护这一行具体逻辑"这个问题。你设计的 3 个方案都应该围绕"故障要与测试的保护意图语义对应"这个原则展开，而并非推荐纯随机变异工具。

## 在设计方案之前，你必须先做的事实核查

不要凭空假设代码库的现状，请先：

1. 读一遍 `<DEBUGGING_GUIDE_PATH>`，了解每个测试对应的生产代码位置、断点、变量。
2. 实际打开 `<CODEBASE_PATH>` 下对应的生产代码文件，核实 debugging guide 里提到的类名、行号、代码逻辑目前是否仍然准确（代码可能已经变动，guide 里的行号可能已经漂移）。如果你没有直接读取本地文件系统的能力，请在文档里明确声明"以下内容基于 debugging guide 原文，未做本地代码核实，正式实现前请用 `grep -n` 重新核实行号"，不要假装自己核实过。
3. 检查代码库的构建配置文件（如 Java 项目的 `pom.xml`/`build.gradle`），确认是否已经集成了某种变异测试框架（如 PITest）。这个事实会影响你在"方案二"里描述的优劣势（如果已经集成了，那么"方案二：引入变异测试框架"的实现成本会大幅降低，需要如实调整该方案的优劣势描述）。

把你的事实核查结果写在文档最前面的"背景与已确认的前提"小节里，再开始设计 3 个方案。

## 任务要求

请设计 **3 个方案**，用于在 `<CODEBASE_PATH>` 这个代码库里，针对 `<ACCEPTANCE_TESTS_DESCRIPTION>` 做故障注入测试，需要同时满足两个用途：

1. **自动化脚本**：依次对每个测试，在其对应生产代码里注入一个与该测试保护意图语义对应的故障，让该测试运行失败；验证失败时的报错信息确实与该测试所保护的生产代码行为相关；然后撤销故障，重新运行该测试，确认它变绿。这个"注入→验证变红→撤销→验证变绿"的闭环要能做成可重复执行的自动化脚本。
2. **手工调试资料**：为每一个测试提供一份人类可读的"故障注入信息"，包含：生产代码的类名+行号、注入前代码示例、注入后代码示例、注入后运行该测试时期望看到的报错信息。这份资料是给我在 VSCode 里用 debug 单步执行的方式手工注入故障、观察测试失败用的，目的是加深我对相关生产代码逻辑的理解。

3 个方案应该按 `<DIFFERENTIATION_DIMENSION>` 这个维度互相区分（即每个方案代表一种不同的"故障怎么注入、怎么撤销"的技术路线），而并非互相之间只是"故障点多少""脚本拆分方式"这类表面差异。

## 输出格式（严格遵守，这是让文档质量达标的关键）

请把最终产出保存为一份 markdown 文件，路径为 `<OUTPUT_PATH>`。文件结构必须是：每个方案一节（主要内容/优势/至少3条/劣势/至少2条/适用场景/不适用场景），最后一节"推荐方案"逐条对应"背景与已确认的前提"里的具体决策给出理由。

## 写作质量要求

- 每个方案的"优势"和"劣势"必须具体到这个方案自己的实现细节上（比如提到会不会改动构建配置文件、会不会引入新依赖、撤销故障的具体手段是什么、报错信息的可读性如何），不要写"简单""复杂""灵活""不灵活"这类没有信息量的形容词而不给出原因。
- "适用场景"和"不适用场景"必须是互补的、具体的条件判断，而并非同一句话正反说两遍。
- 3 个方案的"优势/劣势"之间要能相互印证——如果方案一的优势是"不改构建配置文件"，那么其他把这一点作为劣势的方案就应该明确提到"需要改动构建配置文件"，保持前后一致，不要自相矛盾。
- 推荐方案的理由要逐条对应"背景与已确认的前提"里的具体决策，不要写泛泛而谈的总结性理由。
- 全文使用中文，专有名词（类名、方法名、工具名）保留英文原文。
- 不要在方案设计阶段直接动手实现（不要去改生产代码、不要创建除了这份 markdown 文档之外的任何文件）。文档写完之后，等待我确认选哪个方案，再进入实现阶段。

方案确定后，请按推荐方案实现自动化脚本，并产出手工故障注入指南——每条包含生产代码的类名+行号、注入前代码、注入后代码、注入后运行该测试时期望看到的报错信息。如果某个故障注入后测试依然是绿的，请如实说明"为什么这个故障不会让测试变红"，不要隐瞒或者悄悄换一个更容易触发失败的故障。

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

---

⬅️ 上一章：[第四章 构建面向业务不变式的自动化验收测试](../ch04/README.md#ch4-top) ｜ ➡️ 下一章：[第六章 总结"让 Agent 一次生成可信代码"工程范式](../ch06/README.md#ch6-top)
