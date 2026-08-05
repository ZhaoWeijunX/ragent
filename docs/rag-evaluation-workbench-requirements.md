# RAG 评测工作台需求文档

> 文档状态：Draft  
> 适用项目：Ragent AI  
> 功能域：质量 / RAG 评测  
> 目标版本：待排期  
> 最后更新：2026-07-28

## 1. 背景

Ragent 已具备完整的 RAG 问答链路、评测旁路接口、RAG Trace 以及较完整的评测方法论：

- `/rag/v3/chat` 可执行真实 SSE 问答链路；评测 Runner 可据此收集最终回答并采集用户首字延迟。
- `/rag/eval` 可执行 Query 改写、意图识别与检索，返回召回文档、Chunk、上下文和意图结果。
- `t_rag_trace_run`、`t_rag_trace_node` 可记录真实问答链路及节点耗时。
- `resources/docs/ragent-test/RAG 评测——从指标到实践/` 已定义评估集、Runner、自建指标、RAGAS 指标与报告口径。

目前评测能力主要存在于后端旁路接口、外部 `ragenteval` 项目和教程中。使用者需要准备 JSONL、执行命令行、读取本地报告，并手工关联 Trace。Ragent 管理台没有数据集管理、批量跑测、历史对比、失败下钻和质量门禁能力。

因此需要建设 RAG 评测工作台，将现有方法论产品化，形成以下闭环：

```text
评估集管理
  → 创建评测运行
  → 录制真实问答与检索证据
  → 计算自建指标与 RAGAS 指标
  → 查看总览、切片和失败样本
  → 关联 Trace 定位原因
  → 优化模型 / Prompt / 检索配置
  → 使用同一数据集重新评测并对比
```

## 2. 现状与主要问题

### 2.1 已有能力

| 能力 | 当前实现 | 可复用点 |
| --- | --- | --- |
| 真实问答 | `GET /rag/v3/chat` | 回答、SSE 状态、TTFT、会话与任务 ID |
| 检索旁路 | `GET /rag/eval` | 意图、召回文档、Chunk、上下文、检索耗时 |
| 链路追踪 | RAG Trace | 问答节点、节点耗时、异常、USER_TTFT、LLM_TTFT |
| 评测方法论 | RAG 评测教程与外部 `ragenteval` | 评估集 Schema、Runner、指标、报告和失败归因 |
| 管理端基础 | React Admin、Trace 页面 | 可复用布局、表格、筛选、详情与 Trace 跳转 |

### 2.2 当前问题

1. **使用门槛高**：评测依赖外部 Python 项目和命令行，非研发角色难以使用。
2. **资产不可管理**：评估集、运行记录和报告以本地文件存在，缺少统一版本、状态和审计。
3. **结果不可对比**：无法在管理台直接比较两个版本或两次配置的指标差异。
4. **诊断链路断裂**：失败样本与 RAG Trace 未直接关联。
5. **旁路存在漂移**：`/rag/eval` 与真实聊天是两次独立请求，改写或检索可能受随机性、缓存和后续代码演进影响，证据不保证与模型实际使用的上下文完全一致。
6. **缺少质量门禁**：指标退化只能人工发现，无法形成可配置的通过/失败判定。
7. **RAGAS 有成本与方差**：LLM Judge 耗时、耗费 Token，且存在 NaN、误判和单跑波动，需要任务化、重试、多样本采样和成本控制。

## 3. 产品目标

### 3.1 核心目标

1. 在 Ragent 管理台完成评估集的创建、导入、版本化和维护。
2. 支持选择评估集批量执行真实 RAG 问答，并持续展示任务进度。
3. 自动计算意图、检索、生成质量和性能指标。
4. 支持按意图、难度、是否需要 RAG 等维度切片分析。
5. 支持两次评测运行横向对比，识别回归与提升。
6. 支持从失败样本跳转到对应 Trace，定位问题发生环节。
7. 保留原始录制数据，实现“录制一次，评分 N 次，报告 M 次”。

### 3.2 成功标准

- 管理员不依赖命令行即可完成一次端到端评测。
- 同一评估集可重复运行，并能对比任意两次运行。
- 每个成功录制的样本均可查看问题、回答、标准答案、召回证据和指标。
- 真实聊天成功产生 Trace 时，评测样本可跳转到该 Trace。
- 自建指标可重复计算，结果一致。
- RAGAS 评分失败不影响已录制数据和自建指标。
- 评测任务可以取消、失败重试，并保留失败原因。

## 4. 非目标

首期不包含以下能力：

