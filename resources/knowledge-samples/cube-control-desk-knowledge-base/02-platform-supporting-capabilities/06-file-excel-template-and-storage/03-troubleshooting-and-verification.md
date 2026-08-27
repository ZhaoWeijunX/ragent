---
title: 文件 Excel 模板与存储排障
module: file-excel-template-and-storage
doc_type: troubleshooting-and-verification
audience: backend-ops-test
last_verified: 2026-08-26
---

# 排障与验证

## 分层证据

把问题拆成 STS 签发、OSS 对象、Excel 解析、预览 token、confirm 落库五层，记录 cid、fileKey、importType、模板版本、sheet、token 和错误行。

STS 成功不代表上传完成，parse 成功不代表业务写入。验证需覆盖重复 confirm、过期 token、缺失对象、错误行和大文件；跨存储补偿与生产生命周期当前代码无法确认。

上传失败查 STS/权限、bucket、文件大小和 key；下载失败区分 key 不存在、临时 URL 过期和业务鉴权；Excel 解析失败查 importType、sheet/表头、字段别名和编码；确认失败查预览 token TTL、重复导入和场景落库事务。模板不可用时检查 SQL 中 fileKey 是否已按环境替换并确认 OSS 对象存在。

验证应覆盖空文件、超大文件、恶意格式、重复 confirm、错误行、过期 token、OSS 失败和部分导入。当前代码无法确认生产 bucket、生命周期、病毒扫描和备份策略。证据：`SysFileStsController`、`ExcelImportController`、`ExcelImportApplicationService`、VGM/BILL importing tests 与 SQL；最后核验 2026-08-26。

## 分层定位与一致性边界

文件链路应按“凭证—对象—业务记录—解析结果—确认落库”逐层核对：STS 成功只代表客户端获得临时上传资格，不代表 OSS 对象已经存在；业务表保存了 fileKey，也不代表临时下载 URL 仍有效；预览成功仅证明解析结果可生成，真正写入业务表仍取决于 confirm 阶段的场景校验与事务。排障时必须保存原始 fileKey、模板版本、importType、预览 token 和错误行号，避免只看前端的统一错误提示。

| 现象 | 优先证据 | 常见边界 |
| --- | --- | --- |
| STS 获取失败 | `SysFileStsController` 入参、租户身份、OSS 配置 | 凭证问题不能通过重试业务 confirm 修复 |
| 上传后下载 404 | 对象 key、bucket、环境前缀 | 数据库记录与对象存储不是同一事务 |
| 表头无法识别 | 模板版本、sheet、别名映射、合并单元格 | “文件能打开”不等于符合项目导入契约 |
| 重复确认 | token 状态、业务幂等键、落库记录 | 网络重试可能再次进入 confirm |
| 部分行成功 | 场景服务事务范围、错误行集合 | 当前代码需逐场景确认是全量回滚还是部分提交 |

验证时应先用最小合法样例证明主链，再分别注入过期 token、缺失对象、重复 confirm 和单行字段错误。生产环境的对象生命周期、跨区域复制、病毒扫描和备份恢复策略不在当前仓库中，不能由本地代码推断。
