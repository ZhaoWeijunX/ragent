---
module: route-port-country-and-location-foundation
title: 航线港口国家地点基础能力排障验证
status: source-verified
last_verified: 2026-08-26
source_policy: current-repository-code-config-and-sql
---

# 排障与验证

搜索为空先确认 searchText 是否为空白（代码会直接返回空列表）、请求路径是否为 `/port/getPortList`，再查 PortService 查询条件和第三方响应。代码不接受/返回预期码时，区分五字码、港口名称、船司地点和码头映射，不要跨表猜测。Booking/Manifest 提交失败需同时记录 carrier/channel/account 与最终 payload，确认标准 code 到官网字段的转换。

静态定位：`rg -n "getPortList|PortSearchDto|PodTerminal|BookingOffice|SearchPortResultDTO|LocationInfoDTO"`。本次未调用第三方、未验证生产数据和网关权限，无法确认数据新鲜度、缓存、限流、国家标准和失败重试。源清单：Port/terminal/office Controller、Service、Mapper、Yunba/Station/MH8 clients/DTO。

## 按链路分层排障

| 现象 | 代码路径与首查证据 | 不能直接推断的结论 |
| --- | --- | --- |
| 空搜索结果 | Controller 是否因 blank searchText 直接返回；`PortServiceImpl#getList` 的上游响应是否为空 | 空列表不等于上游不存在该港口 |
| 结果顺序异常 | `PrefixConsecutiveRerank`、英文港口名、无效五字码过滤、limit=30 | 不应比较上游原始顺序判断“数据错乱” |
| 指定 code 查不到 | `getListByCodes` 的 distinct 后输入、portCodeListUrl 响应 | 批量返回不保证保留调用方顺序 |
| 映射到货代失败 | YD2BIZ 的 scene/conversionType、querySource/mappingTarget、单批上限 | 港口搜索成功不代表货代映射配置存在 |
| 官网提交地点失败 | 最终 carrier/channel/account、转换后的 payload、船司策略 | 不能用 `/port/getPortList` 成功替代官网字段校验 |

验证时建议用 mock 上游分别覆盖 success=false、空 body、超时、返回无效五字码和超过 30 个候选项；再用固定输入验证排序、过滤和批量分片。服务内 `HttpUtil.post` 是同步 60 秒调用，单次超时可能占用请求线程；生产熔断、缓存、降级和网关限流当前代码无法确认。文档与代码差异：历史“本地港口字典”描述不适用于当前 PortService 的远程查询主链。
