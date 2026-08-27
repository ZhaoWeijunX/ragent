---
title: 文件 Excel 模板与存储集成配置
module: file-excel-template-and-storage
doc_type: integration-and-configuration
audience: backend
last_verified: 2026-08-26
---

# 调用与配置

## 事务和配置边界

parse 与 confirm 必须使用同一 importType、fileKey 和业务上下文，但 confirm 仍需重新校验 token、唯一键和对象存在。VGM/BILL SQL 中 template.fileKey 只是环境对象引用，不能直接复制示例值到生产。

具体 importing scenario 的全量回滚或部分提交策略需以实现为准，公共 ApplicationService 无法证明全局一致。

`ExcelImportController.parse/confirm/template` 只做协议校验和转发，`ExcelImportApplicationService` 按 `importType` 选择 importing scenario；场景读取 Excel、规范化列名、生成预览 token，再由 confirm 执行落库。`SysFileStsController` 申请 STS、上传文件/截图并返回 key 或临时 URL，业务代码通过 `FileDefiner` 解析类型和存储边界。

```mermaid
flowchart LR
 A[上传/STS]-->B[OSS fileKey]
 B-->C[Excel parse]
 C-->D[preview token/cache]
 D-->E[confirm]
 E-->F[domain import]
```

模板 SQL 中的 `template.fileKey` 是环境相关值，不能直接复制生产 key；导入配置、字段别名和船司规则需与 `VgmImportConfigService`/Bill importing 代码一致。当前代码无法确认 OSS 生命周期、缓存介质和 token 过期实现的生产参数。来源：上述 Controller/Service、VGM/BILL SQL、Excel DTO/VO；最后核验 2026-08-26。
