# ch01 A 臂实验 RUNBOOK（人工执行部分）

对应实施计划 Task 1 Step 2 → Task 3 全部。坐到电脑前照着敲即可。
每个阶段末尾有「回来找我」标记——跑到那里把输出贴回会话，我接着做下一步。

先设两个变量，**每开一个新终端都要重设**：

```bash
export BOOK_REPO=/Users/binwu/OOR/katas/first-pass-trust
export CSV_REPO=/Users/binwu/OOR/katas/commons-csv
```

已就绪：工作副本 `/Users/binwu/OOR/katas/commons-csv`，分支 `2026-07-28-arm-a`，base SHA `66a83820`（与第三章 commit range 起点相同）。

**粘贴命令时一行一行贴**——多行一次性粘进 zsh 会被终端截断，报 `parse error near '\n'`。

---

## 阶段 A：基线（Task 1 Step 2）

```bash
cd $CSV_REPO && mvn test 2>&1 | tee $BOOK_REPO/experiments/ch01-arm-a/baseline.txt
echo "exit=${PIPESTATUS[0]}" >> $BOOK_REPO/experiments/ch01-arm-a/baseline.txt
```

**期望**：退出码 0。记下末尾那行 `Tests run: X, Failures: Y, Errors: Z, Skipped: W`。

⚠️ **若退出码非 0**：停下来，别继续。upstream 自身构建不干净会污染 G1/G2 的全部结论。把失败原因贴回来，我们先处理这个。

> **回来找我 ①**：贴 `Tests run` 那行 + 退出码。

---

## 阶段 B：一次生成（Task 2）

### B1 确认工作树干净

```bash
cd $CSV_REPO && git status --short
```

期望：**无输出**。有输出先清理干净。

### B2 开全新 Codex 会话

要求（破一条整个 ch01 就废）：

- **全新会话**，不带本书任何 AGENTS.md / skill 上下文
- 工作目录指向 `$CSV_REPO`
- 把 `prompt.md` 中 `---` 以下的正文**原样**粘贴进去

```bash
# 方便复制：只打印正文部分
sed -n '/^---$/,$p' $BOOK_REPO/experiments/ch01-arm-a/prompt.md | tail -n +2
```

### B3 期间的纪律

跑到它宣称"完成"为止。期间：

- ❌ 不追问、不纠错、不提示边界、不说"你考虑一下空行的情况"
- ✅ 它自己重跑测试、自查、多轮工具调用——都允许，那是 AI 自驱动的内部循环

**最容易破功的地方**：它问你"行号是从 0 还是 1 开始？"这类问题时。此时**不要回答语义问题**，只回"请你按你认为合理的方式实现"。它替你做主这件事，正是本节要拍下来的证据。

### B4 记下它的完成宣言

原样复制 AI 最后那段"我完成了什么、测试情况如何"的自述。这段话后面要和五闸门实测并排放——**是 1.2 节冲击力最强的素材**。

### B5 归档 diff

```bash
cd $CSV_REPO
git add -A
git diff --cached > $BOOK_REPO/experiments/ch01-arm-a/generated.diff
git commit -q -m "ch01 arm-a: 一次生成产出（未经任何人工纠正）"
git rev-parse HEAD
```

> **回来找我 ②**：贴 ① AI 的完成宣言原文 ② HEAD SHA ③ 模型 ID ④ token 消耗（输入/输出/合计）。我来写 `session.md`。

---

## 阶段 C：五闸门实测（Task 3）

### C1 — G1 编译/构建

```bash
mkdir -p $BOOK_REPO/experiments/ch01-arm-a/gates
cd $CSV_REPO && mvn test 2>&1 | tee $BOOK_REPO/experiments/ch01-arm-a/gates/G1.txt
echo "exit=${PIPESTATUS[0]}" >> $BOOK_REPO/experiments/ch01-arm-a/gates/G1.txt
```

