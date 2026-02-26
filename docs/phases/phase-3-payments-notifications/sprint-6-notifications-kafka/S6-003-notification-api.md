# Story S6-003: Implement Notification API

> 5 points | Priority: P0 | Service: notification-service
> Sprint file: [Back to Sprint Index](./_index.md)
> Tests: [S6-003 Tests Companion](./S6-003-notification-api-tests.md)

---

## Context

The notification-service needs a REST API so that the Angular frontend can display a user's notifications, show an unread badge count, mark notifications as read, and manage per-category notification preferences. This story builds the full API layer on top of the entities and repositories delivered in S6-001 and the notification creation pipeline from S6-002. The controller exposes six endpoints under `/api/v1/notifications`, all protected by JWT via the gateway's `X-User-Id` header. The service layer enforces ownership validation (a user can only access their own notifications) and implements upsert semantics for preferences. DTOs follow the `dto/request/` and `dto/response/` package convention, and the `NotificationMapper` handles all entity-to-DTO conversions. S6-004 (Angular notification feature) depends directly on this story.

## Tasks

| # | Task | File Path | What To Create | How To Verify |
|---|------|-----------|----------------|---------------|
| 1 | NotificationResponse DTO | `backend/notification-service/src/main/java/.../dto/response/NotificationResponse.java` | Response DTO for notification list items | Compiles |
| 2 | UnreadCountResponse DTO | `backend/notification-service/src/main/java/.../dto/response/UnreadCountResponse.java` | Response DTO for unread count | Compiles |
| 3 | NotificationPreferenceRequest DTO | `backend/notification-service/src/main/java/.../dto/request/NotificationPreferenceRequest.java` | Request DTO for preference updates | Compiles |
| 4 | CategoryPreference inner DTO | Embedded in NotificationPreferenceResponse | Per-category email/inApp flags | Compiles |
| 5 | NotificationPreferenceResponse DTO | `backend/notification-service/src/main/java/.../dto/response/NotificationPreferenceResponse.java` | Response DTO for preferences with category map | Compiles |
| 6 | MarkAllReadResponse DTO | `backend/notification-service/src/main/java/.../dto/response/MarkAllReadResponse.java` | Response DTO for mark-all-as-read | Compiles |
| 7 | NotificationMapper | `backend/notification-service/src/main/java/.../mapper/NotificationMapper.java` | Entity <-> DTO conversions | Unit tests pass |
| 8 | NotificationService interface | `backend/notification-service/src/main/java/.../service/NotificationService.java` | Service contract | Compiles |
| 9 | NotificationServiceImpl | `backend/notification-service/src/main/java/.../service/impl/NotificationServiceImpl.java` | Full implementation with ownership validation | Unit tests pass |
| 10 | NotificationController | `backend/notification-service/src/main/java/.../controller/NotificationController.java` | 6 REST endpoints | Integration tests pass |
| 11 | Failing tests (TDD) | See companion file | JUnit 5 test classes | Tests compile, fail (TDD) |

---

## Task 1 Detail: NotificationResponse DTO

- **What**: Response DTO representing a single notification in list and detail views
- **Where**: `backend/notification-service/src/main/java/com/familyhobbies/notificationservice/dto/response/NotificationResponse.java`
- **Why**: Returned by `GET /api/v1/notifications/me` (paginated) and `PUT /api/v1/notifications/{id}/read`
- **Content**:

```java
package com.familyhobbies.notificationservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Response DTO for a single notification.
 *
 * <p>Returned in paginated lists and after mark-as-read operations.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationResponse {

    private Long id;
    private Long userId;
    private String category;
    private String title;
    private String message;
    /** Optional deep-link URL for navigating to the related resource (e.g. "/payments/42"). */
    private String actionUrl;
    private boolean read;
    private Instant readAt;
    private Instant createdAt;
}
```

- **Verify**: `mvn compile -pl backend/notification-service` -> compiles

---

## Task 2 Detail: UnreadCountResponse DTO

- **What**: Response DTO wrapping the unread notification count for the badge display
- **Where**: `backend/notification-service/src/main/java/com/familyhobbies/notificationservice/dto/response/UnreadCountResponse.java`
- **Why**: Returned by `GET /api/v1/notifications/me/unread-count`. Wrapping in an object (not raw long) follows REST best practices.
- **Content**:

