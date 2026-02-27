package com.familyhobbies.paymentservice;

import com.familyhobbies.common.config.HelloAssoProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = {
    "com.familyhobbies.paymentservice",
    "com.familyhobbies.errorhandling",
    "com.familyhobbies.common"
})
@EnableConfigurationProperties(HelloAssoProperties.class)
@EnableDiscoveryClient
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
