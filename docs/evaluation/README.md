# RAG 评测工作台 — 阶段 0 交付物

本目录存放阶段 0（规格冻结与技术验证）产物。**不修改**现有 `/rag/v3/chat`、`/rag/eval`、Trace 业务实现；仅新增契约、ADR、示例与只读验证脚本。

| 文档 / 目录 | 说明 |
|-------------|------|
| [phase-0-exit-report.md](./phase-0-exit-report.md) | 阶段 0 退出条件对照与 Spike 口径 |
| [phase-1-exit-report.md](./phase-1-exit-report.md) | 阶段 1 建表与骨架退出对照 |
| [phase-0-adr.md](./phase-0-adr.md) | 冻结口径：状态机、Thinking、业务码、恢复、双路径等 |
| [field-mapping.md](./field-mapping.md) | Java/API camelCase ↔ Python snake_case 映射 |
| [ragas-scoring-service-contract.md](./ragas-scoring-service-contract.md) | `ragenteval` HTTP 评分服务契约 |
| [schemas/](./schemas/) | `EvalSample` / `EvalRecord` / `MetricResult` JSON Schema |
| [examples/](./examples/) | 双向可反序列化的样例 JSON |
| [fixtures/](./fixtures/) | Schema 校验用夹具（含非法样例） |

配套脚本：

| 脚本 | 说明 |
|------|------|
| [`scripts/evaluation/eval_phase0_validate_schemas.py`](../../scripts/evaluation/eval_phase0_validate_schemas.py) | 离线校验 Schema + 样例互转 |
| [`scripts/evaluation/eval_phase0_spike.py`](../../scripts/evaluation/eval_phase0_spike.py) | 对运行中的 ragent 做 SSE/TTFT、`/rag/eval`、taskId→traceId spike |
| [`scripts/evaluation/README.md`](../../scripts/evaluation/README.md) | 本模块脚本说明（与通用 `scripts/README.md` 分离） |

相关上游文档：

- [需求文档](../rag-evaluation-workbench-requirements.md)
- [开发方案](../rag-evaluation-workbench-development-plan.md)
- 外部参考实现：`D:\code\ragenteval\eval\common\schemas.py`
