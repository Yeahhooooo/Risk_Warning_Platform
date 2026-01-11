package com.riskwarning.report.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.riskwarning.common.config.ElasticSearchConfig;
import com.riskwarning.common.enums.RiskDimensionEnum;
import com.riskwarning.common.enums.RiskLevelEnum;
import com.riskwarning.common.exception.BusinessException;
import com.riskwarning.common.po.indicator.IndicatorResult;
import com.riskwarning.common.po.report.Assessment;
import com.riskwarning.common.po.risk.Risk;
import com.riskwarning.report.entity.vo.general.*;
import com.riskwarning.report.entity.vo.indicator.IndicatorDistributionVO;
import com.riskwarning.report.entity.vo.risk.RiskVO;
import com.riskwarning.report.repository.AssessmentRepository;
import com.riskwarning.report.repository.IndicatorResultRepository;
import com.riskwarning.report.service.ReportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

@Service
@Slf4j
public class ReportServiceImpl implements ReportService {

    @Autowired
    private AssessmentRepository assessmentRepository;

    @Autowired
    private IndicatorResultRepository indicatorResultRepository;

    @Autowired
    private ElasticsearchClient esClient;

    @Override
    public IndicatorDistributionVO assembleIndicatorResult(Long assessmentId) {

        Assessment assessment = assessmentRepository.findById(assessmentId).orElse(null);

        if(assessment == null) {
            throw new BusinessException("Assessment not found");
        }
        // TODO: 检查assessment的detail属性是否已经存在indicatorDistribution信息，若存在则直接反序列化返回，避免重复计算
        // TODO: 目前写在内存中进行聚合，之后可能考虑使用数据库进行聚合计算
        List<IndicatorResult> indicatorResults = indicatorResultRepository.findByAssessmentId(assessmentId);

        IndicatorDistributionVO indicatorDistributionVO = new IndicatorDistributionVO();
        indicatorDistributionVO.setAssessmentId(assessmentId);
        indicatorDistributionVO.setRiskDimensionEnum(null);
        indicatorDistributionVO.setTotalScore(assessment.getOverallScore());
        indicatorDistributionVO.setTotalCount(indicatorResults.size());
        indicatorDistributionVO.setAssessmentTime(assessment.getAssessmentDate());
        indicatorDistributionVO.setScoreDistributions(new ArrayList<>());
        indicatorDistributionVO.setDimensionDistributions(new HashMap<>());

        for(IndicatorResult indicatorResult : indicatorResults) {

        }

        return indicatorDistributionVO;
    }

    @Override
    public List<RiskVO> assembleRisk(Long assessmentId, String dimension, String riskLevel) {
        List<Risk> riskVOs = fetchRisksFromES(assessmentId, dimension, riskLevel);
        List<RiskVO> riskVOList = new ArrayList<>();
        for(Risk risk : riskVOs) {
            RiskVO riskVO = new RiskVO();
            BeanUtils.copyProperties(risk, riskVO);
            riskVOList.add(riskVO);
        }
        return riskVOList;
    }

    @Override
    public AssessmentDetailVO assembleGeneral(Long assessmentId) {
        Assessment assessment = assessmentRepository.findById(assessmentId).orElse(null);
        if(assessment == null) {
            throw new BusinessException("Assessment not found");
        }

        // TODO: 检查assessment的detail属性是否已经存在general信息，若存在则直接反序列化返回，避免重复计算

        AssessmentDetailVO assessmentDetailVO = new AssessmentDetailVO();
        assessmentDetailVO.setProjectId(assessment.getProjectId());
        assessmentDetailVO.setAssessmentId(assessmentId);
        assessmentDetailVO.setAssessmentDate(assessment.getAssessmentDate());
        assessmentDetailVO.setOverallResult(OverallResult.builder()
                .overallScore(assessment.getOverallScore())
                .build()
        );
        assessmentDetailVO.setRiskSummary(RiskSummary.builder()
                .build()
        );
        assessmentDetailVO.setIndicatorOverview(IndicatorOverview.builder()

                .build()
        );
        assessmentDetailVO.setDimensionRiskDistribution(new HashMap<>());
        for(RiskDimensionEnum riskDimensionEnum : RiskDimensionEnum.values()) {
            assessmentDetailVO.getDimensionRiskDistribution().put(riskDimensionEnum, new DimensionRiskDistribution());
        }
        List<Risk> risks = fetchRisksFromES(assessmentId, null, null);
        for(Risk risk : risks) {
            RiskDimensionEnum dimensionEnum = RiskDimensionEnum.fromValue(risk.getDimension());
            RiskLevelEnum riskLevelEnum = RiskLevelEnum.fromValue(risk.getRiskLevel());
            if(dimensionEnum != null && riskLevelEnum != null) {
                DimensionRiskDistribution distribution = assessmentDetailVO.getDimensionRiskDistribution().get(dimensionEnum);
                distribution.setRiskCount(distribution.getRiskCount() + 1);
                switch (riskLevelEnum) {
                    case LOW_RISK:
                        distribution.setLowRiskCount(distribution.getLowRiskCount() + 1);
                        break;
                    case MEDIUM_RISK:
                        distribution.setMediumRiskCount(distribution.getMediumRiskCount() + 1);
                        break;
                    case HIGH_RISK:
                        distribution.setHighRiskCount(distribution.getHighRiskCount() + 1);
                        break;
                    default:
                        log.error("riskLevelEnum error, unexpected value: {}", riskLevelEnum);
                }
            } else {
                log.warn("Unknown risk dimension: {} or risk level: {}", risk.getDimension(), risk.getRiskLevel());
            }
        }


        return assessmentDetailVO;
    }


    private List<Risk> fetchRisksFromES(Long assessmentId, String dimension, String riskLevel) {
        try {
            log.info("[Fetching Risks] assessmentId={}, dimension={}, riskLevel={}",
                    assessmentId, dimension, riskLevel);

            SearchResponse<Risk> resp = esClient.search(s -> s
                            .index(ElasticSearchConfig.RISK_INDEX)
                            .size(1000)  // 假设一个项目不会超过1000个behaviors，如需要可以改成scroll
                            .query(q -> q
                                    .term(t -> t
                                            .field("assessment_id")
                                            .value(assessmentId)
                                            .field("dimension")
                                            .value(dimension)
                                            .field("risk_level")
                                            .value(riskLevel)
                                    )
                            )
                            .sort(sort -> sort
                                    .field(f -> f
                                            .field("create_at")
                                            .order(SortOrder.Desc)
                                    )
                            ),
                    Risk.class
            );

            List<Risk> risks = new ArrayList<>();
            if (resp != null && resp.hits() != null && resp.hits().hits() != null) {
                for (Hit<Risk> hit : resp.hits().hits()) {
                    Risk risk = hit.source();
                    if (risk != null) {
                        risks.add(risk);
                        log.debug("[Risk Fetched] assessmentId={}, riskId={}", assessmentId, risk.getId());
                    }
                }
            }

            log.info("[Fetching Risks Completed] assessmentId={}, totalRisks={}",
                    assessmentId, risks.size());
            return risks;
        } catch (Exception e) {
            log.error("[Fetching Risks Failed] assessmentId={}", assessmentId, e);
            return Collections.emptyList();
        }
    }

    // TODO: 根据某个指标结果查询相关风险，风险对应法规的详细信息接口
}
