package com.riskwarning.report.util;

import com.riskwarning.common.po.indicator.IndicatorResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Convert raw indicator scores at the report boundary, retaining their original maximum weights. */
public final class AssessmentScores {
    private AssessmentScores() { }

    public static Double ratio(IndicatorResult result) {
        Double score = result.getCalculatedScore(), maximum = result.getMaxPossibleScore();
        if (score == null || maximum == null || !Double.isFinite(score) || !Double.isFinite(maximum) || maximum <= 0) return null;
        return Math.max(0, Math.min(1, score / maximum));
    }

    public static Double percentage(IndicatorResult result) {
        Double ratio = ratio(result);
        return ratio == null ? null : round(ratio * 100);
    }

    public static Double percentage(List<IndicatorResult> results) {
        double earned = 0, maximum = 0;
        for (IndicatorResult result : results) {
            Double ratio = ratio(result);
            if (ratio != null) {
                earned += ratio * result.getMaxPossibleScore();
                maximum += result.getMaxPossibleScore();
            }
        }
        return maximum > 0 ? round(earned / maximum * 100) : null;
    }

    public static double round(double score) {
        return BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
