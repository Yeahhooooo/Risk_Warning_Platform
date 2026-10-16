# 按企业名称查询最新评估报告

请求地址保持不变：

```http
GET /api/org/enterprise/{name}/assessment
```

直连 org 时为 `GET http://localhost:8095/enterprise/{name}/assessment`。仍允许匿名 GET，企业名称精确匹配；拼接路径时用 `encodeURIComponent(name)` 编码。

## 最新记录的范围

org 根据企业名取得其全部项目，再按评估 `id` 降序取第一条。待评估、评估中、成功和失败都参与排序；最新评估失败时返回该次失败，不回退到更早的成功结果。企业没有项目或评估记录时仍返回 `{"code":200,"message":"success","data":null}`。企业不存在沿用原来的参数错误处理。

## 返回格式

返回内容与报告概况接口 `GET /api/report/report/report/general/{assessmentId}` 的 `Result<AssessmentDetailVO>` JSON 一致。org 复用 report 当前的组装逻辑，不复制评分、风险统计或维度转换代码。

外层只有一层 `code / message / data`；`data` 不再是数据库 Assessment 记录，不再返回 `details` JSON 字符串，评估 ID 改为 `data.assessmentId`。

成功示例（为便于阅读，维度只展示一项，其余维度同结构）：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "projectId": 20,
    "assessmentId": 81,
    "assessmentDate": "2026-10-15T14:30:00",
    "overallResult": {
      "overallScore": 85.5,
      "overallRiskLevel": "LOW_RISK",
      "status": "ASSESSED",
      "failureCode": null,
      "failureReason": null,
      "failureStage": null,
      "failedAt": null
    },
    "riskSummary": {"totalRisks": 3, "highRiskCount": 1, "mediumRiskCount": 1, "lowRiskCount": 1},
    "indicatorOverview": {"behaviorIndicators": 3, "questionnaireIndicators": null, "riskTriggeredIndicators": 3, "safeIndicators": 17},
    "dimensionRiskDistribution": {
      "企业关联方风险": {"dimension": null, "riskCount": 3, "lowRiskCount": 1, "mediumRiskCount": 1, "highRiskCount": 1}
    }
  }
}
```

失败示例：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "projectId": 20,
    "assessmentId": 81,
    "assessmentDate": null,
    "overallResult": {
      "overallScore": null,
      "overallRiskLevel": null,
      "status": "FAILED",
      "failureCode": "INDICATOR_CALCULATION_FAILED",
      "failureReason": "指标计算失败，请稍后重新评估",
      "failureStage": "INDICATOR_CALCULATION",
      "failedAt": "2026-10-15T14:30:00"
    },
    "riskSummary": null,
    "indicatorOverview": null,
    "dimensionRiskDistribution": null
  }
}
```

`TO_BE_ASSESSED` / `ASSESSING` 同样返回上述报告骨架和当前 `overallResult.status`，失败字段为空，统计块为空，不会查询 ES 或展示未完成的指标数据。成功与失败的返回都以数据库记录为准；失败状态由 report 处理持久失败事件后写入，存在正常的异步传播时间。

## 前端展示与异常追溯

- `code == 200` 表示查询成功；评估是否失败看 `data.overallResult.status`。
- `FAILED` 时展示 `failureReason`，用 `failureStage` 定位阶段，`failedAt` 定位时间。
- `assessmentId` 与 `projectId` 可用于关联任务表及后台日志，`failureCode` 用于前端分支判断。
- 不返回原始异常堆栈或内部服务地址；后台保留的详细异常仍按 [评估流程可靠性与失败处理](assessment-reliability.md) 查询。
- 无评估记录时先判断 `data == null`，再读取嵌套字段。

## 服务调用与部署

接口地址和匿名访问方式不变。org 仍负责企业和最新记录查询，再通过 Nacos/Feign 调用 report 已有 `/report/report/general/{assessmentId}`，取出其 `data` 后返回，避免重复封装 Result。

客户端定义复用 `EnterpriseService` 文件内的嵌套 `ReportClient`，没有新增客户端或公共业务类文件。连接超时为 3 秒，读取超时为 10 秒。

本次需要重新构建并重启 org、report；无数据库表结构变更。report 必须可被 org 通过服务发现访问。

若 report 网络调用失败，返回业务码 503，提示中带评估 ID；报告服务返回的业务错误继续传递。此类查询异常不会将原评估改成 FAILED，也不会返回旧成功报告或伪造评估结果。
