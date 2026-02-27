package com.familyhobbies.userservice.repository;

import com.familyhobbies.userservice.entity.Family;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FamilyRepository extends JpaRepository<Family, Long> {

    Optional<Family> findByCreatedBy_Id(Long userId);

    boolean existsByCreatedBy_Id(Long userId);
}
