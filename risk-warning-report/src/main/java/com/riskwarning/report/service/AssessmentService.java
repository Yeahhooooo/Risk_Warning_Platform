package com.riskwarning.report.service;

public interface AssessmentService {

    public void aggregateInformation(Long userId, Long projectId, Long assessmentId);

    void markAssessmentFailed(Long userId, Long projectId, Long assessmentId, String stage, String detail);

    java.util.Map<String, Object> getAssessmentStatus(Long assessmentId);

}
