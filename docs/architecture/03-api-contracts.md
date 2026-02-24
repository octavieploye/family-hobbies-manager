# 03 - API Contracts

> **Family Hobbies Manager** -- Multi-Association Management Platform
> Architecture Document Series | Document 3 of 12

---

## Table of Contents

1. [Overview](#1-overview)
2. [Gateway Route Configuration](#2-gateway-route-configuration)
3. [Authentication and Headers](#3-authentication-and-headers)
4. [Rate Limiting](#4-rate-limiting)
5. [Common Response Patterns](#5-common-response-patterns)
6. [HTTP Status Codes Summary](#6-http-status-codes-summary)
7. [User Service API](#7-user-service-api)
8. [Association Service API](#8-association-service-api)
9. [Payment Service API](#9-payment-service-api)
10. [Notification Service API](#10-notification-service-api)
11. [Revision History](#11-revision-history)

---

## 1. Overview

All REST APIs in the Family Hobbies Manager platform follow these conventions:

- **Base path**: Every endpoint is versioned under `/api/v1`
- **Content type**: `application/json` for all request and response bodies
- **Date format**: ISO 8601 (`2025-09-15T10:30:00Z`) for all timestamps
- **Date-only format**: ISO 8601 date (`2025-09-15`) for date fields without time
- **Currency**: Amounts in EUR, represented as `BigDecimal` with 2 decimal places
- **Identifiers**: `Long` auto-increment IDs for internal entities
- **Encoding**: UTF-8 throughout
- **Locale**: `fr-FR` is the primary locale; all user-facing messages support French

All requests enter through the **API Gateway** (port 8080), which validates JWT tokens,
applies rate limiting, and forwards requests to downstream services with identity headers.

---

## 2. Gateway Route Configuration

The API Gateway (Spring Cloud Gateway) routes all incoming requests to the appropriate
downstream microservice based on path predicates.

### Route Table

| Route ID | Path Predicate | Downstream Service | Port | Strip Prefix | Notes |
|---|---|---|---|---|---|
| `auth-routes` | `/api/v1/auth/**` | user-service | 8081 | No | Public endpoints (register, login, refresh) |
| `user-routes` | `/api/v1/users/**` | user-service | 8081 | No | JWT required (except admin list) |
| `family-routes` | `/api/v1/families/**` | user-service | 8081 | No | JWT required, FAMILY role |
| `rgpd-routes` | `/api/v1/rgpd/**` | user-service | 8081 | No | JWT required, any authenticated |
| `association-routes` | `/api/v1/associations/**` | association-service | 8082 | No | Public read, ADMIN for sync |
| `activity-routes` | `/api/v1/activities/**` | association-service | 8082 | No | Public read |
| `session-routes` | `/api/v1/sessions/**` | association-service | 8082 | No | Public read |
| `subscription-routes` | `/api/v1/subscriptions/**` | association-service | 8082 | No | JWT required, FAMILY role |
| `attendance-routes` | `/api/v1/attendance/**` | association-service | 8082 | No | JWT required |
| `payment-routes` | `/api/v1/payments/**` | payment-service | 8083 | No | JWT required (webhook is HMAC) |
| `invoice-routes` | `/api/v1/invoices/**` | payment-service | 8083 | No | JWT required |
| `notification-routes` | `/api/v1/notifications/**` | notification-service | 8084 | No | JWT required |

### Gateway YAML Configuration (excerpt)

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: auth-routes
          uri: lb://user-service
          predicates:
            - Path=/api/v1/auth/**
          filters:
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 5
                redis-rate-limiter.burstCapacity: 10

        - id: user-routes
          uri: lb://user-service
          predicates:
            - Path=/api/v1/users/**
          filters:
            - JwtAuthenticationFilter

        - id: family-routes
          uri: lb://user-service
          predicates:
            - Path=/api/v1/families/**
          filters:
            - JwtAuthenticationFilter

        - id: rgpd-routes
          uri: lb://user-service
          predicates:
            - Path=/api/v1/rgpd/**
          filters:
            - JwtAuthenticationFilter

        - id: association-routes
          uri: lb://association-service
          predicates:
            - Path=/api/v1/associations/**

        - id: activity-routes
          uri: lb://association-service
          predicates:
            - Path=/api/v1/activities/**

        - id: session-routes
          uri: lb://association-service
          predicates:
            - Path=/api/v1/sessions/**

        - id: subscription-routes
          uri: lb://association-service
          predicates:
            - Path=/api/v1/subscriptions/**
          filters:
            - JwtAuthenticationFilter

        - id: attendance-routes
          uri: lb://association-service
          predicates:
            - Path=/api/v1/attendance/**
          filters:
            - JwtAuthenticationFilter

        - id: payment-routes
          uri: lb://payment-service
          predicates:
            - Path=/api/v1/payments/**

        - id: invoice-routes
          uri: lb://payment-service
          predicates:
            - Path=/api/v1/invoices/**
          filters:
            - JwtAuthenticationFilter

        - id: notification-routes
          uri: lb://notification-service
          predicates:
            - Path=/api/v1/notifications/**
          filters:
            - JwtAuthenticationFilter
```

---

## 3. Authentication and Headers

### JWT Authentication Flow

1. Client sends `POST /api/v1/auth/login` with credentials
2. User-service returns an `accessToken` (JWT) and `refreshToken`
3. Client includes the JWT in all subsequent requests via the `Authorization` header
4. Gateway validates the JWT signature and expiry
5. Gateway extracts claims and forwards identity headers to downstream services
6. Downstream services use forwarded headers for authorization -- they do not re-validate the token

### Request Header Format

```http
Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxIiw...
Content-Type: application/json
Accept: application/json
X-Correlation-Id: 550e8400-e29b-41d4-a716-446655440000
```

### Forwarded Headers (set by Gateway after JWT validation)

| Header | Type | Description | Example |
|---|---|---|---|
| `X-User-Id` | Long | Authenticated user's ID extracted from JWT `sub` claim | `1` |
| `X-User-Roles` | String | Comma-separated roles from JWT `roles` claim | `FAMILY` or `ASSOCIATION,ADMIN` |
| `X-Correlation-Id` | UUID | Unique request trace ID (generated if not present) | `550e8400-e29b-41d4-a716-446655440000` |

### JWT Token Structure (decoded payload)

```json
{
  "sub": "1",
  "email": "dupont@email.com",
  "roles": ["FAMILY"],
  "familyId": 1,
  "iat": 1694775600,
  "exp": 1694779200,
  "iss": "family-hobbies-manager"
}
```

### Role Hierarchy

| Role | Inherits From | Description |
|---|---|---|
| `FAMILY` | -- | Default role for registered family users |
| `ASSOCIATION` | `FAMILY` | Association managers; can manage sessions, attendance, member lists |
| `ADMIN` | `ASSOCIATION` | Platform administrators; full system access |

---

## 4. Rate Limiting

The API Gateway enforces rate limiting using Spring Cloud Gateway's `RequestRateLimiter`
filter backed by Redis. Limits are applied per client IP for public endpoints and per
authenticated user ID for protected endpoints.

### Rate Limit Configuration

| Endpoint Category | Replenish Rate (req/sec) | Burst Capacity | Key Resolver |
|---|---|---|---|
| Authentication (`/api/v1/auth/**`) | 5 | 10 | Client IP |
| Public reads (`/api/v1/associations/**`) | 20 | 40 | Client IP |
| Authenticated writes | 10 | 20 | User ID |
| Webhook endpoints | 50 | 100 | Client IP |
| Admin endpoints | 30 | 60 | User ID |

### Rate Limit Response Headers

```http
X-RateLimit-Remaining: 17
X-RateLimit-Limit: 20
X-RateLimit-Reset: 1694775660
```

### Rate Limit Exceeded Response

**HTTP 429 Too Many Requests**

```json
{
  "timestamp": "2025-09-15T10:30:00Z",
  "status": 429,
  "error": "Too Many Requests",
  "message": "Rate limit exceeded. Please retry after 1 second.",
  "path": "/api/v1/associations"
}
```

---

## 5. Common Response Patterns

### 5.1 Paginated Response (Spring Page)

All list endpoints that support pagination return a Spring `Page` wrapper.

**Query Parameters for Pagination:**

| Parameter | Type | Default | Description |
|---|---|---|---|
| `page` | int | `0` | Zero-based page index |
| `size` | int | `20` | Number of elements per page (max 100) |
| `sort` | string | varies | Sort field and direction (e.g., `name,asc` or `createdAt,desc`) |

**Response Structure:**

```json
{
  "content": [
    { "id": 1, "name": "Judo Club Lyon" },
    { "id": 2, "name": "Danse Etoile Paris" }
  ],
  "totalElements": 150,
  "totalPages": 8,
  "size": 20,
  "number": 0,
  "first": true,
  "last": false,
  "numberOfElements": 20,
  "empty": false,
  "sort": {
    "sorted": true,
    "unsorted": false,
    "empty": false
  }
}
```

### 5.2 Error Response

All error responses follow a consistent structure across every service.

```json
{
  "timestamp": "2025-09-15T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/v1/families",
  "correlationId": "550e8400-e29b-41d4-a716-446655440000",
  "details": [
    { "field": "name", "message": "must not be blank" },
    { "field": "email", "message": "must be a valid email address" }
  ]
}
```

### 5.3 Empty Successful Response

Operations that succeed without a response body return:

- **HTTP 204 No Content** with an empty body (for DELETE operations, mark-as-read, etc.)

---

## 6. HTTP Status Codes Summary

| Code | Name | When Used |
|---|---|---|
| `200` | OK | Successful GET, PUT, or PATCH request that returns data |
| `201` | Created | Successful POST that creates a new resource; includes `Location` header |
| `204` | No Content | Successful DELETE or action that returns no body |
| `400` | Bad Request | Validation failure, malformed JSON, missing required fields |
| `401` | Unauthorized | Missing or invalid JWT token, expired token |
| `403` | Forbidden | Valid JWT but insufficient role/permissions for the resource |
| `404` | Not Found | Resource does not exist or user has no access to it |
| `409` | Conflict | Duplicate resource (e.g., email already registered, duplicate subscription) |
| `422` | Unprocessable Entity | Syntactically valid but semantically invalid (e.g., end date before start date) |
| `429` | Too Many Requests | Rate limit exceeded |
| `500` | Internal Server Error | Unexpected server error; details logged server-side, generic message returned |
| `502` | Bad Gateway | Downstream service unavailable (circuit breaker open) |
| `503` | Service Unavailable | Service temporarily unavailable (maintenance, overload) |

---

## 7. User Service API

**Service**: user-service
**Port**: 8081
**Database**: `familyhobbies_users`
**Base Routes**: `/api/v1/auth`, `/api/v1/users`, `/api/v1/families`, `/api/v1/rgpd`

### 7.1 Authentication Endpoints

#### POST /api/v1/auth/register

Register a new user account. Default role is `FAMILY`.

| Property | Value |
|---|---|
| **Auth** | PUBLIC |
| **Rate Limit** | 5 req/sec per IP |

**Request Body:**

```json
{
  "email": "dupont@email.com",
  "password": "SecureP@ss1",
  "firstName": "Jean",
  "lastName": "Dupont",
  "phone": "+33612345678"
}
```

| Field | Type | Required | Validation |
|---|---|---|---|
| `email` | string | Yes | Valid email format, max 255 chars, unique |
| `password` | string | Yes | Min 8 chars, 1 uppercase, 1 lowercase, 1 digit, 1 special char |
| `firstName` | string | Yes | Max 100 chars, not blank |
| `lastName` | string | Yes | Max 100 chars, not blank |
| `phone` | string | No | E.164 format (e.g., `+33612345678`) |

**Response 201 Created:**

```json
{
  "id": 1,
  "email": "dupont@email.com",
  "firstName": "Jean",
  "lastName": "Dupont",
  "phone": "+33612345678",
  "role": "FAMILY",
  "status": "ACTIVE",
  "emailVerified": false,
  "createdAt": "2025-09-15T10:00:00Z"
}
```

**Response Headers:**

```http
Location: /api/v1/users/1
```

**Error Responses:**

| Status | Condition |
|---|---|
| `400` | Validation failure (blank fields, invalid email format, weak password) |
| `409` | Email already registered |

---

#### POST /api/v1/auth/login

Authenticate user and return JWT access token with refresh token.

| Property | Value |
|---|---|
| **Auth** | PUBLIC |
| **Rate Limit** | 5 req/sec per IP |

**Request Body:**

```json
{
  "email": "dupont@email.com",
  "password": "SecureP@ss1"
}
```

| Field | Type | Required | Validation |
|---|---|---|---|
| `email` | string | Yes | Valid email format |
| `password` | string | Yes | Not blank |

**Response 200 OK:**

```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxIiwiZW1haWwiOiJkdXBvbnRAZW1haWwuY29tIiwicm9sZXMiOlsiRkFNSUxZIl0sImZhbWlseUlkIjoxLCJpYXQiOjE2OTQ3NzU2MDAsImV4cCI6MTY5NDc3OTIwMCwiaXNzIjoiZmFtaWx5LWhvYmJpZXMtbWFuYWdlciJ9.signature",
  "refreshToken": "dGhpcyBpcyBhIHJlZnJlc2ggdG9rZW4gZXhhbXBsZQ==",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

| Field | Type | Description |
|---|---|---|
| `accessToken` | string | JWT token, valid for 1 hour |
| `refreshToken` | string | Opaque refresh token, valid for 30 days |
| `tokenType` | string | Always `Bearer` |
| `expiresIn` | int | Token lifetime in seconds |

**Error Responses:**

| Status | Condition |
|---|---|
| `401` | Invalid email or password |
| `403` | Account suspended or soft-deleted |

---

#### POST /api/v1/auth/refresh

Obtain a new access token using a valid refresh token.

| Property | Value |
|---|---|
| **Auth** | PUBLIC |
| **Rate Limit** | 5 req/sec per IP |

**Request Body:**

```json
{
  "refreshToken": "dGhpcyBpcyBhIHJlZnJlc2ggdG9rZW4gZXhhbXBsZQ=="
}
```

**Response 200 OK:**

```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiIs...",
  "refreshToken": "bmV3IHJlZnJlc2ggdG9rZW4gYWZ0ZXIgcm90YXRpb24=",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

**Error Responses:**

| Status | Condition |
|---|---|
| `401` | Refresh token expired, revoked, or invalid |

---

#### POST /api/v1/auth/logout

Revoke the current refresh token, effectively logging out.

| Property | Value |
|---|---|
| **Auth** | FAMILY, ASSOCIATION, ADMIN |

**Request Body:**

```json
{
  "refreshToken": "dGhpcyBpcyBhIHJlZnJlc2ggdG9rZW4gZXhhbXBsZQ=="
}
```

**Response 204 No Content** (empty body)

**Error Responses:**

| Status | Condition |
|---|---|
| `401` | Missing or invalid JWT |

---

### 7.2 User Management Endpoints

#### GET /api/v1/users/me

Get the current authenticated user's profile.

| Property | Value |
|---|---|
| **Auth** | FAMILY, ASSOCIATION, ADMIN |

**Response 200 OK:**

```json
{
  "id": 1,
  "email": "dupont@email.com",
  "firstName": "Jean",
  "lastName": "Dupont",
  "phone": "+33612345678",
  "role": "FAMILY",
  "status": "ACTIVE",
  "emailVerified": true,
  "familyId": 1,
  "lastLoginAt": "2025-09-15T10:00:00Z",
  "createdAt": "2025-09-01T08:00:00Z",
  "updatedAt": "2025-09-10T14:30:00Z"
}
```

**Error Responses:**

| Status | Condition |
|---|---|
| `401` | Missing or invalid JWT |

---

#### PUT /api/v1/users/me

Update the current authenticated user's profile.

| Property | Value |
|---|---|
| **Auth** | FAMILY, ASSOCIATION, ADMIN |

**Request Body:**

```json
{
  "firstName": "Jean-Pierre",
  "lastName": "Dupont",
  "phone": "+33698765432"
}
```

| Field | Type | Required | Validation |
|---|---|---|---|
| `firstName` | string | No | Max 100 chars |
| `lastName` | string | No | Max 100 chars |
| `phone` | string | No | E.164 format |

**Response 200 OK:**

```json
{
  "id": 1,
  "email": "dupont@email.com",
  "firstName": "Jean-Pierre",
  "lastName": "Dupont",
  "phone": "+33698765432",
  "role": "FAMILY",
  "status": "ACTIVE",
  "emailVerified": true,
  "familyId": 1,
  "lastLoginAt": "2025-09-15T10:00:00Z",
  "createdAt": "2025-09-01T08:00:00Z",
  "updatedAt": "2025-09-15T11:00:00Z"
}
```

**Error Responses:**

| Status | Condition |
|---|---|
| `400` | Validation failure |
| `401` | Missing or invalid JWT |

---

#### DELETE /api/v1/users/me

Soft-delete the current user's account (RGPD right to erasure). The account is marked
as `DELETED`, personal data is anonymized, and the user is logged out. This action is
irreversible after the 30-day grace period.

| Property | Value |
|---|---|
| **Auth** | FAMILY, ASSOCIATION, ADMIN |

**Response 204 No Content** (empty body)

**Error Responses:**

| Status | Condition |
|---|---|
| `401` | Missing or invalid JWT |
| `409` | Account has active subscriptions with pending payments |

---

#### GET /api/v1/users/{id}

Get a user by ID. Restricted to administrators.

| Property | Value |
|---|---|
| **Auth** | ADMIN |

**Path Parameters:**

| Parameter | Type | Description |
|---|---|---|
| `id` | Long | User ID |

**Response 200 OK:**

```json
{
  "id": 42,
  "email": "martin@email.com",
  "firstName": "Sophie",
  "lastName": "Martin",
  "phone": "+33611223344",
  "role": "FAMILY",
  "status": "ACTIVE",
  "emailVerified": true,
  "familyId": 12,
  "lastLoginAt": "2025-09-14T18:00:00Z",
  "createdAt": "2025-08-20T09:00:00Z",
  "updatedAt": "2025-09-14T18:00:00Z"
}
```

**Error Responses:**

| Status | Condition |
|---|---|
| `401` | Missing or invalid JWT |
| `403` | Caller does not have ADMIN role |
| `404` | User not found |

---

#### GET /api/v1/users

List and search users with pagination. Restricted to administrators.

| Property | Value |
|---|---|
| **Auth** | ADMIN |

**Query Parameters:**

| Parameter | Type | Default | Description |
|---|---|---|---|
| `search` | string | -- | Search by email, first name, or last name (partial match) |
| `role` | string | -- | Filter by role: `FAMILY`, `ASSOCIATION`, `ADMIN` |
| `status` | string | -- | Filter by status: `ACTIVE`, `SUSPENDED`, `DELETED` |
| `page` | int | `0` | Page number (zero-based) |
| `size` | int | `20` | Page size (max 100) |
| `sort` | string | `createdAt,desc` | Sort field and direction |

**Example Request:**

```http
GET /api/v1/users?search=dupont&role=FAMILY&status=ACTIVE&page=0&size=20&sort=lastName,asc
```

**Response 200 OK:**

```json
{
  "content": [
    {
      "id": 1,
      "email": "dupont@email.com",
      "firstName": "Jean",
      "lastName": "Dupont",
      "role": "FAMILY",
      "status": "ACTIVE",
      "emailVerified": true,
      "createdAt": "2025-09-01T08:00:00Z"
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "size": 20,
  "number": 0,
  "first": true,
  "last": true,
  "numberOfElements": 1,
  "empty": false
}
```

**Error Responses:**

| Status | Condition |
|---|---|
| `401` | Missing or invalid JWT |
| `403` | Caller does not have ADMIN role |

---

### 7.3 Family Management Endpoints

#### POST /api/v1/families

Create a new family group for the authenticated user.

| Property | Value |
|---|---|
| **Auth** | FAMILY |

**Request Body:**

```json
{
  "name": "Famille Dupont"
}
```

| Field | Type | Required | Validation |
|---|---|---|---|
| `name` | string | Yes | Max 150 chars, not blank |

**Response 201 Created:**

```json
{
  "id": 1,
  "name": "Famille Dupont",
  "createdBy": 1,
  "members": [],
  "createdAt": "2025-09-15T10:00:00Z",
  "updatedAt": "2025-09-15T10:00:00Z"
}
```

**Response Headers:**

```http
Location: /api/v1/families/1
```

**Error Responses:**

| Status | Condition |
|---|---|
| `400` | Validation failure (blank name) |
| `401` | Missing or invalid JWT |
| `409` | User already belongs to a family |

---

#### GET /api/v1/families/me

Get the current authenticated user's family with all members.

| Property | Value |
|---|---|
| **Auth** | FAMILY |

**Response 200 OK:**

```json
{
  "id": 1,
  "name": "Famille Dupont",
  "createdBy": 1,
  "members": [
    {
      "id": 1,
      "familyId": 1,
      "firstName": "Lucas",
      "lastName": "Dupont",
      "dateOfBirth": "2015-03-20",
      "age": 10,
      "relationship": "CHILD",
      "medicalNote": "Allergique aux arachides",
      "createdAt": "2025-09-15T10:05:00Z"
    },
    {
      "id": 2,
      "familyId": 1,
      "firstName": "Emma",
      "lastName": "Dupont",
      "dateOfBirth": "2012-07-11",
      "age": 13,
      "relationship": "CHILD",
      "medicalNote": null,
      "createdAt": "2025-09-15T10:10:00Z"
    }
  ],
  "createdAt": "2025-09-15T10:00:00Z",
  "updatedAt": "2025-09-15T10:10:00Z"
}
```

**Error Responses:**

| Status | Condition |
|---|---|
| `401` | Missing or invalid JWT |
| `404` | User does not belong to any family |

---

#### GET /api/v1/families/{id}

Get a family by ID. FAMILY users can only access their own family. ADMIN can access any.

| Property | Value |
|---|---|
| **Auth** | FAMILY (own), ADMIN |

**Path Parameters:**

| Parameter | Type | Description |
|---|---|---|
| `id` | Long | Family ID |

**Response 200 OK:** Same structure as `GET /api/v1/families/me`.

**Error Responses:**

| Status | Condition |
|---|---|
| `401` | Missing or invalid JWT |
| `403` | FAMILY user attempting to access another family |
| `404` | Family not found |

---

#### PUT /api/v1/families/{id}

Update a family's information.

| Property | Value |
|---|---|
| **Auth** | FAMILY (own), ADMIN |

**Path Parameters:**

| Parameter | Type | Description |
|---|---|---|
| `id` | Long | Family ID |

**Request Body:**

```json
{
  "name": "Famille Dupont-Martin"
}
```

**Response 200 OK:**

```json
{
  "id": 1,
  "name": "Famille Dupont-Martin",
  "createdBy": 1,
  "members": [ ... ],
  "createdAt": "2025-09-15T10:00:00Z",
  "updatedAt": "2025-09-15T12:00:00Z"
}
```

**Error Responses:**

| Status | Condition |
|---|---|
| `400` | Validation failure |
| `401` | Missing or invalid JWT |
| `403` | FAMILY user attempting to update another family |
| `404` | Family not found |

---

#### POST /api/v1/families/{familyId}/members

Add a family member (child, spouse, etc.) to a family.

| Property | Value |
|---|---|
| **Auth** | FAMILY (own) |

**Path Parameters:**

| Parameter | Type | Description |
|---|---|---|
| `familyId` | Long | Family ID |

**Request Body:**

```json
{
  "firstName": "Lucas",
  "lastName": "Dupont",
  "dateOfBirth": "2015-03-20",
  "relationship": "CHILD",
  "medicalNote": "Allergique aux arachides"
}
```

| Field | Type | Required | Validation |
|---|---|---|---|
| `firstName` | string | Yes | Max 100 chars, not blank |
| `lastName` | string | Yes | Max 100 chars, not blank |
| `dateOfBirth` | date | Yes | ISO 8601 date, must be in the past |
| `relationship` | enum | Yes | One of: `CHILD`, `SPOUSE`, `PARENT`, `SIBLING`, `OTHER` |
| `medicalNote` | string | No | Max 500 chars, encrypted at rest |

**Response 201 Created:**

```json
{
  "id": 1,
  "familyId": 1,
  "firstName": "Lucas",
  "lastName": "Dupont",
  "dateOfBirth": "2015-03-20",
  "age": 10,
  "relationship": "CHILD",
  "medicalNote": "Allergique aux arachides",
  "createdAt": "2025-09-15T10:05:00Z"
}
```

**Response Headers:**

```http
Location: /api/v1/families/1/members/1
```

**Error Responses:**

| Status | Condition |
|---|---|
| `400` | Validation failure (blank name, future date of birth) |
| `401` | Missing or invalid JWT |
| `403` | FAMILY user attempting to add member to another family |
| `404` | Family not found |

---

#### GET /api/v1/families/{familyId}/members

List all members of a family.

| Property | Value |
|---|---|
| **Auth** | FAMILY (own), ADMIN |

**Path Parameters:**

| Parameter | Type | Description |
|---|---|---|
| `familyId` | Long | Family ID |

**Response 200 OK:**

```json
[
  {
    "id": 1,
    "familyId": 1,
    "firstName": "Lucas",
    "lastName": "Dupont",
    "dateOfBirth": "2015-03-20",
    "age": 10,
    "relationship": "CHILD",
    "medicalNote": "Allergique aux arachides",
    "createdAt": "2025-09-15T10:05:00Z"
  },
  {
    "id": 2,
    "familyId": 1,
    "firstName": "Emma",
    "lastName": "Dupont",
    "dateOfBirth": "2012-07-11",
    "age": 13,
    "relationship": "CHILD",
    "medicalNote": null,
    "createdAt": "2025-09-15T10:10:00Z"
  }
]
```

**Error Responses:**

| Status | Condition |
|---|---|
| `401` | Missing or invalid JWT |
| `403` | FAMILY user attempting to access another family's members |
| `404` | Family not found |

---

#### PUT /api/v1/families/{familyId}/members/{memberId}

Update a family member's information.

| Property | Value |
|---|---|
| **Auth** | FAMILY (own) |

**Path Parameters:**

| Parameter | Type | Description |
|---|---|---|
| `familyId` | Long | Family ID |
| `memberId` | Long | Member ID |

**Request Body:**

```json
{
  "firstName": "Lucas",
  "lastName": "Dupont",
  "dateOfBirth": "2015-03-20",
  "relationship": "CHILD",
  "medicalNote": "Allergique aux arachides et au gluten"
}
```

**Response 200 OK:**

```json
{
  "id": 1,
  "familyId": 1,
  "firstName": "Lucas",
  "lastName": "Dupont",
  "dateOfBirth": "2015-03-20",
  "age": 10,
  "relationship": "CHILD",
  "medicalNote": "Allergique aux arachides et au gluten",
  "createdAt": "2025-09-15T10:05:00Z",
  "updatedAt": "2025-09-15T14:00:00Z"
}
```

**Error Responses:**

| Status | Condition |
|---|---|
| `400` | Validation failure |
| `401` | Missing or invalid JWT |
| `403` | FAMILY user attempting to update another family's member |
| `404` | Family or member not found |

---

#### DELETE /api/v1/families/{familyId}/members/{memberId}

Remove a family member. Active subscriptions for this member must be cancelled first.

| Property | Value |
|---|---|
| **Auth** | FAMILY (own) |

**Path Parameters:**

| Parameter | Type | Description |
|---|---|---|
| `familyId` | Long | Family ID |
| `memberId` | Long | Member ID |

**Response 204 No Content** (empty body)

**Error Responses:**

| Status | Condition |
|---|---|
| `401` | Missing or invalid JWT |
| `403` | FAMILY user attempting to delete another family's member |
| `404` | Family or member not found |
| `409` | Member has active subscriptions |

---

### 7.4 RGPD Compliance Endpoints

#### POST /api/v1/rgpd/consent

Grant or revoke a specific consent type.

| Property | Value |
|---|---|
| **Auth** | FAMILY, ASSOCIATION, ADMIN |

**Request Body:**

```json
{
  "consentType": "MARKETING_EMAIL",
  "granted": true
}
```

| Field | Type | Required | Validation |
|---|---|---|---|
| `consentType` | enum | Yes | One of: `MARKETING_EMAIL`, `DATA_SHARING`, `ANALYTICS`, `THIRD_PARTY` |
| `granted` | boolean | Yes | `true` to grant, `false` to revoke |

**Response 200 OK:**

```json
{
  "id": 1,
  "userId": 1,
  "consentType": "MARKETING_EMAIL",
  "granted": true,
  "grantedAt": "2025-09-15T10:00:00Z",
  "ipAddress": "192.168.1.100",
  "userAgent": "Mozilla/5.0..."
}
```

**Error Responses:**

| Status | Condition |
|---|---|
| `400` | Invalid consent type |
| `401` | Missing or invalid JWT |

---

#### GET /api/v1/rgpd/consent

List all consent records for the current user.

| Property | Value |
|---|---|
| **Auth** | FAMILY, ASSOCIATION, ADMIN |

**Response 200 OK:**

```json
[
  {
    "id": 1,
    "userId": 1,
    "consentType": "MARKETING_EMAIL",
    "granted": true,
    "grantedAt": "2025-09-15T10:00:00Z",
    "ipAddress": "192.168.1.100",
    "userAgent": "Mozilla/5.0..."
  },
  {
    "id": 2,
    "userId": 1,
    "consentType": "ANALYTICS",
    "granted": false,
    "grantedAt": "2025-09-15T10:00:00Z",
    "revokedAt": "2025-09-20T08:00:00Z",
    "ipAddress": "192.168.1.100",
    "userAgent": "Mozilla/5.0..."
  }
]
```

---

#### POST /api/v1/rgpd/data-export

Request a full export of the user's personal data in JSON format. The export is
generated asynchronously and the user is notified via email when ready.

| Property | Value |
|---|---|
| **Auth** | FAMILY, ASSOCIATION, ADMIN |

**Request Body:** Empty or `{}`

**Response 202 Accepted:**

```json
{
  "requestId": "exp-550e8400-e29b-41d4-a716-446655440000",
  "status": "PROCESSING",
  "requestedAt": "2025-09-15T10:00:00Z",
  "estimatedCompletionMinutes": 30,
  "message": "Your data export request has been received. You will be notified by email when the export is ready for download."
}
```

**Error Responses:**

| Status | Condition |
|---|---|
| `401` | Missing or invalid JWT |
| `429` | Export already requested within the last 24 hours |

---

#### POST /api/v1/rgpd/data-deletion

Request permanent deletion of the user's account and all associated data.
A 30-day grace period applies before irreversible deletion.

| Property | Value |
|---|---|
| **Auth** | FAMILY, ASSOCIATION, ADMIN |

**Request Body:**

```json
{
  "confirmEmail": "dupont@email.com",
  "reason": "Je ne souhaite plus utiliser le service"
}
```

| Field | Type | Required | Validation |
|---|---|---|---|
| `confirmEmail` | string | Yes | Must match the authenticated user's email |
| `reason` | string | No | Max 500 chars |

**Response 202 Accepted:**

```json
{
  "requestId": "del-550e8400-e29b-41d4-a716-446655440000",
  "status": "PENDING",
  "requestedAt": "2025-09-15T10:00:00Z",
  "scheduledDeletionAt": "2025-10-15T10:00:00Z",
  "message": "Your account deletion request has been received. Your data will be permanently deleted on 2025-10-15. You may cancel this request by logging in before that date."
}
```

**Error Responses:**

| Status | Condition |
|---|---|
| `400` | Confirmation email does not match |
| `401` | Missing or invalid JWT |
| `409` | Deletion request already pending |

---

## 8. Association Service API

**Service**: association-service
**Port**: 8082
**Database**: `familyhobbies_associations`
**Base Routes**: `/api/v1/associations`, `/api/v1/activities`, `/api/v1/sessions`, `/api/v1/subscriptions`, `/api/v1/attendance`

### 8.1 Association Directory Endpoints

#### GET /api/v1/associations

Search and filter associations with pagination. Publicly accessible.

| Property | Value |
|---|---|
| **Auth** | PUBLIC |

**Query Parameters:**

| Parameter | Type | Default | Description |
|---|---|---|---|
| `search` | string | -- | Full-text search on name, description |
| `city` | string | -- | Filter by city name (exact match, case-insensitive) |
| `postalCode` | string | -- | Filter by postal code (exact match) |
| `department` | string | -- | Filter by department code (e.g., `69`, `75`) |
| `category` | enum | -- | Filter by category (see values below) |
| `page` | int | `0` | Page number (zero-based) |
| `size` | int | `20` | Page size (max 100) |
| `sort` | string | `name,asc` | Sort field and direction |

**Category Values:** `SPORT`, `DANCE`, `MUSIC`, `THEATER`, `ART`, `EDUCATION`, `WELLNESS`, `NATURE`, `CULTURE`, `OTHER`

**Example Request:**

```http
GET /api/v1/associations?city=Lyon&category=SPORT&postalCode=69001&search=judo&page=0&size=20&sort=name,asc
```

**Response 200 OK:**

```json
{
  "content": [
    {
      "id": 1,
      "name": "Judo Club Lyon",
      "slug": "judo-club-lyon",
      "city": "Lyon",
      "postalCode": "69001",
      "department": "69",
      "category": "SPORT",
      "logoUrl": "https://cdn.familyhobbies.fr/logos/judo-club-lyon.png",
      "activityCount": 5,
      "memberCount": 120,
      "status": "ACTIVE"
    },
    {
      "id": 2,
      "name": "Lyon Judo Academie",
      "slug": "lyon-judo-academie",
      "city": "Lyon",
      "postalCode": "69003",
      "department": "69",
      "category": "SPORT",
      "logoUrl": null,
      "activityCount": 3,
      "memberCount": 85,
      "status": "ACTIVE"
    }
  ],
  "totalElements": 2,
  "totalPages": 1,
  "size": 20,
  "number": 0,
  "first": true,
  "last": true,
  "numberOfElements": 2,
  "empty": false
}
```

---

#### GET /api/v1/associations/{id}

Get full association details including activities list.

| Property | Value |
|---|---|
| **Auth** | PUBLIC |

**Path Parameters:**

| Parameter | Type | Description |
|---|---|---|
| `id` | Long | Association ID |

**Response 200 OK:**

```json
{
  "id": 1,
  "name": "Judo Club Lyon",
  "slug": "judo-club-lyon",
  "description": "Club de judo affilie a la FFJDA, accueillant les enfants des 4 ans et les adultes. Cours de judo, jujitsu et self-defense.",
  "address": "12 rue des Sports",
  "city": "Lyon",
  "postalCode": "69001",
  "department": "69",
  "region": "Auvergne-Rhone-Alpes",
  "phone": "+33478123456",
  "email": "contact@judoclublyon.fr",
  "website": "https://judoclublyon.fr",
  "logoUrl": "https://cdn.familyhobbies.fr/logos/judo-club-lyon.png",
  "bannerUrl": "https://cdn.familyhobbies.fr/banners/judo-club-lyon.jpg",
  "category": "SPORT",
  "status": "ACTIVE",
  "helloassoSlug": "judo-club-lyon",
  "helloassoUrl": "https://www.helloasso.com/associations/judo-club-lyon",
  "activities": [
    {
      "id": 1,
      "name": "Judo Eveil (4-5 ans)",
      "description": "Initiation au judo pour les tout-petits",
      "ageMin": 4,
      "ageMax": 5,
      "annualFee": 180.00,
      "sessionCount": 3
    },
    {
      "id": 2,
      "name": "Judo Poussins (6-7 ans)",
      "description": "Judo pour enfants debutants et intermediaires",
      "ageMin": 6,
      "ageMax": 7,
      "annualFee": 200.00,
      "sessionCount": 4
    },
    {
      "id": 3,
      "name": "Judo Adultes",
      "description": "Cours tous niveaux pour adultes",
      "ageMin": 16,
      "ageMax": null,
      "annualFee": 280.00,
      "sessionCount": 5
    }
  ],
  "createdAt": "2025-08-01T09:00:00Z",
  "updatedAt": "2025-09-10T14:00:00Z"
}
```

**Error Responses:**

| Status | Condition |
|---|---|
| `404` | Association not found |

---

#### POST /api/v1/associations/sync

Trigger a manual synchronization of association data from HelloAsso directory.
This is an admin-only operation that updates the local cache.

| Property | Value |
|---|---|
| **Auth** | ADMIN |

**Request Body:** Empty or `{}`

**Response 202 Accepted:**

```json
{
  "syncId": "sync-550e8400-e29b-41d4-a716-446655440000",
  "status": "STARTED",
  "startedAt": "2025-09-15T10:00:00Z",
  "message": "HelloAsso directory synchronization started. This may take several minutes."
}
```

**Error Responses:**

| Status | Condition |
|---|---|
| `401` | Missing or invalid JWT |
| `403` | Caller does not have ADMIN role |
| `409` | Sync already in progress |

---

#### GET /api/v1/associations/{id}/activities

List all activities offered by an association.

| Property | Value |
|---|---|
| **Auth** | PUBLIC |

**Path Parameters:**

| Parameter | Type | Description |
|---|---|---|
| `id` | Long | Association ID |

**Response 200 OK:**

```json
[
  {
    "id": 1,
    "associationId": 1,
    "name": "Judo Eveil (4-5 ans)",
    "description": "Initiation au judo pour les tout-petits. Developpement de la motricite et decouverte des valeurs du judo.",
    "category": "SPORT",
    "ageMin": 4,
    "ageMax": 5,
    "maxParticipants": 20,
    "annualFee": 180.00,
    "registrationFee": 15.00,
    "season": "2025-2026",
    "status": "OPEN",
    "sessions": [
      {
        "id": 1,
        "dayOfWeek": "WEDNESDAY",
        "startTime": "14:00",
        "endTime": "15:00",
        "location": "Dojo Principal",
        "instructor": "Maitre Tanaka"
      },
      {
        "id": 2,
        "dayOfWeek": "SATURDAY",
        "startTime": "10:00",
        "endTime": "11:00",
        "location": "Dojo Principal",
        "instructor": "Maitre Tanaka"
      }
    ],
    "createdAt": "2025-08-01T09:00:00Z"
  }
]
```

**Error Responses:**

| Status | Condition |
|---|---|
| `404` | Association not found |

---

### 8.2 Activities and Sessions Endpoints

#### GET /api/v1/activities/{id}

Get full activity details including all sessions.

| Property | Value |
|---|---|
| **Auth** | PUBLIC |

**Path Parameters:**

| Parameter | Type | Description |
|---|---|---|
| `id` | Long | Activity ID |

**Response 200 OK:**

```json
{
  "id": 3,
  "associationId": 1,
  "associationName": "Judo Club Lyon",
  "name": "Judo Adultes",
  "description": "Cours tous niveaux pour adultes. Technique, randori, et preparation aux competitions.",
  "category": "SPORT",
  "ageMin": 16,
  "ageMax": null,
  "maxParticipants": 30,
  "currentParticipants": 22,
  "annualFee": 280.00,
  "registrationFee": 20.00,
  "season": "2025-2026",
  "status": "OPEN",
  "sessions": [
    {
      "id": 7,
      "activityId": 3,
      "dayOfWeek": "MONDAY",
      "startTime": "19:00",
      "endTime": "20:30",
      "location": "Dojo Principal",
      "instructor": "Maitre Tanaka",
      "maxParticipants": 30,
      "recurrence": "WEEKLY",
      "startDate": "2025-09-01",
      "endDate": "2026-06-30"
    },
    {
      "id": 8,
      "activityId": 3,
      "dayOfWeek": "WEDNESDAY",
      "startTime": "19:00",
      "endTime": "20:30",
      "location": "Dojo Principal",
      "instructor": "Maitre Tanaka",
      "maxParticipants": 30,
      "recurrence": "WEEKLY",
      "startDate": "2025-09-01",
      "endDate": "2026-06-30"
    }
  ],
  "createdAt": "2025-08-01T09:00:00Z",
  "updatedAt": "2025-09-10T14:00:00Z"
}
```

**Error Responses:**

| Status | Condition |
|---|---|
| `404` | Activity not found |

---

#### GET /api/v1/activities/{id}/sessions

List all sessions for a given activity.

| Property | Value |
|---|---|
| **Auth** | PUBLIC |

**Path Parameters:**

| Parameter | Type | Description |
|---|---|---|
| `id` | Long | Activity ID |

**Response 200 OK:**

```json
[
  {
    "id": 7,
    "activityId": 3,
    "activityName": "Judo Adultes",
    "dayOfWeek": "MONDAY",
    "startTime": "19:00",
    "endTime": "20:30",
    "location": "Dojo Principal",
    "instructor": "Maitre Tanaka",
    "maxParticipants": 30,
    "currentParticipants": 22,
    "recurrence": "WEEKLY",
    "startDate": "2025-09-01",
    "endDate": "2026-06-30"
  },
  {
    "id": 8,
    "activityId": 3,
    "activityName": "Judo Adultes",
    "dayOfWeek": "WEDNESDAY",
    "startTime": "19:00",
    "endTime": "20:30",
    "location": "Dojo Principal",
    "instructor": "Maitre Tanaka",
    "maxParticipants": 30,
    "currentParticipants": 22,
    "recurrence": "WEEKLY",
    "startDate": "2025-09-01",
    "endDate": "2026-06-30"
  }
]
```

**Error Responses:**

| Status | Condition |
|---|---|
| `404` | Activity not found |

---

#### GET /api/v1/sessions/{id}

Get a single session's full details.

| Property | Value |
|---|---|
| **Auth** | PUBLIC |

**Path Parameters:**

| Parameter | Type | Description |
|---|---|---|
| `id` | Long | Session ID |

**Response 200 OK:**

```json
{
  "id": 7,
  "activityId": 3,
  "activityName": "Judo Adultes",
  "associationId": 1,
  "associationName": "Judo Club Lyon",
  "dayOfWeek": "MONDAY",
  "startTime": "19:00",
  "endTime": "20:30",
  "location": "Dojo Principal",
  "instructor": "Maitre Tanaka",
  "maxParticipants": 30,
  "currentParticipants": 22,
  "recurrence": "WEEKLY",
  "startDate": "2025-09-01",
  "endDate": "2026-06-30",
  "nextOccurrence": "2025-09-22",
  "createdAt": "2025-08-01T09:00:00Z",
  "updatedAt": "2025-09-10T14:00:00Z"
}
```

**Error Responses:**

| Status | Condition |
|---|---|
| `404` | Session not found |

---

### 8.3 Subscription Endpoints

#### POST /api/v1/subscriptions

Create a subscription (inscription) for a family member to an activity session.

| Property | Value |
|---|---|
| **Auth** | FAMILY |

**Request Body:**

```json
{
  "familyMemberId": 1,
  "associationId": 1,
  "activityId": 3,
  "sessionId": 7,
  "season": "2025-2026"
}
```

| Field | Type | Required | Validation |
|---|---|---|---|
| `familyMemberId` | Long | Yes | Must belong to the caller's family |
| `associationId` | Long | Yes | Must exist and be ACTIVE |
| `activityId` | Long | Yes | Must belong to the specified association |
| `sessionId` | Long | Yes | Must belong to the specified activity |
| `season` | string | Yes | Format `YYYY-YYYY` (e.g., `2025-2026`) |

**Response 201 Created:**

```json
{
  "id": 1,
  "familyMemberId": 1,
  "familyMemberName": "Lucas Dupont",
  "familyId": 1,
  "associationId": 1,
  "associationName": "Judo Club Lyon",
  "activityId": 3,
  "activityName": "Judo Adultes",
  "sessionId": 7,
  "season": "2025-2026",
  "status": "PENDING",
  "startDate": "2025-09-01",
  "endDate": "2026-06-30",
  "annualFee": 280.00,
  "registrationFee": 20.00,
  "totalAmount": 300.00,
  "paymentStatus": "UNPAID",
  "createdAt": "2025-09-15T10:30:00Z"
}
```

**Response Headers:**

```http
Location: /api/v1/subscriptions/1
```

**Error Responses:**

| Status | Condition |
|---|---|
| `400` | Validation failure (missing fields, invalid season format) |
| `401` | Missing or invalid JWT |
| `403` | Family member does not belong to the caller's family |
| `404` | Association, activity, or session not found |
| `409` | Member already subscribed to this session for this season |
| `422` | Member does not meet age requirements for the activity |

---

#### GET /api/v1/subscriptions/me

List all subscriptions for the current user's family.

| Property | Value |
|---|---|
| **Auth** | FAMILY |

**Query Parameters:**

| Parameter | Type | Default | Description |
|---|---|---|---|
| `season` | string | -- | Filter by season (e.g., `2025-2026`) |
| `status` | enum | -- | Filter by status: `PENDING`, `ACTIVE`, `CANCELLED`, `EXPIRED` |
| `memberId` | Long | -- | Filter by family member ID |
| `page` | int | `0` | Page number (zero-based) |
| `size` | int | `20` | Page size (max 100) |
| `sort` | string | `createdAt,desc` | Sort field and direction |

**Example Request:**

```http
GET /api/v1/subscriptions/me?season=2025-2026&status=ACTIVE&page=0&size=20
```

**Response 200 OK:**

```json
{
  "content": [
    {
      "id": 1,
      "familyMemberId": 1,
      "familyMemberName": "Lucas Dupont",
      "familyId": 1,
      "associationId": 1,
      "associationName": "Judo Club Lyon",
      "activityId": 3,
      "activityName": "Judo Adultes",
      "sessionId": 7,
      "sessionSummary": "Lundi 19:00-20:30",
      "season": "2025-2026",
      "status": "ACTIVE",
      "startDate": "2025-09-01",
      "endDate": "2026-06-30",
      "totalAmount": 300.00,
      "paymentStatus": "PAID",
      "attendanceRate": 0.85,
      "createdAt": "2025-09-15T10:30:00Z"
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "size": 20,
  "number": 0,
  "first": true,
  "last": true,
  "numberOfElements": 1,
  "empty": false
}
```

---

#### GET /api/v1/subscriptions/{id}

Get a single subscription's full details.

| Property | Value |
|---|---|
| **Auth** | FAMILY (own), ADMIN |

**Path Parameters:**

| Parameter | Type | Description |
|---|---|---|
| `id` | Long | Subscription ID |

**Response 200 OK:**

```json
{
  "id": 1,
  "familyMemberId": 1,
  "familyMemberName": "Lucas Dupont",
  "familyId": 1,
  "associationId": 1,
  "associationName": "Judo Club Lyon",
  "activityId": 3,
  "activityName": "Judo Adultes",
  "sessionId": 7,
  "sessionDetails": {
    "dayOfWeek": "MONDAY",
    "startTime": "19:00",
    "endTime": "20:30",
    "location": "Dojo Principal",
    "instructor": "Maitre Tanaka"
  },
  "season": "2025-2026",
  "status": "ACTIVE",
  "startDate": "2025-09-01",
  "endDate": "2026-06-30",
  "annualFee": 280.00,
  "registrationFee": 20.00,
  "totalAmount": 300.00,
  "paymentStatus": "PAID",
  "paymentId": 1,
  "attendanceRate": 0.85,
  "totalSessions": 40,
  "attendedSessions": 34,
  "createdAt": "2025-09-15T10:30:00Z",
  "updatedAt": "2025-09-15T14:00:00Z"
}
```

**Error Responses:**

| Status | Condition |
|---|---|
| `401` | Missing or invalid JWT |
| `403` | FAMILY user attempting to access another family's subscription |
| `404` | Subscription not found |

---

#### POST /api/v1/subscriptions/{id}/cancel

Cancel an active subscription.

| Property | Value |
|---|---|
| **Auth** | FAMILY (own), ADMIN |

**Path Parameters:**

| Parameter | Type | Description |
|---|---|---|
| `id` | Long | Subscription ID |

**Request Body:**

```json
{
  "reason": "Changement de ville",
  "effectiveDate": "2025-12-31"
}
```

| Field | Type | Required | Validation |
|---|---|---|---|
| `reason` | string | No | Max 500 chars |
| `effectiveDate` | date | No | ISO 8601 date, must be today or in the future. Defaults to today. |

**Response 200 OK:**

```json
{
  "id": 1,
  "status": "CANCELLED",
  "cancelledAt": "2025-09-15T10:30:00Z",
  "effectiveDate": "2025-12-31",
  "reason": "Changement de ville",
  "refundEligible": true,
  "refundAmount": 140.00
}
```

**Error Responses:**

| Status | Condition |
|---|---|
| `401` | Missing or invalid JWT |
| `403` | FAMILY user attempting to cancel another family's subscription |
| `404` | Subscription not found |
| `422` | Subscription is not in a cancellable state (already cancelled or expired) |

---

#### GET /api/v1/subscriptions/family/{familyId}

List all subscriptions for a specific family.

| Property | Value |
|---|---|
| **Auth** | FAMILY (own), ADMIN |

**Path Parameters:**

| Parameter | Type | Description |
|---|---|---|
| `familyId` | Long | Family ID |

**Query Parameters:** Same as `GET /api/v1/subscriptions/me`.

**Response 200 OK:** Same paginated structure as `GET /api/v1/subscriptions/me`.

**Error Responses:**

| Status | Condition |
|---|---|
| `401` | Missing or invalid JWT |
| `403` | FAMILY user attempting to access another family's subscriptions |
| `404` | Family not found |

---

### 8.4 Attendance Endpoints

#### POST /api/v1/attendance/mark

Mark or update attendance for a subscribed member on a specific session date.

| Property | Value |
|---|---|
| **Auth** | FAMILY, ASSOCIATION, ADMIN |

**Request Body:**

```json
{
  "subscriptionId": 1,
  "sessionId": 7,
  "sessionDate": "2025-09-17",
  "status": "PRESENT",
  "note": ""
}
```

| Field | Type | Required | Validation |
|---|---|---|---|
| `subscriptionId` | Long | Yes | Must exist, must be ACTIVE |
| `sessionId` | Long | Yes | Must match the subscription's session |
| `sessionDate` | date | Yes | ISO 8601 date, must not be in the future |
| `status` | enum | Yes | One of: `PRESENT`, `ABSENT`, `EXCUSED`, `LATE` |
| `note` | string | No | Max 255 chars |

**Response 201 Created:**

```json
{
  "id": 1,
  "subscriptionId": 1,
  "familyMemberId": 1,
  "familyMemberName": "Lucas Dupont",
  "sessionId": 7,
  "sessionDate": "2025-09-17",
  "status": "PRESENT",
  "note": "",
  "markedBy": 1,
  "markedAt": "2025-09-17T20:35:00Z"
}
```

**Error Responses:**

| Status | Condition |
|---|---|
| `400` | Validation failure (missing fields, future date) |
| `401` | Missing or invalid JWT |
| `403` | FAMILY user marking attendance for a member outside their family |
| `404` | Subscription or session not found |
| `409` | Attendance already recorded for this member/session/date (use PUT to update) |
| `422` | Session date does not match the session's scheduled day of week |

---

#### GET /api/v1/attendance/session/{sessionId}

Get attendance records for a specific session on a specific date.

| Property | Value |
|---|---|
| **Auth** | ASSOCIATION, ADMIN |

**Path Parameters:**

| Parameter | Type | Description |
|---|---|---|
| `sessionId` | Long | Session ID |

**Query Parameters:**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `date` | date | Yes | Session date in ISO 8601 format (e.g., `2025-09-17`) |

**Example Request:**

```http
GET /api/v1/attendance/session/7?date=2025-09-17
```

**Response 200 OK:**

```json
{
  "sessionId": 7,
  "sessionDate": "2025-09-17",
  "activityName": "Judo Adultes",
  "totalSubscribed": 22,
  "totalPresent": 18,
  "totalAbsent": 2,
  "totalExcused": 1,
  "totalLate": 1,
  "totalUnmarked": 0,
  "records": [
    {
      "id": 1,
      "subscriptionId": 1,
      "familyMemberId": 1,
      "familyMemberName": "Lucas Dupont",
      "status": "PRESENT",
      "note": "",
      "markedBy": 1,
      "markedAt": "2025-09-17T20:35:00Z"
    },
    {
      "id": 2,
      "subscriptionId": 5,
      "familyMemberId": 8,
      "familyMemberName": "Sophie Martin",
      "status": "ABSENT",
      "note": "Prevenu par telephone",
      "markedBy": 3,
      "markedAt": "2025-09-17T20:36:00Z"
    }
  ]
}
```

**Error Responses:**

| Status | Condition |
|---|---|
| `400` | Missing or invalid date parameter |
| `401` | Missing or invalid JWT |
| `403` | Caller does not have ASSOCIATION or ADMIN role |
| `404` | Session not found |

---

#### GET /api/v1/attendance/member/{memberId}

Get attendance history for a specific family member.

| Property | Value |
|---|---|
| **Auth** | FAMILY (own), ADMIN |

**Path Parameters:**

| Parameter | Type | Description |
|---|---|---|
| `memberId` | Long | Family member ID |

**Query Parameters:**

| Parameter | Type | Default | Description |
|---|---|---|---|
| `from` | date | -- | Start date filter (inclusive) |
| `to` | date | -- | End date filter (inclusive) |
| `sessionId` | Long | -- | Filter by specific session |
| `status` | enum | -- | Filter by status: `PRESENT`, `ABSENT`, `EXCUSED`, `LATE` |
| `page` | int | `0` | Page number (zero-based) |
| `size` | int | `20` | Page size (max 100) |
| `sort` | string | `sessionDate,desc` | Sort field and direction |

**Example Request:**

```http
GET /api/v1/attendance/member/1?from=2025-09-01&to=2025-09-30&page=0&size=20
```

**Response 200 OK:**

```json
{
  "content": [
    {
      "id": 1,
      "subscriptionId": 1,
      "sessionId": 7,
      "activityName": "Judo Adultes",
      "sessionDate": "2025-09-17",
      "status": "PRESENT",
      "note": "",
      "markedBy": 1,
      "markedAt": "2025-09-17T20:35:00Z"
    },
    {
      "id": 5,
      "subscriptionId": 1,
      "sessionId": 7,
      "activityName": "Judo Adultes",
      "sessionDate": "2025-09-10",
      "status": "EXCUSED",
      "note": "Rendez-vous medical",
      "markedBy": 1,
      "markedAt": "2025-09-10T20:30:00Z"
    }
  ],
  "totalElements": 2,
  "totalPages": 1,
  "size": 20,
  "number": 0,
  "first": true,
  "last": true,
  "numberOfElements": 2,
  "empty": false
}
```

**Error Responses:**

| Status | Condition |
|---|---|
| `401` | Missing or invalid JWT |
| `403` | FAMILY user accessing another family's member attendance |
| `404` | Member not found |

---

#### GET /api/v1/attendance/subscription/{subscriptionId}

Get attendance records for a specific subscription across all session dates.

| Property | Value |
|---|---|
| **Auth** | FAMILY (own), ADMIN |

**Path Parameters:**

| Parameter | Type | Description |
|---|---|---|
| `subscriptionId` | Long | Subscription ID |

**Query Parameters:**

| Parameter | Type | Default | Description |
|---|---|---|---|
| `from` | date | -- | Start date filter (inclusive) |
| `to` | date | -- | End date filter (inclusive) |
| `page` | int | `0` | Page number (zero-based) |
| `size` | int | `20` | Page size (max 100) |
| `sort` | string | `sessionDate,desc` | Sort field and direction |

**Response 200 OK:**

```json
{
  "subscriptionId": 1,
  "familyMemberName": "Lucas Dupont",
  "activityName": "Judo Adultes",
  "season": "2025-2026",
  "summary": {
    "totalSessions": 6,
    "present": 4,
    "absent": 1,
    "excused": 1,
    "late": 0,
    "attendanceRate": 0.67
  },
  "records": {
    "content": [
      {
        "id": 1,
        "sessionDate": "2025-09-17",
        "status": "PRESENT",
        "note": "",
        "markedAt": "2025-09-17T20:35:00Z"
      }
    ],
    "totalElements": 6,
    "totalPages": 1,
    "size": 20,
    "number": 0,
    "first": true,
    "last": true,
    "numberOfElements": 6,
    "empty": false
  }
}
```

**Error Responses:**

| Status | Condition |
|---|---|
| `401` | Missing or invalid JWT |
| `403` | FAMILY user accessing another family's subscription attendance |
| `404` | Subscription not found |

---

## 9. Payment Service API

**Service**: payment-service
**Port**: 8083
**Database**: `familyhobbies_payments`
**Base Routes**: `/api/v1/payments`, `/api/v1/invoices`

### 9.1 Payment Endpoints

#### POST /api/v1/payments/checkout

Initiate a HelloAsso checkout session for a subscription payment. Returns a redirect URL
where the user completes payment on HelloAsso's hosted checkout page.

| Property | Value |
|---|---|
| **Auth** | FAMILY |

**Request Body:**

```json
{
  "subscriptionId": 1,
  "amount": 300.00,
  "description": "Inscription Judo Adultes - Lucas Dupont - Saison 2025-2026",
  "paymentType": "FULL",
  "returnUrl": "https://app.familyhobbies.fr/payments/success",
  "cancelUrl": "https://app.familyhobbies.fr/payments/cancel"
}
```

| Field | Type | Required | Validation |
|---|---|---|---|
| `subscriptionId` | Long | Yes | Must exist, must belong to caller's family |
| `amount` | BigDecimal | Yes | Must be positive, max 2 decimal places |
| `description` | string | Yes | Max 255 chars, not blank |
| `paymentType` | enum | Yes | One of: `FULL`, `INSTALLMENT_3X`, `INSTALLMENT_10X` |
| `returnUrl` | string | Yes | Valid URL |
| `cancelUrl` | string | Yes | Valid URL |

**Response 200 OK:**

```json
{
  "paymentId": 1,
  "subscriptionId": 1,
  "amount": 300.00,
  "paymentType": "FULL",
  "status": "PENDING",
  "checkoutUrl": "https://www.helloasso-sandbox.com/checkout/chk_abc123",
  "helloassoCheckoutId": "chk_abc123",
  "expiresAt": "2025-09-15T11:00:00Z",
  "createdAt": "2025-09-15T10:30:00Z"
}
```

**Error Responses:**

| Status | Condition |
|---|---|
| `400` | Validation failure (invalid amount, missing URL) |
| `401` | Missing or invalid JWT |
| `403` | Subscription does not belong to the caller's family |
| `404` | Subscription not found |
| `409` | Payment already completed for this subscription |
| `422` | Subscription is not in a payable state (cancelled, expired) |
| `502` | HelloAsso API unavailable (circuit breaker open) |

---

#### GET /api/v1/payments/{id}

Get payment details by payment ID.

| Property | Value |
|---|---|
| **Auth** | FAMILY (own), ADMIN |

**Path Parameters:**

| Parameter | Type | Description |
|---|---|---|
| `id` | Long | Payment ID |

**Response 200 OK:**

```json
{
  "id": 1,
  "subscriptionId": 1,
  "familyId": 1,
  "familyMemberName": "Lucas Dupont",
  "associationName": "Judo Club Lyon",
  "activityName": "Judo Adultes",
  "amount": 300.00,
  "paymentType": "FULL",
  "status": "COMPLETED",
  "helloassoCheckoutId": "chk_abc123",
  "helloassoPaymentId": "pay_xyz789",
  "paymentMethod": "CARD",
  "paidAt": "2025-09-15T10:35:00Z",
  "invoiceId": 1,
  "metadata": {
    "cardLast4": "4242",
    "cardBrand": "VISA"
  },
  "createdAt": "2025-09-15T10:30:00Z",
  "updatedAt": "2025-09-15T10:35:00Z"
}
```

**Payment Status Values:**

| Status | Description |
|---|---|
| `PENDING` | Checkout initiated, waiting for user payment |
| `PROCESSING` | Payment received, being validated |
| `COMPLETED` | Payment successfully processed |
| `FAILED` | Payment failed (declined, insufficient funds) |
| `REFUNDED` | Full refund processed |
| `PARTIALLY_REFUNDED` | Partial refund processed |
| `CANCELLED` | Checkout abandoned or cancelled |
| `EXPIRED` | Checkout session expired without payment |

**Error Responses:**

| Status | Condition |
|---|---|
| `401` | Missing or invalid JWT |
| `403` | FAMILY user accessing another family's payment |
| `404` | Payment not found |

---

#### GET /api/v1/payments/family/{familyId}

List all payments for a specific family.

| Property | Value |
|---|---|
| **Auth** | FAMILY (own), ADMIN |

**Path Parameters:**

| Parameter | Type | Description |
|---|---|---|
| `familyId` | Long | Family ID |

**Query Parameters:**

| Parameter | Type | Default | Description |
|---|---|---|---|
| `status` | enum | -- | Filter by payment status |
| `from` | date | -- | Start date filter (inclusive) |
| `to` | date | -- | End date filter (inclusive) |
| `page` | int | `0` | Page number (zero-based) |
| `size` | int | `20` | Page size (max 100) |
| `sort` | string | `createdAt,desc` | Sort field and direction |

**Example Request:**

```http
GET /api/v1/payments/family/1?status=COMPLETED&from=2025-09-01&to=2025-12-31&page=0&size=20
```

**Response 200 OK:**

```json
{
  "content": [
    {
      "id": 1,
      "subscriptionId": 1,
      "familyMemberName": "Lucas Dupont",
      "associationName": "Judo Club Lyon",
      "activityName": "Judo Adultes",
      "amount": 300.00,
      "paymentType": "FULL",
      "status": "COMPLETED",
      "paymentMethod": "CARD",
      "paidAt": "2025-09-15T10:35:00Z",
      "invoiceId": 1,
      "createdAt": "2025-09-15T10:30:00Z"
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "size": 20,
  "number": 0,
  "first": true,
  "last": true,
  "numberOfElements": 1,
  "empty": false
}
```

**Error Responses:**

| Status | Condition |
|---|---|
| `401` | Missing or invalid JWT |
| `403` | FAMILY user accessing another family's payments |
| `404` | Family not found |

---

#### POST /api/v1/payments/webhook/helloasso

Receive and process payment event webhooks from HelloAsso. This endpoint is publicly
accessible but validates the request using HMAC signature verification.

| Property | Value |
|---|---|
| **Auth** | PUBLIC (HMAC-validated) |

**Request Headers:**

```http
Content-Type: application/json
X-HelloAsso-Signature: sha256=abc123def456...
```

**Request Body (sent by HelloAsso):**

```json
{
  "eventType": "Payment",
  "data": {
    "id": 12345,
    "amount": 30000,
    "state": "Authorized",
    "paymentReceiptUrl": "https://www.helloasso.com/receipt/12345",
    "order": {
      "id": 67890,
      "formSlug": "inscription-judo-2025-2026",
      "formType": "Membership",
      "organizationSlug": "judo-club-lyon"
    },
    "payer": {
      "email": "dupont@email.com",
      "firstName": "Jean",
      "lastName": "Dupont"
    },
    "items": [
      {
        "id": 11111,
        "amount": 30000,
        "type": "Membership",
        "state": "Processed"
      }
    ],
    "date": "2025-09-15T10:35:00+02:00"
  },
  "metadata": {
    "paymentId": 1,
    "subscriptionId": 1,
    "familyId": 1
  }
}
```

**Response 200 OK:**

```json
{
  "received": true,
  "eventType": "Payment",
  "processedAt": "2025-09-15T10:35:01Z"
}
```

**Error Responses:**

| Status | Condition |
|---|---|
| `400` | Invalid or missing request body |
| `401` | Invalid HMAC signature |
| `409` | Webhook event already processed (idempotency) |

**HMAC Validation Process:**

1. HelloAsso includes `X-HelloAsso-Signature` header with each webhook
2. The payment-service computes `HMAC-SHA256(request_body, webhook_secret)`
3. The computed signature is compared against the header value
4. Request is rejected with 401 if signatures do not match

---

### 9.2 Invoice Endpoints

#### GET /api/v1/invoices/{id}

Get invoice details by invoice ID.

| Property | Value |
|---|---|
| **Auth** | FAMILY (own), ADMIN |

**Path Parameters:**

| Parameter | Type | Description |
|---|---|---|
| `id` | Long | Invoice ID |

**Response 200 OK:**

```json
{
  "id": 1,
  "invoiceNumber": "FHM-2025-000001",
  "paymentId": 1,
  "subscriptionId": 1,
  "familyId": 1,
  "familyName": "Famille Dupont",
  "associationName": "Judo Club Lyon",
  "activityName": "Judo Adultes",
  "familyMemberName": "Lucas Dupont",
  "season": "2025-2026",
  "lineItems": [
    {
      "description": "Cotisation annuelle - Judo Adultes",
      "amount": 280.00
    },
    {
      "description": "Frais d'inscription",
      "amount": 20.00
    }
  ],
  "subtotal": 300.00,
  "tax": 0.00,
  "total": 300.00,
  "currency": "EUR",
  "status": "PAID",
  "issuedAt": "2025-09-15T10:35:00Z",
  "paidAt": "2025-09-15T10:35:00Z",
  "payerEmail": "dupont@email.com",
  "payerName": "Jean Dupont",
  "downloadUrl": "/api/v1/invoices/1/download",
  "createdAt": "2025-09-15T10:35:00Z"
}
```

**Error Responses:**

| Status | Condition |
|---|---|
| `401` | Missing or invalid JWT |
| `403` | FAMILY user accessing another family's invoice |
| `404` | Invoice not found |

---

#### GET /api/v1/invoices/family/{familyId}

List all invoices for a specific family.

| Property | Value |
|---|---|
| **Auth** | FAMILY (own), ADMIN |

**Path Parameters:**

| Parameter | Type | Description |
|---|---|---|
| `familyId` | Long | Family ID |

**Query Parameters:**

| Parameter | Type | Default | Description |
|---|---|---|---|
| `season` | string | -- | Filter by season (e.g., `2025-2026`) |
| `status` | enum | -- | Filter by status: `PAID`, `UNPAID`, `REFUNDED` |
| `from` | date | -- | Start date filter on issue date (inclusive) |
| `to` | date | -- | End date filter on issue date (inclusive) |
| `page` | int | `0` | Page number (zero-based) |
| `size` | int | `20` | Page size (max 100) |
| `sort` | string | `issuedAt,desc` | Sort field and direction |

**Example Request:**

```http
GET /api/v1/invoices/family/1?season=2025-2026&status=PAID&page=0&size=20
```

**Response 200 OK:**

```json
{
  "content": [
    {
      "id": 1,
      "invoiceNumber": "FHM-2025-000001",
      "associationName": "Judo Club Lyon",
      "activityName": "Judo Adultes",
      "familyMemberName": "Lucas Dupont",
      "season": "2025-2026",
      "total": 300.00,
      "currency": "EUR",
      "status": "PAID",
      "issuedAt": "2025-09-15T10:35:00Z",
      "paidAt": "2025-09-15T10:35:00Z",
      "downloadUrl": "/api/v1/invoices/1/download"
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "size": 20,
  "number": 0,
  "first": true,
  "last": true,
  "numberOfElements": 1,
  "empty": false
}
```

**Error Responses:**

| Status | Condition |
|---|---|
| `401` | Missing or invalid JWT |
| `403` | FAMILY user accessing another family's invoices |
| `404` | Family not found |

---

#### GET /api/v1/invoices/{id}/download

Download an invoice as a PDF file.

| Property | Value |
|---|---|
| **Auth** | FAMILY (own), ADMIN |

**Path Parameters:**

| Parameter | Type | Description |
|---|---|---|
| `id` | Long | Invoice ID |

**Response 200 OK:**

```http
Content-Type: application/pdf
Content-Disposition: attachment; filename="FHM-2025-000001.pdf"
Content-Length: 45678

<binary PDF data>
```

**Error Responses:**

| Status | Condition |
|---|---|
| `401` | Missing or invalid JWT |
| `403` | FAMILY user accessing another family's invoice |
| `404` | Invoice not found |

---

## 10. Notification Service API

**Service**: notification-service
**Port**: 8084
**Database**: `familyhobbies_notifications`
**Base Routes**: `/api/v1/notifications`

**Note**: The notification-service primarily operates as a Kafka consumer, processing
domain events (`UserRegisteredEvent`, `SubscriptionCreatedEvent`, `PaymentCompletedEvent`,
`PaymentFailedEvent`, `AttendanceMarkedEvent`) to generate and send notifications.
The REST API below exposes endpoints for users to manage their notifications.

### 10.1 Notification Endpoints

#### GET /api/v1/notifications/me

List notifications for the current authenticated user, with optional filters.

| Property | Value |
|---|---|
| **Auth** | FAMILY, ASSOCIATION, ADMIN |

**Query Parameters:**

| Parameter | Type | Default | Description |
|---|---|---|---|
| `read` | boolean | -- | Filter by read status: `true` for read, `false` for unread |
| `category` | enum | -- | Filter by category (see values below) |
| `from` | datetime | -- | Start datetime filter (inclusive), ISO 8601 |
| `to` | datetime | -- | End datetime filter (inclusive), ISO 8601 |
| `page` | int | `0` | Page number (zero-based) |
| `size` | int | `20` | Page size (max 100) |
| `sort` | string | `createdAt,desc` | Sort field and direction |

**Category Values:** `REGISTRATION`, `SUBSCRIPTION`, `PAYMENT_SUCCESS`, `PAYMENT_FAILED`, `ATTENDANCE`, `SESSION_REMINDER`, `SYSTEM`, `RGPD`

**Example Request:**

```http
GET /api/v1/notifications/me?read=false&category=PAYMENT_SUCCESS&page=0&size=20
```

**Response 200 OK:**

```json
{
  "content": [
    {
      "id": 1,
      "userId": 1,
      "category": "PAYMENT_SUCCESS",
      "title": "Paiement confirme",
      "message": "Votre paiement de 300.00 EUR pour l'inscription de Lucas Dupont au Judo Adultes (Judo Club Lyon) a ete confirme.",
      "read": false,
      "data": {
        "paymentId": 1,
        "subscriptionId": 1,
        "amount": 300.00,
        "associationName": "Judo Club Lyon"
      },
      "actionUrl": "/dashboard/payments/1",
      "createdAt": "2025-09-15T10:35:00Z"
    },
    {
      "id": 2,
      "userId": 1,
      "category": "SUBSCRIPTION",
      "title": "Inscription enregistree",
      "message": "L'inscription de Lucas Dupont au Judo Adultes (Judo Club Lyon) pour la saison 2025-2026 a ete enregistree avec succes.",
      "read": false,
      "data": {
        "subscriptionId": 1,
        "associationName": "Judo Club Lyon",
        "activityName": "Judo Adultes"
      },
      "actionUrl": "/dashboard/subscriptions/1",
      "createdAt": "2025-09-15T10:30:00Z"
    }
  ],
  "totalElements": 2,
  "totalPages": 1,
  "size": 20,
  "number": 0,
  "first": true,
  "last": true,
  "numberOfElements": 2,
  "empty": false
}
```

**Error Responses:**

| Status | Condition |
|---|---|
| `401` | Missing or invalid JWT |

---

#### PUT /api/v1/notifications/{id}/read

Mark a single notification as read.

| Property | Value |
|---|---|
| **Auth** | FAMILY, ASSOCIATION, ADMIN |

**Path Parameters:**

| Parameter | Type | Description |
|---|---|---|
| `id` | Long | Notification ID |

**Response 200 OK:**

```json
{
  "id": 1,
  "read": true,
  "readAt": "2025-09-15T11:00:00Z"
}
```

**Error Responses:**

| Status | Condition |
|---|---|
| `401` | Missing or invalid JWT |
| `403` | Notification does not belong to the authenticated user |
| `404` | Notification not found |

---

#### PUT /api/v1/notifications/read-all

Mark all unread notifications as read for the current user.

| Property | Value |
|---|---|
| **Auth** | FAMILY, ASSOCIATION, ADMIN |

**Response 200 OK:**

```json
{
  "markedCount": 5,
  "readAt": "2025-09-15T11:00:00Z"
}
```

**Error Responses:**

| Status | Condition |
|---|---|
| `401` | Missing or invalid JWT |

---

#### GET /api/v1/notifications/preferences

Get the current user's notification preferences.

| Property | Value |
|---|---|
| **Auth** | FAMILY, ASSOCIATION, ADMIN |

**Response 200 OK:**

```json
{
  "userId": 1,
  "emailEnabled": true,
  "pushEnabled": false,
  "categories": {
    "REGISTRATION": { "email": true, "inApp": true },
    "SUBSCRIPTION": { "email": true, "inApp": true },
    "PAYMENT_SUCCESS": { "email": true, "inApp": true },
    "PAYMENT_FAILED": { "email": true, "inApp": true },
    "ATTENDANCE": { "email": false, "inApp": true },
    "SESSION_REMINDER": { "email": true, "inApp": true },
    "SYSTEM": { "email": false, "inApp": true },
    "RGPD": { "email": true, "inApp": true }
  },
  "quietHoursStart": "22:00",
  "quietHoursEnd": "08:00",
  "locale": "fr-FR",
  "updatedAt": "2025-09-15T10:00:00Z"
}
```

**Error Responses:**

| Status | Condition |
|---|---|
| `401` | Missing or invalid JWT |

---

#### PUT /api/v1/notifications/preferences

Update the current user's notification preferences.

| Property | Value |
|---|---|
| **Auth** | FAMILY, ASSOCIATION, ADMIN |

**Request Body:**

```json
{
  "emailEnabled": true,
  "pushEnabled": false,
  "categories": {
    "REGISTRATION": { "email": true, "inApp": true },
    "SUBSCRIPTION": { "email": true, "inApp": true },
    "PAYMENT_SUCCESS": { "email": true, "inApp": true },
    "PAYMENT_FAILED": { "email": true, "inApp": true },
    "ATTENDANCE": { "email": false, "inApp": true },
    "SESSION_REMINDER": { "email": true, "inApp": true },
    "SYSTEM": { "email": false, "inApp": true },
    "RGPD": { "email": true, "inApp": true }
  },
  "quietHoursStart": "23:00",
  "quietHoursEnd": "07:00",
  "locale": "fr-FR"
}
```

| Field | Type | Required | Validation |
|---|---|---|---|
| `emailEnabled` | boolean | No | Global email toggle |
| `pushEnabled` | boolean | No | Global push notification toggle |
| `categories` | object | No | Per-category channel preferences |
| `quietHoursStart` | time | No | HH:mm format, no notifications sent during quiet hours |
| `quietHoursEnd` | time | No | HH:mm format |
| `locale` | string | No | BCP 47 locale (e.g., `fr-FR`) |

**Response 200 OK:**

```json
{
  "userId": 1,
  "emailEnabled": true,
  "pushEnabled": false,
  "categories": {
    "REGISTRATION": { "email": true, "inApp": true },
    "SUBSCRIPTION": { "email": true, "inApp": true },
    "PAYMENT_SUCCESS": { "email": true, "inApp": true },
    "PAYMENT_FAILED": { "email": true, "inApp": true },
    "ATTENDANCE": { "email": false, "inApp": true },
    "SESSION_REMINDER": { "email": true, "inApp": true },
    "SYSTEM": { "email": false, "inApp": true },
    "RGPD": { "email": true, "inApp": true }
  },
  "quietHoursStart": "23:00",
  "quietHoursEnd": "07:00",
  "locale": "fr-FR",
  "updatedAt": "2025-09-15T11:00:00Z"
}
```

**Error Responses:**

| Status | Condition |
|---|---|
| `400` | Invalid time format, invalid locale |
| `401` | Missing or invalid JWT |

---

## 11. Revision History

| Date | Version | Author | Changes |
|---|---|---|---|
| 2026-02-23 | 1.0 | Architecture Team | Initial version -- complete API contracts for all 4 data services |