- 在线自动训练 Embedding、Rerank 或大模型。
- 自动修改 Prompt、意图树、知识库或检索配置。
- 替代生产监控平台；评测关注受控样本的离线质量，不承担线上流量 SLO 监控。
- 从含个人信息的生产对话中自动生成评估集。
- 多轮对话评测、Agent 多步骤计划质量评测和人工众包标注平台。
- 将 P95/P99 作为小样本评估集的默认性能结论。

## 5. 用户角色

| 角色 | 主要诉求 | 首期权限 |
| --- | --- | --- |
| 管理员 | 管理评估集、发起评测、配置 Judge、维护阈值 | 全部操作 |
| RAG 研发 | 查看报告、对比运行、分析失败样本和 Trace | 查看与运行 |
| 产品 / 测试 | 查看总览、按业务场景验收、分析低分样本 | 查看 |
| 普通用户 | 无评测工作台需求 | 无权限 |

首期至少通过现有 `ADMIN` 角色保护评测管理接口和页面。后续引入细粒度 RBAC 后，可拆分“查看、执行、维护、配置”权限。

## 6. 核心概念

| 概念 | 说明 |
| --- | --- |
| 评估集 Dataset | 一组具有共同业务范围的评估样本，例如“售后政策评估集” |
| 数据集版本 Dataset Version | 评估集的一次不可变快照，例如 `v1.2` |
| 评估样本 Case | 用户问题及其意图、标准文档、标准答案等标注 |
| 评测运行 Run | 使用指定数据集版本和配置执行的一次批量评测 |
| 录制 Record | 某个样本在一次运行中的问题、回答、检索证据、耗时和 Trace 信息 |
| 评分 Score | 基于录制结果计算的自建指标或 RAGAS 指标 |
| 基线 Baseline | 用于对比的历史运行 |
| Judge | 对回答与上下文进行语义评分的 LLM |

## 7. 功能范围与分期

### 7.1 MVP：可运行、可查看、可诊断

1. 评估集 CRUD、JSONL/JSON 导入、样本校验。
2. 数据集版本发布与不可变快照。
3. 创建、取消和查看评测运行。
4. 调用真实聊天与评测旁路，保存录制结果。
5. 计算自建指标：
   - Intent Top-1 Accuracy
   - Hit@1/3/5/10
   - Recall@1/3/5/10
   - MRR@10
   - 误拒率
   - 过召回率
   - TTFT P50 与平均值
6. 总览、运行列表、运行详情、样本详情。
7. 按二级意图、难度和执行状态筛选。
8. 保存并跳转真实问答 Trace。
9. 导出运行记录和自建指标。

### 7.2 V1：语义评分与回归对比

1. RAGAS 五项指标：
   - Faithfulness
   - Answer Relevancy
   - Answer Correctness
   - Context Precision
   - Context Recall
2. RAGAS 异步评分、并发控制、重试、NaN 标记和成本统计。
3. 任意两个 Run 的 A/B 对比。
4. 失败规则与失败原因聚合。
5. 可配置质量阈值与 Run 通过/失败判定。

### 7.3 V2：持续质量与高级能力

1. CI 查询接口或 Webhook，用于合并前质量门禁。
2. 评测计划与定时运行。
3. 多轮对话和多问题拆分评测。
4. 从已脱敏的线上 Trace 提议候选评估样本，人工审核后入集。
5. 跨数据集趋势看板和长期质量基线。

## 8. 业务流程

### 8.1 数据集准备

1. 管理员新建评估集，填写名称、业务域和说明。
2. 通过页面新增样本，或上传 JSONL/JSON。
3. 系统执行字段、类型、唯一性和引用关系校验。
4. 用户修复错误后发布版本。
5. 已发布版本不可直接修改；修改样本将生成新草稿版本。

### 8.2 创建评测运行

用户需要选择或填写：

- 数据集及已发布版本。
- 运行名称和说明。
- 是否执行 RAGAS。
- Judge 配置引用，仅保存配置 ID，不在 Run 中明文保存密钥。
- RAGAS 独立采样次数，默认 1，可选 1～3。
- 最大并发数、单样本超时和失败重试次数。
- 可选基线 Run。
- 可选标签，例如分支、Commit ID、环境、实验名称。

创建后系统必须保存本次配置快照，避免运行结束后因系统配置变化而无法复现。

建议快照至少包含：

- 模型 Tier 与候选模型标识。
- Embedding、Rerank 配置标识。
- 检索通道开关、TopK、RRF 和 Rerank 参数。
- 意图树版本或更新时间。
- 知识库 ID 及文档/索引版本信息。
- Prompt 版本或内容摘要。
- 应用版本、Git Commit、运行环境。

