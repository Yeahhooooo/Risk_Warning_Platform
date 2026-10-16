
package com.riskwarning.org.service;

import com.riskwarning.common.dto.project.ProjectMemberResponse;
import com.riskwarning.common.po.project.Project;
import com.riskwarning.common.dto.project.ProjectUpdateRequest;
import com.riskwarning.common.po.report.Assessment;

import java.util.List;
import com.riskwarning.org.entity.dto.UploadConfirmDto;
import com.riskwarning.common.message.AssessmentCompletedEventMessage;


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

    /**
     * 根据项目ID查询评估记录
     *
     * @param projectId 项目ID
     * @return 最新评估记录；项目存在但尚未评估时返回 null
     */
    Assessment getAssessmentByProjectId(Long projectId);

    Long prepareUploadAssessment(Long projectId, String taskId);

    Assessment beginUploadAssessment(Long projectId);

    Assessment requireUploadingAssessment(Long projectId);

    void confirmUploadAssessment(UploadConfirmDto task);

    void onUploadFailure(AssessmentCompletedEventMessage event);

    Project getProject(Long id);

    Project updateProject(Long id, ProjectUpdateRequest request);

    void deleteProject(Long id);

    /** Delete all child projects atomically after enterprise-admin authorization. */
    void deleteProjectsByEnterprise(Long enterpriseId);
}
