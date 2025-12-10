package com.riskwarning.org.repository;

import com.riskwarning.common.po.enterprise.Enterprise;
import com.riskwarning.common.po.enterprise.EnterpriseUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EnterpriseRepository extends JpaRepository<Enterprise, Long> {
    Optional<Enterprise> findByCreditCode(String creditCode);

}
