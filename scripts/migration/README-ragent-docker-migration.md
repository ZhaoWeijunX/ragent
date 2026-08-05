# Ragent Docker 完整迁移指南

本方案将应用与 Docker 基础设施分离：Ragent 后端和前端运行在应用机，PostgreSQL、Redis、RustFS、RocketMQ、Neo4j 与 LightRAG 运行在远程 Linux Docker 主机。迁移完成后，Docker 主机不需要克隆完整 Ragent 项目，日常使用一个原生 Compose 文件启动全部服务。

迁移包使用标准 `tar`、数据卷 `tar.gz`、PostgreSQL SQL、Redis RDB 与 `docker image save` 格式。旧机可以是 Windows Docker Desktop，新机可以是同 CPU 架构的原生 Linux Docker Engine；不要求使用相同 Docker Desktop，也不要复制 Docker Desktop 的 WSL 虚拟磁盘或 Linux 的 `/var/lib/docker`。

## 备份内容

- PostgreSQL：停写后的完整数据卷，加一份 `pg_dumpall` 逻辑兜底。
- RustFS：完整数据卷，包含对象及服务器持久化数据，不依赖 IAM ZIP。
- Redis：同步 `SAVE` 后的 `dump.rdb`，在新机恢复到正式命名卷。
- RocketMQ：停机后的 broker 文件系统快照，包含消息、消费进度和日志，在新机恢复到正式命名卷。
- Neo4j 与 LightRAG：完整数据卷。
- 正在使用的精确镜像、本地配置、统一 Compose、校验和及恢复工具。

## 一、旧 Windows 机器备份

先停止 Ragent 后端、上传任务和其他生产者/消费者。随后在项目根目录执行：

```powershell
$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$target = "D:\code\ragent\.output\ragent-docker-backup-$timestamp"
.\scripts\migration\backup-ragent-docker.ps1 `
  -BackupDirectory $target `
  -ConfirmStop `
  -RestartAfterBackup
```

成功后得到：

```text
<backup-directory>.tar
<backup-directory>.tar.sha256
```

只需传输这两个文件。旧机容器会恢复到备份前的运行状态；脚本不会删除或重建任何旧机容器、镜像或卷。

## 二、清空新的 Linux Docker 主机

安装 Docker Engine 和 Docker Compose plugin，确认 Docker 使用 Linux 容器，并且 Compose 能识别迁移包中的 `include`：

```bash
docker info --format '{{.OSType}} {{.Architecture}}'
docker compose version
```

传输并解压迁移包：

```bash
cd /mnt/transfer
sha256sum -c ragent-docker-backup-YYYYMMDD-HHMMSS.tar.sha256
tar -xf ragent-docker-backup-YYYYMMDD-HHMMSS.tar
```

仅在确认这是允许清空的新 Docker 主机后执行：

```bash
bash /mnt/transfer/ragent-docker-backup-YYYYMMDD-HHMMSS/tools/reset-new-docker-linux.sh \
  --confirm-destroy-all-docker-data
```

脚本要求再次输入 Linux 主机名，然后永久删除该 Docker daemon 的全部容器、镜像、命名卷、自定义网络和构建缓存。绝对不要在旧机器执行。

## 三、在 Linux Docker 主机恢复

确定一个能够被应用机访问的 Linux 主机 IPv4 地址，例如 `192.168.10.20`。该地址会写入 RocketMQ 的 `brokerIP1`，不能填写 `127.0.0.1` 或 `0.0.0.0`。

```bash
sudo install -d -o "$USER" -g "$(id -gn)" /opt/ragent-docker

bash /mnt/transfer/ragent-docker-backup-YYYYMMDD-HHMMSS/tools/restore-ragent-docker.sh \
  --backup-dir /mnt/transfer/ragent-docker-backup-YYYYMMDD-HHMMSS \
  --deploy-dir /opt/ragent-docker \
  --docker-host-ip 192.168.10.20
```

恢复脚本会：

1. 校验包内全部文件和 CPU 架构。
2. 导入备份中的精确镜像。
3. 将统一部署文件安装到 `/opt/ragent-docker`。
4. 创建停止状态的容器和空命名卷。
5. 恢复 PostgreSQL、Redis、RustFS、RocketMQ、Neo4j 与 LightRAG 数据。

脚本拒绝覆盖非空部署目录或非空数据卷。恢复结束时服务仍未启动。

## 四、无附加参数启动 Docker

首次和以后日常启动都使用同一条原生 Compose 命令：

```bash
cd /opt/ragent-docker
docker compose -f ragent-stack.compose.yaml up -d
```

统一入口会在内部加载远程 Linux 专用配置，但命令行不再需要 migration override、`--env-file`、项目源码目录或第二套 Compose 命令。项目原有的本地开发 Compose 未加入 Redis/RocketMQ 新卷，不会改变旧机以后按原方式启动的行为。不要启动包中的 `source-rocketmq-stack.compose.yaml`。

常用管理命令：

```bash
docker compose -f ragent-stack.compose.yaml ps
docker compose -f ragent-stack.compose.yaml logs --tail 200
docker compose -f ragent-stack.compose.yaml stop
docker compose -f ragent-stack.compose.yaml up -d --remove-orphans
```

容器均配置 `restart: unless-stopped`。启用 Docker 开机启动后，Linux 重启时容器会自动恢复：

```bash
sudo systemctl enable --now docker
```

## 五、配置运行 Ragent 的应用机

应用机项目根目录的 `.env` 至少调整以下两项：

```dotenv
RAGENT_INFRA_HOST=192.168.10.20
```

`RAGENT_INFRA_HOST` 同时控制 PostgreSQL、Redis、RocketMQ NameServer、RustFS 与 LightRAG 地址。Spring Boot 不会自动读取 `.env`，仍需按项目既有方式将文件注入运行环境。前端与后端在同一应用机运行时，Vite 的 `localhost:9090` 代理不需要修改。

## 六、网络与防火墙

远程 Docker 主机至少允许应用机访问以下 TCP 端口：

- PostgreSQL：`5432`
- Redis：`6379`
- RustFS S3 API：`9000`
- RocketMQ NameServer：`9876`
- RocketMQ Broker：`10909`、`10911`
- LightRAG：`9621`

管理端口 `9001`（RustFS）、`8082`（RocketMQ Dashboard）、`7474/7687`（Neo4j）只在确有管理需求时开放。不要把这些使用默认账号密码的端口直接暴露到公网；应通过内网、VPN、安全组或防火墙仅允许应用机来源地址。

## 七、恢复后验证

在 Docker 主机执行：

```bash
cd /opt/ragent-docker
docker compose -f ragent-stack.compose.yaml ps
docker exec postgres pg_isready -U postgres -d ragent
curl -f http://127.0.0.1:9621/health
```

在应用机检查网络连通性后，启动 Ragent，并完成一次“上传文档、入库、检索问答”端到端验证。同时核对 RustFS 桶和对象、RocketMQ Topic/消费组以及 Neo4j 图数据。
