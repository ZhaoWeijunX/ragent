# RAGAS 评分服务 HTTP 契约（ragenteval 服务化）

- 版本：`v1`
- 状态：Draft（阶段 0 冻结接口形状；阶段 5 实现）
- 仓库：`D:\code\ragenteval`（独立进程，不嵌入 Java）
- 数据：请求/响应中的业务对象使用 **snake_case**，对齐现有 Python dataclass；Java 侧 Adapter 按 [field-mapping.md](./field-mapping.md) 转换

## 1. 设计原则

1. Java 负责录制与确定性指标；本服务主责 **RAGAS 五指标**（可选附带透传确定性结果校验，非必须）。
2. RAGAS 失败不得要求 Java 回滚 Record。
3. 支持异步任务：提交 → 查询 → 取消。
4. Judge 密钥只读部署环境变量，不接受请求体传明文 key。
5. 幂等：同一 `idempotency_key` 重复提交返回同一 `job_id`。

## 2. 端点一览

| Method | Path | 说明 |
|--------|------|------|
| `GET` | `/health` | 存活与依赖探测 |
| `POST` | `/v1/evaluations/score` | 提交评分任务 |
| `GET` | `/v1/evaluations/score/{job_id}` | 查询任务状态与结果 |
| `POST` | `/v1/evaluations/score/{job_id}/cancel` | 协作式取消 |

Base URL 示例：`http://ragenteval:8089`

## 3. `GET /health`

### 200 响应

```json
{
  "status": "ok",
  "schema_version": "1.0.0",
  "ragas_available": true,
  "judge_configured": true,
  "detail": {
    "chat_model": "gpt-4o-mini",
    "embedding_model": "text-embedding-3-small"
  }
}
```

`judge_configured=false` 时 Java 应跳过 RAGAS 并标记 batch 失败/跳过，不影响确定性评分。

## 4. `POST /v1/evaluations/score`

### 请求头

| Header | 必填 | 说明 |
|--------|------|------|
| `Content-Type` | 是 | `application/json` |
| `Idempotency-Key` | 推荐 | 与 body.`idempotency_key` 二选一，优先 Header |

### 请求体