```java
package com.familyhobbies.notificationservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Response DTO for unread notification count.
 *
 * <p>Used by the frontend notification badge to display the number of
 * unread notifications without loading the full notification list.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnreadCountResponse {

    private long count;
}
```

- **Verify**: `mvn compile -pl backend/notification-service` -> compiles

---

## Task 3 Detail: NotificationPreferenceRequest DTO

- **What**: Request DTO for updating a single category's notification preference
- **Where**: `backend/notification-service/src/main/java/com/familyhobbies/notificationservice/dto/request/NotificationPreferenceRequest.java`
- **Why**: Received as a list in `PUT /api/v1/notifications/preferences`. Each element updates one category.
- **Content**:

```java
package com.familyhobbies.notificationservice.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request DTO for updating notification preferences for a single category.
 *
 * <p>Sent as part of a list in the preferences update endpoint.
 * Each request specifies one category and whether email and in-app
 * notifications should be enabled for that category.
 *
 * <p>Example JSON element:
 * <pre>
 * {
 *   "category": "PAYMENT_SUCCESS",
 *   "emailEnabled": true,
 *   "inAppEnabled": false
 * }
 * </pre>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationPreferenceRequest {

    @NotNull(message = "La categorie est requise")
    private String category;

    @NotNull(message = "Le statut email est requis")
    private Boolean emailEnabled;

    @NotNull(message = "Le statut in-app est requis")
    private Boolean inAppEnabled;
}
```

- **Verify**: `mvn compile -pl backend/notification-service` -> compiles

---

## Task 4 Detail: CategoryPreference Inner DTO

- **What**: Nested DTO inside `NotificationPreferenceResponse` representing the email/inApp flags for one category
- **Where**: Embedded as a static inner class in `NotificationPreferenceResponse`
- **Why**: Keeps the response structure clean: `{ userId, categories: { "WELCOME": { emailEnabled: true, inAppEnabled: true }, ... } }`
- **Content**: See Task 5 (embedded in NotificationPreferenceResponse)

---

## Task 5 Detail: NotificationPreferenceResponse DTO

- **What**: Response DTO for the user's complete notification preferences across all categories
- **Where**: `backend/notification-service/src/main/java/com/familyhobbies/notificationservice/dto/response/NotificationPreferenceResponse.java`
- **Why**: Returned by both `GET /api/v1/notifications/preferences` and `PUT /api/v1/notifications/preferences`. Contains a map of category to preference flags, with defaults applied for categories without explicit preferences.
- **Content**:

```java
package com.familyhobbies.notificationservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

/**
 * Response DTO for notification preferences.
 *
 * <p>Contains a map of all notification categories to their preference flags.
 * Categories without explicit user preferences default to both channels enabled.
 *
 * <p>Example JSON:
 * <pre>
 * {
 *   "userId": 1,
 *   "categories": {
 *     "WELCOME":                { "emailEnabled": true,  "inAppEnabled": true },
 *     "PAYMENT_SUCCESS":        { "emailEnabled": true,  "inAppEnabled": false },
 *     "PAYMENT_FAILED":         { "emailEnabled": true,  "inAppEnabled": true },
 *     "SUBSCRIPTION_CONFIRMED": { "emailEnabled": false, "inAppEnabled": true },
 *     "SUBSCRIPTION_CANCELLED": { "emailEnabled": true,  "inAppEnabled": true },
 *     "ATTENDANCE_REMINDER":    { "emailEnabled": true,  "inAppEnabled": true },
 *     "SYSTEM":                 { "emailEnabled": true,  "inAppEnabled": true }
 *   }
 * }
 * </pre>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationPreferenceResponse {

    private Long userId;
    private Map<String, CategoryPreference> categories;

    /**
     * Per-category preference flags.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CategoryPreference {
        private boolean emailEnabled;
        private boolean inAppEnabled;
    }
}
```

- **Verify**: `mvn compile -pl backend/notification-service` -> compiles

---

## Task 6 Detail: MarkAllReadResponse DTO

- **What**: Response DTO returned by `PUT /api/v1/notifications/read-all` with the count of marked notifications
- **Where**: `backend/notification-service/src/main/java/com/familyhobbies/notificationservice/dto/response/MarkAllReadResponse.java`
- **Why**: Tells the frontend how many notifications were marked as read and the timestamp, enabling optimistic UI updates.
- **Content**:

