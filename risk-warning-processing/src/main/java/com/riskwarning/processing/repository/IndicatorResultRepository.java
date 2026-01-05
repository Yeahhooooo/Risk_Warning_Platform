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
     * 根据 assessmentId 和 indicatorEsId 查找记录
     */
    Optional<IndicatorResult> findByAssessmentIdAndIndicatorEsId(Long assessmentId, String indicatorEsId);

    /**
     * 统计指定 assessmentId 的记录数
     */
    long countByAssessmentId(Long assessmentId);

    /*
    * 根据indicatorEsI查找记录
    * */
    Optional<IndicatorResult> findByIndicatorEsId(String indicatorEsId);

}
