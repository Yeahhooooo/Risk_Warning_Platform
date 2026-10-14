# 评估结果百分制输出

评分方向和风险判定规则保持不变，只统一报告输出与新评估总分的分值尺度。

- 指标分：`calculatedScore / maxPossibleScore * 100`。
- 总分、维度分、分数区间分：`sum(calculatedScore) / sum(maxPossibleScore) * 100`，保留原满分权重，保留两位小数。
- 高分代表低风险。单指标原有分界：40 分及以上低风险，20 分及以上且低于 40 分中风险，低于 20 分高风险。
- 原有低于满分 50% 触发风险的规则，以及总体按风险数量计算等级的规则保持不变。因此总体风险等级并不是直接按总体百分制分数划分。
- 缺失、非有限数值或非正满分的指标不参与分数汇总；没有有效指标时分数为 `null`，不是满分。新评估没有有效评分时汇总失败，不标记为已评估。有效指标得分限制在 0–100 内。

## 前端取值

以下路径为 controller 实际映射，网关外部前缀沿用现有部署。

| 接口 | 字段 | 含义 |
|---|---|---|
| `GET /report/report/general/{assessmentId}` | `overallResult.overallScore` | 总体百分制得分 |
| `GET /report/report/indicatorResult/{assessmentId}` | `totalScore` | 总体百分制得分 |
| 同上 | `dimensionDistributions[维度].totalScore` | 对应维度百分制得分 |
| 同上 | `indicatorScores` | 所有指标的名称、维度、百分制得分、原有等级、风险触发状态 |
| 同上 | `dimensionDistributions[维度].indicatorScores` | 对应维度的指标明细 |
| `GET /report/report/risk?assessmentId=...` | `score`、`maxScore` | 关联指标汇总分与满分 100 |
| 同上 | `relatedIndicators[].score`、`maxScore` | 行为证据百分制分与满分 100 |

`score`、`totalScore`、`overallScore` 是 JSON 数字，例如 `77.27`；前端显示“77.27 分”，不要再乘 100。
`startScoreRatio`、`endScoreRatio`、`ratio` 仍是 0–1 的区间边界和数量占比，不是分数。
没有指标的维度不虚构分数；区间没有有效指标时 `totalScore` 为 `null`。

## 历史数据与部署

原始指标及行为证据数据不改写。报告查询根据原始指标重新计算百分制分数，避免旧 JSON 缓存的累加分覆盖新结果；已存在风险等级保持原值。
新评估将总体百分制得分持久化，并将同样的得分用于完成通知。历史通知和数据库中既有总分不批量回写。
本次修改位于 report 模块，构建并重启 report 后生效。前端已有总分和维度分字段名称不变，指标列表与风险主分数为新增字段。
