# 阶段 5 退出报告

- 日期：2026-07-31
- 范围：M3 ragenteval 服务化 + Java RAGAS 接入（可降级）
- 约束：未改 Chat Pipeline / EvalController / Trace 写路径；RAGAS 失败不回滚 Record / 自建分数

## 退出条件对照

| 退出条件 | 状态 | 证据 |
|----------|------|------|
| ragenteval HTTP 服务契约端点 | 通过 | `D:\code\ragenteval` 分支 `feat/ragas-http-service`：`/health`、`POST/GET/cancel /v1/evaluations/score*` |
| 纯服务层与 CLI 解耦 | 部分 | `eval/service/scoring.py` 复用 `ragas_judge.compute`；CLI 仍走原 pipeline |
| Java 异步提交 + 轮询写独立 score_batch | 通过 | `SemanticEvaluationProvider` + `EvalScoreService#scoreRagas` |
| 服务不可用 / NaN / 失败可降级 | 通过 | batch `FAILED`/`PARTIAL_SUCCESS`；Worker catch 后继续 REPORTING |
| Judge 配置快照 / externalJobId | 通过 | `t_eval_score_batch.judge_config_snapshot` / `external_job_id` |
| 前端 RAGAS 区与口径提示 | 通过 | Run 详情「RAGAS LLM-as-judge」表 + Context Recall 不可替换提示 |
| 同一 Record 可多 RAGAS 批次 | 通过 | `POST .../ragas-rescore` 每次新建 batch |
| 采样 n=1..3 | 通过 | `app.eval.ragas.max-independent-runs` → `ragas_n` |
| 自动化测试 | 部分 | Python：`tests/test_score_api.py`（skip_ragas / idempotency）；Java 暂无 RAGAS 集成测 |

## ragenteval（外部仓库）

- **分支**：`feat/ragas-http-service`（相对 `main`）
- **启动**：见 `eval/service/README.md`
- **Dry-run**：`skip_ragas=true` 无需 API Key
- **真评测**：`pip install -e ".[ragas]"` + `AIHUBMIX_API_KEY`

## Java API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/runs/{runId}/ragas-rescore` | **异步**提交 RAGAS 批次：立即返回 `batchId`，后台轮询外部 job；已有 PENDING/RUNNING 则复用 |
| POST | `/runs/{runId}/ragas-batches/{batchId}/cancel` | 取消进行中的 RAGAS 批次（外部 job + batch FAILED） |
| GET | `/runs/{runId}/metrics?scoreType=RAGAS` | RAGAS 报告 |
| GET | `/runs/{runId}/score-batches` | 含 DETERMINISTIC / RAGAS；含 progress / tokenUsage / estimatedCost |

配置：

```yaml
app.eval.ragas:
  enabled: true
  endpoint: http://127.0.0.1:8089
  service-token: ${RAGAS_SERVICE_TOKEN:}
  max-independent-runs: 1
```

Run 创建需 `ragasEnabled=true`，且全局 `app.eval.ragas.enabled=true`。

## 前端

- 创建 Run 可选「启用 RAGAS」
- 详情：自建表 + RAGAS 表；Intent L2 切片合并两路；「RAGAS 评分」弹窗选模型；进行中可取消；展示 token/cost（有回传时）

## 已知缺口

1. `records_uri` / `callback_url`：**延期**（非 MVP；契约保留字段，当前仅内联 `records`）。
2. token/cost：库表与 VO/FE 已透传；Judge 侧数值仍可能为占位 0/null。
3. 三次采样的「每轮原始分」未单独落库（仅聚合均值）。
4. 无真实 Judge 的端到端 CI（仅 skip_ragas 契约测）。

## 如何测试

### 0. 前置

| 组件 | 要求 |
|------|------|
| ragent | `app.eval.workbench-enabled=true`；评测表已建；管理员登录 |
| ragenteval | 分支 `feat/ragas-http-service`；本机可起 HTTP 服务 |
| 评估集 | 至少一个 `PUBLISHED` 版本（建议先用 20 条 smoke 集） |

路径约定：

- ragent：`D:\code\ragent`
- ragenteval：`D:\code\ragenteval`

### 1. 自动化（不依赖 Judge）

