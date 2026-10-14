# 指标关联行为

`GET /report/report/indicatorResult/{assessmentId}` 返回的 `indicatorScores` 中，每个指标新增 `relatedBehaviors`，类型为字符串数组，仅包含行为描述：

```json
{
  "indicatorId": "indicator-1",
  "indicatorName": "劳动合同签订情况",
  "dimension": "劳动法律风险",
  "relatedBehaviors": ["已与全部员工签订劳动合同", "定期核查劳动合同续签情况"]
}
```

同样适用于 `dimensionDistributions` 下各维度的 `indicatorScores`。其余已有字段保持原有含义。

- 描述来自 `calculation_details.relatedIndicators[].relatedBehaviors[].description`，不额外查询 ES。
- 去除重复描述和空描述；没有可用详情时返回 `[]`。
- 数组不返回行为 ID、项目 ID、法规或其他元数据。
- 实际参与评分的行为均被记录，包括未匹配法规、采用兜底评分的行为。

数据库使用已有的 `t_indicator_result.matched_behaviors_ids TEXT[]` 列，实体属性为 `matchedBehaviorIds`。新增指标时记录关联行为 ES ID，更新指标时合并已有 ID 和计算详情中的 ID，过滤空值并去重。计算详情内部新增 `behaviorId` 用于保留行为身份。

历史记录若已有计算详情，可直接返回描述；此前未保存的行为 ID 不进行猜测或自动回填。历史记录没有详情时返回空数组。

部署时需要重新构建并启动 processing 和 report 服务（均依赖修改后的 common 模块）。已有数据库列无需迁移。
