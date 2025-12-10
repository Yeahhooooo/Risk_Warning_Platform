package com.riskwarning.org.repository;


import com.riskwarning.common.po.project.ProjectMember;
import com.riskwarning.common.po.project.ProjectMemberId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface ProjectMemberRepository extends JpaRepository<ProjectMember, ProjectMemberId> {
}