若某些信息当前无法获得，允许为空，但必须预留字段。

### 8.3 录制阶段

每个样本执行：

1. 调用真实聊天接口，收集：
   - 最终回答 `response`
   - Thinking（如启用且允许存储）
   - `conversationId`
   - `taskId`
   - `traceId`
   - 首个非空 `response` Delta 到达时间，即 TTFT
   - 总耗时和最终状态
2. 调用 `/rag/eval`，收集：
   - 预测意图
   - 召回文档 ID
   - 召回 Chunk ID
   - 召回上下文
   - MCP / KB 分支
   - 旁路检索耗时
3. 合并为不可变录制记录。
4. 单条失败时记录错误并继续处理其余样本。
5. 按任务进度更新成功、失败、运行中和待执行数量。

MVP 允许使用“双接口录制”，但页面和报告必须标记该技术限制：检索证据来自独立旁路请求，不一定与真实生成时使用的上下文完全一致。

后续应优先将真实生产链路使用的意图与检索上下文写入 Trace 或评测专用捕获对象，通过同一个 `traceId` 直接获取证据，消除旁路漂移。

### 8.4 评分阶段

评分必须与录制解耦：

- 自建指标由 Ragent 后端基于已录制数据计算。
- RAGAS 通过独立评分适配器异步执行。
- 调整指标算法、阈值或 Judge 后，可在不重新调用被测系统的情况下重新评分。
- 重新评分需要生成新的评分批次，保留历史结果，不覆盖旧结果。

### 8.5 报告与诊断

运行完成后，用户按以下层级查看：

```text
总体指标
  → 按意图 / 难度切片
  → 失败样本列表
  → 单样本回答、上下文、评分与失败原因
  → 对应 Trace 节点详情
```

## 9. 评估集需求

### 9.1 样本字段

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `queryId` | 是 | 数据集版本内唯一的稳定业务 ID |
| `query` | 是 | 用户原始问题 |
| `intentL1` | 否 | 一级意图标注 |
| `intentL2` | 否 | 二级意图或叶子意图编码 |
| `difficulty` | 否 | `easy`、`medium`、`hard` |
| `requiresRag` | 是 | 该问题是否应进入知识检索 |
| `expectedAnswerType` | 否 | 事实、推荐、步骤、拒答等 |
| `expectedDocIds` | 条件必填 | `requiresRag=true` 时建议必填 |
| `niceToHaveDocIds` | 否 | 命中可加分但非必要的文档 |
| `groundTruth` | 条件必填 | 执行 Answer Correctness 时必填 |
| `trapType` | 否 | 歧义、预算、时效、越界等陷阱类型 |
| `enabledMetrics` | 否 | 单样本需要执行的指标；为空使用运行默认值 |
| `tags` | 否 | 自定义标签 |
| `metadata` | 否 | 可扩展 JSON |

### 9.2 校验规则

1. `queryId` 在同一版本中唯一。
2. `query` 去除首尾空格后不能为空。
3. `requiresRag=true` 且启用检索指标时，至少存在一个 `expectedDocIds` 或 `niceToHaveDocIds`。
4. 启用 Answer Correctness 时必须有 `groundTruth`。
5. `intentL2` 如能映射当前意图树，应展示映射状态；无法映射时允许导入但给出警告。
6. 文档业务码无法映射到当前知识库时给出警告，不直接阻止历史数据集导入。
7. 导入时返回成功数、失败数、警告数及逐行错误。
8. 评估集禁止保存未脱敏手机号、身份证、令牌等敏感信息；首期至少提供显式风险提示。

### 9.3 版本规则

- 数据集版本包含 `DRAFT`、`PUBLISHED`、`ARCHIVED` 状态。
- Run 只能引用 `PUBLISHED` 版本。
- 已被 Run 引用的版本不可物理删除。
- 发布后的样本不可修改；编辑操作生成新草稿版本。
- 支持从现有版本复制创建新版本。

## 10. 评测运行需求

### 10.1 状态机

```text
PENDING
  → RECORDING
  → DETERMINISTIC_SCORING
  → RAGAS_SCORING（可选）
  → REPORTING
  → COMPLETED
```

任意执行阶段可进入：

- `CANCELLED`：用户取消。
- `FAILED`：任务级不可恢复错误。
- `PARTIAL_SUCCESS`：存在样本失败，但已生成可用报告。

### 10.2 执行约束

- 同一环境默认只允许一个 Run 进入录制阶段，避免模型限流与评测互相干扰；该值可配置。
- 单 Run 内样本并发默认 1，最大值由管理员配置。
- RAGAS 并发与聊天录制并发独立控制。
- 单样本超时不能导致整个 Run 失败。
- 支持从失败样本继续执行，不重复成功样本。
- 已开始的 Run 不允许修改数据集版本和配置快照。
- 取消操作为协作式取消：不强制中断已发出的模型请求，但不再调度新样本。