```java
package com.familyhobbies.notificationservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Response DTO for the mark-all-as-read operation.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarkAllReadResponse {

    private int markedCount;
    private Instant readAt;
}
```

- **Verify**: `mvn compile -pl backend/notification-service` -> compiles

---

## Task 7 Detail: NotificationMapper

- **What**: Mapper component converting between Notification/NotificationPreference entities and their DTOs
- **Where**: `backend/notification-service/src/main/java/com/familyhobbies/notificationservice/mapper/NotificationMapper.java`
- **Why**: Centralizes all entity-to-DTO conversion logic. Used by NotificationServiceImpl and prevents DTO construction from leaking into service logic.
- **Content**:

```java
package com.familyhobbies.notificationservice.mapper;

import com.familyhobbies.notificationservice.dto.response.NotificationPreferenceResponse;
import com.familyhobbies.notificationservice.dto.response.NotificationPreferenceResponse.CategoryPreference;
import com.familyhobbies.notificationservice.dto.response.NotificationResponse;
import com.familyhobbies.notificationservice.entity.Notification;
import com.familyhobbies.notificationservice.entity.NotificationPreference;
import com.familyhobbies.notificationservice.enums.NotificationCategory;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Maps notification entities to DTOs.
 *
 * <p>Handles:
 * <ul>
 *   <li>{@link Notification} -> {@link NotificationResponse}</li>
 *   <li>{@link NotificationPreference} list -> {@link NotificationPreferenceResponse}</li>
 * </ul>
 *
 * <p>For preferences, categories without explicit user preferences are
 * populated with defaults (both channels enabled).
 */
@Component
public class NotificationMapper {

    /**
     * Converts a Notification entity to a NotificationResponse DTO.
     *
     * @param entity the Notification entity
     * @return the response DTO
     */
    public NotificationResponse toNotificationResponse(Notification entity) {
        return NotificationResponse.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .category(entity.getCategory().name())
                .title(entity.getTitle())
                .message(entity.getMessage())
                .actionUrl(entity.getActionUrl())
                .read(entity.getRead())
                .readAt(entity.getReadAt())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    /**
     * Converts a list of NotificationPreference entities to a full
     * preference response with defaults for missing categories.
     *
     * @param userId      the user's ID
     * @param preferences the user's existing preferences (may not cover all categories)
     * @return the full preference response with all categories populated
     */
    public NotificationPreferenceResponse toPreferenceResponse(
            Long userId, List<NotificationPreference> preferences) {

        // Build a lookup map from existing preferences
        Map<NotificationCategory, NotificationPreference> existing =
                preferences.stream()
                        .collect(Collectors.toMap(
                                NotificationPreference::getCategory,
                                pref -> pref));

        // Build full category map with defaults for missing categories
        Map<String, CategoryPreference> categoryMap = new java.util.LinkedHashMap<>();

        for (NotificationCategory category : NotificationCategory.values()) {
            NotificationPreference pref = existing.get(category);
            if (pref != null) {
                categoryMap.put(category.name(), CategoryPreference.builder()
                        .emailEnabled(pref.getEmailEnabled())
                        .inAppEnabled(pref.getInAppEnabled())
                        .build());
            } else {
                // Default: both channels enabled (opt-out model)
                categoryMap.put(category.name(), CategoryPreference.builder()
                        .emailEnabled(true)
                        .inAppEnabled(true)
                        .build());
            }
        }

        return NotificationPreferenceResponse.builder()
                .userId(userId)
                .categories(categoryMap)
                .build();
    }
}
```

- **Verify**: `mvn compile -pl backend/notification-service` -> compiles

---

## Task 8 Detail: NotificationService Interface

- **What**: Service interface defining the notification API contract
- **Where**: `backend/notification-service/src/main/java/com/familyhobbies/notificationservice/service/NotificationService.java`
- **Why**: Decouples the controller from the implementation. Enables mocking in controller tests.
- **Content**:

