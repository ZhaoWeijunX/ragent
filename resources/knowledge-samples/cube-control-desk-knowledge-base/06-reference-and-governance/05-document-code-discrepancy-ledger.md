# 文档与代码差异台账

> 最后核验：2026-08-26。台账记录现状，不直接删除历史资料。

| 主题 | 历史文档/常见表述 | 当前代码证明 | 影响 |
| --- | --- | --- | --- |
| Bill 语义 | Bill 可能被解释为 invoice/OCR 或单一模块 | 当前明确拆分 BL Intake 与 Bill Input | 检索、主表和接口不可混用 |
| VGM | VGM 被当作 BL 详情的一部分 | Intake 有 `vgm_info/vgm_detail`，Input 有 `biz_vgm_*` | 保存、提交和回调边界独立 |
| 联合 VGM | BL 提交前先建 VGM | 当前在船公司成功/强制监听成功后做事实投影 | 预览成功不能生成 VGM |
| Booking/Release | 订舱成功即流程成功 | Release 是独立任务、监听与回调 | 状态和测试必须拆分 |
| Controller-Service-Mapper | 所有模块都走固定三层 | 新模块使用 Manager/Provider/Processor/Handler/Registry | 定位入口不能只搜 Service |
| Manifest | 设计稿描述完整能力 | 当前实现范围需按 Submission/Receipt/Monitor 逐项确认 | 设计存在不等于上线 |
| api-test | 场景目录可作为验证证明 | 只有新鲜执行输出能证明通过 | 文档必须写运行边界 |
| 本地启动 | solution 使用 macOS 命令 | Windows 需要等价端口/进程命令 | 不照抄平台命令 |

## 处理规则

每次发现差异，记录来源、当前符号、影响和迁移建议；若业务期望未知，转入未知项台账。修正文档不能改写历史事实，也不能用推断填空。

当前代码无法确认上述差异是否已在所有外部文档、前端和运维手册同步。来源：当前知识库模块文章、`doc/wiki`、`doc/design`、Controller/实体/状态实现。

