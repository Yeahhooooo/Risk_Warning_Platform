package com.riskwarning.processing.util.behavior;

import java.util.List;

public final class VectorUtils {

    private VectorUtils() {}

    /**
     * 计算两向量的余弦相似度，若任意向量为空则返回 0.0
     */
    public static double cosineSimilarity(List<Float> a, List<Float> b) {
        if (a == null || b == null) {
            return 0.0;
        }

        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }

        int n = Math.min(a.size(), b.size());
        double dot = 0.0, na = 0.0, nb = 0.0;
        for (int i = 0; i < n; i++) {
            double va = a.get(i);
            double vb = b.get(i);
            dot += va * vb;
            na += va * va;
            nb += vb * vb;
        }

        double normA = Math.sqrt(na);
        double normB = Math.sqrt(nb);
        double denom = normA * normB;

        return denom == 0.0 ? 0.0 : dot / denom;
    }

    /**
     * 将值限制到 [0,1]
     */
    public static double clamp01(double v) {
        if (Double.isNaN(v)) return 0.0;
        return Math.max(0.0, Math.min(1.0, v));
    }
}
