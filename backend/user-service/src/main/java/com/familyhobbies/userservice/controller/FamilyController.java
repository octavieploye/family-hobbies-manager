package com.familyhobbies.userservice.controller;

import com.familyhobbies.userservice.dto.request.FamilyMemberRequest;
import com.familyhobbies.userservice.dto.request.FamilyRequest;
import com.familyhobbies.userservice.dto.response.FamilyMemberResponse;
import com.familyhobbies.userservice.dto.response.FamilyResponse;
import com.familyhobbies.userservice.service.FamilyService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/families")
public class FamilyController {

    private final FamilyService familyService;

    public FamilyController(FamilyService familyService) {
        this.familyService = familyService;
    }

    @PostMapping
    public ResponseEntity<FamilyResponse> createFamily(
            @Valid @RequestBody FamilyRequest request,
            @RequestHeader("X-User-Id") Long userId) {
        FamilyResponse response = familyService.createFamily(request, userId);
        URI location = URI.create("/api/v1/families/" + response.id());
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/me")
    public ResponseEntity<FamilyResponse> getMyFamily(
            @RequestHeader("X-User-Id") Long userId) {
        FamilyResponse response = familyService.getMyFamily(userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<FamilyResponse> getFamilyById(
            @PathVariable Long id) {
        FamilyResponse response = familyService.getFamilyById(id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<FamilyResponse> updateFamily(
            @PathVariable Long id,
            @Valid @RequestBody FamilyRequest request,
            @RequestHeader("X-User-Id") Long userId) {
        FamilyResponse response = familyService.updateFamily(id, request, userId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{familyId}/members")
    public ResponseEntity<FamilyMemberResponse> addMember(
            @PathVariable Long familyId,
            @Valid @RequestBody FamilyMemberRequest request,
            @RequestHeader("X-User-Id") Long userId) {
        FamilyMemberResponse response = familyService.addMember(familyId, request, userId);
        URI location = URI.create("/api/v1/families/" + familyId + "/members/" + response.id());
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{familyId}/members")
    public ResponseEntity<List<FamilyMemberResponse>> getMembers(
            @PathVariable Long familyId) {
        List<FamilyMemberResponse> members = familyService.getMembers(familyId);
        return ResponseEntity.ok(members);
    }

    @PutMapping("/{familyId}/members/{memberId}")
    public ResponseEntity<FamilyMemberResponse> updateMember(
            @PathVariable Long familyId,
            @PathVariable Long memberId,
            @Valid @RequestBody FamilyMemberRequest request,
            @RequestHeader("X-User-Id") Long userId) {
        FamilyMemberResponse response = familyService.updateMember(familyId, memberId, request, userId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{familyId}/members/{memberId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable Long familyId,
            @PathVariable Long memberId,
            @RequestHeader("X-User-Id") Long userId) {
        familyService.removeMember(familyId, memberId, userId);
        return ResponseEntity.noContent().build();
    }
}
