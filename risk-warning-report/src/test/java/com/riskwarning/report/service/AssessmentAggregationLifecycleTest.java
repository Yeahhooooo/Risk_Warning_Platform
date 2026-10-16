package com.riskwarning.report.service;

import com.alibaba.fastjson2.JSON;
import com.riskwarning.common.enums.AssessmentStatusEnum;
import com.riskwarning.common.message.*;
import com.riskwarning.common.po.indicator.IndicatorResult;
import com.riskwarning.common.po.project.Project;
import com.riskwarning.common.po.report.Assessment;
import com.riskwarning.common.utils.KafkaUtils;
import com.riskwarning.report.repository.*;
import com.riskwarning.report.service.impl.AssessmentServiceImpl;
import com.riskwarning.report.task.MessageTask;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import com.riskwarning.common.reliability.DurableWorkStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.support.TransactionTemplate;
import javax.persistence.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssessmentAggregationLifecycleTest {
    private final AssessmentServiceImpl service = new AssessmentServiceImpl();
    private final EntityManager em = mock(EntityManager.class);
    private final IndicatorResultRepository indicators = mock(IndicatorResultRepository.class);
    private final AssessmentRepository assessments = mock(AssessmentRepository.class);
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final KafkaUtils kafka = mock(KafkaUtils.class);
    private final Assessment row = Assessment.builder().id(12L).projectId(10L).status(AssessmentStatusEnum.ASSESSING).build();

    @BeforeEach void setup() {
        ReflectionTestUtils.setField(service, "em", em);
        ReflectionTestUtils.setField(service, "indicatorResultRepository", indicators);
        ReflectionTestUtils.setField(service, "assessmentRepository", assessments);
        ReflectionTestUtils.setField(service, "projectRepository", projects);
        ReflectionTestUtils.setField(service, "reportService", mock(ReportService.class));
        ReflectionTestUtils.setField(service, "kafkaUtils", kafka);
        when(em.find(Assessment.class, 12L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(row);
    }

    @Test void successfulAggregationCompletesAndNotifies() {
        IndicatorResult indicator = new IndicatorResult();
        indicator.setCalculatedScore(100.0); indicator.setMaxPossibleScore(100.0);
        when(indicators.findByAssessmentId(12L)).thenReturn(Collections.singletonList(indicator));
        when(projects.findById(10L)).thenReturn(Optional.of(Project.builder().id(10L).build()));
        service.aggregateInformation(8L, 10L, 12L);
        assertEquals(AssessmentStatusEnum.ASSESSED, row.getStatus());
        assertNotNull(row.getAssessmentDate());
        verify(assessments).saveAndFlush(row);
        ArgumentCaptor<NotificationMessage> notification = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(kafka).sendMessage(notification.capture());
        assertEquals(NotificationMessage.NotificationType.ASSESSMENT_COMPLETED, notification.getValue().getNotificationType());
    }

    @Test void lateCompletionCannotOverwriteFailureOrResendSuccess() {
        for (AssessmentStatusEnum status : Arrays.asList(AssessmentStatusEnum.FAILED, AssessmentStatusEnum.ASSESSED)) {
            row.setStatus(status);
            service.aggregateInformation(8L, 10L, 12L);
            assertEquals(status, row.getStatus());
        }
        verifyNoInteractions(indicators, assessments, kafka);
    }

    @Test void reportExhaustionPersistsFailureWithAssessmentIdentity() {
        AssessmentService lifecycle = mock(AssessmentService.class);
        MessageTask handler = new MessageTask();
        ReflectionTestUtils.setField(handler, "assessmentService", lifecycle);
        Throwable failure = new RuntimeException("ES unavailable");
        handler.onExhausted(JSON.toJSONString(new AssessmentCompletedEventMessage("event", "now", "trace", 8L, 10L, 12L)), failure);
        verify(lifecycle).markAssessmentFailed(8L, 10L, 12L, "REPORT_AGGREGATION", failure.toString());
    }

    @Test void upstreamFailureUsesExistingReportInboxAndDoesNotAggregate() {
        AssessmentService assessments = mock(AssessmentService.class);
        MessageTask handler = new MessageTask();
        ReflectionTestUtils.setField(handler, "assessmentService", assessments);
        AssessmentCompletedEventMessage event = new AssessmentCompletedEventMessage("failed", "now", "trace", 8L, 10L, 12L);
        event.setFailureStage("BEHAVIOR_PROCESSING");
        event.setFailureDetail("model unavailable");
        handler.execute(JSON.toJSONString(event));
        verify(assessments).markAssessmentFailed(8L, 10L, 12L, "BEHAVIOR_PROCESSING", "model unavailable");
        verify(assessments, never()).aggregateInformation(any(), any(), any());
        handler.onExhausted(JSON.toJSONString(event), new RuntimeException("persistence unavailable"));
        verify(assessments, times(2)).markAssessmentFailed(8L, 10L, 12L, "BEHAVIOR_PROCESSING", "model unavailable");
    }

    @Test void statusChecksProjectMembershipAndOnlyReturnsPublicFailureSummary() {
        com.riskwarning.common.po.user.User user = new com.riskwarning.common.po.user.User();
        user.setId(8L);
        com.riskwarning.common.context.UserContext.setUser(user);
        when(em.find(Assessment.class, 12L)).thenReturn(row);
        when(em.find(Project.class, 10L)).thenReturn(Project.builder().id(10L).enterpriseId(2L).build());
        try {
            assertEquals(403, assertThrows(com.riskwarning.common.exception.BusinessException.class,
                    () -> service.getAssessmentStatus(12L)).getCode());
            when(em.find(com.riskwarning.common.po.project.ProjectMember.class,
                    new com.riskwarning.common.po.project.ProjectMemberId(10L, 8L)))
                    .thenReturn(new com.riskwarning.common.po.project.ProjectMember());
            row.setStatus(AssessmentStatusEnum.FAILED);
            row.setFailureReason("评估报告汇总失败，请稍后重新评估");
            Map<String, Object> status = service.getAssessmentStatus(12L);
            assertEquals("FAILED", status.get("status"));
            assertEquals(row.getFailureReason(), status.get("failureReason"));
            assertFalse(status.containsKey("failureDetail"));
            verifyNoInteractions(indicators, projects, kafka);
            assertEquals(404, assertThrows(com.riskwarning.common.exception.BusinessException.class,
                    () -> service.getAssessmentStatus(99L)).getCode());
        } finally { com.riskwarning.common.context.UserContext.clear(); }
        assertEquals(401, assertThrows(com.riskwarning.common.exception.BusinessException.class,
                () -> service.getAssessmentStatus(12L)).getCode());
    }

    @Test void failureGeneralDoesNotDependOnElasticsearch() {
        row.setStatus(AssessmentStatusEnum.FAILED);
        row.setFailureCode("REPORT_AGGREGATION_FAILED");
        com.riskwarning.report.entity.vo.general.OverallResult result =
                new com.riskwarning.report.service.impl.ReportServiceImpl().assembleGeneral(row).getOverallResult();
        assertEquals(AssessmentStatusEnum.FAILED, result.getStatus());
        assertEquals(row.getFailureCode(), result.getFailureCode());
    }

    @Test void pendingReportsHaveTheSameShapeWithoutFetchingIncompleteData() {
        for (AssessmentStatusEnum status : new AssessmentStatusEnum[]{AssessmentStatusEnum.TO_BE_ASSESSED, AssessmentStatusEnum.ASSESSING}) {
            row.setStatus(status);
            com.riskwarning.report.entity.vo.general.AssessmentDetailVO result =
                    new com.riskwarning.report.service.impl.ReportServiceImpl().assembleGeneral(row);
            assertEquals(12L, result.getAssessmentId());
            assertEquals(10L, result.getProjectId());
            assertEquals(status, result.getOverallResult().getStatus());
            assertNull(result.getOverallResult().getFailureReason());
            assertNull(result.getRiskSummary());
            assertNull(result.getDimensionRiskDistribution());
        }
    }

    @Test void failureSurvivesJpaRollbackAndCommitsWithOutboxExactlyOnce() {
        DriverManagerDataSource ds = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE DOMAIN assessment_status_enum AS VARCHAR(32)");
        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(ds);
        factory.setPackagesToScan("com.riskwarning.common.po.report");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Collections.singletonMap("hibernate.hbm2ddl.auto", "create-drop"));
        factory.afterPropertiesSet();
        try {
            EntityManagerFactory emf = factory.getObject();
            EntityManager em = SharedEntityManagerCreator.createSharedEntityManager(emf);
            TransactionTemplate tx = new TransactionTemplate(new JpaTransactionManager(emf));
            DurableWorkStore store = new DurableWorkStore(jdbc, tx, "failure-test");
            store.initialize();
            KafkaUtils kafka = new KafkaUtils();
            ReflectionTestUtils.setField(kafka, "durableWorkStore", store);
            AssessmentServiceImpl lifecycle = new AssessmentServiceImpl();
            ReflectionTestUtils.setField(lifecycle, "em", em);
            ReflectionTestUtils.setField(lifecycle, "kafkaUtils", kafka);
            Long id = tx.execute(s -> {
                Assessment row = Assessment.builder().projectId(10L).status(AssessmentStatusEnum.ASSESSING).build();
                em.persist(row); em.flush(); return row.getId();
            });
            store.enqueue("REPORT_INBOX", "report", "payload");
            store.runOne("REPORT_INBOX", p -> {
                em.find(Assessment.class, id).setOverallScore(99.0);
                em.flush();
                throw new IllegalStateException("private host: password=secret");
            }, 1, (p, failure) -> lifecycle.markAssessmentFailed(8L, 10L, id, "REPORT_AGGREGATION", failure.toString()));
            tx.execute(s -> {
                Assessment row = em.find(Assessment.class, id);
                assertEquals(AssessmentStatusEnum.FAILED, row.getStatus());
                assertNull(row.getOverallScore(), "failed business write must roll back");
                assertEquals("REPORT_AGGREGATION_FAILED", row.getFailureCode());
                assertNotNull(row.getFailedAt());
                assertFalse(row.getFailureReason().contains("secret"));
                lifecycle.markAssessmentFailed(8L, 10L, id, "FILE_MERGE", "late failure");
                assertEquals("REPORT_AGGREGATION", row.getFailureStage(), "late failure must not replace first failure");
                return null;
            });
            assertEquals("FAILED", jdbc.queryForObject("select state from assessment_work where kind='REPORT_INBOX'", String.class));
            assertEquals(1, jdbc.queryForObject("select count(*) from assessment_work where kind='KAFKA_OUTBOX'", Integer.class));
            String notification = jdbc.queryForObject("select payload from assessment_work where kind='KAFKA_OUTBOX'", String.class);
            assertTrue(notification.contains("ASSESSMENT_FAILED"));
            assertFalse(notification.contains("secret"));
            assertTrue(jdbc.queryForObject("select last_error from assessment_work where kind='REPORT_INBOX'", String.class).contains("secret"));
        } finally { factory.destroy(); }
    }
}