```java
package com.familyhobbies.notificationservice.service;

import com.familyhobbies.notificationservice.dto.request.NotificationPreferenceRequest;
import com.familyhobbies.notificationservice.dto.response.MarkAllReadResponse;
import com.familyhobbies.notificationservice.dto.response.NotificationPreferenceResponse;
import com.familyhobbies.notificationservice.dto.response.NotificationResponse;
import com.familyhobbies.notificationservice.enums.NotificationCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

/**
 * Service contract for the notification REST API.
 *
 * <p>All methods require the authenticated user's ID (from X-User-Id header).
 * Ownership validation is enforced at the service level.
 */
public interface NotificationService {

    /**
     * Returns the user's notifications with optional filters, paginated.
     *
     * @param userId   the authenticated user's ID
     * @param read     optional filter: true = read only, false = unread only, null = all
     * @param category optional filter by notification category
     * @param from     optional start datetime filter (inclusive)
     * @param to       optional end datetime filter (inclusive)
     * @param pageable pagination and sort parameters
     * @return paginated notification responses sorted by createdAt DESC
     */
    Page<NotificationResponse> getMyNotifications(
            Long userId, Boolean read, NotificationCategory category,
            Instant from, Instant to, Pageable pageable);

    /**
     * Returns the count of unread notifications for the user.
     *
     * @param userId the authenticated user's ID
     * @return unread notification count
     */
    long getUnreadCount(Long userId);

    /**
     * Marks a single notification as read. Validates ownership.
     *
     * @param notificationId the notification ID
     * @param userId         the authenticated user's ID
     * @return the updated notification
     * @throws com.familyhobbies.errorhandling.exception.web.ResourceNotFoundException
     *         if notification not found
     * @throws com.familyhobbies.errorhandling.exception.web.ForbiddenException
     *         if notification belongs to another user
     */
    NotificationResponse markAsRead(Long notificationId, Long userId);

    /**
     * Marks all unread notifications as read for the user.
     *
     * @param userId the authenticated user's ID
     * @return count of marked notifications and the timestamp
     */
    MarkAllReadResponse markAllAsRead(Long userId);

    /**
     * Returns the user's notification preferences for all categories.
     * Categories without explicit preferences default to both channels enabled.
     *
     * @param userId the authenticated user's ID
     * @return full preference response
     */
    NotificationPreferenceResponse getPreferences(Long userId);

    /**
     * Batch updates notification preferences for the user. Uses upsert semantics:
     * creates new preference rows for categories that do not exist, updates
     * existing ones.
     *
     * @param userId   the authenticated user's ID
     * @param requests list of preference updates per category
     * @return the updated full preference response
     */
    NotificationPreferenceResponse updatePreferences(
            Long userId, List<NotificationPreferenceRequest> requests);
}
```

- **Verify**: `mvn compile -pl backend/notification-service` -> compiles

---

## Task 9 Detail: NotificationServiceImpl

- **What**: Full implementation of NotificationService with ownership validation, paginated queries, and upsert preferences
- **Where**: `backend/notification-service/src/main/java/com/familyhobbies/notificationservice/service/impl/NotificationServiceImpl.java`
- **Why**: Core business logic for the notification API. Uses repositories from S6-001, error-handling module exceptions for security, and the NotificationMapper for DTO conversion.
- **Content**:

