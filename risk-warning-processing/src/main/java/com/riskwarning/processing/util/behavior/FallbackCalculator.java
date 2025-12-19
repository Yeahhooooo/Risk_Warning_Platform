package com.riskwarning.processing.util.behavior;

public final class FallbackCalculator {

    private FallbackCalculator() {}

    public static double qualitativeFallback(String status) {
        if (status == null) return 0.5;
        switch (status) {
            case "已完成":
            case "completed": return 1.0;
            case "进行中":
            case "in_progress": return 0.8;
            case "暂停":
            case "paused": return 0.3;
            case "终止":
            case "terminated": return 0.0;
            default: return 0.5;
        }
    }

    public static double clamp01(double v) {
        if (Double.isNaN(v)) return 0.0;
        return Math.max(0.0, Math.min(1.0, v));
    }
}
