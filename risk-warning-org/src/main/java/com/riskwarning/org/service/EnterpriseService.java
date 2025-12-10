package com.riskwarning.org.service;

import com.riskwarning.common.dto.enterprise.EnterpriseUserResponse;
import com.riskwarning.common.po.enterprise.Enterprise;

import java.util.List;


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
}
