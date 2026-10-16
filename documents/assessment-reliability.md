# 评估流程可靠性改造（2026-09-15）

2026-10-15 补充：失败状态处理已合并至本文后半部分，业务逻辑归属 org、processing、report 各自已有 Service。下文前半部分保留原始可靠性设计背景。

本次覆盖最高优先级的任务丢失、失败删除输入、内存派发和数据库/消息交接问题。

## 新流程

1. 第一次调用 initUpload 时，org 在独立事务中创建 UPLOADING 评估和延迟执行的 ASSESSMENT_WATCHDOG；后续文件复用这一批次。响应立即包含 uploadId、assessmentId。
2. 分片、确认、删除请求必须携带 assessmentId，迟到的旧批次请求不能改变新批次状态。确认上传时，将文件元数据快照、taskId、assessmentId 写入 PostgreSQL UPLOAD_INBOX，并在同一事务内将状态改为 TO_BE_ASSESSED。新上传不再经过 Redis 确认队列；原 RPOPLPUSH 交接器保留用于旧任务兼容。
3. 上传工作线程对任务行加锁，同一个事务内保存文件记录、待发送消息和任务 DONE。失败回滚数据库并保留源分片；评估已提前独立提交，不会被合并失败回滚删除。
4. processing、report 的 Kafka 监听器只做持久化接收。落库成功才返回并允许按 RECORD 提交位点；落库失败每五秒重试，不静默跳过。相同命名空间、任务类型和 messageId 去重。
5. 文件合并、行为提取、指标计算和报告汇总第一次异常即停止业务执行并触发失败回调，不再自动重跑三次。输入及堆栈保留；失败状态/通知补写失败仍会持续重试。
6. 下游消息写入 KAFKA_OUTBOX，和业务数据库改动同事务提交。发送线程等待 broker ACK 后才把消息任务标记 DONE；失败每三十秒持续重试。重启会继续处理 READY 记录。

## 启用和重启

- 修改涉及 common、org、processing、report。构建后重启 org、processing、report；运行中的旧 JVM 不会自动获得这套机制。
- 三个服务的 application.yml 已启用 `assessment.reliability.enabled: true`。这些服务现在依赖持久化工作组件，不应单独关闭该开关。
- 首次启动自动创建 `assessment_work` 表及查询索引。PostgreSQL 账号需要对应 schema 的建表权限；初始化失败会阻止启动，不能降级为易丢失的内存执行。初始化使用数据库 advisory lock，避免三个服务同时建表冲突。
- 默认 namespace 为 `${COMPUTERNAME:local}-org/processing/report`。可设置 `ASSESSMENT_WORK_NAMESPACE` 作为三个后缀之前的稳定部署标识。重启、迁移时保持一致，否则原 namespace 下的任务不会被新实例领取。共用 namespace 的实例必须能够访问任务中的文件。
- Redis 仍需配置合适的持久化；这次没有修改远程 Redis 配置，也没有迁移或重置 Kafka 位点。
- 已经被旧版消费者确认且未留下持久记录的历史任务，不能仅靠重启自动找回。

## 查询和恢复

只读检查任务：

```sql
SELECT id, namespace, kind, task_key, state, attempts, last_error,
       created_at, updated_at, available_at
FROM assessment_work
ORDER BY created_at DESC
LIMIT 100;
```

READY 表示待执行、等待重试或正在执行（执行权由事务行锁持有）。DONE 表示该任务已提交；KAFKA_OUTBOX 的 DONE 只表示 broker 已确认，不表示前端已收到。FAILED 表示自动重试已耗尽。

仅对已确认允许重放、且不对应已结束评估的运维任务，可使用以下恢复方式。新版已失败/已完成评估具有终态保护，用户应重新提交评估，不能仅靠重置任务状态重跑：

```sql
UPDATE assessment_work
SET state='READY', attempts=0, last_error=NULL,
    available_at=CURRENT_TIMESTAMP, updated_at=CURRENT_TIMESTAMP
WHERE id='替换为具体任务ID' AND state='FAILED';
```

不要批量重置所有 DONE 记录或删除去重记录。READY 的长任务不能直接以“运行超时”为由复制一份重跑，应先查看 DURABLE_WORK START 及该数据库会话。

