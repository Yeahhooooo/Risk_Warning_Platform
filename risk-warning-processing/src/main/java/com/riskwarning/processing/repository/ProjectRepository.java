package com.riskwarning.processing.repository;

import com.riskwarning.common.po.project.Project;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {
}
