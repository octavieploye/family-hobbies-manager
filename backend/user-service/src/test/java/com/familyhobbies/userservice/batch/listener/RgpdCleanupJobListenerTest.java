package com.familyhobbies.userservice.batch.listener;

import com.familyhobbies.userservice.batch.writer.RgpdCleanupWriter;
import com.familyhobbies.userservice.entity.RgpdCleanupLog;
import com.familyhobbies.userservice.entity.enums.CrossServiceCleanupStatus;
import com.familyhobbies.userservice.repository.RgpdCleanupLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RgpdCleanupJobListenerTest {

    @Mock
    private RgpdCleanupLogRepository rgpdCleanupLogRepository;

    @Mock
    private RgpdCleanupWriter rgpdCleanupWriter;

    private RgpdCleanupJobListener listener;

    @BeforeEach
    void setUp() {
        listener = new RgpdCleanupJobListener(rgpdCleanupLogRepository, rgpdCleanupWriter);
    }

    @Test
    @DisplayName("Should reset writer counters before job starts")
    void shouldResetCountersBeforeJob() {
        JobExecution jobExecution = new JobExecution(1L);
        listener.beforeJob(jobExecution);
        verify(rgpdCleanupWriter).resetCounters();
    }

    @Test
    @DisplayName("Should create audit log entry after successful job")
    void shouldCreateAuditLogAfterSuccessfulJob() {
        JobExecution jobExecution = new JobExecution(1L);
        jobExecution.setStatus(BatchStatus.COMPLETED);
        StepExecution stepExecution = jobExecution.createStepExecution("rgpdCleanupStep");
        stepExecution.setReadCount(3);

        when(rgpdCleanupWriter.getAnonymizedCount()).thenReturn(3);
        when(rgpdCleanupWriter.getOverallCleanupStatus()).thenReturn(CrossServiceCleanupStatus.SUCCESS);
        when(rgpdCleanupWriter.getErrorDetailsAsString()).thenReturn(null);

        listener.afterJob(jobExecution);

        ArgumentCaptor<RgpdCleanupLog> captor = ArgumentCaptor.forClass(RgpdCleanupLog.class);
        verify(rgpdCleanupLogRepository).save(captor.capture());

        RgpdCleanupLog log = captor.getValue();
        assertThat(log.getUsersProcessed()).isEqualTo(3);
        assertThat(log.getUsersAnonymized()).isEqualTo(3);
        assertThat(log.getCrossServiceCleanupStatus()).isEqualTo(CrossServiceCleanupStatus.SUCCESS);
        assertThat(log.getErrorDetails()).isNull();
        assertThat(log.getExecutionTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("Should record error details when job fails")
    void shouldRecordErrorDetailsWhenJobFails() {
        JobExecution jobExecution = new JobExecution(2L);
        jobExecution.setStatus(BatchStatus.FAILED);
        jobExecution.addFailureException(new RuntimeException("DB connection lost"));

        when(rgpdCleanupWriter.getAnonymizedCount()).thenReturn(0);
        when(rgpdCleanupWriter.getOverallCleanupStatus()).thenReturn(CrossServiceCleanupStatus.SUCCESS);
        when(rgpdCleanupWriter.getErrorDetailsAsString()).thenReturn(null);

        listener.afterJob(jobExecution);

        ArgumentCaptor<RgpdCleanupLog> captor = ArgumentCaptor.forClass(RgpdCleanupLog.class);
        verify(rgpdCleanupLogRepository).save(captor.capture());

        RgpdCleanupLog log = captor.getValue();
        assertThat(log.getErrorDetails()).contains("Job failed");
        assertThat(log.getErrorDetails()).contains("DB connection lost");
    }

    @Test
    @DisplayName("Should combine cross-service errors and job failure errors")
    void shouldCombineErrors() {
        JobExecution jobExecution = new JobExecution(3L);
        jobExecution.setStatus(BatchStatus.FAILED);
        jobExecution.addFailureException(new RuntimeException("batch error"));

        when(rgpdCleanupWriter.getAnonymizedCount()).thenReturn(1);
        when(rgpdCleanupWriter.getOverallCleanupStatus()).thenReturn(CrossServiceCleanupStatus.PARTIAL_FAILURE);
        when(rgpdCleanupWriter.getErrorDetailsAsString()).thenReturn("payment-service cleanup failed");

        listener.afterJob(jobExecution);

        ArgumentCaptor<RgpdCleanupLog> captor = ArgumentCaptor.forClass(RgpdCleanupLog.class);
        verify(rgpdCleanupLogRepository).save(captor.capture());

        RgpdCleanupLog log = captor.getValue();
        assertThat(log.getCrossServiceCleanupStatus()).isEqualTo(CrossServiceCleanupStatus.PARTIAL_FAILURE);
        assertThat(log.getErrorDetails()).contains("payment-service cleanup failed");
        assertThat(log.getErrorDetails()).contains("Job failed");
    }
}