主要日志：UPLOAD_HANDOFF PERSISTED → DURABLE_WORK START → KAFKA_OUTBOX PERSISTED（事务内暂存，仍需等提交）→ KAFKA_SEND ACK → 下游 TASK PERSISTED。

## 验证和剩余边界

回归测试使用本地 H2 的 PostgreSQL 模式，以及真实 Hibernate/JPA 事务管理器，验证业务回滚、outbox 回滚、并发领取、重复消息、失败保留和恢复。Kafka broker 故障使用受控失败 future 验证；没有向远程生产库写测试任务，也没有发送真实评估消息。

分片测试覆盖十个以上分片的数字顺序、重试截断旧输出，以及失败不得删除输入。

这是至少执行一次的恢复机制。若 broker 已接收但本地提交前崩溃，outbox 会重发；已接入的 inbox 根据原 messageId 去重。同任务事务失败后的 ES 写入、Spring Batch 元数据和文件操作无法随 PostgreSQL 一起回滚，依然需要下一阶段完成业务幂等性。notification 暂未接入持久化通知/跨实例投递，不能据此宣称前端通知已可靠送达。

长任务执行期间持有任务行锁和数据库连接，Spring Batch 启动会挂起外层事务并独立管理其元数据事务。当前每种任务一个工作线程以限制同时执行数量；提高吞吐前需评估连接池容量和数据库会话超时。

源分片及中间文件暂时保留，以支持失败恢复；需后续增加基于已完成任务的保留期限清理。不要在确认处理结果之前手工清理失败任务的输入。

---

# 评估失败状态与前端接入（2026-10-15 重构）

## 部署

先执行 `documents/migrations/20261014-assessment-failure.sql`，再重新构建并升级 org、processing、report、notification、gateway。由于内部事件增加可选的失败结果字段，这些服务应协调升级，避免旧 report 将失败事件当作完成事件处理。
脚本可重复执行，新增字段、上传任务唯一索引和数据库枚举值 `上传中`。数据库存储中文状态，Java/API 返回 `UPLOADING`、`FAILED` 等英文名称；不要把数据库枚举改成英文。

2026-10-15 的上传监听改造同时更新了前端独立仓库。部署前须先提交枚举迁移，再协调更新 common、org、processing、report；旧服务无法读取新增的上传中状态。

## 状态与错误存储

- `UPLOADING`：首次上传初始化已创建评估，正在上传分片或等待用户确认。
- `TO_BE_ASSESSED`：确认上传成功，等待合并/处理。
- `ASSESSING`：行为分析、指标计算或等待报告汇总。
- `ASSESSED`：报告汇总、完成状态及完成通知 outbox 同事务提交成功。
- `FAILED`：发生业务异常、取消本批全部文件或达到超时期限，失败摘要与通知 outbox 已提交。

上传任务使用 `upload_task_id` 幂等地创建评估，且在文件合并事务开始前独立提交。合并失败或业务回滚不会删除这条评估记录；旧版队列消息会在开始处理时补建评估。已删除项目的旧上传任务只在任务表保留错误。

上传请求通过 FileServiceImpl.observe 发布 Spring 失败事件，由 ProjectServiceImpl.onUploadFailure 同步监听并在独立事务中保存失败状态、异常事件 outbox 和失败通知 outbox。异常堆栈位于 outbox 的 failureDetail；异步工作异常同时保存在 assessment_work.last_error。评估表保存适合前端展示的 failure_code、failure_reason、failure_stage、failed_at。

默认超时为从首次初始化起 1800 秒（上传、确认等待和后续评估共用同一截止时间），可通过 ASSESSMENT_TIMEOUT_SECONDS 配置。超时任务存储在数据库，org 重启后继续执行；已完成、已失败、已删除的评估不会再次更新。服务进程退出不能触发本进程监听器，org/数据库不可用期间也无法保证即时状态更新，服务恢复后超时任务继续兜底。若业务事务仍持有评估行锁，超时处理也需要等待该锁释放。

