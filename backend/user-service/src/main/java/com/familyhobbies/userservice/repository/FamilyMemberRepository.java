package com.familyhobbies.userservice.repository;

import com.familyhobbies.userservice.entity.FamilyMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FamilyMemberRepository extends JpaRepository<FamilyMember, Long> {

    List<FamilyMember> findByFamily_Id(Long familyId);

    Optional<FamilyMember> findByIdAndFamily_Id(Long id, Long familyId);
}