### 10.3 进度信息

页面至少展示：

- 总样本数。
- 待执行、运行中、成功、失败数量。
- 当前阶段。
- 当前阶段进度。
- 开始时间、已运行时长、预计剩余时间（可选）。
- RAGAS 已完成数、失败数、Token 用量和估算成本（可获取时）。

## 11. 指标定义

### 11.1 意图指标

**Intent Top-1 Accuracy**

```text
预测 Top-1 意图与 intentL2 完全一致的样本数
÷
具有 intentL2 标注的样本总数
```

多子问题场景的 MVP 口径为取第一个非空预测意图。页面必须标注该限制；V2 再定义集合匹配或逐子问题匹配。

### 11.2 检索指标

检索指标默认只统计 `requiresRag=true` 且具有期望文档标注的样本。

- **Hit@K**：Top-K 召回文档是否至少命中一个 Gold 文档。
- **Recall@K**：Top-K 命中的 Gold 文档数 ÷ Gold 文档总数。
- **MRR@10**：第一个 Gold 文档在 Top-10 中排名的倒数；未命中为 0。
- `niceToHaveDocIds` 是否计入 Gold 需要作为运行配置，默认不计入严格口径。

### 11.3 分流指标

- **误拒率**：`requiresRag=true`，但没有形成有效 RAG 回答的样本比例。
- **过召回率**：`requiresRag=false`，但实际进入 KB 检索的样本比例。

有效回答和是否进入 KB 的判断必须使用结构化字段，不通过回答文本关键词猜测。

### 11.4 性能指标

- **TTFT**：从客户端发出真实聊天请求到收到第一个非空 `type=response` Delta 的时间。
- Thinking 首包不计入 TTFT。
- 展示 P50、平均值、最小值、最大值和有效样本数。
- 当有效样本少于 500 条时，P95/P99 可显示为实验数据，但不作为默认结论或质量门禁。
- `/rag/eval` 的 `latencyMs` 仅表示旁路改写、意图与检索总耗时，不得标记为 TTFT。

### 11.5 RAGAS 指标

| 指标 | 核心问题 | 主要输入 |
| --- | --- | --- |
| Faithfulness | 回答中的事实能否从召回上下文推出 | 回答、召回上下文 |
| Answer Relevancy | 回答是否切题 | 问题、回答 |
| Answer Correctness | 回答是否与标准答案一致 | 问题、回答、标准答案 |
| Context Precision | 召回上下文中相关内容是否排在前面 | 问题、召回上下文、标准答案 |
| Context Recall | 标准答案所需信息是否被上下文覆盖 | 标准答案、召回上下文 |

注意：RAGAS Context Recall 是基于语义 Judge 的指标，不等同于基于文档 ID 集合计算的 Recall@K，页面必须分组展示，避免混淆。

### 11.6 聚合维度

所有适用指标至少支持：

- `overall`
- `byIntentL1`
- `byIntentL2`
- `byDifficulty`
- `perSample`

聚合结果必须同时保存样本数，避免将 1 条样本的 100% 与 50 条样本的 100% 等价解读。

## 12. 失败判定与归因

一条样本可具有多个失败原因。首期建议支持：

- `intent_mismatch`
- `hit_at_5_miss`
- `recall_at_5_low`
- `rag_required_but_not_used`
- `rag_not_required_but_used`
- `faithfulness_low`
- `answer_correctness_low`
- `ttft_exceeded`
- `chat_request_failed`
- `eval_request_failed`
- `ragas_failed`

阈值必须可配置并保存到评分批次，不应硬编码在前端。

失败样本详情必须同时展示：

- 原始问题与标注。
- 系统回答与标准答案。
- 预测意图与期望意图。
- 召回文档与期望文档差异。
- 召回上下文。
- 单样本指标及 Judge 原始说明（如可获取）。
- 所有失败原因。
- 请求错误。
- Trace 跳转。

## 14. A/B 对比

用户选择当前 Run 与基线 Run 后，页面展示：

1. 配置快照差异。
2. 核心指标的当前值、基线值、绝对差和相对变化。
3. 按二级意图的指标变化。
4. 新增失败、已修复失败和持续失败样本。
5. TTFT 变化。
6. 两次运行的样本交集和缺失情况。

只有数据集版本一致时才能给出严格回归结论。版本不一致时允许查看，但必须提示“样本集不同，指标不可直接归因于系统变化”。

