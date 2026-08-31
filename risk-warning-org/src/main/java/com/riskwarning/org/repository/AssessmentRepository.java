package com.riskwarning.org.repository;

import com.riskwarning.common.po.report.Assessment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;

@Repository
public interface AssessmentRepository extends JpaRepository<Assessment, Integer> {

    Assessment findByProjectId(long l);

    Assessment findById(long l);

    /**
     * 在指定项目ID集合中，按评估日期降序取最近一条评估记录
     */
    Optional<Assessment> findFirstByProjectIdInOrderByAssessmentDateDesc(Collection<Long> projectIds);
}
