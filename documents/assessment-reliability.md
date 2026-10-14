# 评估流程可靠性改造（2026-09-15）

本次覆盖最高优先级的任务丢失、失败删除输入、内存派发和数据库/消息交接问题。

## 新流程

1. org 确认上传时，把文件元数据快照和 taskId 一起写入原 Redis 队列。
2. 后台使用 RPOPLPUSH 原子移到 `<queue-key>:processing`，再写入 PostgreSQL `assessment_work`。数据库成功后才移除 Redis 原始字节；数据库失败或进程退出时，下次从 processing 列表继续。旧版 Java 序列化数据也按原始字节确认。
3. 上传工作线程对任务行加锁，同一个事务内保存文件记录、Assessment、待发送消息和任务 DONE。失败回滚数据库并保留源分片；不会在 finally 删除输入。旧队列任务若元数据已过期，无法凭空恢复文件信息，会保留失败记录。
4. processing、report 的 Kafka 监听器只做持久化接收。落库成功才返回并允许按 RECORD 提交位点；落库失败每五秒重试，不静默跳过。相同命名空间、任务类型和 messageId 去重。
5. 后台从数据库执行行为提取、指标计算和报告汇总。正常失败每三十秒重试，累计三次后标记任务 FAILED，并保留输入和错误。processing 尝试把评估业务状态同步为 FAILED。
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

修复具体故障后，只对确认需要恢复的失败任务执行：

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
