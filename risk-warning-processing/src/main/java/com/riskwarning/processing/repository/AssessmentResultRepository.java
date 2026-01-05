package com.riskwarning.processing.repository;

import com.riskwarning.common.po.assessment.AssessmentResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
@Repository
public interface AssessmentResultRepository extends JpaRepository<AssessmentResult, Long> {

    /**
     * 获取某 project 的最新一条 assessment 结果（按 assessment_date 降序）
     */
    AssessmentResult findTopByProjectIdOrderByAssessmentDateDesc(Long projectId);

    /**
     * 获取所有属于 project 的 assessments（按日期降序）
     */
    List<AssessmentResult> findByProjectIdOrderByAssessmentDateDesc(Long projectId);
}