## 15. Trace 对接

### 15.1 关联字段

每条录制记录至少保存：

- `traceId`
- `conversationId`
- `taskId`

### 15.2 页面行为

- 样本详情提供“查看 Trace”入口，跳转 `/admin/traces/{traceId}`。
- Trace 不存在或已清理时，入口置灰并说明原因。
- 运行详情可按 Trace 是否存在筛选。

### 15.3 后续演进

为了消除双接口旁路漂移，建议在评测模式下扩展真实聊天 Trace：

- 在 Trace `extra_data` 或独立捕获表中记录实际预测意图。
- 记录生成时实际使用的 `retrievedChunkIds`、`retrievedContexts` 和业务文档 ID。
- 由 Run 通过 `traceId` 拉取真实证据。
- 当真实证据完整时，不再调用 `/rag/eval`。

评测字段较大时不建议全部写入 `t_rag_trace_node.extra_data`；长文本上下文应存放在评测录制表或对象存储中，Trace 只保存引用。

## 16. 数据模型建议

以下为逻辑模型，具体字段类型遵循项目 PostgreSQL 和 MyBatis-Plus 约定。

### 16.1 `t_eval_dataset`

- `id`
- `name`
- `description`
- `domain`
- `status`
- `created_by`
- `create_time`
- `update_time`
- `deleted`

### 16.2 `t_eval_dataset_version`

- `id`
- `dataset_id`
- `version`
- `status`
- `sample_count`
- `content_hash`
- `published_by`
- `published_at`
- `create_time`
- `update_time`
- `deleted`

唯一约束：`dataset_id + version`。

### 16.3 `t_eval_case`

- `id`
- `dataset_version_id`
- `query_id`
- `query`
- `intent_l1`
- `intent_l2`
- `difficulty`
- `requires_rag`
- `expected_answer_type`
- `expected_doc_ids` JSON
- `nice_to_have_doc_ids` JSON
- `ground_truth` TEXT
- `trap_type`
- `enabled_metrics` JSON
- `tags` JSON
- `metadata` JSON
- `create_time`
- `update_time`
- `deleted`

唯一约束：`dataset_version_id + query_id`。

### 16.4 `t_eval_run`

- `id`
- `name`
- `dataset_version_id`
- `baseline_run_id`
- `status`
- `current_phase`
- `config_snapshot` JSON
- `total_count`
- `success_count`
- `failed_count`
- `progress`
- `ragas_enabled`
- `created_by`
- `started_at`
- `finished_at`
- `error_message`
- `create_time`
- `update_time`
- `deleted`

### 16.5 `t_eval_record`

- `id`
- `run_id`
- `case_id`
- `status`
- `question`
- `response` TEXT
- `thinking` TEXT
- `retrieved_doc_ids` JSON
- `retrieved_chunk_ids` JSON
- `retrieved_contexts` JSON 或对象存储引用
- `retrieved_context_doc_ids` JSON
- `predicted_intents` JSON
- `has_kb`
- `has_mcp`
- `ttft_ms`
- `total_latency_ms`
- `eval_latency_ms`
- `conversation_id`
- `task_id`
- `trace_id`
- `error_message`
- `raw_payload` JSON 或对象存储引用
- `started_at`
- `finished_at`
- `create_time`
- `update_time`
- `deleted`

唯一约束：`run_id + case_id`。

### 16.6 `t_eval_score_batch`

- `id`
- `run_id`
- `score_type`：`DETERMINISTIC` / `RAGAS`
- `status`
- `algorithm_version`
- `judge_config_snapshot` JSON
- `sample_count`
- `token_usage` JSON
- `estimated_cost`
- `started_at`
- `finished_at`
- `error_message`
- `create_time`
- `update_time`
- `deleted`

### 16.7 `t_eval_score`

- `id`
- `score_batch_id`
- `run_id`
- `record_id`：聚合指标为空
- `metric_name`
- `dimension_type`：`OVERALL` / `INTENT_L1` / `INTENT_L2` / `DIFFICULTY` / `SAMPLE`
- `dimension_value`
- `score_value`
- `sample_count`
- `detail` JSON
- `create_time`
- `update_time`
- `deleted`

## 17. API 草案

统一前缀建议使用 `/admin/evaluations`，并由管理员权限保护。

