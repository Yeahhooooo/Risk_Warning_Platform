package com.riskwarning.org.repository;

import com.riskwarning.common.po.project.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findByEnterpriseId(Long enterpriseId);
}
