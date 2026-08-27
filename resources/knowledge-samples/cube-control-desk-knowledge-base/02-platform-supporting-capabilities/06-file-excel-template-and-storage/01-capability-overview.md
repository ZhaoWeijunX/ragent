---
title: 文件 Excel 模板与存储能力概览
module: file-excel-template-and-storage
doc_type: capability-overview
audience: backend-frontend-ops
last_verified: 2026-08-26
---

# 文件、Excel、模板与 OSS

## 生命周期和一致性

SysFileStsController 处理临时凭证和文件 key；ExcelImportController 转发 parse/confirm/template；ExcelImportApplicationService 按 importType 选择 importing scenario。parse 只形成预览 token 和错误行，confirm 才执行领域校验与落库。

OSS 对象、预览缓存、关系库和 Mongo 详情不具备单一事务。fileKey 是稳定引用，临时 URL 是短期访问凭证；当前代码无法确认对象生命周期、病毒扫描、备份和孤儿对象清理。

能力由 `SysFileStsController`、`ExcelImportController`、`ExcelImportApplicationService`、各领域 importing scenario、模板服务和 OSS/FileDefiner 组成。它解决上传凭证、临时访问地址、Excel 解析预览、确认导入、模板下载和业务附件保存；不拥有具体业务记录状态。

Excel 通用链为 parse→缓存预览→confirm；业务场景再按字段目录、别名和租户/船司规则导入。VGM/BILL 的 SQL 配置要求标准模板先上传 OSS，再填 `template.fileKey`。文件 key 是跨请求证据，数据库通常只保存 key/元数据，真实二进制在 OSS；Mongo 详情和关系库状态仍由业务模块维护。

风险是临时文件、预览 TTL、错误行反馈和跨库/OSS 非原子。`SysFileStsController` 同时存在登录与内部免登录入口，必须区分鉴权边界。证据：Controller、Excel application service、`FileDefiner`、`sql/vgm/vgm_import_config.sql`、`sql/bill/bl_import_config.sql`；最后核验 2026-08-26。

## 导入与附件不应混为同一事务

导入的 `parse` 阶段只产生预览和错误行，`confirm` 才进入 importing scenario 的领域校验与持久化；因此客户端在预览成功后仍必须处理确认失败，而不能把 parse 结果展示成“已导入”。模板配置中的 `fileKey` 同样只是对象引用：SQL 先要求模板上传到 OSS，再把 key 放入配置，下载时才由文件服务解析成可访问资源。

业务附件路径也不能借用导入确认的成功语义。业务表或 Mongo detail 保存了 key 并不证明对象仍可下载，反过来 OSS 上传成功也不证明业务写入完成。涉及删除、替换或补偿时，当前源码没有给出统一的对象引用计数与垃圾回收协议，必须把对象孤儿和失效 key 作为运维核对项。

## 维护时的最小检查

新增导入类型应同时确认模板字段目录、parse token 的有效期、confirm 的幂等/重复提交行为以及每个 importing scenario 的错误行格式；新增附件字段则需确认其是永久 fileKey 还是临时 URL。两类改动都应保留“OSS 成功、关系库失败”和“预览成功、确认失败”的补偿或人工恢复证据。
