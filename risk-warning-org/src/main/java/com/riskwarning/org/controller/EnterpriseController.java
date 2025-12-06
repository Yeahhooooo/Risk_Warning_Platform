package com.riskwarning.org.controller;

import com.riskwarning.common.annotation.AuthRequired;
import com.riskwarning.common.dto.enterprise.EnterpriseUserResponse;
import com.riskwarning.common.po.enterprise.Enterprise;
import com.riskwarning.common.dto.AddMemberRequest;
import com.riskwarning.common.dto.enterprise.EnterpriseCreateRequest;
import com.riskwarning.common.result.Result;
import com.riskwarning.org.service.EnterpriseService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/enterprise")

public class EnterpriseController {

    private final EnterpriseService enterpriseService;

    public EnterpriseController(EnterpriseService enterpriseService) {
        this.enterpriseService = enterpriseService;
    }

    @PostMapping
    @AuthRequired
    public Result<Enterprise> createEnterprise(@RequestBody EnterpriseCreateRequest req) {
        Enterprise e = new Enterprise();
        e.setName(req.getName());
        e.setCreditCode(req.getCreditCode());
        e.setType(req.getType());

        e.setIndustry(req.getIndustry());
        e.setBusinessScope(req.getBusinessScope());
        e.setRegisteredCapital(req.getRegisteredCapital());
        e.setEstablishmentDate(req.getEstablishmentDate());
        e.setLegalRepresentative(req.getLegalRepresentative());
        e.setRegisteredAddress(req.getRegisteredAddress());
        e.setBusinessStatus(req.getBusinessStatus());

        Enterprise saved = enterpriseService.createEnterprise(e);
        return Result.success(saved);
    }

    @PostMapping("/{enterpriseId}/members")

    public Result<Void> addMember(@PathVariable Long enterpriseId, @RequestBody AddMemberRequest req) {
        enterpriseService.addUserToEnterprise(enterpriseId, req.getUserId(), req.getRole());
        return Result.success();
    }

    @GetMapping
    public Result<List<Enterprise>> getAllEnterprises() {
        // This method would call a service method to retrieve all enterprises
        // For demonstration, returning an empty list
        return Result.success(enterpriseService.getAllEnterprises());
    }

    @GetMapping("/{enterpriseId}/members")
    public Result<List<EnterpriseUserResponse>> getEnterpriseMembers(@PathVariable Long enterpriseId) {
        // This method would call a service method to retrieve members of the enterprise
        // For demonstration, returning an empty list
        List<EnterpriseUserResponse> users = enterpriseService.getUsersByEnterprise(enterpriseId);
        return Result.success(users);
    }


}