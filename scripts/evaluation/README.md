# scripts/evaluation/

RAG 评测工作台专用脚本。与仓库根目录 [`scripts/README.md`](../README.md) 中的通用运维脚本分离；**本目录后续阶段脚本均放于此**。

配套规格与 ADR 见 [`docs/evaluation/`](../../docs/evaluation/README.md)。

## 脚本一览

| 脚本 | 阶段 | 作用 |
|------|------|------|
| [`eval_phase0_validate_schemas.py`](eval_phase0_validate_schemas.py) | 0 | 离线校验 EvalSample / EvalRecord / MetricResult Schema，并验证 camelCase ↔ snake_case 互转 |
| [`eval_phase0_spike.py`](eval_phase0_spike.py) | 0 | 只读验证 `/rag/v3/chat` SSE（含 TTFT）、`/rag/eval`、`taskId → traceId`；不改业务代码 |

## 常用命令

在仓库根目录执行：

```powershell
# 离线契约校验（无需启动服务）
python scripts/evaluation/eval_phase0_validate_schemas.py

# 在线 spike（需 ragent 已启动）
$env:RAGENT_BASE_URL = "http://localhost:9090/api/ragent"
$env:RAGENT_USERNAME = "admin"
$env:RAGENT_PASSWORD = "admin"
python scripts/evaluation/eval_phase0_spike.py --limit 1
python scripts/evaluation/eval_phase0_spike.py --limit 20
```

可选环境变量：`RAGENT_TOKEN`、`EVAL_SET_PATH`、`SPIKE_OUT_DIR`（默认 `docs/evaluation/fixtures/spike-out`）。

## 约定

- 默认只读：不写业务库、不修改 Chat / Eval / Trace 实现。
- Spike 产物写入 `docs/evaluation/fixtures/spike-out/`（该目录已 gitignore 运行结果）。
- 仓库根路径解析：本目录脚本使用 `Path(__file__).resolve().parents[2]`。
