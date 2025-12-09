package com.riskwarning.org.repository;


import com.riskwarning.common.po.file.ProjectFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FileRepository extends JpaRepository<ProjectFile, Long> {
}
