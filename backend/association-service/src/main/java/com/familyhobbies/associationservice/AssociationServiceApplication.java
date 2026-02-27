package com.familyhobbies.associationservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = {
    "com.familyhobbies.associationservice",
    "com.familyhobbies.errorhandling",
    "com.familyhobbies.common"
})
@EnableDiscoveryClient
public class AssociationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AssociationServiceApplication.class, args);
    }
}
