package com.familyhobbies.associationservice;

import com.familyhobbies.common.config.HelloAssoProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = {
    "com.familyhobbies.associationservice",
    "com.familyhobbies.errorhandling",
    "com.familyhobbies.common"
})
@EnableDiscoveryClient
@EnableConfigurationProperties(HelloAssoProperties.class)
public class AssociationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AssociationServiceApplication.class, args);
    }
}
