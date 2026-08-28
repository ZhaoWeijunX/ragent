# 项目架构说明

# iscm\-trace\-notify\-platform 分层架构与核心链路



## 1\. 分层架构总览

```mermaid
flowchart TB
    subgraph L1["L1 事件入口层"]
      A1["RocketMQ DataCompare.TOPIC\n(tag: SHIP/PORT/FUSION)"]
      A2["Ops 手工触发 compare-{type}-data"]
    end

    subgraph L2["L2 消费与路由层"]
      B1["DataCompareListener\n(超时丢弃 + 分布式锁)"]
      B2["DataCompareStrategy\n按 type 路由"]
      B3["ShipCleanDataCompare"]
      B4["PortCleanDataCompare"]
      B5["FullLinkCleanDataCompare"]
    end

    subgraph L3["L3 规则编排层(AbstractDataCompare)"]
      C1["查询订阅记录/规则"]
      C2["用户信息聚合(UserInfoQuery)"]
      C3["预警链 sendNotifyForWarning"]
      C4["普通通知链 sendNotify/firstSub"]
      C5["BI清洗消息 sendBiCleanMessage"]
    end

    subgraph L4["L4 通知引擎层(NotifyModule链)"]
      D1["Assembly"]
      D2["Parse/Calculate\n(JsonPath + EL)"]
      D3["DataFilter\n(去重/频控/状态持久化)"]
      D4["DataSend\n(按用户/箱号聚合)"]
      D5["WebSaveForMysql\n(预警落库)"]
    end

    subgraph L5["L5 通道聚合层"]
      E1["ThirdNotifyObserver"]
      E2["EmailListener"]
      E3["WechatListener"]
      E4["ApiPushListener"]
      E5["AggregateNotify"]
    end

    subgraph L6["L6 外部依赖层"]
      F1["MySQL"]
      F2["MongoDB"]
      F3["CargoBaby/SF/Schedule API"]
      F4["邮件/微信/API推送"]
      F5["Nacos配置"]
    end

    A1 --> B1 --> B2 --> B3
    B2 --> B4
    B2 --> B5

    B3 --> C1
    B4 --> C1
    B5 --> C1

    C1 --> C2 --> C3 --> D1 --> D2 --> D5
    C2 --> C4 --> D1 --> D2 --> D3 --> D4 --> E1 --> E2 --> E5
    E1 --> E3 --> E5
    E1 --> E4 --> E5
    C3 --> C5
    C4 --> C5

    C1 -.-> F1
    D3 -.-> F2
    D1 -.-> F3
    C2 -.-> F3
    E5 -.-> F4
    B1 -.-> F5
```

## 2\. 核心执行时序

```mermaid
sequenceDiagram
    participant MQ as RocketMQ
    participant L as DataCompareListener
    participant S as DataCompareStrategy
    participant C as AbstractDataCompare
    participant U as UserInfoQuery
    participant P as PushService
    participant M as NotifyModule链
    participant F as DataFilter
    participant O as ThirdNotifyObserver

    MQ->>L: 投递 DataCompareDTO
    L->>L: >1小时消息直接丢弃
    L->>L: 分布式锁(订阅维度)
    L->>S: 按 type 路由
    S->>C: 进入对应 Compare 实现

    C->>C: queryConsumeRecordList/queryCount
    C->>U: 聚合用户+节点配置
    U-->>C: NodeConfigAndUserInfoDTO

    C->>P: sendNotifyForWarning
    P->>M: Assembly->ParseForWarning->WebSave

    alt 等待窗口未到(queryCount<=0)
      C->>C: 不走普通通知
    else
      C->>P: sendNotify/firstSub
      P->>M: Assembly->Parse->Filter->Send
      M->>F: 去重+频控
      F-->>M: 保留可发送事件
      M->>O: addNotify
      O->>O: 通道聚合后发送
    end

    C->>P: sendBiCleanMessage
```

## 3\. 关键机制详解



### 3\.1 用户信息聚合（`UserInfoQuery`）

1. 聚合两类通知对象：

- 客户内部用户（用户ID维度，支持邮箱/微信/API账号）

- 通讯录转发用户（联系人邮箱维度）

