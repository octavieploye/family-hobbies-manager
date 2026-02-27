package com.familyhobbies.associationservice.repository;

import com.familyhobbies.associationservice.entity.Association;
import com.familyhobbies.associationservice.entity.enums.AssociationCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * Repository for {@link Association} entities.
 * Extends {@link JpaSpecificationExecutor} for dynamic search criteria.
 * No @Repository annotation -- Spring Data auto-detects JpaRepository interfaces.
 */
public interface AssociationRepository extends JpaRepository<Association, Long>,
        JpaSpecificationExecutor<Association> {

    Optional<Association> findBySlug(String slug);

    Optional<Association> findByHelloassoSlug(String helloassoSlug);

    long countByHelloassoSlugIsNotNull();

    Page<Association> findByCityIgnoreCase(String city, Pageable pageable);

    Page<Association> findByCategory(AssociationCategory category, Pageable pageable);
}