```bash
cd D:\code\ragenteval
.\.venv\Scripts\activate   # 或先 python -m venv .venv && pip install -e ".[dev]"
pytest tests/test_score_api.py -q
```

预期：`3 passed`（health / skip_ragas 异步成功 / 幂等键复用同一 job_id）。

### 2. 评分服务单测（curl）

启动：

```bash
cd D:\code\ragenteval
.\.venv\Scripts\activate
pip install -e ".[dev]"
uvicorn eval.service.app:app --host 0.0.0.0 --port 8089
```

健康检查：

```bash
curl http://127.0.0.1:8089/health
```

预期 JSON 含 `"status":"ok"`、`ragas_available`、`judge_configured`。

干跑提交（无需 API Key）：

```bash
curl -X POST http://127.0.0.1:8089/v1/evaluations/score ^
  -H "Content-Type: application/json" ^
  -d "{\"mode\":\"async\",\"skip_ragas\":true,\"records\":[{\"query_id\":\"q1\",\"user_input\":\"hi\",\"reference\":\"x\",\"reference_doc_ids\":[],\"reference_doc_ids_nice\":[],\"intent_l1\":\"\",\"intent_l2\":\"\",\"difficulty\":\"easy\",\"requires_rag\":false,\"response\":\"y\",\"thinking\":null,\"latency_ms\":1,\"first_token_ms\":1,\"final_status\":\"success\",\"error\":null,\"conversation_id\":null,\"task_id\":null,\"retrieved_doc_ids\":[],\"retrieved_doc_ids_raw\":[],\"retrieved_chunk_ids\":[],\"retrieved_contexts\":[],\"retrieved_context_doc_ids\":[],\"intent_pred\":null,\"intent_pred_all\":[],\"has_kb\":false,\"has_mcp\":false,\"trace_id\":null,\"retrieval_skipped\":true,\"skip_reason\":null}]}"
```

response:
```json
{
  "schema_version": "1.0.0",
  "job_id": "job_25a2c679ee1249079edd8eb6fef84d75",
  "status": "SUCCEEDED",
  "created_at": "2026-07-31T02:34:03.340Z",
  "progress": {
    "total": 1,
    "completed": 1,
    "failed": 0,
    "skipped": 1
  },
  "token_usage": {
    "prompt_tokens": 0,
    "completion_tokens": 0,
    "total_tokens": 0
  },
  "estimated_cost_usd": null,
  "metrics": [],
  "sample_errors": [],
  "started_at": "2026-07-31T02:34:03.344Z",
  "finished_at": "2026-07-31T02:34:03.344Z",
  "error_message": null
}
```
记下返回的 `job_id`，再查：

```bash
curl http://127.0.0.1:8089/v1/evaluations/score/<job_id>
```

预期终态 `SUCCEEDED`，`metrics` 为空数组。

### 3. 工作台联调：降级路径（推荐先跑）

目的：验证 **RAGAS 挂了也不影响自建报告**。

1. **不要启动** ragenteval，或故意把 endpoint 指错。
2. ragent `application.yaml`（或本地覆盖）：

```yaml
app:
  eval:
    workbench-enabled: true
    ragas:
      enabled: true
      endpoint: http://127.0.0.1:8089   # 服务未起亦可
```

3. 管理台「评测运行」→ 创建 Run → **勾选「启用 RAGAS」** → 选已发布版本 → 开始。
4. 观察详情页：
   - 录制正常结束；自建指标表有数。
   - 过程中可能短暂出现 `RAGAS_SCORING`。
   - RAGAS 表五项为 `-`；或 `score-batches` 中有 `scoreType=RAGAS` 且 `status=FAILED`。
5. API 核对（替换 token / runId）：

```bash
curl -H "Authorization: <admin-token>" ^
  "http://localhost:9090/api/ragent/admin/evaluations/runs/<runId>/score-batches"

curl -H "Authorization: <admin-token>" ^
  "http://localhost:9090/api/ragent/admin/evaluations/runs/<runId>/metrics?scoreType=DETERMINISTIC"

curl -H "Authorization: <admin-token>" ^
  "http://localhost:9090/api/ragent/admin/evaluations/runs/<runId>/metrics?scoreType=RAGAS"
```

