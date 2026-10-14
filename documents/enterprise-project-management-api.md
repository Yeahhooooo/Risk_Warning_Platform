# 企业、项目修改与删除

当前仓库包含后端服务，没有前端页面源码。本次已添加可供前端直接调用的详情、修改、删除接口。接口直接位于原有 `EnterpriseController`、`ProjectController`；服务方法位于原有 `EnterpriseService`、`ProjectService` 及其 `ServiceImpl` 实现。单独的 `OrganizationManagementController` 和 `OrganizationManagementService` 已移除。

## 接口与权限

通过网关访问时前缀为 `/api/org`，直接访问 org 服务时省略此前缀。所有新接口需要 `Authorization` 登录令牌，沿用系统现有的 `AuthRequired` 鉴权。

| 方法 | 地址（网关） | 功能 | 权限 |
| --- | --- | --- | --- |
| GET | `/api/org/enterprise/{enterpriseId}` | 获取企业详情、回填编辑表单 | 企业成员 |
| PUT / PATCH | `/api/org/enterprise/{enterpriseId}` | 修改企业 | 企业管理员 |
| DELETE | `/api/org/enterprise/{enterpriseId}` | 删除企业及其项目 | 企业管理员 |
| GET | `/api/org/project/{projectId}` | 获取项目详情、回填编辑表单 | 项目成员或所属企业成员 |
| PUT / PATCH | `/api/org/project/{projectId}` | 修改项目 | 项目管理员或所属企业管理员 |
| DELETE | `/api/org/project/{projectId}` | 删除项目及其关联数据 | 项目管理员或所属企业管理员 |

成功响应沿用 `Result`：详情、修改返回 `data` 为最新实体，删除返回无数据的成功结果。权限不足、资源不存在、校验失败、项目仍在评估等情况返回系统现有的业务错误结构，前端必须检查业务成功标志，不只检查 HTTP 状态。

## 修改规则

PUT 与 PATCH 都采用部分修改语义：未提交或值为 `null` 的字段保留原值；可选文本字段提交 `""` 可以清空。企业、项目名称和统一社会信用代码不能为纯空白。信用代码不能与其他企业重复，注册资本不能为负，成立日期不能晚于今天。项目计划完成日期不能早于开始日期；日期校验会同时检查本次提交值和保留的旧值。

企业可修改字段：`name`、`creditCode`、`type`、`industry`、`businessScope`、`registeredCapital`、`establishmentDate`、`legalRepresentative`、`registeredAddress`、`businessStatus`。

项目可修改字段：`name`、`type`、`description`、`startDate`、`plannedCompletionDate`、`industry`、`region`、`orientedUser`。枚举编码沿用创建接口。`id`、`enterpriseId`、创建时间、评估状态、实际完成时间不可通过修改接口重写，避免项目换属或绕过真实评估流程。

企业修改示例：

```http
PATCH /api/org/enterprise/7
Authorization: <现有登录令牌>
Content-Type: application/json

{"name":"企业新名称","registeredAddress":"新的注册地址"}
```

项目修改示例：

```http
PATCH /api/org/project/18
Authorization: <现有登录令牌>
Content-Type: application/json

{"name":"项目新名称","description":"更新后的项目说明","plannedCompletionDate":"2026-12-31"}
```

## 删除行为

- 删除企业时，先锁定企业和全部项目，确认所有项目均可删除后再开始清理。任一项目有待评估、评估中或持久待处理任务时，整个操作失败，其他项目也不会被部分删除。
- 在数据库事务内删除项目的指标结果、评估、文件记录、成员关系及项目本身；企业删除还删除企业成员关系和企业记录。`t_event` 按现有 `schema.sql` 的项目外键级联删除；共享指标知识库、法规和用户账号保留。
- 数据库事务同时写入 `PROJECT_RESOURCE_CLEANUP` 持久任务。数据库提交后，后台按 `project_id` 清理 `t_behavior`、`t_risk`，清理项目 Redis 缓存及当前 org 进程工作目录下 `storage/{temp,persist,internal,processing}/{projectId}`。ES 查询超时、存在失败项、版本冲突、Redis 或文件清理失败会持续重试，不会静默标记完成。
- 删除接口成功代表数据库删除及清理任务持久化成功，ES 和本地文件清理为异步最终完成。可检查 `assessment_work` 中清理任务的 `state`、`attempts`、`last_error`。部署在其他机器上的 processing 文件需由该机器的文件保留策略清理；此接口不会删除其他主机上的目录。
- 已删除项目无法再初始化、确认上传；删除前进入 Redis 的上传任务在执行前再次检查项目，不会重建已删除的项目。
- 项目配置修改也会拒绝正在评估或仍有待处理任务的项目，保证计算使用的配置稳定。历史已完成报告保留原来的评估依据，修改元数据不会自动重算历史报告。

前端接入建议：详情接口回填表单，保存调用 PATCH，成功后刷新列表；删除前展示企业级联项目及评估记录的影响，用户确认后调用 DELETE，业务成功后移除列表项。

重新构建、重启 org 服务后生效；保持既有 `assessment.reliability.enabled=true`，以运行持久清理任务。没有执行任何真实企业或项目的删除操作。

## 验证范围

2026-10-12：common 17 项和 org 30 项本地测试通过，共 47 项。覆盖管理员与普通成员权限、部分修改、信用代码去重、非法字段值与日期、活动评估和持久任务拦截、企业删除前检查全部项目、关联记录清理及持久任务提交、ES 按项目 ID 删除与失败重试、临时目录文件清理及其他项目保留、删除后旧上传任务拒绝执行，以及接口请求映射。

`mvn -pl risk-warning-org -am package -DskipTests` 打包通过。

没有连接真实业务数据库执行删除，也未进行实际在线服务的端到端验证。
