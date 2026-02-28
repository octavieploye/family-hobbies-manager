package com.familyhobbies.notificationservice.controller;

import com.familyhobbies.notificationservice.dto.request.NotificationPreferenceRequest;
import com.familyhobbies.notificationservice.dto.response.NotificationPreferenceResponse;
import com.familyhobbies.notificationservice.dto.response.NotificationResponse;
import com.familyhobbies.notificationservice.dto.response.UnreadCountResponse;
import com.familyhobbies.notificationservice.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "In-app notifications and notification preferences")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(summary = "Get notifications",
               description = "Returns paginated notifications for the authenticated user")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Notifications page returned")
    })
    public ResponseEntity<Page<NotificationResponse>> getNotifications(
            @RequestHeader("X-User-Id") Long userId,
            Pageable pageable) {
        return ResponseEntity.ok(notificationService.getNotifications(userId, pageable));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Get unread count",
               description = "Returns the count of unread notifications for the authenticated user")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Unread count returned")
    })
    public ResponseEntity<UnreadCountResponse> getUnreadCount(
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(notificationService.getUnreadCount(userId));
    }

    @PutMapping("/{id}/read")
    @Operation(summary = "Mark notification as read",
               description = "Marks a single notification as read")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Notification marked as read"),
        @ApiResponse(responseCode = "404", description = "Notification not found")
    })
    public ResponseEntity<Void> markAsRead(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId) {
        notificationService.markAsRead(id, userId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/read-all")
    @Operation(summary = "Mark all notifications as read",
               description = "Marks all notifications as read for the authenticated user")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "All notifications marked as read")
    })
    public ResponseEntity<Void> markAllAsRead(
            @RequestHeader("X-User-Id") Long userId) {
        notificationService.markAllAsRead(userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/preferences")
    @Operation(summary = "Get notification preferences",
               description = "Returns notification preferences for the authenticated user")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Preferences returned")
    })
    public ResponseEntity<List<NotificationPreferenceResponse>> getPreferences(
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(notificationService.getPreferences(userId));
    }

    @PutMapping("/preferences")
    @Operation(summary = "Update notification preferences",
               description = "Updates notification preferences for the authenticated user")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Preferences updated"),
        @ApiResponse(responseCode = "400", description = "Invalid preferences data")
    })
    public ResponseEntity<NotificationPreferenceResponse> updatePreference(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody NotificationPreferenceRequest request) {
        return ResponseEntity.ok(notificationService.updatePreference(userId, request));
    }
}
