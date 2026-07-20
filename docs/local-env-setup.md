# 本地环境快速配置

本文面向本地开发 / 体验试用，帮助在干净机器上拉起中间件、初始化数据库并启动前后端。生产部署请另行评估资源与高可用方案。

官网 IDE / JDK / Maven 说明见：[本地开发环境搭建](https://nageoffer.com/ragent/local-dev/)。本文补齐中间件、库表与联调部分。

## 1. 基础工具

| 工具 | 版本要求 | 说明 |
|------|----------|------|
| JDK | 17+ | Spring Boot 3 |
| Maven | 建议 3.8.x；避开 3.6.x 与 3.9.3+ 兼容坑 | 编译多模块工程 |
| Node.js | 18+ LTS | 前端 `frontend/` |
| Docker | 支持 Compose v2 | 中间件容器 |
| Git | 任意近期版本 | 建议 clone，勿用 zip |

可选：IntelliJ IDEA 2023+。

## 2. 中间件一览

默认配置见 `bootstrap/src/main/resources/application.yaml`，本机主机名统一为 `localhost`（可通过环境变量 `RAGENT_INFRA_HOST` 覆盖，见根目录 `.env`）。

| 组件 | 端口 | 账号 / 密码 | 是否必开 |
|------|------|-------------|----------|
| PostgreSQL (pgvector) | 5432 | `postgres` / `postgres`，库名 `ragent` | 必开 |
| Redis | 6379 | 密码 `123456` | 必开 |
| RustFS（S3 兼容） | 9000 / 控制台 9001 | `rustfsadmin` / `rustfsadmin` | 必开 |
| RocketMQ NameServer | 9876 | — | 必开 |
| RocketMQ Broker | 10909 / 10911 / 10912 | — | 必开 |
| RocketMQ Dashboard | 8082 | — | 可选（排障） |
| Ollama | 11434 | — | 可选（本地模型） |
| Milvus | 19530 | — | 可选（`rag.vector.type=milvus`） |
| Elasticsearch | 9200 | — | 可选（`rag.keyword.type=es`） |
| mcp-server | 9099 | — | 可选（MCP 工具） |

默认向量存储为 **PostgreSQL + pgvector**（`rag.vector.type: pg`），无需先装 Milvus。

---

## 3. 拉取中间件

> Windows PowerShell 可将下方 `\` 续行改为 `` ` ``，或改成单行执行。

### 3.1 PostgreSQL（含 pgvector）

```bash
docker run -d \
  --name postgres \
  -e POSTGRES_DB=ragent \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  -v pgdata:/var/lib/postgresql/data \
  pgvector/pgvector:pg16
```

健康检查：

```bash
docker exec -it postgres psql -U postgres -d ragent -c "SELECT 1;"
```

### 3.2 Redis

```bash
docker run \
  -p 6379:6379 \
  --name redis \
  -d redis \
  redis-server \
  --requirepass "123456"
```

健康检查：

```bash
docker exec -it redis redis-cli -a 123456 ping
```

### 3.3 RustFS

```bash
docker run -d \
  --name rustfs \
  -p 9000:9000 \
  -p 9001:9001 \
  -v rustfs-data:/data \
  -e RUSTFS_ACCESS_KEY=rustfsadmin \
  -e RUSTFS_SECRET_KEY=rustfsadmin \
  -e RUSTFS_CONSOLE_ENABLE=true \
  rustfs/rustfs:1.0.0-alpha.72 \
  --address :9000 \
  --console-enable \
  --access-key rustfsadmin \
  --secret-key rustfsadmin \
  /data
```

- API：`http://localhost:9000`
- 控制台：`http://localhost:9001`
- 应用侧配置与上述密钥一致（`rustfs.access-key-id` / `secret-access-key`）
- 首次入库前可在控制台确认 bucket（如 `ragent-assets`）是否按需创建；也可由业务侧按配置自动处理

### 3.4 RocketMQ

仓库已提供 Compose：`resources/docker/rocketmq-stack-5.2.0.compose.yaml`。

内容如下（与仓库文件一致）：

```yaml
name: rocketmq-stack

services:
  rmqnamesrv:
    image: apache/rocketmq:5.2.0
    container_name: rmqnamesrv
    ports:
      - "9876:9876"
    environment:
      JAVA_OPT_EXT: "-Xms256m -Xmx400m"
    command: sh mqnamesrv
    networks:
      - rocketmq
    restart: unless-stopped
    healthcheck:
      test: [ "CMD-SHELL", "grep -q 'The Name Server boot success' /home/rocketmq/logs/rocketmqlogs/namesrv.log || exit 1" ]
      interval: 10s
      timeout: 5s
      retries: 10

  rmqbroker:
    image: apache/rocketmq:5.2.0
    container_name: rmqbroker
    ports:
      - "10912:10912"
      - "10911:10911"
      - "10909:10909"
      - "8080:8080"
      - "8081:8081"
      - "8082:8082"
    environment:
      NAMESRV_ADDR: "rmqnamesrv:9876"
      JAVA_OPT_EXT: "-Xms512m -Xmx512m -XX:-UseContainerSupport"
    depends_on:
      rmqnamesrv:
        condition: service_healthy
    command:
      - sh
      - -c
      - |
        cat > /home/rocketmq/rocketmq-5.2.0/conf/broker.conf << EOF
        brokerClusterName = DefaultCluster
        brokerName = broker-a
        brokerId = 0
        deleteWhen = 04
        fileReservedTime = 48
        brokerRole = ASYNC_MASTER
        flushDiskType = ASYNC_FLUSH
        brokerIP1 = 127.0.0.1
        timerMaxDelaySec = 31622400
        coldDataScanEnable = false
        coldDataFlowControlEnable = false
        EOF
        sh mqbroker -c /home/rocketmq/rocketmq-5.2.0/conf/broker.conf
    networks:
      - rocketmq
    restart: unless-stopped

  dashboard:
    image: apacherocketmq/rocketmq-dashboard:2.1.0
    container_name: rocketmq-dashboard
    network_mode: "service:rmqbroker"
    environment:
      JAVA_OPTS: >-
        -Xms256m -Xmx512m
        -XX:MaxMetaspaceSize=256m
        -Drocketmq.config.enableDashBoardCollect=false
        -Drocketmq.namesrv.addr=rmqnamesrv:9876
        -Dserver.port=8082
    depends_on:
      rmqbroker:
        condition: service_started
    restart: unless-stopped

networks:
  rocketmq:
    driver: bridge
```

启动：

```bash
cd resources/docker
docker compose -f rocketmq-stack-5.2.0.compose.yaml up -d
```

- NameServer：`localhost:9876`（对应 `rocketmq.name-server`）
- Dashboard：`http://localhost:8082`
- Apple Silicon 等非 amd64 环境可改用同目录 `rocketmq-stack-amd-5.2.0.compose.yaml`（按机器架构选择）

> 注意：`brokerIP1 = 127.0.0.1` 便于本机客户端直连；中间件与应用不在同一台机器时需改为宿主机可达 IP。

---

## 4. 数据库脚本导入顺序

脚本目录：`resources/database/`。

**全新库只跑「基线」即可。** 当前 `schema_pg.sql` 已包含至 v1.4 的表结构（含飞书 Wiki、深度思考字段、向量 Collection、业务审计表等），**不要**在全新库上再依次执行 upgrade 脚本。

### 4.1 必做：基线（全新安装）

按顺序执行：

| 顺序 | 脚本 | 作用 |
|------|------|------|
| 1 | `resources/database/schema_pg.sql` | 建表 + `CREATE EXTENSION vector` |
| 2 | `resources/database/init_data_pg.sql` | 初始化管理员：`admin` / `admin` |

示例（容器内）：

```bash
# 将脚本拷进容器，或挂载仓库目录后执行
docker exec -i postgres psql -U postgres -d ragent < resources/database/schema_pg.sql
docker exec -i postgres psql -U postgres -d ragent < resources/database/init_data_pg.sql
```

本机已装 `psql` 时：

```bash
psql "postgresql://postgres:postgres@localhost:5432/ragent" -f resources/database/schema_pg.sql
psql "postgresql://postgres:postgres@localhost:5432/ragent" -f resources/database/init_data_pg.sql
```

验证：

```sql
SELECT extname FROM pg_extension WHERE extname = 'vector';
SELECT username, role FROM t_user;
```

### 4.2 可选：MCP 业务表与模拟数据

需要演示销售汇总 / 工单查询 MCP 时再执行：

| 顺序 | 脚本 | 作用 |
|------|------|------|
| 3 | `resources/database/biz_mcp_schema.sql` | `t_biz_sales_order` / `t_biz_support_ticket` |
| 4 | `resources/database/biz_mcp_seed.sql` | 写入可重复执行的模拟数据 |

```bash
docker exec -i postgres psql -U postgres -d ragent < resources/database/biz_mcp_schema.sql
docker exec -i postgres psql -U postgres -d ragent < resources/database/biz_mcp_seed.sql
```

### 4.3 可选：意图树与示例问题

脚本在 `docs/examples/intent-node-import/`。按能力按需导入；**有依赖时请按下表顺序**。

| 顺序 | 脚本 | 说明 |
|------|------|------|
| 5 | `mcp-intent-nodes-import.sql` | MCP 意图节点（销售 / 工单 / 天气） |
| 6 | `mcp-intent-nodes-weather-prompt-update.sql` | 天气提参 Prompt（依赖上一步 weather 节点） |
| 7 | `mcp-intent-nodes-biz-prompt-update.sql` | 销售 / 工单提参 Prompt（依赖上一步对应节点） |
| 8 | `onboarding-intent-nodes-import.sql` | 入职知识库意图树（需先按脚本注释替换 kb 占位符） |
| 9 | `biz-security-intent-nodes-import.sql` | 信息安全知识库意图树（同上，先建库再替换占位符） |
| 10 | `ragent-test-intent-nodes-import.sql` | 技术专栏意图树（同上） |
| 11 | `sample-questions-import.sql` | 欢迎页示例问题（建议在相关意图 / 知识库就绪后执行） |

意图节点导入后如管理台仍显示旧树，可清 Redis 缓存，例如：

```bash
docker exec -it redis redis-cli -a 123456 DEL ragent:intent:tree
```

### 4.4 存量库升级（仅已有旧库时）

全新安装**跳过**本节。仅当数据库是旧版本、需要跟代码对齐时，按版本号升序执行：

| 顺序 | 脚本 |
|------|------|
| a | `upgrade_v1.0_to_v1.1.sql` |
| b | `upgrade_v1.1_to_v1.2.sql` |
| c | `upgrade_v1.2_to_v1.2.1_feishu.sql` |
| d | `upgrade_v1.2_to_v1.3.sql` |
| e | `upgrade_v1.3_to_v1.4.sql` |

从中间版本升级时，只执行「当前版本之后」的脚本。

### 4.5 导入顺序总览（推荐路径）

```text
【全新最小可跑】
  schema_pg.sql
  → init_data_pg.sql

【加上 MCP 演示】
  → biz_mcp_schema.sql
  → biz_mcp_seed.sql
  → mcp-intent-nodes-import.sql
  → mcp-intent-nodes-weather-prompt-update.sql
  → mcp-intent-nodes-biz-prompt-update.sql

【加上示例知识库意图 / 欢迎页问题】
  → 管理台创建知识库并替换占位符后导入对应 *-intent-nodes-import.sql
  → sample-questions-import.sql
```

---

## 5. 本地密钥配置（环境变量 / `.env`）

密钥统一放在**仓库根目录** `.env`，由进程环境变量注入；`application.yaml` 已通过 `${BAILIAN_API_KEY:}` 等占位符读取。Spring Boot **不会**自动加载 `.env`。

```bash
cp .env.example .env
# 编辑 .env，填写真实值
```

启动前注入（任选其一）：

| 方式 | 做法 |
|------|------|
| IDEA | Run Configuration → Environment variables → 加载仓库根目录 `.env`（可用 EnvFile 插件） |
| PowerShell | `. .\scripts\export-dotenv.ps1` 后再 `mvn spring-boot:run` |
| bash | `set -a && source .env && set +a` |

按需填写（至少保证 **Chat** 与 **Embedding** 各有一个可用候选）：

| 环境变量 | 用途 | 缺失影响 |
|----------|------|----------|
| `BAILIAN_API_KEY` / `SILICONFLOW_API_KEY` / `AIHUBMIX_API_KEY` / `DEEPSEEK_API_KEY` | 各云厂商模型 | 对应模型不可用；可改用 Ollama |
| `MINERU_API_KEY` | PDF / Word / PPT 解析 | 复杂文档入库失败 |
| `FEISHU_APP_ID` / `FEISHU_APP_SECRET` | 飞书 Wiki / 文档 | 飞书导入不可用，其它功能不受影响 |
| `YDC_API_KEY` | You.com 联网搜索（bootstrap 通道 / mcp-server 工具） | 联网搜索不可用 |
| `QWEATHER_API_KEY` / `QWEATHER_API_HOST` | mcp-server 天气工具 | 天气查询不可用 |
| `RAGENT_INFRA_HOST` | PG / Redis / RocketMQ / RustFS 主机名 | 默认写在 `.env` 为 `localhost`；远端中间件改此变量即可 |

无 Rerank 密钥时可保持配置中的 `rerank-noop` 候选，或暂时关闭 `rag.rerank.enabled`。

GraphRAG Compose 仍使用独立的 `resources/docker/graphrag/.env`（见该目录 README）。

---

## 6. 启动应用

### 6.1 后端（bootstrap）

入口：`com.nageoffer.ai.ragent.RagentApplication`

- 端口：`9090`
- Context path：`/api/ragent`
- 健康探活：浏览器或 `curl` 访问管理相关接口；日志无数据源 / Redis / RocketMQ 报错即为基本成功

### 6.2 前端

```bash
cd frontend
npm install
npm run dev
```

- 默认代理见 `frontend/vite.config.ts` → `http://localhost:9090`
- `.env` 中 `VITE_API_BASE_URL=/api/ragent`

默认登录：`admin` / `admin`（来自 `init_data_pg.sql`）。

### 6.3 MCP Server（可选）

入口：`com.nageoffer.ai.ragent.mcp.McpServerApplication`，端口 `9099`，与 `rag.mcp.servers[0].url` 一致。

天气 / You.com 等密钥与 bootstrap 共用仓库根目录 `.env`（`QWEATHER_*`、`YDC_API_KEY`），启动 mcp-server 前同样需注入环境变量。

---

## 7. 最小验证清单

1. 四个中间件容器 / 服务均在运行（Postgres、Redis、RustFS、RocketMQ）。
2. 库表已导入，`admin` 可登录管理台。
3. 创建知识库 → 上传文档 → 分块入库成功。
4. 聊天页提问，SSE 能返回检索内容与回答。
5. （可选）MCP：销售 / 工单查询能走工具；意图树与示例问题展示正常。

---

## 8. 常见问题

| 现象 | 处理 |
|------|------|
| `relation ... does not exist` | 未执行 `schema_pg.sql`，或连错库 |
| `extension "vector" does not exist` | 镜像必须用 `pgvector/pgvector`，不要用官方纯 postgres |
| Redis `NOAUTH` | 确认 `--requirepass 123456` 与配置一致 |
| RocketMQ 收发失败 | 等 NameServer healthy 后再看 Broker；本机确认 `brokerIP1` |
| 向量维度报错 | Embedding 模型维度需与 `rag.default.dimension`（默认 1536）一致 |
| 意图树不更新 | 清 Redis `ragent:intent:tree` 后刷新 |
| 端口占用 | 修改映射或停掉本机旧进程 |

---

## 9. 端口速查

| 服务 | 端口 |
|------|------|
| 后端 API | 9090 |
| 前端 Vite | 视 `npm run dev` 输出（常为 5173） |
| MCP | 9099 |
| PostgreSQL | 5432 |
| Redis | 6379 |
| RustFS API / Console | 9000 / 9001 |
| RocketMQ NameServer | 9876 |
| RocketMQ Dashboard | 8082 |

## 参考

- [根目录 `.env.example`](../.env.example)
- [数据库脚本目录](../resources/database/)
- [意图节点示例 SQL](./examples/intent-node-import/)
- [Milvus 轻量 Compose](../resources/docker/lightweight/README.md)
- [官网本地开发](https://nageoffer.com/ragent/local-dev/)