```java
package com.familyhobbies.notificationservice.service.impl;

import com.familyhobbies.errorhandling.exception.web.ForbiddenException;
import com.familyhobbies.errorhandling.exception.web.ResourceNotFoundException;
import com.familyhobbies.notificationservice.dto.request.NotificationPreferenceRequest;
import com.familyhobbies.notificationservice.dto.response.MarkAllReadResponse;
import com.familyhobbies.notificationservice.dto.response.NotificationPreferenceResponse;
import com.familyhobbies.notificationservice.dto.response.NotificationResponse;
import com.familyhobbies.notificationservice.entity.Notification;
import com.familyhobbies.notificationservice.entity.NotificationPreference;
import com.familyhobbies.notificationservice.enums.NotificationCategory;
import com.familyhobbies.notificationservice.mapper.NotificationMapper;
import com.familyhobbies.notificationservice.repository.NotificationPreferenceRepository;
import com.familyhobbies.notificationservice.repository.NotificationRepository;
import com.familyhobbies.notificationservice.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Implementation of {@link NotificationService}.
 *
 * <p>Enforces ownership validation: a user can only access their own
 * notifications and preferences. Uses {@link ForbiddenException} for
 * cross-user access attempts and {@link ResourceNotFoundException} when
 * a notification ID does not exist.
 *
 * <p>Preferences use upsert semantics: if a preference row already exists
 * for a user+category, it is updated; otherwise a new row is created.
 */
@Service
@Transactional(readOnly = true)
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log =
            LoggerFactory.getLogger(NotificationServiceImpl.class);

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final NotificationMapper notificationMapper;

    public NotificationServiceImpl(
            NotificationRepository notificationRepository,
            NotificationPreferenceRepository preferenceRepository,
            NotificationMapper notificationMapper) {
        this.notificationRepository = notificationRepository;
        this.preferenceRepository = preferenceRepository;
        this.notificationMapper = notificationMapper;
    }

    @Override
    public Page<NotificationResponse> getMyNotifications(
            Long userId, Boolean read, NotificationCategory category,
            Instant from, Instant to, Pageable pageable) {

        log.debug("Fetching notifications for userId={}, read={}, "
                + "category={}, from={}, to={}", userId, read, category, from, to);

        Page<Notification> notifications =
                notificationRepository.findByUserIdWithFilters(
                        userId, read, category, from, to, pageable);

        return notifications.map(notificationMapper::toNotificationResponse);
    }

    @Override
    public long getUnreadCount(Long userId) {
        long count = notificationRepository.countByUserIdAndReadFalse(userId);
        log.debug("Unread count for userId={}: {}", userId, count);
        return count;
    }

    @Override
    @Transactional
    public NotificationResponse markAsRead(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Notification non trouvee: " + notificationId));

        if (!notification.getUserId().equals(userId)) {
            throw new ForbiddenException(
                    "Acces interdit a la notification " + notificationId);
        }

        if (!notification.getRead()) {
            notification.setRead(true);
            notification.setReadAt(Instant.now());
            notificationRepository.save(notification);
            log.info("Marked notification {} as read for userId={}",
                    notificationId, userId);
        }

        return notificationMapper.toNotificationResponse(notification);
    }

    @Override
    @Transactional
    public MarkAllReadResponse markAllAsRead(Long userId) {
        Instant readAt = Instant.now();

        List<Notification> unread =
                notificationRepository.findByUserIdAndReadFalse(userId);

        for (Notification notification : unread) {
            notification.setRead(true);
            notification.setReadAt(readAt);
        }
        notificationRepository.saveAll(unread);

        int markedCount = unread.size();
        log.info("Marked {} notifications as read for userId={}",
                markedCount, userId);

        return MarkAllReadResponse.builder()
                .markedCount(markedCount)
                .readAt(readAt)
                .build();
    }

    @Override
    public NotificationPreferenceResponse getPreferences(Long userId) {
        List<NotificationPreference> preferences =
                preferenceRepository.findByUserId(userId);

        return notificationMapper.toPreferenceResponse(userId, preferences);
    }

    @Override
    @Transactional
    public NotificationPreferenceResponse updatePreferences(
            Long userId, List<NotificationPreferenceRequest> requests) {

        for (NotificationPreferenceRequest request : requests) {
            NotificationCategory category =
                    NotificationCategory.valueOf(request.getCategory());

            Optional<NotificationPreference> existingOpt =
                    preferenceRepository.findByUserIdAndCategory(userId, category);

            if (existingOpt.isPresent()) {
                NotificationPreference existing = existingOpt.get();
                existing.setEmailEnabled(request.getEmailEnabled());
                existing.setInAppEnabled(request.getInAppEnabled());
                existing.setUpdatedAt(Instant.now());
                preferenceRepository.save(existing);
                log.debug("Updated preference for userId={}, category={}",
                        userId, category);
            } else {
                NotificationPreference newPref = NotificationPreference.builder()
                        .userId(userId)
                        .category(category)
                        .emailEnabled(request.getEmailEnabled())
                        .inAppEnabled(request.getInAppEnabled())
                        .build();
                preferenceRepository.save(newPref);
                log.debug("Created preference for userId={}, category={}",
                        userId, category);
            }
        }

        // Return the full updated preferences
        List<NotificationPreference> allPreferences =
                preferenceRepository.findByUserId(userId);

        return notificationMapper.toPreferenceResponse(userId, allPreferences);
    }
}
```

- **Verify**: `mvn compile -pl backend/notification-service` -> compiles

