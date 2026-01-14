package com.riskwarning.common;


import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.riskwarning.common.config.TestConsumer;
import com.riskwarning.common.enums.AssessmentStatusEnum;
import com.riskwarning.common.enums.risk.RiskLevelEnum;
import com.riskwarning.common.po.report.Assessment;
import com.riskwarning.common.message.*;
import com.riskwarning.common.utils.RedisUtil;
import com.riskwarning.report.ReportApplication;
import com.riskwarning.report.repository.AssessmentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import javax.sql.DataSource;
import java.time.LocalDateTime;


@SpringBootTest(classes = ReportApplication.class)
public class ConnectivityTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ElasticsearchClient client;

    @Autowired
    private RedisUtil redisUtil;


    @Autowired
    private KafkaTemplate<String, Message> kafkaTemplate;

    @Autowired
    private TestConsumer testConsumer;

//    @Autowired
//    private IndicatorResultRepository indicatorResultRepository;

    @Autowired
    private AssessmentRepository assessmentRepository;

//    @Test
//    public void testDatabaseConnection() {
//        try {
//            // 尝试获取PostgreSQL连接以验证连接
//
//            Assessment assessment = assessmentRepository.findById(88L).get();
//
//            IndicatorResult ir = IndicatorResult.builder()
//                    .projectId(22L)
//                    .assessmentId(1000L)
//                    .indicatorEsId("indicatorEsId")
//                    .indicatorName("test")
//                    .indicatorLevel(0)
//                    .dimension("test")
//                    .type("test")
//                    .calculatedScore(0.0)
//                    .maxPossibleScore(0.0)
//                    .usedCalculationRuleType("auto")
//                    .calculationDetails(null)
//                    .riskTriggered(false)
//                    .riskStatus(IndicatorRiskStatus.fromCode("NOT_EVALUATED"))
//                    .calculatedAt(LocalDateTime.now())
//                    .createdAt(LocalDateTime.now())
//                    .build();
//            IndicatorResult indicatorResult = indicatorResultRepository.save(ir);
//            System.out
//                    .println("Connected to the database and saved IndicatorResult with ID: " + indicatorResult.getId());
//        } catch (Exception e) {
//            e.printStackTrace();
//            assert false : "Failed to connect to the database";
//        }
//    }

    @Test
    public void testAssessmentQuery() {
        try {


            Assessment assessment = Assessment.builder()
                    .projectId(1000L)
                    .assessmentDate(LocalDateTime.now())
                    .overallScore(0.0)
                    .overallRiskLevel(RiskLevelEnum.LOW_RISK)
                    .details("")
                    .recommendations("")
                    .status(AssessmentStatusEnum.ASSESSING)
                    .createdAt(LocalDateTime.now())
                    .build();
            assessmentRepository.saveAndFlush(assessment);
        } catch (Exception e) {
            e.printStackTrace();
            assert false : "Failed to query assessments from the database";
        }
    }

}