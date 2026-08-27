---
module: route-port-country-and-location-foundation
title: 航线港口国家地点基础能力集成配置
status: source-verified
last_verified: 2026-08-26
source_policy: current-repository-code-config-and-sql
---

# 集成与配置

`POST /port/getPortList` 输入 `PortSearchDto.searchText`；空白不访问下游，非空由 PortService 查询。Booking/Manifest 等页面通常还按 carrier、channel、账号或船司官网策略查询地点，不能把五字码搜索接口当作船司提交字段映射。Pod terminal 与 booking office 是独立映射边界，应沿各自 Controller→Service→Mapper 核对。

第三方 Yunba/Station/MH8 client 的响应 DTO 只代表外部返回模型，进入业务前应转换为项目标准 code/name。免登录注解意味着接口暴露面较宽，生产仍需检查网关、频控和租户策略。当前代码无法确认国家代码标准、缓存 TTL、模糊匹配排序和第三方凭证配置。

源清单：PortController/PortService/PortSearchDto、PodTerminalMappingController、booking office mapping、YunBa/Station/MH8 location DTO/client。

## 配置与参数契约

`PortServiceImpl` 读取 `mid.resource.port.portListUrl`、`portCodeListUrl`、`data12BizUrl`、`data12BizBatchUrl`、`schedulePortUrl`、`standardMappingUrl` 六类地址。它们的目标服务和认证方式不在本模块代码中可见；部署时应按环境检查 URL、超时、网关和凭证，而不要把本地启动成功当作外部码表可用。当前 `getList` 和 `getListByCodes` 的 HTTP 超时是 60 秒，调用链较长时应避免在高频前端输入中无节制重试。

请求 `getPortList` 时只需 searchText，channelCode 是可选的筛选上下文；无 channelCode 不会发送 companyType/companyCode。返回的 `Data1Port` 经过无效五字码过滤和前缀连续匹配排序，因此不能依赖原上游顺序，也不能假设返回必然包含用户输入的所有模糊命中。批量 code 查询会去重，调用方若需要保留原列表顺序或重复项，应自行保存输入到输出的映射。

Pod terminal、booking office、YunBa/Station/MH8 location 是相邻但独立的映射通道。新增船司字段时应先确认业务需要五字码、码头、官网地点 ID 还是展示名称，再沿对应 Controller→Service→Mapper/Client 添加；把一种 DTO 直接复用于另一通道会造成“页面可查但官网提交失败”。验证要覆盖空白搜索、success=false、空 body、无效五字码、channelCode 筛选、超过 10 项的标准映射批次和上游超时。国家标准、缓存 TTL、生产限流仍是当前代码无法确认项。