**Additional repository method needed**: The `NotificationRepository` from S6-001 provides `findByUserIdOrderByCreatedAtDesc` and `countByUserIdAndReadFalse`, but our filtered query and mark-all-read query need two additional methods. Add these to the repository (additive, does not modify existing methods):

```java
// Add to NotificationRepository.java (S6-001)

import com.familyhobbies.notificationservice.enums.NotificationCategory;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;

/**
 * Filtered notification query with optional read, category, date range.
 * Null parameters are ignored (all-pass).
 */
@Query("""
        SELECT n FROM Notification n
        WHERE n.userId = :userId
          AND (:read IS NULL OR n.read = :read)
          AND (:category IS NULL OR n.category = :category)
          AND (:from IS NULL OR n.createdAt >= :from)
          AND (:to IS NULL OR n.createdAt <= :to)
        ORDER BY n.createdAt DESC
        """)
Page<Notification> findByUserIdWithFilters(
        @Param("userId") Long userId,
        @Param("read") Boolean read,
        @Param("category") NotificationCategory category,
        @Param("from") Instant from,
        @Param("to") Instant to,
        Pageable pageable);

/**
 * Returns all unread notifications for a user. Used by mark-all-as-read.
 */
List<Notification> findByUserIdAndReadFalse(Long userId);
```

---

## Task 10 Detail: NotificationController

- **What**: REST controller exposing 6 notification endpoints, all requiring JWT authentication via gateway X-User-Id header
- **Where**: `backend/notification-service/src/main/java/com/familyhobbies/notificationservice/controller/NotificationController.java`
- **Why**: Entry point for the Angular frontend to interact with notifications. All routes are under `/api/v1/notifications`. The `X-User-Id` header is set by the API gateway after JWT validation.
- **Content**:

```java
package com.familyhobbies.notificationservice.controller;

import com.familyhobbies.notificationservice.dto.request.NotificationPreferenceRequest;
import com.familyhobbies.notificationservice.dto.response.MarkAllReadResponse;
import com.familyhobbies.notificationservice.dto.response.NotificationPreferenceResponse;
import com.familyhobbies.notificationservice.dto.response.NotificationResponse;
import com.familyhobbies.notificationservice.dto.response.UnreadCountResponse;
import com.familyhobbies.notificationservice.enums.NotificationCategory;
import com.familyhobbies.notificationservice.service.NotificationService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * REST controller for notification management.
 *
 * <p>All endpoints require authentication via the API gateway, which
 * validates the JWT token and forwards the user ID in the
 * {@code X-User-Id} header.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET  /api/v1/notifications/me} -- paginated notification list</li>
 *   <li>{@code GET  /api/v1/notifications/me/unread-count} -- unread badge count</li>
 *   <li>{@code PUT  /api/v1/notifications/{id}/read} -- mark single as read</li>
 *   <li>{@code PUT  /api/v1/notifications/read-all} -- mark all as read</li>
 *   <li>{@code GET  /api/v1/notifications/preferences} -- get preferences</li>
 *   <li>{@code PUT  /api/v1/notifications/preferences} -- update preferences</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private static final Logger log =
            LoggerFactory.getLogger(NotificationController.class);

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * GET /api/v1/notifications/me -- paginated notification list.
     *
     * @param userId   injected from X-User-Id header
     * @param read     optional filter: true = read only, false = unread only
     * @param category optional filter by notification category
     * @param from     optional start datetime filter
     * @param to       optional end datetime filter
     * @param pageable pagination params (default: page=0, size=20, sort=createdAt,desc)
     * @return paginated list of notifications
     */
    @GetMapping("/me")
    public ResponseEntity<Page<NotificationResponse>> getMyNotifications(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(required = false) Boolean read,
            @RequestParam(required = false) NotificationCategory category,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {

        log.debug("GET /notifications/me userId={}", userId);

        Page<NotificationResponse> notifications =
                notificationService.getMyNotifications(
                        userId, read, category, from, to, pageable);

        return ResponseEntity.ok(notifications);
    }

    /**
     * GET /api/v1/notifications/me/unread-count -- unread notification count.
     *
     * @param userId injected from X-User-Id header
     * @return the unread count wrapped in UnreadCountResponse
     */
    @GetMapping("/me/unread-count")
    public ResponseEntity<UnreadCountResponse> getUnreadCount(
            @RequestHeader("X-User-Id") Long userId) {

        long count = notificationService.getUnreadCount(userId);

        return ResponseEntity.ok(UnreadCountResponse.builder()
                .count(count)
                .build());
    }

    /**
     * PUT /api/v1/notifications/{id}/read -- mark a single notification as read.
     *
     * @param userId injected from X-User-Id header
     * @param id     the notification ID
     * @return the updated notification with read=true and readAt set
     */
    @PutMapping("/{id}/read")
    public ResponseEntity<NotificationResponse> markAsRead(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long id) {

        log.debug("PUT /notifications/{}/read userId={}", id, userId);

        NotificationResponse response =
                notificationService.markAsRead(id, userId);

        return ResponseEntity.ok(response);
    }

    /**
     * PUT /api/v1/notifications/read-all -- mark all unread notifications as read.
     *
     * @param userId injected from X-User-Id header
     * @return the count of marked notifications and the timestamp
     */
    @PutMapping("/read-all")
    public ResponseEntity<MarkAllReadResponse> markAllAsRead(
            @RequestHeader("X-User-Id") Long userId) {

        log.debug("PUT /notifications/read-all userId={}", userId);

        MarkAllReadResponse response =
                notificationService.markAllAsRead(userId);

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/notifications/preferences -- get the user's notification preferences.
     *
     * @param userId injected from X-User-Id header
     * @return full preferences for all categories
     */
    @GetMapping("/preferences")
    public ResponseEntity<NotificationPreferenceResponse> getPreferences(
            @RequestHeader("X-User-Id") Long userId) {

        NotificationPreferenceResponse response =
                notificationService.getPreferences(userId);

        return ResponseEntity.ok(response);
    }

    /**
     * PUT /api/v1/notifications/preferences -- batch update notification preferences.
     *
     * @param userId   injected from X-User-Id header
     * @param requests list of preference updates per category
     * @return the updated full preferences for all categories
     */
    @PutMapping("/preferences")
    public ResponseEntity<NotificationPreferenceResponse> updatePreferences(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody @Valid List<NotificationPreferenceRequest> requests) {

        log.debug("PUT /notifications/preferences userId={}, categories={}",
                userId, requests.size());

        NotificationPreferenceResponse response =
                notificationService.updatePreferences(userId, requests);

        return ResponseEntity.ok(response);
    }
}
```

