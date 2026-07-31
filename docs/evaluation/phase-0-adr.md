# ADR：RAG 评测工作台阶段 0 口径冻结

- 状态：Accepted
- 日期：2026-07-30
- 范围：阶段 0 规格冻结；阻塞阶段 1 建表前的默认决策
- 约束：**尽量小范围改动原始代码**——不改 Chat Pipeline / EvalController / MetaPayload / Trace 写路径；旁路与生产链路保持现状，仅新增工作台模块与外部评分服务

## 1. 背景

需求文档 §26.1 列出 7 项开发前确认项。结合已确认决策：

1. RAGAS：将 `D:\code\ragenteval` **服务化**，Java 经 HTTP 调用。
2. 证据采集：MVP **继续双路径**（`/rag/v3/chat` + `/rag/eval`），接受漂移风险，后期再改进。

本 ADR 冻结其余默认口径，使阶段 1 可直接建表与实现状态机。

## 2. 决策一览

| 主题 | 冻结结论 |
|------|----------|
| 数据集 vs 版本状态 | 版本持有 `DRAFT/PUBLISHED/ARCHIVED`；数据集 `status` 仅作展示汇总，不参与 Run 引用校验 |
| Run 终态 | `COMPLETED` / `PARTIAL_SUCCESS` / `FAILED` / `CANCELLED` 互斥终态 |
| `PARTIAL_SUCCESS` | 至少 1 条样本录制成功且已产出可用自建报告，同时存在样本失败 |
| Thinking | MVP **默认不落库**（Record.thinking 持久化为 null）；内存可暂存用于调试开关 |
| 文档业务码 | MVP 沿用 `doc_name` 去文件后缀；不新增知识库字段 |
| 重试 / 取消 / 恢复 | 协作式取消 + 租约恢复 + 失败样本幂等重跑 |
| Judge 密钥 | 仅环境 Secret；DB 只存配置引用与脱敏快照 |
| 评测环境 | 优先专用环境；共享环境须 feature flag + `max-active-runs=1` |
| 知识库版本 | MVP 存快照指纹/时间戳，标为 `unknown` 时不得宣称可精确复现 |
| 原始代码改动 | 阶段 0–4 **禁止**为评测修改 SSE meta、Chat 管线、EvalController 行为；仅允许配置项扩展与新增包 |

## 3. 数据集与版本状态

### 3.1 版本状态（权威）

| 状态 | 含义 | 可改 Case | 可被 Run 引用 |
|------|------|-----------|---------------|
| `DRAFT` | 草稿 | 是 | 否 |
| `PUBLISHED` | 已发布不可变快照 | 否 | 是 |
| `ARCHIVED` | 归档 | 否 | 否（已有 Run 保留历史引用） |

- 编辑已发布版本 → **复制为新 DRAFT**，不得原地修改。
- 已被 Run 引用的版本禁止物理删除（软删亦需校验引用）。

### 3.2 数据集级 `status`

需求表 `t_eval_dataset.status` 存在，但未定义枚举。冻结为：

- `ACTIVE`：默认可选
- `ARCHIVED`：列表默认隐藏

**不**用数据集状态替代版本状态；Run 创建只校验 `dataset_version.status == PUBLISHED`。

## 4. Run 状态机

### 4.1 阶段与状态分离

- `currentPhase`：执行阶段（进行中语义）
- `status`：含终态的总状态

```text
status=PENDING, phase=PENDING
  → status=RECORDING, phase=RECORDING
  → status=DETERMINISTIC_SCORING, phase=DETERMINISTIC_SCORING
  → [可选] status=RAGAS_SCORING, phase=RAGAS_SCORING
  → status=REPORTING, phase=REPORTING
  → 终态之一
```

### 4.2 互斥终态

| status | 条件 |
|--------|------|
| `COMPLETED` | 全部应执行样本录制成功，且自建报告完成；若开启 RAGAS 则 RAGAS 批次成功或显式跳过 |
| `PARTIAL_SUCCESS` | 存在样本录制/评分失败，但 `success_count >= 1` 且自建报告已生成 |
| `FAILED` | 任务级不可恢复错误，或 `success_count == 0` 且无法生成可用报告 |
| `CANCELLED` | 用户取消后不再调度新样本；已录制 Record **保留** |

说明：

- `COMPLETED` 与 `PARTIAL_SUCCESS` **互斥**，不是父子关系。
- RAGAS 失败 **不得**单独把已成功的自建结果打成 `FAILED`；若录制与自建均成功仅 RAGAS 失败 → `PARTIAL_SUCCESS`（或 `COMPLETED` + RAGAS batch 自身 FAILED，见下节）。

### 4.3 RAGAS 与 Run 状态（V1）

- MVP（阶段 4）可不进入 `RAGAS_SCORING`。
- V1：RAGAS 写独立 `t_eval_score_batch`；batch.status 可为 `FAILED`，Run 仍可为 `COMPLETED`（自建完整）或 `PARTIAL_SUCCESS`（样本有失败）。
- UI 需同时展示 Run.status 与最新 RAGAS batch.status。

## 5. 取消、重试、重启恢复