判据：**不带任何特殊参数**退出码为 0 则 G1 通过。（`-Drat.skip=true` 在这一关不许用。）

### C2 — G2 回归绿

不用另跑命令，从 C1 输出里读 `Tests run / Failures / Errors / Skipped`，和阶段 A 的基线比。

判据：Failures = 0，Errors = 0，且 Tests run 不低于基线。

> **回来找我 ③**：贴 G1 退出码 + G1/基线两组 `Tests run` 数字。G1 或 G2 破了就到此为止（单调收紧），我直接开始写 1.2 节。

### C3 — G3 规格覆盖（人工逐条，不用 Agent）

先看 AI 事实上把四个边界实现成了什么：

```bash
cd $CSV_REPO
git diff 66a83820..HEAD --stat                    # 改了哪些文件
git diff 66a83820..HEAD -- src/main/java          # 生产代码怎么写的
git diff 66a83820..HEAD -- src/test/java          # 测试断言了什么
```

对着七条规则填 `gates/G3.md`（表格骨架我建好，你填"AI 实际实现成什么"和"覆盖它的测试"两列）：

| # | 规则 | 来源 |
| --- | --- | --- |
| R1 | 字段数不一致时抛异常 | 提示词明文 |
| R2 | 异常含行号、期望字段数、实际字段数 | 提示词明文 |
| R3 | 可开关、默认关闭、向后兼容 | 提示词明文 |
| R4 | **空行算不算一条记录** | 提示词未提 |
| R5 | **末尾无换行的那行算不算** | 提示词未提 |
| R6 | **quoted 内含换行时行号是物理行还是记录号** | 提示词未提 |
| R7 | **行号从 0 还是 1 起、含不含表头行** | 提示词未提 |

找测试的办法：

```bash
cd $CSV_REPO && grep -rn "assert" src/test/java --include=*.java | grep -i "line\|row\|count\|mismatch"
```

> **回来找我 ④**：贴上面三条 `git diff` 的输出（生产代码那份最重要）+ grep 结果。我来判档位、算 SCS、写 `G3.md`。

### C4 — G4 变异敏感

**只在 G3 至少有一条判为第 1 档时才做。** 具体注释掉哪一行等我看完 C3 再告诉你。

安全约束（这一步在动生产代码）：

- 注入前 `git status --short` 必须为空
- 只改一处
- 用完立即 `git checkout -- <文件>` 精确还原，再验证 `git status --short` 为空
- **全程禁用 `git reset --hard`**

```bash
cd $CSV_REPO && git status --short          # 必须空
# —— 按我给的位置注释掉那一行 ——
mvn -Drat.skip=true test 2>&1 | tee $BOOK_REPO/experiments/ch01-arm-a/gates/G4.txt
echo "exit=${PIPESTATUS[0]}" >> $BOOK_REPO/experiments/ch01-arm-a/gates/G4.txt
git checkout -- <被改的文件>
git status --short                          # 必须空
mvn -Drat.skip=true test                    # 必须恢复为绿
```

判据：注释后**至少一个测试变红**，且失败原因是断言不满足（业务语义）。编译失败、测试发现失败、超时——**都不算成功的变异**。

### C5 — G5 范围守卫

```bash
cd $CSV_REPO && git diff 66a83820..HEAD -- src/main/java > /tmp/g5-review.diff
wc -l /tmp/g5-review.diff
```

> **回来找我 ⑤**：G4 的结果 + 确认工作树已还原干净。G5 我基于已有的 `generated.diff` 直接判，不用你额外操作。

---

## 一句话版本

阶段 A 跑基线 → 阶段 B 开全新 Codex 会话粘提示词、**全程不纠正** → 阶段 C 跑 G1/G2 →
把输出贴回来，剩下的 G3/G4/G5 判定和 1.2 节写稿我来做。

五个「回来找我」节点，可以分几次跑完，不用一口气。
