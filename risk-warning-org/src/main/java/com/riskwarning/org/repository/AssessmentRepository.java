package com.riskwarning.org.repository;

import com.riskwarning.common.po.report.Assessment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;

@Repository
public interface AssessmentRepository extends JpaRepository<Assessment, Long> {

    Optional<Assessment> findFirstByProjectIdOrderByIdDesc(Long projectId);

    /**
     * 在指定项目ID集合中，取评估ID最大的一条记录作为最新评估
     */
    Optional<Assessment> findFirstByProjectIdInOrderByIdDesc(Collection<Long> projectIds);
}
