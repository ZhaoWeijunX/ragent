---
module: route-port-country-and-location-foundation
title: 航线港口国家地点基础能力概览
status: source-verified
last_verified: 2026-08-26
source_policy: current-repository-code-config-and-sql
---

# 航线港口国家地点基础能力概览

该能力为 Booking、Bill、Manifest 等业务提供港口、航线、国家/地点和码头映射查询。已确认页面入口包括 `PortController`（`POST /port/getPortList`）、`PodTerminalMappingController`、`BizPorBookingOfficeMappingController` 与 Ops 侧港口接口；服务层分布在 `PortService`、third-system Yunba/Station client 和各业务 location service。PortController 标记免登录/免 API 权限，空搜索文本直接返回空列表，非空才委托 `portService.getList`。

基础数据是查询支撑，不拥有业务单据的 POL/POD 状态；调用方应保存规范代码和原始名称的边界。当前代码检索确认了港口 DTO、第三方 SearchPortResultDTO、万海/MH8 location DTO，但未确认统一国家主表、全量缓存和线上数据源。源清单：PortController、PortService、port DTO、YunBa/Station clients、PodTerminalMappingController、location 相关 Mapper。

## PortService 的真实查询链

`PortController#getPortList` 对空白 searchText 直接返回空集合，非空进入 `PortServiceImpl#getList`。该方法固定 `portType=SEA`、limit=30；有 channelCode 时额外传 `companyType=BookingChannel` 与 `companyCode`，再用 `HttpUtil.post(portListUrl, ..., 60s)` 请求中台资源服务。返回体为空会转为空列表；响应 success=false 时抛参数异常。结果不会原样返回：它用 `PrefixConsecutiveRerank` 对英文港口名重排，过滤 `EntrustedConstant.INVALID_FIVE_CODES`，最后截断到 30 条。

同一个 service 还拥有按 code 查询、五字码到大掌柜/货代的映射、批量标准映射等能力。`getListByCodes` 会过滤空值并 distinct；标准映射单批上限由 `STANDARD_MAPPING_BATCH_SIZE=10` 控制。这些是基础码值转换，不代表 Booking、Bill 或 Manifest 已经接受该 code；业务域仍要在 carrier/channel/账号上下文下转换为官网字段。

## 设计、风险与证据边界

基础地点服务避免各业务域自行维护一套模糊搜索和五字码数据，但它把可用性、排序和上游正确性依赖于多个外部 URL。`@Login(false)`、`@ApiPermission(false)` 只说明该 Controller 的注解，生产网关是否限制该查询当前代码无法确认。历史资料若把所有地点数据描述为本地统一主表，与 `PortServiceImpl` 当前 HTTP 查询实现不一致；以源码中的 `mid.resource.port.*Url` 配置和 DTO 转换为准。
