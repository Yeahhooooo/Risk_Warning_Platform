# 恢复真实评估流程

## 修复内容

- 删除运行代码中的模拟完成处理器和模拟开关。上传工作项 `UPLOAD_INBOX` 始终由真实执行器认领，创建待评估记录后发送 `behavior_processing_tasks`。
- 修复合并文件路径缺少目录分隔符的问题，文件实际保存到对应项目目录。
- 企业名称查询先查企业及其项目，再取最新创建的评估；项目查询校验项目存在并取最新评估，避免多次评估时单条查询失败。
- 报告接口使用请求中的评估 ID 查询数据库，指标结果来自 PostgreSQL，风险来自 ES；不存在的记录返回错误。
- ES 查询失败时返回错误，避免显示成“没有风险”。保留真实报告快照和现有百分制评分。
- 已写入数据库的模拟快照（`assessmentDetailVO.assessmentId` 和 `projectId` 均为 `900001`）会提示“历史模拟评估，请上传原始文件重新评估”，不再当作真实结论返回。
- 固定数据、旧模拟执行器及其测试已移至 `documents/legacy-simulation/`，仅作历史归档，不进入 Maven 编译及应用 classpath。

## 正常流程

确认上传 → Redis 队列 → org 持久工作项 → 合并原始文件、创建待评估 → Kafka 行为处理任务 → processing 文本提取、分类、向量化、指标计算 → 评估完成事件 → report 风险汇总及报告 → notification → WebSocket。

## 历史数据与生效方式

本次修改没有连接或修改业务数据库，也没有重启正在运行的服务。旧模拟分数没有真实计算依据，无法转换为真实结果；必须提交原始文件创建新评估。新评估通过上述完整流程产生分数和风险。

重新构建并重启 org、report（以及使用 common 模块的相关服务）后代码生效。旧环境变量或 Nacos 中的 `org.assessment.simulate.*` 配置已没有执行入口。确保 processing、report、notification、模型服务及基础设施可用，上传队列、持久任务 namespace、Kafka 消费环境仍需使用本部署的配置。

## 回归验证

相关测试覆盖：上传文件合并及真实处理消息、待评估状态及空初始分数、Redis 持久交接、企业与项目范围查询、三个报告入口按 ID 取数、不存在的评估报错、历史模拟快照拒绝返回，以及原有百分制评分、维度与行为证据。

2026-10-12 验证结果：common 17 项、org 14 项、report 13 项，共 44 项本地测试通过。修正文件目录拼接后，真实上传执行测试单独复测通过。

全项目 `mvn -DskipTests package` 构建通过，8 个模块均成功生成构建产物。

本地测试命令（排除依赖外部数据库和服务的 ConnectivityTest）：

```powershell
mvn -pl risk-warning-org,risk-warning-report -am test '-Dtest=*Test,!ConnectivityTest' '-Dsurefire.failIfNoSpecifiedTests=false'
```

尚未执行实际上传到全部在线服务的端到端验证。

## 2026-10-14 同步前验证

全模块 `mvn package '-Dtest=*Test,!ConnectivityTest,!BatchTest,!ESSearchTest' '-Dsurefire.failIfNoSpecifiedTests=false'` 成功：8 个模块完成打包，74 项测试通过，1 项需显式启用的在线 ES 只读检查跳过。依赖在线基础设施的 ConnectivityTest、BatchTest 和 ESSearchTest 未执行。

已检查 common、org 的 JAR，不包含 FixedAssessmentData、SimulatedAssessmentCompletionTask 或 fixed-assessment-details。部署环境建议执行 `mvn clean package -DskipTests` 后更新并重启相关服务，避免增量构建残留旧模拟 class。Git 同步不会自动部署或重启在线服务，线上端到端评估仍需部署后验证。