| failureStage | failureCode | failureReason |
| --- | --- | --- |
| UPLOAD_INIT | UPLOAD_INIT_FAILED | 文件上传初始化失败，请检查文件后重新上传 |
| UPLOAD_CHUNK | UPLOAD_CHUNK_FAILED | 文件分片上传失败，请重新上传 |
| UPLOAD_CONFIRM | UPLOAD_CONFIRM_FAILED | 文件上传确认失败，请检查分片后重新上传 |
| UPLOAD_CANCELLED | UPLOAD_CANCELLED_FAILED | 文件上传已取消，请重新上传后评估 |
| ASSESSMENT_TIMEOUT | ASSESSMENT_TIMEOUT_FAILED | 上传或评估超时，请检查服务后重新评估 |
| FILE_MERGE | FILE_MERGE_FAILED | 上传文件合并失败，请检查文件是否完整后重新上传 |
| BEHAVIOR_PROCESSING | BEHAVIOR_PROCESSING_FAILED | 材料解析或行为分析失败，请检查材料或稍后重新评估 |
| INDICATOR_CALCULATION | INDICATOR_CALCULATION_FAILED | 指标计算失败，请稍后重新评估 |
| REPORT_AGGREGATION | REPORT_AGGREGATION_FAILED | 评估报告汇总失败，请稍后重新评估 |

前端摘要按失败阶段生成，具体根因由后台任务记录和服务日志定位，不把原始异常中的地址、SQL、文件路径等返回前端。

任务表内部状态 `FAILURE_PENDING` 表示业务执行已停止，失败回调等待补写。org/processing 的回调会补写失败事件 outbox；report 的回调会补写失败状态和通知。补写失败会回滚并继续重试，服务重启后可恢复；只有对应回调成功才将该任务置为 `FAILED`。这不是对外的评估状态。

上传、合并及超时失败由 org 已有 ProjectServiceImpl 保存终态及通知；原始异常通过既有 AssessmentCompletedEventMessage outbox 保留追溯。processing 继续通过该事件上报失败，由 report 的 AssessmentServiceImpl 保存终态及通知。failureStage 为空表示计算完成，非空表示失败。report 暂时不可用时，processing 的失败通知会延后，持久任务在其恢复后继续处理；org 的上传失败落库不依赖 report。

org 的上传记录创建放在已有 `ProjectServiceImpl.prepareUploadAssessment`；processing 的开始状态和上报方法放在已有 `BehaviorProcessingService`；查询入口合并到 `ReportController`，旧报告接口和状态接口路径均保持不变。common 只保留实体、共享消息和通用任务基础设施，不再提供评估业务 Service。

失败与完成都是终态。重复/延迟消息不能覆盖已完成或已失败的结果。用户重新上传时创建新的评估记录；不要只将旧失败任务的 state 改成 READY 来重跑，因为终态保护会阻止旧评估重新执行。历史失败记录不会在迁移时自动补造原因。

## 查询接口

```http
GET /api/assessment/123/status
Authorization: Bearer <token>
```

