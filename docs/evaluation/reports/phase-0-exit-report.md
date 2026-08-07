# 阶段 0 退出报告

- 日期：2026-07-30
- 约束：尽量小范围改动原始代码（本阶段 **零** 业务代码改动）

## 退出条件对照

| 退出条件 | 状态 | 证据 |
|----------|------|------|
| 三份跨语言 Schema 固化 | 通过 | `docs/evaluation/schemas/*.schema.json` |
| camelCase ↔ snake_case 双向可转换 | 通过 | `scripts/evaluation/eval_phase0_validate_schemas.py` + examples |
| ADR 冻结状态机 / Thinking / 业务码 / 恢复等 | 通过 | `docs/evaluation/design/phase-0-adr.md` |
| RAGAS HTTP 服务契约设计完成 | 通过 | `docs/evaluation/contracts/ragas-scoring-service-contract.md` |
| SSE/TTFT spike 脚本就绪 | 通过 | `scripts/evaluation/eval_phase0_spike.py` |
| taskId→traceId 重试策略写入方案 | 通过 | Spike 默认 retries=10, interval=300ms；ADR §9 |
| 未决项不再阻塞建表 | 通过 | ADR §10 回写需求 §26.1 |
| 对运行中 ragent 的 20 条 dry-run | **待环境** | 需本地服务启动后执行下方命令 |

## 原始代码改动范围

| 类型 | 变更 |
|------|------|
| Java / 前端业务代码 | **无** |
| `EvalController` / `MetaPayload` / Chat Pipeline | **无** |
| 新增文档 | `docs/evaluation/**` |
| 新增脚本 | `scripts/evaluation/eval_phase0_*.py` + `scripts/evaluation/README.md` |
| 开发方案勾选 | `docs/evaluation/planning/development-plan.md` |

## 离线校验命令

```powershell
python scripts/evaluation/eval_phase0_validate_schemas.py
```

## 在线 Spike 命令（服务启动后）

```powershell
$env:RAGENT_BASE_URL = "http://localhost:9090/api/ragent"
$env:RAGENT_USERNAME = "admin"
$env:RAGENT_PASSWORD = "admin"
python scripts/evaluation/eval_phase0_spike.py --limit 1
# 冒烟通过后再跑 20 条：
python scripts/evaluation/eval_phase0_spike.py --limit 20
```

产物写入：`docs/evaluation/fixtures/spike-out/`。

## Spike 已固化的实现口径（供阶段 3 Runner 直接采用）

1. TTFT = 首个 `event=message` 且 `type=response` 且 `delta` 非空的耗时。
2. `meta` 仅含 `conversationId` + `taskId`（不扩展 MetaPayload）。
3. `traceId` = Chat 结束后轮询 `GET /rag/traces/runs?taskId=`，默认 10 次 × 300ms。
4. `/rag/eval` 的 `latencyMs` 映射为 Record.`evalLatencyMs`，不得当作 TTFT。
5. `thinking` 可在内存统计长度，持久化字段固定 `null`（ADR）。
6. `evidenceSource` 固定 `DUAL_PATH_CHAT_AND_EVAL`，UI 必须披露漂移风险。

## 阶段 1 可开始

在 ADR 与 Schema 约束下，可直接进入建表与 `rag/evaluation` 新包骨架；仍保持不改动现有聊天/旁路实现。
