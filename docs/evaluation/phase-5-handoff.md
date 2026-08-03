# 阶段 5 交接（压缩上下文）

- 日期：2026-07-31
- 范围：M3 RAGAS 服务化 + Java/FE 接入收尾
- 详细退出对照：[phase-5-exit-report.md](./phase-5-exit-report.md)
- 方案节点：[rag-evaluation-workbench-development-plan.md](../rag-evaluation-workbench-development-plan.md) §阶段 5

## 1. 结论（一句话）

**阶段 5 / M3 主路径基本对齐**：自建 + 可选 RAGAS、失败不回滚、独立 batch、异步进度/取消、Judge 端点由 Java 解析下发。完整 V1（A/B、门禁、真实成本计量）属阶段 6+；人工覆盖已取消。

## 2. 仓库与分支

| 仓库 | 路径 | 分支 | 远程状态 |
|------|------|------|----------|
| ragent | `D:\code\ragent` | `feat/eval_workbench_v1.0` | 已推 `origin`；近期 commit `【PHASE_5】接入 RAGAS 评分服务` |
| ragenteval | `D:\code\ragenteval` | `feat/ragas-http-service` | commit `8c7a221` 已推 **github**；**gitcode origin 403 无权限** |

硬约束（全程勿破）：不改 Chat Pipeline / EvalController / Trace 写路径；RAGAS 失败不回滚 Record / 自建分数。

## 3. 关键能力地图

```
创建 Run (ragasEnabled?)
  → 录制 → 自建评分 → [可选] Worker.scoreRagas(request=null)
       → resolveJudgeModels(默认 yaml) → HTTP submit → poll work_* → persist batch

详情「RAGAS 评分」弹窗
  → submitRagasAsync(chatModelId, embeddingModelId) → 同上（可选模型）
  → 进行中：进度条 + 取消 → POST .../ragas-batches/{batchId}/cancel
```

| 路径 | Judge 模型 | 可选？ |
|------|------------|--------|
| 创建 Run 自动 RAGAS | `app.eval.ragas.judge-chat.default-model` + `ai.embedding.default-model` | 否（改 yaml） |
| 详情弹窗重评 | 弹窗选 chat/embedding | 是 |

当前 Judge 候选：`deepseek-v4-pro`（deepseek）、`gpt-5.4-mini`（aihubmix）；默认 chat=`gpt-5.4-mini`。

## 4. 关键文件

**Java（ragent）**

- `EvalScoreServiceImpl`：自建/RAGAS、默认 Judge、cancel、chunk/context ids 送评
- `RagasHttpSemanticEvaluationProvider`：submit/poll/cancel
- `EvalRunWorker`：录制后可选 `scoreRagas`
- `EvalRunController`：`ragas-rescore` / `ragas-batches/{id}/cancel` / `ragas-judge-models`
- `application.yaml` → `app.eval.ragas.*` + `judge-chat`

**FE**

- `EvalRunDetailPage.tsx`：自建/RAGAS 分区、进度、取消、弹窗选模型；**不展示占位 Token**
- `evaluationService.ts`：对应 API

**Python（ragenteval）**

- `eval/service/*`：FastAPI score/poll/cancel、`work_*` 进度
- `eval/rag/metrics/ragas_judge.py`：Java 下发 endpoint/key；DeepSeek 关 thinking
- 契约镜像：`docs/evaluation/ragas-scoring-service-contract.md`（ragent 侧）

## 5. 本轮相对退出报告的补强

1. **成本字段**：库表/`EvalScoreBatchVO` 已透传；Python 仍写死 `TokenUsage()`/`cost=null` → FE **先不展示 Token**
2. **跳过语义**：异步未启用抛错；去掉死代码 `!batchId` toast
3. **Worker 默认 Judge**：`request=null` 时用 yaml 默认解析 base_url+key（不再只靠 Python 环境）
4. **取消 UI**：管理台接 cancel API
5. **送评字段**：`retrieved_chunk_ids` / `retrieved_context_doc_ids` 真实落库字段
6. **文案**：方案/需求/阶段文档「确定性」→「自建」；`records_uri` 标明延期
7. **DeepSeek Judge**：flash → pro；Python 侧 `thinking: disabled`

## 6. 已知缺口（勿当回归）

| 项 | 状态 |
|----|------|
| `records_uri` / `callback_url` | 延期 |
| 真实 token/cost 计量 | Python 未接 usage；FE 隐藏 Token |
| 采样 n>1 每轮原始分 | 仅聚合均值 |
| 真实 Judge E2E CI | 仅 skip_ragas 契约测 |
| 创建 Run 选 Judge | 未做 |
| OutputParser / 402 | Judge/余额问题，非 Java bug |
| 阶段 6：同版本 Run A/B（本轮） | 进行中；门禁暂不实现；人工覆盖已取消 |

## 7. 本地联调备忘

1. 起 ragenteval（见 `eval/service/README.md`），默认 `:8089`
2. ragent：`app.eval.ragas.enabled=true`，`endpoint` 指向服务；`DEEPSEEK_API_KEY` / `AIHUBMIX_API_KEY` 按选用模型
3. Run 勾选「启用 RAGAS」或详情「RAGAS 评分」
4. 进度看 `work_completed/work_total`（评分项），勿用误导的「已评样本」中间态
5. 改 yaml / Python 后需重启对应进程

## 8. 下一任优先建议

1. **若收口阶段 5**：补 Python usage 聚合或继续隐藏成本；gitcode 推送权限；Java 集成测（可选）
2. **若进阶段 6**：本轮仅做同版本 Run A/B 对比；门禁 / CI 暂不实现；人工覆盖已取消
3. **运维**：确认生产默认关 RAGAS、限并发与角色

## 9. 术语

- **自建指标**：原「确定性指标」；DB `score_type=DETERMINISTIC` 不变
- **RAGAS**：LLM-as-judge 五指标；独立 `score_type=RAGAS` batch
