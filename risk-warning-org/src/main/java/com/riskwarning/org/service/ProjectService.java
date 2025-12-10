
package com.riskwarning.org.service;

import com.riskwarning.common.dto.project.ProjectMemberResponse;
import com.riskwarning.common.po.project.Project;

import java.util.List;


/**
 * 项目服务接口
 */

public interface ProjectService {

    /**
     * 创建项目
     *
     * @param project 项目信息
     * @return 创建的项目
     */
    Project createProject(Project project);

    /**
     * 将用户添加到项目
     *
     * @param projectId 项目ID
     * @param userId 用户ID
     * @param role 用户角色
     */
    void addUserToProject(Long projectId, Long userId, String role);


    /**
     * 获取所有项目
     *
     * @return 项目列表
     */
    List<Project> getAllProjects();


    /**
     * 根据项目ID获取项目成员列表
     *
     * @param projectId 项目ID
     * @return 项目成员列表
     */
    List<ProjectMemberResponse> getProjectMembers(Long projectId);
}
