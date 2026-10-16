package com.riskwarning.org.service;

import com.riskwarning.common.dto.enterprise.EnterpriseUserResponse;
import com.riskwarning.common.po.enterprise.Enterprise;
import com.riskwarning.common.dto.enterprise.EnterpriseUpdateRequest;
import com.riskwarning.common.po.report.Assessment;

import java.util.List;
import java.util.Map;
import com.riskwarning.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;


/**
 * 企业服务接口
 */
public interface EnterpriseService {

    /**
     * 创建企业
     *
     * @param enterprise 企业信息
     * @return 创建的企业
     */
    Enterprise createEnterprise(Enterprise enterprise);


    /**
     * 将用户添加到企业
     *
     * @param enterpriseId 企业ID
     * @param userId 用户ID
     * @param role 用户角色
     */
    void addUserToEnterprise(Long enterpriseId, Long userId, String role);

    /**
     * 获取所有企业
     *
     * @return 企业列表
     */


    List<Enterprise> getAllEnterprises();

    /**
     * 根据企业ID获取用户列表
     *
     * @param enterpriseId 企业ID
     * @return 用户列表
     */
    List<EnterpriseUserResponse> getUsersByEnterprise(Long enterpriseId);

    /**
     * 根据企业名称查询最近一次的评估结果
     *
     * @param name 企业名称
     * @return 最近一次评估结果，若企业不存在或没有评估记录则返回 null
     */
    Assessment getLatestAssessmentByEnterpriseName(String name);

    /** Same general report payload as the assessment-ID report endpoint, including failure fields. */
    Map<String, Object> getLatestAssessmentReportByEnterpriseName(String name);

    /** Kept beside its only caller's contract to avoid adding a separate client file. */
    @FeignClient(name = "risk-warning-report", contextId = "enterpriseAssessmentReport")
    interface ReportClient {
        @GetMapping("/report/report/general/{assessmentId}")
        Result<Map<String, Object>> general(@PathVariable("assessmentId") Long assessmentId);
    }

    Enterprise getEnterprise(Long id);

    Enterprise updateEnterprise(Long id, EnterpriseUpdateRequest request);

    void deleteEnterprise(Long id);
}
