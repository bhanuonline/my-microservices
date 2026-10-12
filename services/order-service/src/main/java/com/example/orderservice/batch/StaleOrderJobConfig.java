package com.example.orderservice.batch;

import com.example.orderservice.model.Order;
import com.example.orderservice.repository.OrderRepository;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class StaleOrderJobConfig {

    @Bean
    public StaleOrderWriter staleOrderWriter(OrderRepository orderRepository) {
        return new StaleOrderWriter(orderRepository);
    }

    // chunk(10): read+process 10 orders, then write them as one DB batch — repeat until reader returns null.
    @Bean
    public Step staleOrderStep(JobRepository jobRepository,
                               PlatformTransactionManager txManager,
                               StaleOrderReader staleOrderReader,
                               StaleOrderProcessor staleOrderProcessor,
                               StaleOrderWriter staleOrderWriter) {
        return new StepBuilder("staleOrderStep", jobRepository)
                .<Order, Order>chunk(10, txManager)
                .reader(staleOrderReader)
                .processor(staleOrderProcessor)
                .writer(staleOrderWriter)
                .build();
    }

    @Bean
    public Job staleOrderCleanupJob(JobRepository jobRepository, Step staleOrderStep) {
        return new JobBuilder("staleOrderCleanupJob", jobRepository)
                .start(staleOrderStep)
                .build();
    }
}