### 17.1 数据集

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/datasets` | 分页查询数据集 |
| `POST` | `/datasets` | 新建数据集 |
| `GET` | `/datasets/{id}` | 数据集详情 |
| `PUT` | `/datasets/{id}` | 修改数据集元数据 |
| `DELETE` | `/datasets/{id}` | 删除未被引用的数据集 |
| `POST` | `/datasets/{id}/versions` | 创建草稿版本 |
| `POST` | `/dataset-versions/{versionId}/import` | 导入 JSONL/JSON |
| `GET` | `/dataset-versions/{versionId}/cases` | 查询样本 |
| `POST` | `/dataset-versions/{versionId}/cases` | 新增样本 |
| `PUT` | `/cases/{caseId}` | 修改草稿样本 |
| `DELETE` | `/cases/{caseId}` | 删除草稿样本 |
| `POST` | `/dataset-versions/{versionId}/validate` | 校验版本 |
| `POST` | `/dataset-versions/{versionId}/publish` | 发布版本 |
| `GET` | `/dataset-versions/{versionId}/export` | 导出版本 |

### 17.2 运行与评分

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/runs` | 分页查询运行 |
| `POST` | `/runs` | 创建并启动运行 |
| `GET` | `/runs/{runId}` | 运行详情与进度 |
| `POST` | `/runs/{runId}/cancel` | 取消运行 |
| `POST` | `/runs/{runId}/resume` | 从失败样本继续 |
| `POST` | `/runs/{runId}/rescore` | 创建新评分批次 |
| `GET` | `/runs/{runId}/metrics` | 查询聚合指标 |
| `GET` | `/runs/{runId}/records` | 查询样本记录 |
| `GET` | `/records/{recordId}` | 样本详情 |
| `GET` | `/runs/{runId}/export` | 导出运行与报告 |
| `GET` | `/runs/{runId}/compare/{baselineRunId}` | A/B 对比 |

## 18. 前端页面

### 18.1 菜单与路由

建议在 Admin 新增一级菜单“RAG 评测”：

- `/admin/evaluations/datasets`
- `/admin/evaluations/datasets/:datasetId`
- `/admin/evaluations/dataset-versions/:versionId`
- `/admin/evaluations/runs`
- `/admin/evaluations/runs/:runId`
- `/admin/evaluations/runs/:runId/compare/:baselineRunId`
- `/admin/evaluations/records/:recordId`

### 18.2 数据集列表

展示名称、业务域、最新版本、样本数、状态、更新时间和创建人。支持新建、复制、归档和进入版本详情。

### 18.3 数据集版本详情

- 版本信息和发布状态。
- 样本列表、筛选和分页。
- 新增、编辑、删除草稿样本。
- JSONL/JSON 导入和导出。
- 校验报告。
- 发布确认。

### 18.4 运行列表

展示运行名称、数据集版本、状态、进度、是否启用 RAGAS、核心指标、创建人、开始时间和耗时。支持创建、取消、继续、重新评分和对比。

### 18.5 运行详情

建议分为以下区域：

1. **运行摘要**：状态、数据集版本、配置快照、进度和成本。
2. **核心指标**：Intent Top-1、Hit@5、Faithfulness、Answer Correctness、TTFT P50。
3. **检索指标**：Hit@K、Recall@K、MRR。
4. **生成指标**：RAGAS 五项。
5. **性能指标**：TTFT P50、平均值和逐样本分布。
6. **意图切片**：按 `intentL2` 展示样本数和指标。
7. **失败样本**：按失败原因筛选。
8. **配置快照**：运行时系统配置。

### 18.6 样本详情

左右或上下对照展示：

- 用户问题、标准答案、系统回答。
- 期望意图、预测意图。
- 期望文档、召回文档及差异。
- 召回 Chunk 与完整上下文。
- 指标、失败原因和 Judge 说明。
- 性能时间。
- Trace 跳转。

## 19. 后端架构建议

### 19.1 模块边界

建议在 `bootstrap` 下新增独立 `rag/evaluation` 或扩展现有 `rag/eval` 包，内部按职责拆分：

- `controller`：数据集、Run、报告 API。
- `service`：业务编排与状态机。
- `runner`：真实问答录制和旁路证据采集。
- `metric`：自建指标 SPI。
- `judge`：RAGAS / LLM Judge 适配器。
- `report`：聚合、对比和失败归因。
- `dao`：评测资产持久化。
- `task`：异步执行、取消和恢复。

### 19.2 指标 SPI

自建指标不应写成一个大型条件分支。建议定义指标扩展接口，至少包含：

- 指标名称。
- 适用样本判断。
- 单样本计算。
- 聚合逻辑。
- 算法版本。

这样后续增加 NDCG、Citation Correctness 等指标时无需修改运行主流程。

### 19.3 RAGAS 集成

Java 项目不应直接嵌入 Python 依赖。建议定义 `SemanticEvaluationProvider`：

