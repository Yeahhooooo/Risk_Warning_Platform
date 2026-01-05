package com.riskwarning.processing.util.behavior;

/**
 * 定量计算器
 * 根据法规定量指标和行为定量数据计算得分
 * 
 * 计算公式：(1 - 偏差值/法规要求值)
 * - 符合要求（偏差<=0）: 得分 = 1.0
 * - 偏差超过100%（偏差率>1）: 得分 = 0.0
 * - 其他情况: 得分 = 1 - 偏差率
 */
public final class QuantitativeCalculator {

    private QuantitativeCalculator() {}

    /**
     * 根据法规定量指标和行为定量数据计算得分
     *
     * @param regulationQuantitativeIndicator 法规要求值（法规指标）
     * @param behaviorQuantitativeData 行为实际数据
     * @param regulationDirection 法规方向（禁止、必须、可选）
     * @param behaviorStatus 行为状态（进行中、已完成、暂停、终止）
     * @return 0.0-1.0 之间的得分
     */
    public static double computeQuantitativeScore(
            Double regulationQuantitativeIndicator,
            Double behaviorQuantitativeData,
            String regulationDirection,
            String behaviorStatus) {

        // 1. 参数校验
        if (regulationQuantitativeIndicator == null || behaviorQuantitativeData == null) {
            // 如果缺少定量数据，返回默认中等分数
            return 0.5;
        }

        // 2. 避免除零错误
        if (regulationQuantitativeIndicator == 0.0) {
            // 如果法规要求值为0，则行为数据也应为0才符合要求
            return behaviorQuantitativeData == 0.0 ? 1.0 : 0.0;
        }

        // 3. 计算偏差值和偏差率
        double deviation = Math.abs(regulationQuantitativeIndicator - behaviorQuantitativeData);
        double deviationRate = deviation / Math.abs(regulationQuantitativeIndicator);

        // 4. 基础得分计算
        double baseScore;
        if (deviationRate <= 0.0) {
            // 符合要求或完全符合（理论上偏差率不会<0，这里作为保护）
            baseScore = 1.0;
        } else if (deviationRate > 1.0) {
            // 偏差超过100%，严重违规
            baseScore = 0.0;
        } else {
            // 正常范围：1 - 偏差率
            baseScore = 1.0 - deviationRate;
        }

        // 5. 根据法规方向和行为状态调整得分
        double adjustedScore = adjustByDirectionAndStatus(
                baseScore,
                regulationDirection,
                behaviorStatus,
                deviationRate
        );

        // 6. 确保得分在 0.0-1.0 范围内
        return Math.max(0.0, Math.min(1.0, adjustedScore));
    }

    /**
     * 根据法规方向和行为状态调整得分
     * 
     * @param baseScore 基础得分（基于偏差率计算）
     * @param regulationDirection 法规方向
     * @param behaviorStatus 行为状态
     * @param deviationRate 偏差率
     * @return 调整后的得分
     */
    private static double adjustByDirectionAndStatus(
            double baseScore,
            String regulationDirection,
            String behaviorStatus,
            double deviationRate) {

        // 标准化输入
        String normDirection = normalizeDirection(regulationDirection);
        String normStatus = normalizeStatus(behaviorStatus);

        // 根据法规方向调整
        switch (normDirection) {
            case "PROHIBITED": // 禁止
                // 禁止类：实际值越接近标准值，违规越严重
                // 如果行为发生了（有数值），且接近标准值，说明违规严重
                // 偏差率越小，说明越接近禁止的标准，违规越严重
                if (deviationRate < 0.1) {
                    // 非常接近禁止的标准值，严重违规
                    return baseScore * 0.3;
                } else if (deviationRate < 0.3) {
                    // 较接近，违规
                    return baseScore * 0.5;
                } else {
                    // 远离禁止的标准值，相对较好
                    return baseScore * 0.8;
                }

            case "REQUIRED": // 必须
                // 必须类：实际值越接近标准值，合规越好
                // baseScore 已经正确反映了合规程度，但可以根据行为状态微调
                return adjustByStatusForRequired(baseScore, normStatus);

            case "OPTIONAL": // 可选
                // 可选类：影响较小，得分可以稍微提高
                return Math.min(1.0, baseScore * 1.1);

            default:
                return baseScore;
        }
    }

    /**
     * 对于"必须"类法规，根据行为状态调整得分
     */
    private static double adjustByStatusForRequired(double baseScore, String status) {
        switch (status) {
            case "COMPLETED": // 已完成 - 如果已完成且接近标准值，应该高分
                return baseScore;
            case "IN_PROGRESS": // 进行中 - 进行中但接近标准值，给予一定鼓励
                return Math.min(1.0, baseScore * 1.05);
            case "PAUSED": // 暂停 - 暂停状态，降低得分
                return baseScore * 0.9;
            case "TERMINATED": // 终止 - 终止状态，严重违规
                return baseScore * 0.5;
            default:
                return baseScore;
        }
    }

    /**
     * 兜底计算方案（当没有匹配的法规时使用）
     * 计算公式：(1 - 偏差值/兜底指标)
     *
     * @param fallbackIndicator 兜底指标值
     * @param behaviorQuantitativeData 行为实际数据
     * @return 0.0-1.0 之间的得分
     */
    public static double computeFallbackScore(
            Double fallbackIndicator,
            Double behaviorQuantitativeData) {

        if (fallbackIndicator == null || behaviorQuantitativeData == null) {
            return 0.5;
        }

        if (fallbackIndicator == 0.0) {
            return behaviorQuantitativeData == 0.0 ? 1.0 : 0.0;
        }

        double deviation = Math.abs(fallbackIndicator - behaviorQuantitativeData);
        double deviationRate = deviation / Math.abs(fallbackIndicator);

        if (deviationRate <= 0.0) {
            return 1.0;
        } else if (deviationRate > 1.0) {
            return 0.0;
        } else {
            return 1.0 - deviationRate;
        }
    }

    /**
     * 标准化法规方向
     * （复用 QualitativeCalculator 的逻辑，保持一致性）
     */
    private static String normalizeDirection(String direction) {
        if (direction == null) return "UNKNOWN";
        String lower = direction.toLowerCase().trim();
        if (lower.contains("禁止") || lower.equals("prohibited")) {
            return "PROHIBITED";
        } else if (lower.contains("必须") || lower.equals("required") || lower.equals("mandatory") || lower.equals("必要")) {
            return "REQUIRED";
        } else if (lower.contains("可选") || lower.equals("optional") || lower.equals("recommended")) {
            return "OPTIONAL";
        }
        return "UNKNOWN";
    }

    /**
     * 标准化行为状态
     * （复用 QualitativeCalculator 的逻辑，保持一致性）
     */
    private static String normalizeStatus(String status) {
        if (status == null) return "UNKNOWN";
        String lower = status.toLowerCase().trim();
        if (lower.contains("已完成") || lower.equals("completed")) {
            return "COMPLETED";
        } else if (lower.contains("进行中") || lower.equals("processing") || lower.equals("in_progress")) {
            return "IN_PROGRESS";
        } else if (lower.contains("暂停") || lower.equals("paused")) {
            return "PAUSED";
        } else if (lower.contains("终止") || lower.equals("terminated")) {
            return "TERMINATED";
        }
        return "UNKNOWN";
    }
}

