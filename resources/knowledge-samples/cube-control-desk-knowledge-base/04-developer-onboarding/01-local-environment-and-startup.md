# 本地环境与启动

> 最后核验：2026-08-26；证据类型：源码、Maven 与配置静态核验。生产凭据和地址不属于本文内容。

## 目标与范围

本文帮助开发者完成“构建最新代码、确认新进程启动、进行受控接口验证”。不承诺本地 profile 的外部依赖可用，也不复制仓库中的敏感配置值。

## 工程与启动入口

根 `pom.xml` 聚合 app、biz、api、model、integration 五个模块。启动类是 `cube-control-desk-app/.../CubecontrolDeskApplication.java`；`application.yml` 默认端口 8066、context path `/cube-control-desk`，profile 决定数据源、Mongo、Redis、OSS、MQ、XXL-Job 和外部客户端连接。

推荐先用当前团队约定的 JDK/Maven 版本确认：

```powershell
java -version
mvn -version
mvn -pl cube-control-desk-app -am -DskipTests package
```

本地启动前检查 8066 是否已被旧 Java 进程占用；Windows 可用 `Get-NetTCPConnection -LocalPort 8066 -State Listen` 查看。终止进程是有副作用操作，只处理已确认属于当前开发实例的 PID。

启动时应显式选择 profile；需要只做接口烟测时，可按环境允许关闭 scheduler，避免本地误执行定时任务。看到 Tomcat 监听和 `Started CubecontrolDeskApplication` 后再请求接口，避免把“旧进程”“新进程未启动完”和“真实接口失败”混在一起。

## 依赖和安全边界

- 本地 profile 仍可能连接共享环境；写接口、Job、脚本和 MQ 消费前先确认数据范围。
- 配置文件可能包含敏感连接信息，文档、日志和提交中不得复制。
- `@Transactional` 不覆盖 HTTP、MQ、OSS、Mongo 等外部副作用；本地验证应使用隔离数据。
- scheduler、MQ listener 和回调入口可能在启动后自动工作，按测试目标最小化启用范围。

## 排障

构建成功但接口仍表现旧逻辑时，先核对监听 PID 的启动时间和 jar 修改时间。日志目录不可写时使用 `log4j2.xml` 支持的 `LOG_HOME`、`MONITOR_LOG_HOME` 指向可写目录。依赖下载失败只能说明构建未完成，不能推断代码编译结果。

## 证据、差异与未知项

历史 `docs/solutions/developer-experience/local-startup-...md` 给出 macOS 示例；Windows 命令需按本机调整。当前代码无法确认公司统一 JDK 发行版、生产 profile 值和外部依赖可用性。来源：根/模块 POM、启动类、`application.yml`、profile 配置、`log4j2.xml`、上述 solution 文档。

面试追问：Spring Boot 多 profile 如何覆盖配置、为什么本地要禁用调度、如何证明运行的是新构建。

