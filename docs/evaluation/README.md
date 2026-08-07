# RAG 评测工作台

本目录汇总评测工作台的规划、设计契约、阶段报告和配套校验资产。业务实现边界与历史结论以相应文档为准。

| 分类 | 内容 |
|------|------|
| [planning/](planning/) | 需求与逐阶段开发方案 |
| [design/](design/) | ADR 与冻结的系统口径 |
| [contracts/](contracts/) | JSON Schema、样例、字段映射与 RAGAS HTTP 契约 |
| [reports/](reports/) | 阶段 0–5 退出报告 |

配套脚本见 [`scripts/evaluation/`](../../scripts/evaluation/README.md)：离线 Schema 校验无需启动服务；在线 Spike 需要运行中的 ragent。
