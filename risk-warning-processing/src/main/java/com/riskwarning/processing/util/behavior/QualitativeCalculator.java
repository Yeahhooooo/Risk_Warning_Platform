package com.riskwarning.processing.util.behavior;

public final class QualitativeCalculator {

    private QualitativeCalculator() {}

    /**
     * 根据法规方向和行为状态计算定性得分
     *
     * @param regulationDirection 法规方向（禁止、必须、可选）
     * @param behaviorStatus 行为状态（进行中、已完成、暂停、终止）
     * @return 0.0-1.0 之间的得分
     */
    public static double computeQualitativeScore(String regulationDirection, String behaviorStatus) {
        if (regulationDirection == null || behaviorStatus == null) {
            return 0.5; // 默认中等分数
        }

        // 标准化法规方向
        String normDirection = normalizeDirection(regulationDirection);
        // 标准化行为状态
        String normStatus = normalizeStatus(behaviorStatus);

        // 矩阵映射：根据法规方向和行为状态决定得分
        // 法规方向：禁止 - 行为不应发生，发生了就是违规
        // 法规方向：必须 - 行为必须发生且完成，未完成就是违规
        // 法规方向：可选 - 行为可做可不做，影响较小

        switch (normDirection) {
            case "PROHIBITED": // 禁止
                return computeForProhibited(normStatus);
            case "REQUIRED": // 必须
                return computeForRequired(normStatus);
            case "OPTIONAL": // 可选
                return computeForOptional(normStatus);
            default:
                return 0.5; // 未知方向，返回中等分数
        }
    }

    /**
     * 禁止类法规的计算逻辑
     * 行为越接近完成，违规程度越高，得分越低
     */
    private static double computeForProhibited(String status) {
        switch (status) {
            case "COMPLETED": // 已完成 - 严重违规
                return 0.0;
            case "IN_PROGRESS": // 进行中 - 违规中
                return 0.2;
            case "PAUSED": // 暂停 - 轻度违规
                return 0.5;
            case "TERMINATED": // 终止 - 符合禁止要求
                return 1.0;
            default:
                return 0.5;
        }
    }

    /**
     * 必须类法规的计算逻辑
     * 行为越接近完成，合规程度越高，得分越高
     */
    private static double computeForRequired(String status) {
        switch (status) {
            case "COMPLETED": // 已完成 - 完全合规
                return 1.0;
            case "IN_PROGRESS": // 进行中 - 部分合规
                return 0.7;
            case "PAUSED": // 暂停 - 轻度违规
                return 0.3;
            case "TERMINATED": // 终止 - 严重违规
                return 0.0;
            default:
                return 0.5;
        }
    }

    /**
     * 可选类法规的计算逻辑
     * 影响较小，做了加分，不做也不扣太多分
     */
    private static double computeForOptional(String status) {
        switch (status) {
            case "COMPLETED": // 已完成 - 良好
                return 0.9;
            case "IN_PROGRESS": // 进行中 - 较好
                return 0.7;
            case "PAUSED": // 暂停 - 一般
                return 0.6;
            case "TERMINATED": // 终止 - 稍差
                return 0.5;
            default:
                return 0.6;
        }
    }

    /**
     * 标准化法规方向
     */
    private static String normalizeDirection(String direction) {
        if (direction == null) return "UNKNOWN";
        String lower = direction.toLowerCase().trim();
        if (lower.contains("禁止") || lower.equals("prohibited")) {
            return "PROHIBITED";
        } else if (lower.contains("必须") || lower.equals("required") || lower.equals("mandatory")||lower.equals("必要")) {
            return "REQUIRED";
        } else if (lower.contains("可选") || lower.equals("optional") || lower.equals("recommended")) {
            return "OPTIONAL";
        }
        return "UNKNOWN";
    }

    /**
     * 标准化行为状态
     */
    private static String normalizeStatus(String status) {
        if (status == null) return "UNKNOWN";
        String lower = status.toLowerCase().trim();
        if (lower.contains("已完成") || lower.equals("completed")) {
            return "COMPLETED";
        } else if (lower.contains("进行中") || lower.equals("processing")) {
            return "IN_PROGRESS";
        } else if (lower.contains("暂停") || lower.equals("paused")) {
            return "PAUSED";
        } else if (lower.contains("终止") || lower.equals("terminated")) {
            return "TERMINATED";
        }
        return "UNKNOWN";
    }
}