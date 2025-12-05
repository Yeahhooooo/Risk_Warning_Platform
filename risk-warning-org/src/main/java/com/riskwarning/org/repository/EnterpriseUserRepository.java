package com.riskwarning.org.repository;

import com.riskwarning.common.po.enterprise.EnterpriseUser;
import com.riskwarning.common.po.enterprise.EnterpriseUserId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EnterpriseUserRepository extends JpaRepository<EnterpriseUser, EnterpriseUserId> {
    List<EnterpriseUser> findByEnterprise_Id(Long enterpriseId);
}