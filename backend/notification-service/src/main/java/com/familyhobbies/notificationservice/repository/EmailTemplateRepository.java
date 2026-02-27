package com.familyhobbies.notificationservice.repository;

import com.familyhobbies.notificationservice.entity.EmailTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmailTemplateRepository extends JpaRepository<EmailTemplate, Long> {

    Optional<EmailTemplate> findByCodeAndActiveTrue(String code);
}
