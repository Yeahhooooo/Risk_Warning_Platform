package com.riskwarning.processing.util.behavior;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SimilarityCalculator {

    private SimilarityCalculator() {}

    // 默认权重：行为 -> (法规/指标)
    public static final double DEFAULT_A_BEHAVIOR_TO_X = 0.65;
    public static final double DEFAULT_B_BEHAVIOR_TO_X = 0.35;

    // 默认权重：法规 -> 指标
    public static final double DEFAULT_A_REG_TO_IND = 0.65;
    public static final double DEFAULT_B_REG_TO_IND = 0.25;
    public static final double DEFAULT_GAMMA_REG_TO_IND = 0.10;

    /**
     * 公式：score = a * cos(vecB, vecX) + b * Jaccard(tagsB, tagsX)
     * 用于：行为 -> {法规, 指标} 的相似度计算
     */
    public static double scoreBehaviorToTarget(float[] behaviorVec,
                                               float[ ] targetVec,
                                               List<String> behaviorTags,
                                               List<String> targetTags,
                                               double a,
                                               double b) {
        System.out.println("[BehaviorToTarget DEBUG] Starting calculation...");
        System.out.println("[BehaviorToTarget DEBUG] behaviorVec: " + (behaviorVec == null ? "null" : "length=" + behaviorVec.length));
        System.out.println("[BehaviorToTarget DEBUG] targetVec: " + (targetVec == null ? "null" : "length=" + targetVec.length));
        System.out.println("[BehaviorToTarget DEBUG] behaviorTags: " + behaviorTags);
        System.out.println("[BehaviorToTarget DEBUG] targetTags: " + targetTags);
        System.out.println("[BehaviorToTarget DEBUG] weights: a=" + a + ", b=" + b);

        double cos = VectorUtils.cosineSimilarity(behaviorVec, targetVec);
        double j = jaccard(behaviorTags, targetTags);
        double score = a * cos + b * j;
        double clampedScore = VectorUtils.clamp01(score);

        System.out.println(String.format("[BehaviorToTarget DEBUG] cos=%.6f, jaccard=%.6f, rawScore=%.6f (%.2f*%.6f + %.2f*%.6f), clampedScore=%.6f",
                cos, j, score, a, cos, b, j, clampedScore));

        return clampedScore;
    }

    public static double scoreBehaviorToTargetDefault(float[] behaviorVec,
                                                      float[] targetVec,
                                                      List<String> behaviorTags,
                                                      List<String> targetTags) {
        return scoreBehaviorToTarget(behaviorVec, targetVec, behaviorTags, targetTags,
                DEFAULT_A_BEHAVIOR_TO_X, DEFAULT_B_BEHAVIOR_TO_X);
    }

    /**
     * 公式：score = a * cos(vecR, vecI) + b * Jaccard(tagsR, tagsI) + gamma * Jaccard(industryR, industryI)
     * 用于：法规 -> 指标 的相似度计算
     */
    public static double scoreRegToIndicator(float[] regVec,
                                             float[] indVec,
                                             List<String> regTags,
                                             List<String> indTags,
                                             List<String> regIndustry,
                                             List<String> indIndustry,
                                             double a,
                                             double b,
                                             double gamma) {
        System.out.println("[RegToIndicator DEBUG] Starting calculation...");
        System.out.println("[RegToIndicator DEBUG] regVec: " + (regVec == null ? "null" : "length=" + regVec.length));
        System.out.println("[RegToIndicator DEBUG] indVec: " + (indVec == null ? "null" : "length=" + indVec.length));
        System.out.println("[RegToIndicator DEBUG] regTags: " + regTags);
        System.out.println("[RegToIndicator DEBUG] indTags: " + indTags);
        System.out.println("[RegToIndicator DEBUG] regIndustry: " + regIndustry);
        System.out.println("[RegToIndicator DEBUG] indIndustry: " + indIndustry);
        System.out.println("[RegToIndicator DEBUG] weights: a=" + a + ", b=" + b + ", gamma=" + gamma);

        double cos = VectorUtils.cosineSimilarity(regVec, indVec);
        double jTags = jaccard(regTags, indTags);
        double jIndustry = jaccard(regIndustry, indIndustry);
        double score = a * cos + b * jTags + gamma * jIndustry;
        double clampedScore = VectorUtils.clamp01(score);

        System.out.println(String.format("[RegToIndicator DEBUG] cos=%.6f, jaccardTags=%.6f, jaccardIndustry=%.6f, rawScore=%.6f (%.2f*%.6f + %.2f*%.6f + %.2f*%.6f), clampedScore=%.6f",
                cos, jTags, jIndustry, score, a, cos, b, jTags, gamma, jIndustry, clampedScore));

        return clampedScore;
    }

    public static double scoreRegToIndicatorDefault(float[] regVec,
                                                    float[] indVec,
                                                    List<String> regTags,
                                                    List<String> indTags,
                                                    List<String> regIndustry,
                                                    List<String> indIndustry) {
        return scoreRegToIndicator(regVec, indVec, regTags, indTags, regIndustry, indIndustry,
                DEFAULT_A_REG_TO_IND, DEFAULT_B_REG_TO_IND, DEFAULT_GAMMA_REG_TO_IND);
    }

    /**
     * Jaccard 相似度（集合交集 / 并集），忽略大小写与空字符串
     */
    public static double jaccard(List<String> a, List<String> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
            System.out.println("[Jaccard DEBUG] One or both lists are null/empty: a=" + a + ", b=" + b);
            return 0.0;
        }
        Set<String> sa = normalizeSet(a);
        Set<String> sb = normalizeSet(b);
        if (sa.isEmpty() || sb.isEmpty()) {
            System.out.println("[Jaccard DEBUG] Normalized sets are empty: sa=" + sa + ", sb=" + sb);
            return 0.0;
        }
        Set<String> inter = new HashSet<>(sa);
        inter.retainAll(sb);
        Set<String> uni = new HashSet<>(sa);
        uni.addAll(sb);
        double similarity = uni.isEmpty() ? 0.0 : (double) inter.size() / (double) uni.size();

        System.out.println(String.format("[Jaccard DEBUG] sa=%s, sb=%s, intersection=%s, union=%s, similarity=%.6f",
                sa, sb, inter, uni, similarity));

        return similarity;
    }

    private static Set<String> normalizeSet(List<String> list) {
        if (list == null || list.isEmpty()) return Collections.emptySet();
        Set<String> set = new HashSet<>();
        for (String s : list) {
            if (s == null) continue;
            String t = s.trim().toLowerCase();
            if (!t.isEmpty()) set.add(t);
        }
        return set;
    }
}