| 能力 | 语义 |
|------|------|
| 取消 | 协作式：设置取消标志，不调度新样本；不强制 kill 已发出的模型请求；已有 Record 保留 |
| 失败重试 | 仅重跑 `finalStatus=error`（或录制失败）的样本；成功样本跳过；`run_id+case_id` 唯一约束下 upsert |
| 重启恢复 | Runner 用 DB 租约（lease_owner / lease_expire_at）；进程崩溃后租约过期可被重新领取；禁止永久卡在非终态 |
| 配置不可变 | Run 一旦离开 `PENDING`，禁止修改数据集版本与 config_snapshot |

建议租约默认：30s 心跳，过期 90s（实现阶段可配置）。

## 6. Thinking 存储

| 项 | 冻结 |
|----|------|
| 默认 | **不落库**（`thinking = null`） |
| 原因 | 体积大、可能含中间推理与敏感信息；需求 §26.1.5 |
| 调试 | 配置 `app.eval.record-thinking=false`（默认）；若未来开启，须脱敏且仅 ADMIN 可见 |
| Spike / 内存 | Runner 内存可聚合 thinking 用于本地 spike，不写入 `t_eval_record` |

## 7. 文档业务码

| 项 | 冻结 |
|----|------|
| 生成规则 | 与现有 `EvalController` 一致：`doc_name` 去掉最后一个文件后缀 |
| 阶段 0–4 代码改动 | **不**为评测新增 `business_doc_id` 列或改解析逻辑 |
| 导入校验 | 检查业务码在当前环境可解析；不可解析 → **警告**，不阻断历史集导入（需求 §9.2.6） |
| Run 快照 | `config_snapshot` 保存导入时/运行时的 `doc_id_map` 摘要或哈希，便于事后对照 |

长期独立字段方案留待阶段 7 评估，不阻塞 MVP。

## 8. Run 标签与阈值快照

### 8.1 标签（存入 `config_snapshot.tags` 或独立 JSON 列，阶段 1 建表时二选一）

推荐阶段 1 在 `t_eval_run` 增加 `tags JSON`，避免塞进过大 snapshot：

```json
{
  "gitBranch": "feat/eval",
  "gitCommit": "abc1234",
  "environment": "eval-staging",
  "appVersion": "1.1.0-SNAPSHOT",
  "extra": {}
}
```

### 8.2 阈值快照骨架（`threshold_snapshot`）

```json
{
  "schemaVersion": "1.0.0",
  "policyId": null,
  "policyVersion": "draft",
  "rules": [
    {
      "metric": "hit@5",
      "dimension": "OVERALL",
      "op": "gte",
      "value": 0.9
    }
  ],
  "onViolate": "FAIL"
}
```

MVP 可存空 `rules: []`，quality verdict 为 `NOT_EVALUATED`。

### 8.3 Judge 配置引用

```json
{
  "judgeConfigId": "default-ragas",
  "providerRef": "env:AIHUBMIX",
  "chatModel": "gpt-4o-mini",
  "embeddingModel": "text-embedding-3-small",
  "apiKeyRef": "secret:AIHUBMIX_API_KEY"
}
```

密钥 **永不**写入 DB 明文；快照只保留引用名与模型名。

## 9. 双路径证据与原始代码边界

### 9.1 证据来源

- Record.`evidenceSource` = `DUAL_PATH_CHAT_AND_EVAL`
- UI / 导出必须披露：旁路检索证据可能与 Chat 实际上下文不一致
- **不**在阶段 0–4 修改 `MetaPayload` 增加 `traceId`；继续用 `taskId` 查询 Trace API

### 9.2 允许的改动（后续阶段）

- **新增** `rag/evaluation/**` 包、表、Admin 页面、配置项
- 扩展 `EvalProperties` 增加 workbench 开关（不改变现有 `enabled` 语义）
- 清理过时注释（可选、非功能）

### 9.3 禁止的改动（直至阶段 7 单轨演进立项）

- 改写 `StreamChatPipeline` / 检索路径以“顺便”采证据
- 改 `EvalController` 组装逻辑以“对齐”生产（除非修 bug 且单独评审）
- 改 SSE 事件协议或 `MetaPayload` 字段（避免影响前端与现网客户端）

## 10. 需求 §26.1 确认回写

| # | 事项 | 结论 |
|---|------|------|
| 1 | RAGAS 部署 | 服务化 `ragenteval`（HTTP） |
| 2 | 证据采集 | MVP 双路径；阶段 7 再评估 Trace 单轨 |
| 3 | 文档业务码 | MVP `doc_name` 去后缀；不改库表字段 |
| 4 | Judge 数据边界 | 默认仅评测环境；字段白名单；生产开启需显式配置 |
| 5 | Thinking | 默认不落库 |
| 6 | 运行环境 | 专用优先；共享须低并发 + flag |
| 7 | 知识库版本 | 快照指纹/时间；不可得则 `unknown` |

## 11. 后果

- 阶段 1 可按本 ADR 建表与状态枚举，无需再开会确认上述默认值。
- 双路径漂移作为已知限制写入验收与 UI，不作为 MVP blocker。
- 原始聊天/旁路代码保持稳定，降低回归风险。