也可以使用已有 report 网关前缀：`GET /api/report/assessment/123/status`。直连 report 服务时路径是 `/assessment/123/status`。
接口检查登录和项目访问权限，沿用项目详情的成员规则：项目成员或所属企业成员可查询。
接口只依赖 PostgreSQL，不调用 Elasticsearch，不读取/解析报告快照。

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "assessmentId": 123,
    "projectId": 10,
    "status": "FAILED",
    "failureCode": "REPORT_AGGREGATION_FAILED",
    "failureReason": "评估报告汇总失败，请稍后重新评估",
    "failureStage": "REPORT_AGGREGATION",
    "failedAt": "2026-10-14T15:30:00"
  }
}
```

查询成功即 `code=200`，评估是否失败由 `data.status` 判断。其余状态的失败字段为空；时间沿用项目已有 `LocalDateTime` 约定。
项目存在但尚无评估记录时，项目最新评估接口返回 `code=200, data=null`，供首次上传使用 ID=0 作为历史基线；排队期间轮询也使用同一约定。项目不存在仍按错误处理。

项目最新评估接口返回的 Assessment 也包含这些字段。按企业名查询最新评估的接口返回统一报告 JSON，失败信息位于 data.overallResult；具体见 [按企业名称查询最新评估结果](org-按企业名称查询最新评估结果-接口说明.md)。initUpload 成功响应为 data: {uploadId, assessmentId}；分片请求增加 assessmentId；confirmUpload、deleteFile 参数增加 assessmentId。确认响应为 data: {assessmentId}，前端直接按此 ID 轮询，不能再等待比初始化记录更大的 ID。
原报告 general 返回对象 `overallResult` 也增加失败字段；失败记录不再为返回概况调用 ES。

## WebSocket

沿用现有通知连接，识别 `notificationType: "ASSESSMENT_FAILED"`。

```json
{
  "type": "notification",
  "notificationType": "ASSESSMENT_FAILED",
  "title": "评估失败通知",
  "content": "评估报告汇总失败，请稍后重新评估",
  "assessmentId": 123,
  "projectId": 10,
  "timestamp": "2026-10-14T15:30:00",
  "extraData": "{\"assessmentId\":123,\"projectId\":10,\"status\":\"FAILED\",\"failureCode\":\"REPORT_AGGREGATION_FAILED\",\"failureReason\":\"评估报告汇总失败，请稍后重新评估\",\"failureStage\":\"REPORT_AGGREGATION\",\"failedAt\":\"2026-10-14T15:30:00\"}"
}
```

`extraData` 沿用 JSON 字符串格式，需要 `JSON.parse`。收到通知后按 assessmentId 更新对应评估，停止加载并显示原因。
页面打开、WebSocket 重连时查询状态；评估期间可以适度轮询，在 `ASSESSED` 或 `FAILED` 时停止。现有 notification 服务不保存离线通知，所以数据库查询是兜底。

## 直接计算接口

`/api/processing/api/behavior/process-project/{projectId}/{assessmentId}` 沿用同步触发方式。
先校验访问权限与评估所属项目；执行异常时，在计算事务回滚后持久化失败事件，由 report 记录失败并发送通知。该入口不自动重试计算，异常后通过 outbox 上报失败；report 异步提交失败终态与通知。已结束的评估返回业务码 409，需新建评估。

## 本地文件上传的队列隔离（2026-10-15）

多个开发环境共享 Redis 时，不能共用上传队列：任务中的 `filePath` 是上传机器的本地绝对路径，其他机器接走任务后无法读取。org 默认队列改为 `file:confirmed:queue:${COMPUTERNAME}`，缺少该环境变量时使用 JVM 的 `user.name`。持久任务命名空间也使用相同的回退方式。可通过 `UPLOAD_CONFIRM_QUEUE_KEY`、`ASSESSMENT_WORK_NAMESPACE` 显式覆盖；只有共享同一文件存储的实例才应共用上传队列和 org 任务命名空间。

项目 359 的分片在本机存在，但原上传任务写在 `local-org`，本机实际运行于 `LAPTOP-G3P5PQ0R-org`，原任务被其他环境接走并因目录不存在而失败。原失败记录应保留；旧版队列任务恢复时在本机隔离队列中重放原任务，沿用 taskId，不需要重新上传，也不应清空共享队列。新版确认上传直接写本机 org 命名空间的 PostgreSQL 任务，不再经 Redis 队列交接。

恢复前必须确认项目仍存在。本次排查后段项目 359 已被删除，无法继续恢复；已撤回本次尝试恢复而加入的单条队列项，原失败任务和分片保留。未将已删除项目自动重建。

分片写盘失败现在回滚 Redis 分片标记并返回错误；确认上传时逐个验证本机分片存在、可读且非空，避免仅凭 Redis 标记接受不完整上传。合并目录不存在时，持久错误中包含具体目录，便于定位生产者和消费者存储不一致。

## 验证范围

本地测试覆盖业务回滚与失败落库、暂时失败后重试成功、补写失败后重启恢复、失败通知 outbox 原子提交、终态保护、上传交接、报告完成时机、访问权限以及不依赖 ES 的失败查询。
测试使用 H2、Mockito 和本地文件，不访问部署中的 PostgreSQL/Kafka/模型服务。实际 PostgreSQL 迁移和端到端消息联调需在部署环境执行。

2026-10-15 上传监听改造验证：67 项针对性后端测试通过；其中 Spring 事件监听与 H2 事务集成测试验证外层回滚后，独立提交的失败状态、完整异常事件和通知仍保留；另覆盖同批多文件复用、旧批次迟到请求隔离、终态保护、延迟任务到期和重启恢复。前端 TypeScript 检查及生产构建通过。当前开发数据库已追加 `上传中` 枚举，并重启本机 org、processing、report；没有向现有项目注入测试失败或重跑真实评估。
