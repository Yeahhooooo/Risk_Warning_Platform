package com.riskwarning.processing.util.behavior;


import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class VectorUtils {

    private VectorUtils() {}

    /**
     * 计算两向量的余弦相似度，若任意向量为空则返回 0.0
     */
    public static double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null) {
            System.out.println("[CosineSim DEBUG] One or both vectors are null: a=" + (a == null) + ", b=" + (b == null));
            return 0.0;
        }

        if (a.length == 0 || b.length == 0) {
            System.out.println("[CosineSim DEBUG] One or both vectors are empty: a.length=" + a.length + ", b.length=" + b.length);
            return 0.0;
        }

        int n = Math.min(a.length, b.length);
        double dot = 0.0, na = 0.0, nb = 0.0;
        for (int i = 0; i < n; i++) {
            double va = a[i];
            double vb = b[i];
            dot += va * vb;
            na += va * va;
            nb += vb * vb;
        }

        double normA = Math.sqrt(na);
        double normB = Math.sqrt(nb);
        double denom = normA * normB;

        double similarity = denom == 0.0 ? 0.0 : dot / denom;

        // 详细调试信息
        System.out.println(String.format("[CosineSim DEBUG] vecA.length=%d, vecB.length=%d, n=%d, dot=%.6f, normA=%.6f, normB=%.6f, denom=%.6f, similarity=%.6f",
                a.length, b.length, n, dot, normA, normB, denom, similarity));

        if (denom == 0.0) {
            System.out.println("[CosineSim WARNING] Denominator is 0! At least one vector has zero norm (all zeros)");
        }

        return similarity;
    }

    /**
     * 将值限制到 [0,1]
     */
    public static double clamp01(double v) {
        if (Double.isNaN(v)) return 0.0;
        return Math.max(0.0, Math.min(1.0, v));
    }
}