预期：自建 metrics 有值；RAGAS 批次失败或无可用 overall；**`t_eval_record` 未被删除/回滚**。

### 4. 工作台联调：服务可达 + 干跑式连通（可选）

当前 Java 提交固定 `skip_ragas=false`。若未配置 Judge Key，Python 会在 submit 时返回 **503 JUDGE_NOT_CONFIGURED**，Java 将 RAGAS batch 标失败——与场景 3 同类，用于验证「健康检查通过但仍因 Judge 缺失降级」。

1. 启动评分服务（§2）。
2. 确认 `GET /health` 的 `judge_configured`：
   - `false`：不配 Key，走失败降级。
   - `true`：已配 Key，可做场景 5。
3. ragent `ragas.enabled=true` + 正确 `endpoint`。
4. 对已终态 Run 点详情「RAGAS 评分」，或新建 Run 勾选 RAGAS。
5. 查 `score-batches`：出现新的 `RAGAS` 行；`externalJobId` 在服务可达且已 submit 成功时应有值。

### 5. 真实 RAGAS（需 Judge，样本宜少）

```bash
cd D:\code\ragenteval
.\.venv\Scripts\activate
pip install -e ".[ragas]"
set AIHUBMIX_API_KEY=<your-key>
# 可选：set JUDGE_MODEL=...  set EMBEDDING_MODEL=...
uvicorn eval.service.app:app --port 8089
```

ragent：

```yaml
app.eval.ragas.enabled: true
app.eval.ragas.endpoint: http://127.0.0.1:8089
app.eval.ragas.max-independent-runs: 1
```

建议：

1. 用 **少量** 已成功录制样本的 Run（含 `groundTruth` + 非空 `retrievedContexts`），否则 RAGAS 会大量 skip。
2. 创建时勾选 RAGAS，或终态后点「RAGAS 评分」。
3. 轮询详情 / `metrics?scoreType=RAGAS`，直到 batch `COMPLETED` 或 `PARTIAL_SUCCESS`。
4. 核对五项：Faithfulness / Answer Relevancy / Answer Correctness / Context Precision / Context Recall。
5. Intent L2 切片中 Faithfulness、Answer Correctness 应有值（有切片数据时）。
6. 再点一次「RAGAS 评分」→ `score-batches` 应 **多一条** RAGAS 批次（不覆盖旧批）。

### 6. 前端检查清单

| 步骤 | 预期 |
|------|------|
| 侧栏「RAG 评测」二级菜单 | 评估集 / 评测运行 |
| 创建 Run 勾选 RAGAS | 请求体带 `ragasEnabled: true` |
| 详情自建「指标明细」 | 与阶段 4 相同两列表 |
| 详情「RAGAS LLM-as-judge」 | 五指标；无批次时为 `-`，并有 Context Recall 口径提示；有回传时展示 Token/成本；进行中可取消 |
| 「重新评分」 | 只新增 DETERMINISTIC batch |
| 「RAGAS 评分」 | 立即返回；按钮禁用并显示「评分中」；下方进度条轮询 `score-batches`；终态 toast；进行中连点复用同一 batch |
| 导出 JSON/CSV | 仍为当前选中/默认自建报告口径 |

### 7. 回归关注点

1. **未勾选 RAGAS** 的 Run：不应进入长时间 `RAGAS_SCORING`，终态与阶段 4 一致。
2. **`app.eval.ragas.enabled=false`**：管理台异步路径抛错提示；Worker 同步路径跳过（日志可见 skip）。
3. 自建 `rescore` 与 RAGAS `ragas-rescore` 互不影响对方历史 batch。
4. 进行中 RAGAS：管理台「取消」→ `POST .../ragas-batches/{batchId}/cancel`；录制中的 Run 取消仍以协作式录制取消为准。

### 8. 建议验收顺序（最短路径）

1. `pytest tests/test_score_api.py`  
2. 场景 3（服务关闭 + 勾选 RAGAS）→ 自建有数、RAGAS 失败可降级  
3. 起服务 + 场景 4（连通 / Judge 缺失降级）  
4. 有 Key 时再跑场景 5（真实五指标）