2. 聚合两类节点配置：

- 用户节点配置（recordId \+ userId）

- 转发节点配置（recordId \+ 联系人邮箱）

3. key 设计：

- 普通用户：`md5(userId, recordId)`；支持全局兜底 `md5(userId, null)`

- 转发用户：`md5(email, recordId)`

4. 最终产物：

- `userNotifyConfigList`：谁可接收通知

- `consumeRecordNodeConfigList`：每人可接收哪些节点（warn/notify/plan）

- `userCodeSetMap`：后续过滤“当前用户不关注节点”的依据

### 3\.2 去重逻辑（四层）

1. 规则结果去重：

- `NotifyInfoItem.uniqueAndBusinessKey()` 避免同业务键重复入集。

2. 持久态去重（最核心）：

- `DataFilter` 从 Mongo `CONTAINER_NODE_NOTIFY_RECORD` 读取历史发送状态。

- 分 `warningNodeNotifyInfoMap` 与 `changeNodeNotifyInfoMap` 两套计数状态。

3. 发送内容聚合去重：

- `DataSend` 在“箱号\+节点\+businessKey”维度合并。

- 冲突按 `sendPriority` 选优先级高者。

4. 通道内去重：

- Email/API：同 `subId + container` 合并并合并 flag。

- Wechat：同 `subId + scope(+container)` 合并模板项。

### 3\.3 频控策略（`FrequencyStrategy`）

支持四种策略：

1. `EVERY`：每次触发都可发。

2. `COUNT`：`eventItem.total < frequency.total` 时可发。

3. `INTERVAL`：距上次通知小时数落入配置区间才可发。

4. `TIME`：满足 cron 且当天发送次数 `< total`。

补充：

- `DataFilter.sendMaxTotal()` 还有总上限保护（默认 10）。

### 3\.4 “X 秒内不发送”的实现

1. X 不是固定值，来自表 `cargo_baby_wait_time_config.wait_time`（秒）。

2. 按类型读取：`ship/port/full_link`。

3. 判定条件本质为：

- `wait_time < TIMESTAMPDIFF(SECOND, create_time, now())`

4. 若不满足：

- 普通通知链被短路（不发邮件/微信/API）

- 预警落库链通常已执行

- BI 清洗消息仍可能发送

## 4\. 三条业务线的模块链



1. SHIP：

- `ShipDataAssembly -> ShipParseAndCalculate -> ShipDataFilter -> ShipDataSend`

- 预警链：`ShipDataAssembly -> ShipParseAndCalculateForWarning -> WebPortAndShipSaveForMysql`

2. PORT：

- `PortDataAssembly -> PortParseAndCalculate -> PortDataFilter -> PortDataSend`

- 预警链：`PortDataAssembly -> PortParseAndCalculateForWarning -> WebPortAndShipSaveForMysql`

3. FUSION：

- `FusionDataAssembly -> FusionParseAndCalculate -> FusionDataFilter -> FusionDataSend`

- 预警链：`FusionDataAssembly -> FusionParseAndCalculateForWarning -> WebFusionSaveForMysql`

## 5\. 关键数据状态（Mongo）



`CONTAINER_NODE_NOTIFY_RECORD`（按订阅记录ID存储）

1. `warningNodeNotifyInfoMap`：预警节点历史发送状态

2. `changeNodeNotifyInfoMap`：变动节点历史发送状态

3. `oldTerminalInfo`：港区链路保留的旧船计划信息

4. `subTableName/updateTime`：状态标识与更新时间

## 6\. 运行排障重点



1. 消息到了但没发通知：

- 先看是否超过 1 小时被消费层丢弃。

- 看 wait\_time 窗口是否未到（queryCount=0）。

- 看 `isOpen`、规则配置、用户通知配置是否为空。

2. 有规则但用户收不到：

- 检查 `userCodeSetMap` 是否把节点过滤掉。

- 检查通知拦截缓存 `NotifyInterceptCache`。

- 检查通道账号字段是否为空（email/openId/businessAccount）。

3. 重复通知：

- 看 Mongo 通知状态是否写入失败。

- 看业务键（`businessKey`）是否正确生成。

- 看频控策略是否配置为 `EVERY`。

