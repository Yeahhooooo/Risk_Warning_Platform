package com.riskwarning.processing.repository;

import com.riskwarning.common.po.report.Assessment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AssessmentRepository extends JpaRepository<Assessment, Integer> {

    public Optional<Assessment> findById(Long id);

    Assessment findByProjectId(Long projectId);
}
