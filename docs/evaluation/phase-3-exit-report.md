# 阶段 3 退出报告

- 日期：2026-07-30
- 范围：M2-A Run 状态机 + 双路径录制
- 约束：未改 Chat Pipeline / EvalController / MetaPayload / Trace 写路径

## 退出条件对照

| 退出条件 | 状态 | 证据 |
|----------|------|------|
| Run 创建/查询/取消/失败重试 | 通过 | `EvalRunController` + `EvalRunServiceImpl` |
| 状态机 PENDING→RECORDING→…→终态 | 通过 | `EvalRunWorker#finalizeRun`；评分阶段见阶段 4 |
| 租约领取与过期恢复 | 通过 | `lease_owner` / `lease_expire_at` + `EvalRunLeaseReclaimer` |
| 双路径录制写入 `t_eval_record` | 通过 | `EvalDualPathSampleRecorder`：真实 Chat 管线 + 旁路证据 + taskId→traceId |
| Thinking 默认不落库 | 通过 | `app.eval.record-thinking=false` |
| 单样本失败 → PARTIAL_SUCCESS | 通过 | `EvalRunTerminalStatus` |
| 取消保留已录制数据 | 通过 | 协作式取消；已录制保留；未开跑样本写 `cancelled` Record |
| 前端 Run 列表/进度/漂移披露 | 通过 | `EvalRunListPage` / `EvalRunDetailPage` |
| Trace 可跳转 | 通过 | Record 行链到 `/admin/traces/{traceId}` |

## API

前缀 `/admin/evaluations`，需 `admin` + `app.eval.workbench-enabled=true`：

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/runs` | 分页 |
| POST | `/runs` | 创建并异步启动（仅 PUBLISHED 版本） |
| GET | `/runs/{runId}` | 详情与进度 |
| POST | `/runs/{runId}/cancel` | 协作式取消 |
| POST | `/runs/{runId}/resume` | 仅重跑失败样本 |
| GET | `/runs/{runId}/records` | 样本录制列表 |
| GET | `/records/{recordId}` | 样本详情 |

## 录制口径

1. **Chat**：进程内调用 `StreamChatPipeline`（与 `/rag/v3/chat` 同管线），`EvalChatCaptureCallback` 采集 response / TTFT / 终态；经正式 `StreamChatEventHandler` 落会话与 Trace。
2. **旁路**：`EvalBypassEvidenceCollector` 与 `GET /rag/eval` 同口径组装证据（不改 EvalController）。
3. **Trace**：Chat 完成后按 taskId 轮询 `RagTraceQueryService`（10×300ms）。
4. **evidenceSource**：`DUAL_PATH_CHAT_AND_EVAL`；UI 固定披露漂移风险。

## 配置

```yaml
app:
  eval:
    enabled: true                 # 旁路；工作台录制旁路证据依赖同口径服务
    workbench-enabled: true       # 本地可开；生产请关
    max-active-runs: 1
    record-concurrency: 1
    sample-timeout-seconds: 120
    sample-retry-times: 1
    record-thinking: false
    lease-heartbeat-seconds: 30
    lease-expire-seconds: 90
```

## 冒烟建议

1. 发布 20 条集（`eval_set_v1.jsonl`）后，管理台「评测运行」创建 Run。
2. 详情页轮询进度；确认 success/failed 计数与 Record 列表。
3. 人为制造单样本超时/失败，终态应为 `PARTIAL_SUCCESS`。
4. 运行中点取消：已写入 Record 保留，状态 `CANCELLED`。
5. 对 FAILED/PARTIAL_SUCCESS 点「重试失败样本」，成功样本应跳过。
6. 重启应用后，租约过期的非终态 Run 应由 `EvalRunLeaseReclaimer` 重新领取。

## 运行实例（本地冒烟步骤与预期）

前置：`app.eval.enabled=true`、`app.eval.workbench-enabled=true`；ragent 已启动；管理员已登录；评估集已有 **PUBLISHED** 版本（建议先导入 `D:\code\ragenteval\eval\rag\dataset\eval_set_v1.jsonl` 的 20 条并发布）。

### 场景 A：创建并跑完 20 条 Run

| 步骤 | 操作 | 预期结果 |
|------|------|----------|
| A1 | 打开 `/admin/evaluations/runs` | 页顶有双路径漂移黄条；表格表头与数据列对齐；无数据时提示「暂无 Run」 |
| A2 | 点「创建 Run」→ 选评估集与已发布版本 → 填写名称 →「开始」 | 立即返回详情 `/admin/evaluations/runs/{runId}`；状态很快变为 `RECORDING` |
| A3 | 停留在详情页（约每 3s 自动刷新） | `progress`、`successCount`/`failedCount` 递增；样本表逐行出现 Record |
| A4 | 全部样本录制结束 | 经过 `DETERMINISTIC_SCORING`（写入 score_batch）/ `REPORTING` 后进入终态：全部成功 → `COMPLETED`；有失败且 success≥1 → `PARTIAL_SUCCESS`；全失败 → `FAILED`；`progress=100`；详情可见确定性指标 |
| A5 | 点某条 Record 的 Trace 链接 | 跳转 `/admin/traces/{traceId}`（若 trace 尚未落库则为 `-`） |
| A6 | 回列表 | 对应 Run 状态/进度/成功失败列与详情一致；点击行进入详情 |

### 场景 B：协作式取消

| 步骤 | 操作 | 预期结果 |
|------|------|----------|
| B1 | 在 `RECORDING` 中点「取消」 | `cancelRequested=true`；不再调度新样本；已写入的 Record **仍在** |
| B2 | 等待当前样本结束后 | Run 终态为 `CANCELLED`；未开跑样本以 `cancelled` 出现在样本表；可用状态筛「cancelled」查看 |

### 场景 C：失败样本重试

| 步骤 | 操作 | 预期结果 |
|------|------|----------|
| C1 | 对 `FAILED` / `PARTIAL_SUCCESS` / `CANCELLED` 点「重试失败样本」 | Run 回到 `PENDING`→`RECORDING`；**已 success/refused 的样本跳过**；`error` / `cancelled` 样本会重跑 |
| C2 | 结束后 | 计数按 Record 重算；若失败清零则为 `COMPLETED`，否则仍可为 `PARTIAL_SUCCESS` |

### 场景 D：租约恢复（可选）

| 步骤 | 操作 | 预期结果 |
|------|------|----------|
| D1 | 录制中强制停进程，等待 `lease-expire-seconds`（默认 90s）后重启 | 非终态 Run 被 `EvalRunLeaseReclaimer` 重新领取，继续录制；不会永久卡在 `RECORDING` |

### API 快速核对（可选）

```bash
# 列表
curl -H "Authorization: <token>" "http://localhost:9090/api/ragent/admin/evaluations/runs?current=1&size=10"

# 创建（datasetVersionId 换成已发布版本 ID）
curl -X POST -H "Authorization: <token>" -H "Content-Type: application/json" \
  -d "{\"name\":\"smoke-20\",\"datasetVersionId\":\"<versionId>\",\"tags\":{\"environment\":\"eval-local\"}}" \
  "http://localhost:9090/api/ragent/admin/evaluations/runs"
```

预期：创建响应 `data` 为 runId；随后 GET `/runs/{runId}` 可见 `status`/`progress`/`dualPathDisclaimer`。

## 非目标（阶段 5+）

- RAGAS HTTP 接入
- A/B 对比与质量门禁
- 进度 SSE 推送（首期轮询）
- 确定性指标细节见 [phase-4-exit-report.md](./phase-4-exit-report.md)