- **Verify**: `mvn compile -pl backend/notification-service` -> compiles

---

## Failing Tests (TDD Contract)

Tests are in the companion file: **[S6-003 Notification API Tests](./S6-003-notification-api-tests.md)**

The companion file contains:
- `NotificationMapperTest` -- 5 tests (entity to response mapping, preference mapping with defaults, empty preferences, partial preferences, all categories present)
- `NotificationServiceImplTest` -- 12 tests (get notifications paginated, unread count, mark as read success, mark as read not found, mark as read forbidden, mark as read idempotent, mark all as read, mark all when none unread, get preferences, update preferences upsert create, update preferences upsert update, update preferences mixed)
- `NotificationControllerTest` -- 8 tests (get notifications, unread count, mark read, mark read not found, mark all read, get preferences, update preferences, update preferences validation error)

---

## Acceptance Criteria Checklist

- [ ] `GET /api/v1/notifications/me` returns paginated notifications sorted by createdAt DESC
- [ ] Query params `read`, `category`, `from`, `to` correctly filter results
- [ ] `GET /api/v1/notifications/me/unread-count` returns accurate unread count
- [ ] `PUT /api/v1/notifications/{id}/read` marks notification as read with readAt timestamp
- [ ] `PUT /api/v1/notifications/{id}/read` returns 404 for non-existent notification
- [ ] `PUT /api/v1/notifications/{id}/read` returns 403 for another user's notification
- [ ] `PUT /api/v1/notifications/read-all` marks all unread notifications for the user
- [ ] `GET /api/v1/notifications/preferences` returns all 7 categories with defaults
- [ ] `PUT /api/v1/notifications/preferences` upserts preferences per category
- [ ] All endpoints require `X-User-Id` header
- [ ] DTOs serialize to expected JSON shapes
- [ ] NotificationMapper correctly applies defaults for missing categories
- [ ] All 25 JUnit 5 tests pass green
