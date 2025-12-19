package com.riskwarning.processing.repository;

import com.riskwarning.common.po.indicator.IndicatorResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 指标结果持久化接口
 */
@Repository
public interface IndicatorResultRepository extends JpaRepository<IndicatorResult, Long> {

    /**
     * 获取按 assessmentId 排序最新的一条记录（用于推算下一个 assessmentId）
     */
    IndicatorResult findTopByOrderByAssessmentIdDesc();

    /**
     * 根据 assessmentId 和 indicatorEsId 查找记录
     */
    Optional<IndicatorResult> findByAssessmentIdAndIndicatorEsId(Long assessmentId, String indicatorEsId);

}
