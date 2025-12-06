
package com.riskwarning.org.service;

import com.riskwarning.common.dto.project.ProjectMemberResponse;
import com.riskwarning.common.po.project.Project;

import java.util.List;

public interface ProjectService {
    Project createProject(Project project);
    void addUserToProject(Long projectId, Long userId, String role);
    List<Project> getAllProjects();
    List<ProjectMemberResponse> getProjectMembers(Long projectId);
}
