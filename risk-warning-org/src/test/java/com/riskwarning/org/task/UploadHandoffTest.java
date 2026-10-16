package com.riskwarning.org.task;

import com.alibaba.fastjson2.JSON;
import com.riskwarning.common.reliability.DurableWorkStore;
import com.riskwarning.common.utils.*;
import com.riskwarning.org.entity.dto.*;
import com.riskwarning.org.repository.*;
import org.junit.jupiter.api.Test;
import java.util.Collections;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UploadHandoffTest {
    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    @org.springframework.transaction.annotation.EnableTransactionManagement
    static class ListenerTransactions { }

    @Test void springListenerCommitsFailureAndNotificationsEvenWhenUploadCallerRollsBack() {
        org.springframework.jdbc.datasource.DriverManagerDataSource ds = new org.springframework.jdbc.datasource.DriverManagerDataSource(
                "jdbc:h2:mem:" + java.util.UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        org.springframework.jdbc.core.JdbcTemplate jdbc = new org.springframework.jdbc.core.JdbcTemplate(ds);
        org.springframework.transaction.PlatformTransactionManager manager = new org.springframework.jdbc.datasource.DataSourceTransactionManager(ds);
        org.springframework.transaction.support.TransactionTemplate tx = new org.springframework.transaction.support.TransactionTemplate(manager);
        DurableWorkStore work = new DurableWorkStore(jdbc, tx, "listener-test"); work.initialize();
        jdbc.execute("create table assessment_probe(id bigint primary key,status varchar(32),stage varchar(64))");
        javax.persistence.EntityManager em = mock(javax.persistence.EntityManager.class);
        com.riskwarning.common.po.project.Project project = com.riskwarning.common.po.project.Project.builder().id(1L).enterpriseId(7L).build();
        when(em.find(eq(com.riskwarning.common.po.project.Project.class), eq(1L), eq(javax.persistence.LockModeType.PESSIMISTIC_WRITE))).thenReturn(project);
        when(em.find(eq(com.riskwarning.common.po.project.ProjectMember.class), any())).thenReturn(new com.riskwarning.common.po.project.ProjectMember());
        AssessmentRepository assessments = mock(AssessmentRepository.class);
        java.util.concurrent.atomic.AtomicReference<com.riskwarning.common.po.report.Assessment> current = new java.util.concurrent.atomic.AtomicReference<>();
        when(assessments.findFirstByProjectIdOrderByIdDesc(1L)).thenAnswer(call -> java.util.Optional.ofNullable(current.get()));
        when(assessments.saveAndFlush(any())).thenAnswer(call -> {
            com.riskwarning.common.po.report.Assessment row = call.getArgument(0); row.setId(123L); current.set(row);
            jdbc.update("insert into assessment_probe(id,status) values(?,?)", row.getId(), row.getStatus().name()); return row;
        });
        when(em.find(eq(com.riskwarning.common.po.report.Assessment.class), eq(123L), eq(javax.persistence.LockModeType.PESSIMISTIC_WRITE)))
                .thenAnswer(call -> current.get());
        doAnswer(call -> { jdbc.update("update assessment_probe set status=?,stage=? where id=123",
                current.get().getStatus().name(), current.get().getFailureStage()); return null; }).when(em).flush();
        KafkaUtils kafka = mock(KafkaUtils.class);
        doAnswer(call -> {
            com.riskwarning.common.message.Message message = call.getArgument(0);
            work.enqueue("KAFKA_OUTBOX", message.getMessageId(), JSON.toJSONString(message)); return null;
        }).when(kafka).sendMessage(any());
        RedisUtil redis = mock(RedisUtil.class);
        when(redis.hmget(anyString())).thenReturn(Collections.emptyMap());
        when(redis.sSetAndTime(anyString(), anyLong(), any())).thenReturn(1L);
        when(redis.hset(anyString(), anyString(), any(), anyLong())).thenReturn(false);
        com.riskwarning.common.po.user.User user = new com.riskwarning.common.po.user.User(); user.setId(15L);
        com.riskwarning.common.context.UserContext.setUser(user);
        try (org.springframework.context.annotation.AnnotationConfigApplicationContext context = new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            context.register(ListenerTransactions.class);
            context.registerBean(org.springframework.transaction.PlatformTransactionManager.class, () -> manager);
            context.registerBean(DurableWorkStore.class, () -> work);
            context.registerBean(KafkaUtils.class, () -> kafka);
            context.registerBean(RedisUtil.class, () -> redis);
            context.registerBean(org.springframework.kafka.core.KafkaTemplate.class, () -> mock(org.springframework.kafka.core.KafkaTemplate.class));
            context.registerBean(org.springframework.data.redis.core.RedisTemplate.class, () -> mock(org.springframework.data.redis.core.RedisTemplate.class));
            context.registerBean(com.riskwarning.org.service.impl.ProjectServiceImpl.class, () ->
                    new com.riskwarning.org.service.impl.ProjectServiceImpl(mock(ProjectRepository.class), mock(ProjectMemberRepository.class), em, assessments));
            context.registerBean(com.riskwarning.org.service.impl.FileServiceImpl.class);
            context.refresh();
            com.riskwarning.org.service.FileService files = context.getBean(com.riskwarning.org.service.FileService.class);
            assertThrows(com.riskwarning.common.exception.BusinessException.class,
                    () -> tx.execute(status -> files.initUpload(1L, "hash", 100L, 1, "docx")));
            assertEquals("FAILED", jdbc.queryForObject("select status from assessment_probe", String.class));
            assertEquals("UPLOAD_INIT", jdbc.queryForObject("select stage from assessment_probe", String.class));
            assertEquals(2, jdbc.queryForObject("select count(*) from assessment_work where kind='KAFKA_OUTBOX'", Integer.class));
            assertTrue(jdbc.queryForList("select payload from assessment_work where kind='KAFKA_OUTBOX'", String.class)
                    .stream().anyMatch(payload -> payload.contains("BusinessException")));
        }
    }

    @org.junit.jupiter.api.io.TempDir java.nio.file.Path chunks;

    @Test void defaultUploadQueuesAndDurableNamespacesAreIsolatedByMachine() {
        org.springframework.beans.factory.config.YamlPropertiesFactoryBean yaml =
                new org.springframework.beans.factory.config.YamlPropertiesFactoryBean();
        yaml.setResources(new org.springframework.core.io.ClassPathResource("application.yml"));
        java.util.Properties config = yaml.getObject();
        org.springframework.mock.env.MockEnvironment first = new org.springframework.mock.env.MockEnvironment()
                .withProperty("COMPUTERNAME", "machine-a").withProperty("user.name", "developer");
        org.springframework.mock.env.MockEnvironment second = new org.springframework.mock.env.MockEnvironment()
                .withProperty("COMPUTERNAME", "machine-b").withProperty("user.name", "developer");
        String queue = config.getProperty("org.upload.queue-key");
        String namespace = config.getProperty("assessment.reliability.namespace");
        assertEquals("file:confirmed:queue:machine-a", first.resolveRequiredPlaceholders(queue));
        assertEquals("file:confirmed:queue:machine-b", second.resolveRequiredPlaceholders(queue));
        assertNotEquals(first.resolveRequiredPlaceholders(namespace), second.resolveRequiredPlaceholders(namespace));
        first.withProperty("UPLOAD_CONFIRM_QUEUE_KEY", "deployment-upload-queue");
        assertEquals("deployment-upload-queue", first.resolveRequiredPlaceholders(queue));
        assertEquals("file:confirmed:queue:developer", new org.springframework.mock.env.MockEnvironment()
                .withProperty("user.name", "developer").resolveRequiredPlaceholders(queue));
    }

    @Test void initializationCreatesAssessmentBeforeRedisAndPublishesMetadataFailures() {
        RedisUtil redis = mock(RedisUtil.class);
        com.riskwarning.org.service.impl.FileServiceImpl service = fileService(redis, mock(UploadTaskQueue.class));
        when(redis.hmget(anyString())).thenReturn(Collections.emptyMap());
        when(redis.sSetAndTime(anyString(), anyLong(), any())).thenReturn(1L);
        when(redis.hset(anyString(), anyString(), any(), anyLong())).thenReturn(false);
        assertThrows(com.riskwarning.common.exception.BusinessException.class,
                () -> service.initUpload(1L, "hash", 100L, 1, "docx"));
        com.riskwarning.org.service.ProjectService projects = (com.riskwarning.org.service.ProjectService)
                org.springframework.test.util.ReflectionTestUtils.getField(service, "projectService");
        org.mockito.InOrder order = inOrder(projects, redis);
        order.verify(projects).beginUploadAssessment(1L);
        order.verify(redis).hmget(anyString());
        org.springframework.context.ApplicationEventPublisher events = (org.springframework.context.ApplicationEventPublisher)
                org.springframework.test.util.ReflectionTestUtils.getField(service, "events");
        verify(events).publishEvent(argThat((Object e) -> e instanceof com.riskwarning.common.message.AssessmentCompletedEventMessage
                && "UPLOAD_INIT".equals(((com.riskwarning.common.message.AssessmentCompletedEventMessage)e).getFailureStage())));
    }

    @Test void delayedChunkFromOldBatchCannotFailNewUpload() {
        RedisUtil redis = mock(RedisUtil.class);
        com.riskwarning.org.service.impl.FileServiceImpl service = fileService(redis, mock(UploadTaskQueue.class));
        when(redis.hget(anyString(), eq("old"))).thenReturn(UploadFileDto.builder().assessmentId(122L).build());
        assertThrows(com.riskwarning.common.exception.BusinessException.class,
                () -> service.uploadChunk(1L, "old", 0, mock(org.springframework.web.multipart.MultipartFile.class), 122L));
        org.springframework.context.ApplicationEventPublisher events = (org.springframework.context.ApplicationEventPublisher)
                org.springframework.test.util.ReflectionTestUtils.getField(service, "events");
        verifyNoInteractions(events);
    }

    @Test void watchdogUsesPersistedAssessmentIdentity() throws Exception {
        ExecuteQueueTask executor = new ExecuteQueueTask(mock(UploadTaskQueue.class), mock(DurableWorkStore.class),
                mock(RedisUtil.class), mock(FileRepository.class), mock(KafkaUtils.class));
        com.riskwarning.org.service.ProjectService projects = mock(com.riskwarning.org.service.ProjectService.class);
        org.springframework.test.util.ReflectionTestUtils.setField(executor, "projectService", projects);
        executor.assessmentWatchdog().execute(JSON.toJSONString(UploadConfirmDto.builder()
                .projectId(1L).assessmentId(123L).taskId("batch-1").userId(15L).build()));
        verify(projects).onUploadFailure(argThat(event -> event.getAssessmentId().equals(123L)
                && "ASSESSMENT_TIMEOUT".equals(event.getFailureStage())));
    }

    @org.junit.jupiter.api.AfterEach void clearUser() { com.riskwarning.common.context.UserContext.clear(); }

    @Test void failedChunkWritePublishesFailureWithoutRecordingACompletedChunk() throws Exception {
        RedisUtil redis = mock(RedisUtil.class);
        UploadTaskQueue queue = mock(UploadTaskQueue.class);
        com.riskwarning.org.service.impl.FileServiceImpl service = fileService(redis, queue);
        when(redis.hget(anyString(), eq("upload-1"))).thenReturn(UploadFileDto.builder()
                .uploadId("upload-1").assessmentId(123L).filePath(chunks.toString()).totalChunks(1).build());
        when(redis.sSetAndTime(anyString(), anyLong(), any())).thenReturn(1L);
        org.springframework.web.multipart.MultipartFile part = mock(org.springframework.web.multipart.MultipartFile.class);
        doThrow(new java.io.IOException("disk unavailable")).when(part).transferTo(any(java.io.File.class));

        assertThrows(RuntimeException.class,
                () -> service.uploadChunk(1L, "upload-1", 0, part, 123L));
        verify(redis, never()).sSetAndTime(anyString(), anyLong(), any());
        org.springframework.context.ApplicationEventPublisher events = (org.springframework.context.ApplicationEventPublisher)
                org.springframework.test.util.ReflectionTestUtils.getField(service, "events");
        org.mockito.ArgumentCaptor<com.riskwarning.common.message.AssessmentCompletedEventMessage> event =
                org.mockito.ArgumentCaptor.forClass(com.riskwarning.common.message.AssessmentCompletedEventMessage.class);
        verify(events).publishEvent(event.capture());
        assertEquals("UPLOAD_CHUNK", event.getValue().getFailureStage());
        assertTrue(event.getValue().getFailureDetail().contains("disk unavailable"));
        verifyNoInteractions(queue);
    }

    @Test void redisChunkMarkersCannotConfirmFilesMissingFromThisMachine() {
        RedisUtil redis = mock(RedisUtil.class);
        UploadTaskQueue queue = mock(UploadTaskQueue.class);
        com.riskwarning.org.service.impl.FileServiceImpl service = fileService(redis, queue);
        when(redis.hmget(anyString())).thenReturn(Collections.singletonMap("upload-1", UploadFileDto.builder()
                .uploadId("upload-1").assessmentId(123L).filePath(chunks.toString()).totalChunks(1).build()));
        when(redis.sGet(anyString())).thenReturn(Collections.singleton(0));

        assertThrows(com.riskwarning.common.exception.BusinessException.class, () -> service.confirmUpload(1L, 123L));
        verifyNoInteractions(queue);
    }

    @Test void readableChunksAreConfirmedWithTheirOriginalMetadata() throws Exception {
        java.nio.file.Files.write(chunks.resolve("0"), new byte[]{1, 2, 3});
        RedisUtil redis = mock(RedisUtil.class);
        UploadTaskQueue queue = mock(UploadTaskQueue.class);
        com.riskwarning.org.service.impl.FileServiceImpl service = fileService(redis, queue);
        UploadFileDto file = UploadFileDto.builder().uploadId("upload-1").assessmentId(123L).filePath(chunks.toString()).totalChunks(1).build();
        when(redis.hmget(anyString())).thenReturn(Collections.singletonMap("upload-1", file));
        when(redis.sGet(anyString())).thenReturn(Collections.singleton(0));
        com.riskwarning.common.po.user.User user = new com.riskwarning.common.po.user.User(); user.setId(15L);
        com.riskwarning.common.context.UserContext.setUser(user);
        try {
            assertEquals(123L, service.confirmUpload(1L, 123L));
            org.mockito.ArgumentCaptor<UploadConfirmDto> task = org.mockito.ArgumentCaptor.forClass(UploadConfirmDto.class);
            com.riskwarning.org.service.ProjectService projects = (com.riskwarning.org.service.ProjectService)
                    org.springframework.test.util.ReflectionTestUtils.getField(service, "projectService");
            verify(projects).confirmUploadAssessment(task.capture());
            assertEquals(123L, task.getValue().getAssessmentId());
            assertEquals("batch-1", task.getValue().getTaskId());
            assertEquals(Collections.singletonList(file), task.getValue().getFiles());
        } finally { com.riskwarning.common.context.UserContext.clear(); }
    }

    private com.riskwarning.org.service.impl.FileServiceImpl fileService(RedisUtil redis, UploadTaskQueue queue) {
        com.riskwarning.org.service.impl.FileServiceImpl service = new com.riskwarning.org.service.impl.FileServiceImpl();
        com.riskwarning.org.service.ProjectService projects = mock(com.riskwarning.org.service.ProjectService.class);
        com.riskwarning.common.po.report.Assessment assessment = com.riskwarning.common.po.report.Assessment.builder()
                .id(123L).projectId(1L).uploadTaskId("batch-1").status(com.riskwarning.common.enums.AssessmentStatusEnum.UPLOADING).build();
        when(projects.requireUploadingAssessment(1L)).thenReturn(assessment);
        when(projects.beginUploadAssessment(1L)).thenReturn(assessment);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "projectService", projects);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "redisUtil", redis);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "events", mock(org.springframework.context.ApplicationEventPublisher.class));
        com.riskwarning.common.po.user.User user = new com.riskwarning.common.po.user.User(); user.setId(15L);
        com.riskwarning.common.context.UserContext.setUser(user);
        return service;
    }

    @Test void failedDatabaseHandoffDoesNotAcknowledgeRedisTask() {
        UploadTaskQueue queue = mock(UploadTaskQueue.class);
        DurableWorkStore store = mock(DurableWorkStore.class);
        UploadConfirmDto task = UploadConfirmDto.builder().projectId(1L).taskId("task-1").files(Collections.emptyList()).build();
        when(queue.poll()).thenReturn(task);
        when(queue.getQueueKey()).thenReturn("isolated-queue");
        doThrow(new IllegalStateException("database unavailable")).when(store).enqueue(anyString(), anyString(), anyString());
        ExecuteQueueTask bridge = new ExecuteQueueTask(queue, store, mock(RedisUtil.class), mock(FileRepository.class), mock(KafkaUtils.class));
        injectProjectService(bridge);
        assertThrows(IllegalStateException.class, bridge::handoffOne);
        verify(queue, never()).acknowledge(any());
    }
    @Test void legacyHandoffKeepsStableIdentityAndDoesNotMutateRedisValue() {
        UploadTaskQueue queue = mock(UploadTaskQueue.class);
        DurableWorkStore store = mock(DurableWorkStore.class);
        UploadConfirmDto task = UploadConfirmDto.builder().projectId(1L).userId(2L).build();
        when(queue.poll()).thenReturn(task);
        when(queue.getQueueKey()).thenReturn("isolated-queue");
        RedisUtil redis = mock(RedisUtil.class);
        UploadFileDto file = UploadFileDto.builder().uploadId("file-1").filePath("chunks").build();
        when(redis.hmget(anyString())).thenReturn(Collections.singletonMap("file-1",file));
        java.util.List<String> ids = new java.util.ArrayList<>();
        doAnswer(call -> {
            ids.add(call.getArgument(1));
            UploadConfirmDto stored = JSON.parseObject((String)call.getArgument(2), UploadConfirmDto.class);
            assertEquals("chunks", stored.getFiles().get(0).getFilePath());
            assertEquals(123L, stored.getAssessmentId());
            return null;
        }).when(store).enqueue(anyString(), anyString(), anyString());
        ExecuteQueueTask bridge = new ExecuteQueueTask(queue, store, redis, mock(FileRepository.class), mock(KafkaUtils.class));
        injectProjectService(bridge);
        bridge.handoffOne(); bridge.handoffOne();
        assertEquals(ids.get(0),ids.get(1));
        assertNull(task.getTaskId()); assertNull(task.getFiles());
    }

    /** The upload handoff must always reach the real executor. */
    @Test void handoffKindMatchesRealExecutorClaim() {
        UploadTaskQueue queue = mock(UploadTaskQueue.class);
        DurableWorkStore store = mock(DurableWorkStore.class);
        UploadConfirmDto task = UploadConfirmDto.builder().projectId(1L).taskId("task-1").build();
        when(queue.poll()).thenReturn(task);
        when(queue.getQueueKey()).thenReturn("isolated-queue");

        List<String> writtenKinds = new java.util.ArrayList<>();
        doAnswer(call -> {
            writtenKinds.add(call.getArgument(0));
            return null;
        }).when(store).enqueue(anyString(), anyString(), anyString());

        ExecuteQueueTask bridge = new ExecuteQueueTask(queue, store, mock(RedisUtil.class),
                mock(FileRepository.class), mock(KafkaUtils.class));
        injectProjectService(bridge);
        bridge.handoffOne();

        assertEquals(Collections.singletonList("UPLOAD_INBOX"), writtenKinds,
                "handoff 必须写入 UPLOAD_INBOX");
        assertEquals("UPLOAD_INBOX", bridge.kind(),
                "真实执行器必须认领上传工作项");
    }

    private void injectProjectService(ExecuteQueueTask bridge) {
        com.riskwarning.org.service.ProjectService projectService = mock(com.riskwarning.org.service.ProjectService.class);
        when(projectService.prepareUploadAssessment(anyLong(), anyString())).thenReturn(123L);
        org.springframework.test.util.ReflectionTestUtils.setField(bridge, "projectService", projectService);
    }
}
