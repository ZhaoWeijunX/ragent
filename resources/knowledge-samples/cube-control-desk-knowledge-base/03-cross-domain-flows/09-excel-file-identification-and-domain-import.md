# Excel、文件识别与领域导入

> 状态：源码静态核验；最后核验：2026-08-26。本文不把各领域模板规则合并。

项目的导入通常包含模板下载/字段配置、文件上传到文件服务、解析或识别、行级标准化与校验、预检、批量写入/创建领域对象、结果文件或错误回传。VGM、Manifest、相关方、订舱等业务各自维护字段目录和业务约束，共用文件/OSS/Excel 技术设施。

```mermaid
flowchart LR
    A[模板/配置] --> B[上传 OSS/文件服务]
    B --> C[Excel parser/识别服务]
    C --> D[Import Field Catalog/Scenario]
    D --> E[行级校验与标准化]
    E --> F[预检/重复检查]
    F --> G[领域 Manager 批量写入]
    G --> H[成功/错误明细与操作日志]
```

文件上传成功只证明对象已保存；解析成功也不代表领域写入成功。批量导入要关注：表头别名、空行、公式/日期/数字精度、重复业务键、部分成功语义、事务批次、同一文件重试和错误行定位。大文件应避免全量对象常驻内存，写入要分批并保持错误可追溯。

VGM 导入已有 `VgmImportConfigService`、`VgmImportFieldCatalog`、`VgmImportScenario` 与 `sql/vgm/vgm_import_config.sql`；Manifest/Entrusted 等模块应使用各自 Controller/Manager 证明实际链路。通用 `doc/excel_import_architecture_design.md` 是设计参考，不能替代当前实现核验。

验证至少包含标准模板、缺列、错类型、重复行、跨批次重复、部分失败、超大文件和重复上传。当前代码无法确认生产模板版本和识别服务准确率。

来源：文件 OpenAPI/`IFileServerService`、Excel 工具、各领域 ImportConfig/FieldCatalog/Manager、模板资源、`doc/excel_import_architecture_design.md`、相关 SQL/tests。面试追问：批量事务策略、幂等键、内存控制和错误可恢复性。

