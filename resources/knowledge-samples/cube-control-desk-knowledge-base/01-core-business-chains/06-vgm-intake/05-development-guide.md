---
title: 接单侧 VGM Intake 开发指南
module: vgm-intake
doc_type: development-guide
audience: new-backend
status: initial-verified
source_policy: current-repository-docs-and-code-only
last_verified: 2026-08-26
---

# 接单侧 VGM Intake 开发指南

## 定位和最小改动面

接口行为从 `VgmInfoController` 进入 `VgmInfoManagerImpl`；列表/详情继续追 `VgmInfoService`、`VgmDetailService`、BL 服务和 VO；能力修改追 `VgmConfigResolverImpl`；联合/强制监听追 `VgmCombinedProjectionManager` 与 BL 回调。不要在 BL Manager 内复制 VGM 人工状态，也不要让接单侧依赖通道表来“同步状态”。

新增表单字段要同时核对标准 VGM DTO、Mongo `vgm_detail` 读写、提交 payload、操作快照和前端设计；只有需要列表筛选/排序时才考虑 `vgm_info` 列。新增状态要检查 page/statusCount、允许动作、回调、操作日志和联合记录权限。

## 幂等、事务与扩展

- `createFromBill` 的锁 key 已按租户和 BL id 划分，新增入口必须复用相同业务幂等语义。
- 联合投影必须继续使用 sourceTaskNo，不能新增提交尝试表替代稳定通道 taskNo。
- MySQL 当前态、Mongo 详情、截图文件和外部提交需设计可恢复顺序；不能用一个 `@Transactional` 声称全部原子。
- 配置读取统一通过 `VgmConfigResolver`，避免 Controller 直接解析 `sys_config` JSON。

## 测试与发布

至少覆盖：来源 BL 不存在/状态非法、独立能力关闭、同来源重复未关闭、官网已提交、缺箱号在创建允许但提交拒绝、保存账号同步、当前单 precheck 排除自身、独立回调成功/失败/重复、联合成功投影、预览不投影、sourceTaskNo 重放。涉及 SQL/Mongo 字段时给迁移、回滚和旧数据兼容。

## 风险、差异和面试

设计文档可能比代码包含更多页面能力，开发前用 Controller/Manager 复核。生产配置、唯一索引和外部 SLA 当前代码无法确认。面试追问可围绕“为什么能力展示不等于授权”“为何联合事实不可编辑”“跨 MySQL/Mongo 如何补偿”。来源：VGM Intake 全部 Controller/Manager/Service/Entity、BL callback、VGM OpenAPI、SQL 与 tests。
