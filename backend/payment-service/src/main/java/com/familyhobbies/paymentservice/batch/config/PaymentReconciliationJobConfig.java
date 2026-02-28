package com.familyhobbies.paymentservice.batch.config;

import com.familyhobbies.errorhandling.exception.container.ExternalApiException;
import com.familyhobbies.paymentservice.adapter.HelloAssoCheckoutClient;
import com.familyhobbies.paymentservice.batch.policy.HelloAssoApiSkipPolicy;
import com.familyhobbies.paymentservice.batch.processor.PaymentReconciliationProcessor;
import com.familyhobbies.paymentservice.batch.reader.StalePaymentItemReader;
import com.familyhobbies.paymentservice.batch.writer.PaymentReconciliationWriter;
import com.familyhobbies.paymentservice.entity.Payment;
import com.familyhobbies.paymentservice.event.PaymentEventPublisher;
import com.familyhobbies.paymentservice.repository.PaymentRepository;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;

/**
 * Spring Batch configuration for the payment reconciliation job.
 *
 * <p>Defines:
 * <ul>
 *     <li>{@code paymentReconciliationJob} -- the top-level job</li>
 *     <li>{@code paymentReconciliationStep} -- single chunk step (read/process/write)</li>
 *     <li>All batch component beans: reader, processor, writer, skip policy</li>
 *     <li>{@link Clock} bean for testable time-based logic</li>
 * </ul>
 *
 * <p>Chunk size is configurable via {@code batch.reconciliation.chunk-size} (default 10).
 * Max skip count is configurable via {@code batch.reconciliation.max-skip-count} (default 50).
 */
@Configuration
public class PaymentReconciliationJobConfig {

    @Value("${batch.reconciliation.chunk-size:10}")
    private int chunkSize;

    @Value("${batch.reconciliation.max-skip-count:50}")
    private int maxSkipCount;

    @Bean
    public Clock reconciliationClock() {
        return Clock.systemUTC();
    }

    @Bean
    public StalePaymentItemReader stalePaymentItemReader(
            PaymentRepository paymentRepository,
            Clock reconciliationClock) {
        return new StalePaymentItemReader(paymentRepository, reconciliationClock);
    }

    @Bean
    public PaymentReconciliationProcessor paymentReconciliationProcessor(
            HelloAssoCheckoutClient helloAssoCheckoutClient) {
        return new PaymentReconciliationProcessor(helloAssoCheckoutClient);
    }

    @Bean
    public PaymentReconciliationWriter paymentReconciliationWriter(
            PaymentRepository paymentRepository,
            PaymentEventPublisher paymentEventPublisher) {
        return new PaymentReconciliationWriter(paymentRepository, paymentEventPublisher);
    }

    @Bean
    public HelloAssoApiSkipPolicy helloAssoApiSkipPolicy() {
        return new HelloAssoApiSkipPolicy(maxSkipCount);
    }

    @Bean
    public Step paymentReconciliationStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            StalePaymentItemReader stalePaymentItemReader,
            PaymentReconciliationProcessor paymentReconciliationProcessor,
            PaymentReconciliationWriter paymentReconciliationWriter,
            HelloAssoApiSkipPolicy helloAssoApiSkipPolicy) {

        return new StepBuilder("paymentReconciliationStep", jobRepository)
                .<Payment, Payment>chunk(chunkSize, transactionManager)
                .reader(stalePaymentItemReader)
                .processor(paymentReconciliationProcessor)
                .writer(paymentReconciliationWriter)
                .faultTolerant()
                .skipPolicy(helloAssoApiSkipPolicy)
                .skip(ExternalApiException.class)
                .build();
    }

    @Bean
    public Job paymentReconciliationJob(
            JobRepository jobRepository,
            Step paymentReconciliationStep) {

        return new JobBuilder("paymentReconciliationJob", jobRepository)
                .start(paymentReconciliationStep)
                .build();
    }
}
