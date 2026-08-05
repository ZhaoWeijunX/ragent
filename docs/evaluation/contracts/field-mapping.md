# 字段映射：Java/API camelCase ↔ Python snake_case

`schemaVersion = 1.0.0`

工作台 API、数据库 JSON 列、前端统一使用 **camelCase**。  
`ragenteval` CLI / 评分服务内部继续使用 **snake_case**（对齐现有 `schemas.py`）。  
跨边界时必须经过显式转换，禁止裸 dict 穿透。

## EvalSample

| API / Java | Python | 备注 |
|------------|--------|------|
| `schemaVersion` | `schema_version` | 必填；固定 `1.0.0` |
| `queryId` | `query_id` | |
| `query` | `query` | |
| `intentL1` | `intent_l1` | |
| `intentL2` | `intent_l2` | |
| `difficulty` | `difficulty` | `easy\|medium\|hard` |
| `requiresRag` | `requires_rag` | |
| `expectedDocIds` | `expected_doc_ids` | must |
| `niceToHaveDocIds` | `expected_doc_ids_nice` | 名称不对等，转换层必须处理 |
| `groundTruth` | `ground_truth` | |
| `expectedAnswerType` | `expected_answer_type` | |
| `trapType` | `trap_type` | |
| `enabledMetrics` | `eval_metrics` | 名称不对等 |
| `tags` | — | Python 暂无；忽略或放入 metadata |
| `metadata` | — | Python 暂无 |

导入兼容：JSONL 若为 snake_case（`eval_set_v1.jsonl`），导入服务先映射为 camelCase 再校验 Schema。

## EvalRecord

| API / Java | Python | 备注 |
|------------|--------|------|
| `schemaVersion` | `schema_version` | |
| `queryId` | `query_id` | |
| `userInput` | `user_input` | |
| `reference` | `reference` | |
| `referenceDocIds` | `reference_doc_ids` | |
| `referenceDocIdsNice` | `reference_doc_ids_nice` | |
| `intentL1` / `intentL2` | `intent_l1` / `intent_l2` | |
| `difficulty` | `difficulty` | |
| `requiresRag` | `requires_rag` | |
| `response` | `response` | |
| `thinking` | `thinking` | MVP 持久化默认 `null` |
| `latencyMs` | `latency_ms` | Chat 总耗时 |
| `firstTokenMs` | `first_token_ms` | TTFT |
| `finalStatus` | `final_status` | |
| `error` | `error` | |
| `conversationId` | `conversation_id` | |
| `taskId` | `task_id` | |
| `traceId` | `trace_id` | |
| `retrievedDocIds` | `retrieved_doc_ids` | |
| `retrievedDocIdsRaw` | `retrieved_doc_ids_raw` | |
| `retrievedChunkIds` | `retrieved_chunk_ids` | |
| `retrievedContexts` | `retrieved_contexts` | |
| `retrievedContextDocIds` | `retrieved_context_doc_ids` | |
| `intentPred` | `intent_pred` | |
| `intentPredAll` | `intent_pred_all` | |
| `hasKb` / `hasMcp` | `has_kb` / `has_mcp` | |
| `retrievalSkipped` | `retrieval_skipped` | |
| `skipReason` | `skip_reason` | |
| `evalLatencyMs` | `eval_latency_ms` | 旁路耗时 ≠ TTFT |
| `evidenceSource` | `evidence_source` | 工作台新增；Python 可透传 |
| `chatStartedAt` 等 | `chat_started_at` 等 | ISO-8601 |

与现有 `EvalResponse`（Java）的映射：

| EvalResponse | EvalRecord |
|--------------|------------|
| `retrievedDocIds` | `retrievedDocIds` |
| `retrievedChunkIds` | `retrievedChunkIds` |
| `retrievedContexts` | `retrievedContexts` |
| `retrievedContextDocIds` | `retrievedContextDocIds` |
| `intentLeafIds` | `intentPredAll`；`intentPred = first` |
| `hasKb` / `hasMcp` | 同名 |
| `retrievalSkipped` / `skipReason` | 同名 |
| `latencyMs` | `evalLatencyMs`（**不是** Chat TTFT） |

## MetricResult

| API / Java | Python | 备注 |
|------------|--------|------|
| `schemaVersion` | `schema_version` | |
| `name` | `name` | |
| `algorithmVersion` | `algorithm_version` | 工作台必填；Python 服务须回填 |
| `overall` | `overall` | |
| `byIntentL1` | `by_intent_l1` | |
| `byIntentL2` | `by_intent_l2` | |
| `byDifficulty` | `by_difficulty` | Python 现有实现可缺省，服务化后补齐 |
| `perSample` | `per_sample` | key = queryId |
| `meta` | `meta` | 内部 key 也建议 snake↔camel 转换 |
| `isPct` | `is_pct` | |

## 转换规则

1. 边界层（Java Adapter / Python FastAPI）是唯一允许改命名的地方。
2. 未知字段：写入侧拒绝（`additionalProperties: false`）；读取历史数据时允许忽略未知字段。
3. `niceToHaveDocIds` ↔ `expected_doc_ids_nice`、`enabledMetrics` ↔ `eval_metrics` 属于特例映射，转换层必须单测覆盖。