- Ragent 负责数据集、Run、任务状态、权限和报告。
- Python 评测服务负责 RAGAS 计算。
- 双方通过批量 HTTP API 或消息队列交互。
- Ragent 保存原始请求摘要、评分结果、算法版本和 Judge 配置快照。

可将现有 `ragenteval` 的评分能力封装为独立服务。若评分服务不可用，Run 应完成自建指标并标记 RAGAS 阶段失败，不能丢失录制结果。

### 19.4 异步任务

评测是分钟级任务，不应在 Controller 请求线程内执行。建议：

- API 创建 Run 后立即返回 Run ID。
- 使用专用评测线程池或消息队列调度。
- 任务状态落库，应用重启后可识别未完成任务并转为可恢复状态。
- 进度可由前端轮询；后续可补 SSE 推送。
- 评测线程池不得与真实用户聊天线程池竞争核心资源。

## 20. 配置建议

```yaml
app:
  eval:
    enabled: false
    workbench-enabled: false
    max-active-runs: 1
    record-concurrency: 1
    sample-timeout-seconds: 120
    sample-retry-times: 1
    ragas:
      enabled: false
      endpoint: ${RAGAS_ENDPOINT:}
      concurrency: 2
      timeout-seconds: 180
      max-samples-per-run: 500
      max-independent-runs: 3
```

说明：

- `ragent.eval.enabled` 继续控制评测旁路。
- `workbench-enabled` 控制工作台 API 与任务执行。
- 生产环境默认关闭；确需生产评测时必须限制角色、并发和成本。
- Judge 密钥使用现有模型配置或 Secret，不允许进入数据库明文字段。

## 21. 安全、隐私与成本

1. 仅管理员或授权角色可创建和执行评测。
2. 评估集和运行记录可能包含内部知识，不应发送给未批准的外部 Judge。
3. 创建 Run 时明确展示 Judge 供应商和数据外发提示。
4. Thinking 内容默认不展示给普通角色，并支持配置不落库。
5. 导出文件必须经过权限校验。
6. 运行取消、数据集发布和 Judge 配置变更进入业务审计。
7. 限制单 Run 样本数、并发、独立采样次数和每日预算。
8. RAGAS 记录 Token 用量与估算费用；超预算时停止调度并保留已完成结果。
9. 禁止在日志中打印完整标准答案、召回上下文和模型密钥。

## 22. 非功能需求

### 22.1 可用性

- 录制或 RAGAS 单样本失败不影响其他样本。
- 任务失败后保留全部已完成数据。
- 应用重启后，运行中任务不能永久停留在 `RUNNING`。

### 22.2 性能

- 数据集和样本列表必须分页。
- 大型上下文不得在运行列表接口返回。
- 运行进度查询不执行实时全表聚合。
- RAGAS 请求支持批处理，但单批大小必须可配置。

### 22.3 可复现性

- 数据集版本、运行配置、评分算法、阈值和 Judge 配置必须版本化或快照化。
- 每个评分批次保存算法版本。
- 报告展示应用版本或 Git Commit（可获取时）。

### 22.4 可观测性

至少记录：

- Run 各阶段耗时。
- 样本成功率和失败类型。
- 聊天录制调用量与耗时。
- RAGAS 调用量、失败率、Token 与成本。
- 任务队列长度和活动 Run 数。

## 23. 验收标准

### 23.1 MVP 验收

1. 管理员可创建评估集，导入至少 150 条 JSONL 样本，并获得逐行校验结果。
2. 已发布数据集版本不可直接修改，修改时生成新草稿版本。
3. 管理员可使用已发布版本创建 Run，接口立即返回 Run ID。
4. 页面可查看 Run 阶段、总数、成功数、失败数和进度。
5. 单样本失败后其余样本继续执行，Run 最终为 `PARTIAL_SUCCESS`。
6. 系统正确保存回答、预测意图、召回文档、TTFT 和关联 ID。
7. 自建指标与教程定义的离线计算结果在同一输入上保持一致。
8. 运行详情可查看 Overall、Intent L2 切片和 Per Sample 指标。
9. 样本详情可跳转到真实聊天 Trace。
10. Run 可取消，并停止调度新的样本。
11. 运行记录与指标可导出。
12. 普通用户无法访问评测管理 API。

### 23.2 V1 验收

1. 可异步执行 RAGAS 五项指标，评分服务失败不影响自建报告。
2. RAGAS NaN 或失败样本可单独重试，并展示最终状态。
3. 用户可选择两次同数据集版本的 Run 查看指标差异和样本回归。
4. 阈值规则能给出 Run 的通过/失败结论，并列出触发规则。
5. 页面明确区分 ID-based Recall@K 与 RAGAS Context Recall。

