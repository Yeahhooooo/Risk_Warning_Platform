package com.riskwarning.org.service;

import com.riskwarning.common.dto.EnterpriseUserResponse;
import com.riskwarning.common.po.enterprise.Enterprise;

import java.util.List;

public interface EnterpriseService {
    Enterprise createEnterprise(Enterprise enterprise);
    void addUserToEnterprise(Long enterpriseId, Long userId, String role);
    List<Enterprise> getAllEnterprises();
    List<EnterpriseUserResponse> getUsersByEnterprise(Long enterpriseId);
}
