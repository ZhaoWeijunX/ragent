# 阶段 4 退出报告

- 日期：2026-07-30
- 范围：M2-B 自建指标 + 报告/导出 + MVP 闭环
- 约束：未改 Chat Pipeline / EvalController / MetaPayload / Trace 写路径；RAGAS 仍为后续阶段

## 退出条件对照

| 退出条件 | 状态 | 证据 |
|----------|------|------|
| Metric SPI + 统一 MetricResult | 通过 | `EvalMetric` / `MetricResult` / `DeterministicMetricEngine` |
| Intent Top-1；Hit/Recall@1/3/5/10；MRR@10 | 通过 | `IntentTop1Metric` / `RetrievalMetrics` |
| 误拒 / 过召回；TTFT P50/均值、总耗时均值 | 通过 | `BehaviorMetrics` / `LatencyMetrics`（不宣称 P95/P99） |
| 检索过滤：`requiresRag` + gold 非空 | 通过 | `MetricSupport.isRetrievalEligible` |
| 行为优先结构化字段 | 通过 | `hasKb` / `retrievalSkipped`，空召回回退 |
| 每次评分新建 `t_eval_score_batch`，不覆盖旧分 | 通过 | `EvalScoreServiceImpl#scoreDeterministic` |
| overall / intentL1/L2 / difficulty / SAMPLE | 通过 | `DIM_*` 写入 `t_eval_score` |
| 报告 + 失败多标签 + Trace 链接 | 通过 | `GET .../metrics` + Run 详情页 |
| JSON / JSONL / CSV 导出 | 通过 | `GET .../export` |
| 录制结束后自动自建评分 | 通过 | `EvalRunWorker#finalizeRun` |
| 可重复重评分 | 通过 | `POST .../rescore` |
| RAGAS 未部署时 MVP 可用 | 通过 | 不依赖 Python 评分服务 |
| 单测覆盖核心口径 | 通过 | `DeterministicMetricsTest` |

## API（新增 / 扩展）

前缀 `/admin/evaluations`，需 `admin` + `app.eval.workbench-enabled=true`：

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/runs/{runId}/rescore` | 新建自建评分批次 |
| GET | `/runs/{runId}/score-batches` | 批次列表 |
| GET | `/runs/{runId}/metrics` | 报告（overall + 切片 + 失败样本） |
| GET | `/runs/{runId}/export` | `format=json\|jsonl\|csv` |

## 指标口径摘要

1. **intent_top1**：`intentPred` 与 Case `intentL2` 精确匹配；空预测不计入分母。
2. **检索类**：仅 `requiresRag=true` 且 gold（must）非空；`recall_inclusive@K` 额外纳入 nice。
3. **refusal_when_required / over_retrieval_rate**：优先 `retrievalSkipped` / `hasKb`，否则以空/非空 `retrievedDocIds` 回退。
4. **延迟**：`ttft_p50_ms` / `ttft_mean_ms` / `total_mean_ms`；低样本量不输出 P95/P99。
5. **algorithmVersion**：`deterministic-1.0.0`。

## MVP §23.1 对照

| # | 要求 | 状态 |
|---|------|------|
| 1–6、9–10、12 | 评估集 / Run 录制 / Trace / 取消 / 权限 | 阶段 2–3 已满足 |
| 7 | 自建指标与离线口径一致 | 本阶段：Java 对齐 ragenteval 公式；单测覆盖主路径（未引入完整 `_scores.json` golden 夹具） |
| 8 | Overall / Intent L2 / Per Sample | 本阶段：报告 VO + 详情页卡片/切片表；SAMPLE 行落库 |
| 11 | 运行记录与指标可导出 | 本阶段：指标报告 JSON/JSONL/CSV；完整 Record 明细仍以样本列表 API 为主 |

## 前端

- Run 详情：自建指标卡片、全量指标表、Intent L2 切片、失败样本表、重新评分、导出 JSON/CSV。
- 终态 Run 才展示重评/导出；录制中继续轮询。

## 已知缺口（不阻塞 MVP 发布，可后续补强）

1. 与 ragenteval 固定 `_scores.json` 的逐项 golden fixture 尚未落地。
2. 导出以指标报告为主，未打包整 Run 全量 Record JSONL。
3. 难度切片已落库，UI 未单独展示 difficulty 表。
4. 质量门禁 verdict（PASS/FAIL）属阶段 6。

## 冒烟建议

1. 对已完成录制的 Run：详情应自动出现自建指标；或点「重新评分」生成新批次。
2. 核对 `intent_top1` / `hit@5` / `mrr@10` 与样本表意图、召回是否同向。
3. 导出 JSON，确认含 metrics + failures；CSV 含 metric 行。
4. 关闭 `workbench-enabled` 时评分 Bean 不注册、API 不可达。