## 24. 测试要求

### 24.1 后端单元测试

- 数据集导入校验。
- 数据集版本状态机。
- Run 状态机和取消。
- 每个自建指标的正常、边界和空样本。
- 多失败原因合并。
- 配置快照和阈值快照。

### 24.2 后端集成测试

- 创建 Run 到报告生成的最小闭环。
- SSE 首字采集。
- `/rag/eval` 结果合并。
- 单样本超时与重试。
- RAGAS 服务不可用时的降级。
- Trace 关联。
- 应用重启后的任务恢复。

### 24.3 前端测试

- JSONL 导入错误展示。
- Run 创建表单校验。
- 运行进度和状态展示。
- 指标切片与筛选。
- A/B 差值计算和不同数据集版本警告。
- Trace 跳转与无 Trace 状态。

## 25. 里程碑建议

### M1：评估集资产化

- 数据库表与后端 CRUD。
- 导入、校验、版本发布。
- 数据集与样本前端页面。

### M2：录制与自建指标

- Run 状态机、异步 Runner、取消与恢复。
- 聊天、评测旁路和 Trace 关联。
- 自建指标与运行详情。

### M3：RAGAS 与失败诊断

- Python RAGAS 评分服务适配。
- 语义指标、失败归因。

### M4：对比与质量门禁

- A/B 对比。
- 阈值规则。
- 导出、CI 查询接口和成本治理。
- 人工评分覆盖已取消，不再实现。

## 26. 风险与待确认事项

### 26.1 必须在开发前确认

1. **RAGAS 部署方式**：复用并服务化 `ragenteval`，还是新建独立评分服务。
2. **真实证据采集方案**：首期采用双接口，还是直接改造真实聊天 Trace 捕获上下文。
3. **文档业务码规则**：当前 `/rag/eval` 通过移除 `doc_name` 扩展名生成业务码，长期是否改为知识文档独立字段。
4. **Judge 数据边界**：哪些知识库允许将问题、回答和上下文发送给外部模型。
5. **Thinking 存储策略**：是否落库、保存期限以及可见角色。
6. **运行环境**：评测只允许专用环境，还是允许生产环境低并发运行。
7. **知识库版本**：如何为文档与向量索引生成可复现的版本或摘要。

### 26.2 已知风险

| 风险 | 影响 | 缓解方式 |
| --- | --- | --- |
| 双接口检索不一致 | 指标证据与真实回答上下文偏离 | 通过 Trace 捕获真实证据；报告标记证据来源 |
| Judge 方差 | 小幅指标变化不可信 | 独立多次评分取均值；保存采样次数 |
| Judge 误判 | 低分样本可能是评分错误 | 失败样本下钻、多次采样与人工抽查 |
| RAGAS 成本失控 | 大数据集或多次采样费用高 | 配额、预算、抽样和成本预估 |
| 评测抢占聊天资源 | 影响线上用户 | 独立线程池、低并发、专用环境 |
| 评估集过拟合 | 指标提升但真实效果无提升 | 保留隐藏集，定期扩充冷门和失败样本 |
| 数据集版本变化 | A/B 对比失真 | 相同版本才给严格回归结论 |
| Trace 生命周期较短 | 历史 Run 无法下钻 | 关键评测证据独立持久化 |

## 27. 与现有实现的关系

- 保留 `EvalController` 作为 MVP 检索旁路，但应将“评测工作台 API”和“单问题旁路 API”分开。
- 复用 `EvalResponse` 中的召回文档、Chunk、上下文、意图和分流字段。
- `EvalResponse.latencyMs` 继续表示旁路检索链路耗时，不替代 TTFT。
- 复用现有 Trace 页面，不在首期重复开发 Trace 树。
- 复用现有 Admin 布局、权限判断、分页与统一响应体。
- 复用教程及 `ragenteval` 的指标口径，避免前后两套定义。
- `EvalProperties` 当前注释中提到 `/rag/eval/sync` 和 `EvalRetrievalCaptureAspect`，但当前代码仅有 `/rag/eval`，实现前应同步清理或补齐该注释，避免误导。

## 28. 最终交付物

完成 V1 后，应包含：

1. 评估集、版本、样本、Run、录制与评分的数据表及迁移脚本。
2. 后端数据集、Run、评分、报告与对比 API。
3. 异步录制 Runner 与任务恢复机制。
4. 自建指标实现。
5. RAGAS 评分适配器及部署说明。
6. Admin 评测菜单和完整页面。
7. Trace 关联。
8. 指标口径、配置、使用和故障排查文档。
9. 后端、前端和集成测试。