```json
{
  "schema_version": "1.0.0",
  "idempotency_key": "run-42-batch-7",
  "mode": "async",
  "skip_ragas": false,
  "ragas_n": 1,
  "ragas_limit": null,
  "metrics": [
    "faithfulness",
    "answer_relevancy",
    "answer_correctness",
    "context_precision",
    "context_recall"
  ],
  "algorithm_version": "ragas-1.0.0",
  "judge": {
    "chat_model": null,
    "embedding_model": null
  },
  "records": [],
  "records_uri": null,
  "callback_url": null
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `mode` | `async` \| `sync` | MVP 服务实现以 `async` 为主；`sync` 仅限小批量（≤20）且超时内可完成 |
| `skip_ragas` | bool | true 时立即返回空 metrics（用于连通性测试） |
| `ragas_n` | 1..3 | 采样次数；>1 时对每条样本多次取均值 |
| `ragas_limit` | int\|null | 最多评多少条；null=全部可评样本 |
| `records` | EvalRecord[] | snake_case；与 `records_uri` 互斥，至少提供一个 |
| `records_uri` | string\|null | 对象存储或内网 URL，指向 JSON/JSONL |
| `callback_url` | string\|null | 可选；完成时 POST 结果摘要（阶段 5 可先不实现，保留字段） |

### 202 响应（async）

```json
{
  "schema_version": "1.0.0",
  "job_id": "job_01HZX...",
  "status": "PENDING",
  "created_at": "2026-07-30T02:10:00.000Z"
}
```

### 200 响应（sync 完成）

同「查询接口的 SUCCEEDED 载荷」。

### 错误

| HTTP | code | 场景 |
|------|------|------|
| 400 | `INVALID_REQUEST` | Schema 不符、records 为空 |
| 409 | `IDEMPOTENCY_CONFLICT` | 同 key 不同 body |
| 503 | `JUDGE_NOT_CONFIGURED` | 未配置密钥且未 skip |
| 413 | `PAYLOAD_TOO_LARGE` | 超批量上限 |

## 5. `GET /v1/evaluations/score/{job_id}`

### 任务状态

`PENDING` → `RUNNING` → `SUCCEEDED` | `FAILED` | `CANCELLED` | `PARTIAL_SUCCESS`

### SUCCEEDED / PARTIAL_SUCCESS 示例

```json
{
  "schema_version": "1.0.0",
  "job_id": "job_01HZX...",
  "status": "PARTIAL_SUCCESS",
  "progress": {
    "total": 20,
    "completed": 18,
    "failed": 2,
    "skipped": 0
  },
  "token_usage": {
    "prompt_tokens": 120000,
    "completion_tokens": 8000,
    "total_tokens": 128000
  },
  "estimated_cost_usd": 0.42,
  "metrics": [
    {
      "schema_version": "1.0.0",
      "name": "faithfulness",
      "algorithm_version": "ragas-1.0.0",
      "overall": 0.81,
      "by_intent_l1": {},
      "by_intent_l2": {},
      "by_difficulty": {},
      "per_sample": {
        "F1-01": 0.9,
        "F2-01": null
      },
      "meta": {
        "nan_count": 1,
        "evaluable_count": 18,
        "ragas_n": 1
      },
      "is_pct": true
    }
  ],
  "sample_errors": [
    {
      "query_id": "F2-01",
      "error_code": "RAGAS_NAN",
      "message": "judge returned NaN after retries"
    }
  ],
  "started_at": "2026-07-30T02:10:01.000Z",
  "finished_at": "2026-07-30T02:18:00.000Z",
  "error_message": null
}
```

约定：

- `per_sample` 中 `null` 表示该样本该指标不可用（空上下文、NaN、跳过）。
- `PARTIAL_SUCCESS`：部分样本失败但至少有一个指标 overall 可算。
- `FAILED`：任务级失败，`metrics` 可为空。

## 6. `POST /v1/evaluations/score/{job_id}/cancel`

协作式取消：停止调度新样本；已完成分片结果尽量保留并返回 `CANCELLED` 或 `PARTIAL_SUCCESS`。

```json
{ "schema_version": "1.0.0", "job_id": "job_01HZX...", "status": "CANCELLED" }
```

## 7. 限流与容量（默认建议）

| 项 | 默认 |
|----|------|
| 单请求 records 上限 | 200（更大用 `records_uri`） |
| 全局并发 job | 2 |
| 单 job 内 RAGAS 并发 | 2 |
| 同步模式超时 | 120s |
| 异步 job TTL | 24h 后可清理结果 |

## 8. 安全

- 内网部署；Java → Python 可加共享 token（`Authorization: Bearer <service-token>`）。
- 请求体字段白名单：默认允许 `user_input/response/retrieved_contexts/reference/query_id/...`；禁止附带 Cookie、Authorization、原始 Thinking（除非显式开启）。
- 日志对 `retrieved_contexts` / `response` 做截断。

## 9. Java 适配预期

`SemanticEvaluationProvider`（阶段 5）：

1. 将 camelCase Record 转为 snake_case。
2. `POST /v1/evaluations/score`，保存 `job_id` 到 score_batch。
3. 轮询至终态（间隔 2s，退避至 10s）。
4. 结果转 camelCase 写入 `t_eval_score`。
5. 超时/5xx/NaN → batch `FAILED` 或 `PARTIAL_SUCCESS`，**不**修改 `t_eval_record`。

## 10. 阶段 0 非目标

- 本阶段 **不实现** FastAPI 服务代码（属阶段 5）。
- 不修改 `ragenteval` 仓库文件（保持参考实现稳定）；阶段 5 再开分支服务化。
- 契约样例见 `docs/evaluation/examples/*python.example.json`。
