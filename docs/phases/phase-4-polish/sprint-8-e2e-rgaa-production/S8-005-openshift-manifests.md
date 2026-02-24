# Story S8-005: OpenShift Deployment Manifests

> 5 points | Priority: P2 | Service: infrastructure
> Sprint file: [Back to Sprint Index](./_index.md)

---

## Context

The Family Hobbies Manager needs production-grade Kubernetes/OpenShift manifests so that the entire microservice stack can be deployed to a real cluster with a single `oc apply -k` command. This story creates Kustomize-based manifests for every service, database, and message broker, organized as a `base/` layer with `overlays/` for dev and prod environments. Each backend service gets a `DeploymentConfig` with rolling strategy, resource limits (512Mi memory / 500m CPU), readiness/liveness probes pointing at Spring Actuator, a `Service` (ClusterIP), and a `ConfigMap` for environment-specific `application.yml` overrides. The api-gateway and frontend additionally get `Route` resources with edge TLS termination. PostgreSQL uses a `StatefulSet` with a 5Gi `PersistentVolumeClaim`. Secrets (JWT key, HelloAsso credentials, DB passwords) are base64-encoded templates -- never committed with real values. Two namespaces (`family-hobbies-dev`, `family-hobbies-prod`) isolate environments. Kustomize overlays patch replica counts, resource limits, image tags, and environment variables without duplicating the base manifests.

## Tasks

| # | Task | File Path | What To Create | How To Verify |
|---|------|-----------|----------------|---------------|
| 1 | Kustomize base kustomization | `k8s/base/kustomization.yaml` | Base kustomization referencing all resources | `kustomize build k8s/base` produces valid YAML |
| 2 | Namespace manifest | `k8s/base/namespace.yaml` | Namespace resource template | Valid YAML, name parameterized via overlay |
| 3 | Shared secrets | `k8s/base/secrets/secrets.yaml` | Secret with base64 placeholders for JWT, HelloAsso, DB | `oc apply --dry-run=client -f k8s/base/secrets/secrets.yaml` succeeds |
| 4 | Discovery-service manifests | `k8s/base/discovery-service/deployment.yaml`, `service.yaml`, `configmap.yaml` | Deployment + Service + ConfigMap for Eureka | `oc apply --dry-run=client` succeeds |
| 5 | API Gateway manifests | `k8s/base/api-gateway/deployment.yaml`, `service.yaml`, `route.yaml`, `configmap.yaml` | Deployment + Service + Route (edge TLS) + ConfigMap | `oc apply --dry-run=client` succeeds |
| 6 | User-service manifests | `k8s/base/user-service/deployment.yaml`, `service.yaml`, `configmap.yaml` | Deployment + Service + ConfigMap | `oc apply --dry-run=client` succeeds |
| 7 | Association-service manifests | `k8s/base/association-service/deployment.yaml`, `service.yaml`, `configmap.yaml` | Deployment + Service + ConfigMap | `oc apply --dry-run=client` succeeds |
| 8 | Payment-service manifests | `k8s/base/payment-service/deployment.yaml`, `service.yaml`, `configmap.yaml` | Deployment + Service + ConfigMap | `oc apply --dry-run=client` succeeds |
| 9 | Notification-service manifests | `k8s/base/notification-service/deployment.yaml`, `service.yaml`, `configmap.yaml` | Deployment + Service + ConfigMap | `oc apply --dry-run=client` succeeds |
| 10 | Frontend manifests | `k8s/base/frontend/deployment.yaml`, `service.yaml`, `route.yaml` | Deployment + Service + Route (edge TLS) | `oc apply --dry-run=client` succeeds |
| 11 | PostgreSQL StatefulSet | `k8s/base/postgresql/statefulset.yaml`, `service.yaml`, `pvc.yaml` | StatefulSet + headless Service + PVC (5Gi) | `oc apply --dry-run=client` succeeds |
| 12 | Kafka StatefulSet | `k8s/base/kafka/statefulset.yaml`, `service.yaml` | Kafka + Zookeeper StatefulSet + Service | `oc apply --dry-run=client` succeeds |
| 13 | Dev overlay | `k8s/overlays/dev/kustomization.yaml`, `k8s/overlays/dev/patches/` | Dev-specific patches (1 replica, dev image tags, lower limits) | `kustomize build k8s/overlays/dev` succeeds |
| 14 | Prod overlay | `k8s/overlays/prod/kustomization.yaml`, `k8s/overlays/prod/patches/` | Prod-specific patches (2 replicas, prod tags, higher limits) | `kustomize build k8s/overlays/prod` succeeds |
| 15 | Deployment guide | Section in this file | `oc apply` commands, prerequisites, verification steps | Readable, commands correct |

---

## Task 1 Detail: Kustomize Base Kustomization

- **What**: Root kustomization file that references all base resources. Kustomize reads this file to know which manifests to include.
- **Where**: `k8s/base/kustomization.yaml`
- **Why**: Kustomize requires a `kustomization.yaml` in each directory. This file is the entry point for `kustomize build k8s/base`.

```yaml
# k8s/base/kustomization.yaml
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization

namespace: family-hobbies-dev

commonLabels:
  app.kubernetes.io/part-of: family-hobbies-manager
  app.kubernetes.io/managed-by: kustomize

resources:
  - namespace.yaml
  - secrets/secrets.yaml
  # Infrastructure
  - postgresql/statefulset.yaml
  - postgresql/service.yaml
  - postgresql/pvc.yaml
  - kafka/statefulset.yaml
  - kafka/service.yaml
  # Microservices
  - discovery-service/deployment.yaml
  - discovery-service/service.yaml
  - discovery-service/configmap.yaml
  - api-gateway/deployment.yaml
  - api-gateway/service.yaml
  - api-gateway/route.yaml
  - api-gateway/configmap.yaml
  - user-service/deployment.yaml
  - user-service/service.yaml
  - user-service/configmap.yaml
  - association-service/deployment.yaml
  - association-service/service.yaml
  - association-service/configmap.yaml
  - payment-service/deployment.yaml
  - payment-service/service.yaml
  - payment-service/configmap.yaml
  - notification-service/deployment.yaml
  - notification-service/service.yaml
  - notification-service/configmap.yaml
  # Frontend
  - frontend/deployment.yaml
  - frontend/service.yaml
  - frontend/route.yaml
```

- **Verify**: `kustomize build k8s/base` -> outputs combined YAML without errors

---

## Task 2 Detail: Namespace Manifest

- **What**: Namespace resource that isolates all Family Hobbies Manager resources.
- **Where**: `k8s/base/namespace.yaml`
- **Why**: Namespaces provide isolation between environments and prevent resource name collisions with other projects on the cluster.

```yaml
# k8s/base/namespace.yaml
apiVersion: v1
kind: Namespace
metadata:
  name: family-hobbies-dev
  labels:
    app.kubernetes.io/part-of: family-hobbies-manager
    environment: dev
```

- **Verify**: `oc apply --dry-run=client -f k8s/base/namespace.yaml` -> `namespace/family-hobbies-dev created (dry run)`

---

## Task 3 Detail: Shared Secrets

- **What**: Kubernetes Secret containing base64-encoded placeholder values for all sensitive configuration. Real values are injected at deployment time via CI/CD or `oc create secret`.
- **Where**: `k8s/base/secrets/secrets.yaml`
- **Why**: Centralizing secrets in one manifest simplifies rotation and audit. Placeholders (`CHANGE_ME`) make it obvious which values need replacement. Never commit real credentials.

```yaml
# k8s/base/secrets/secrets.yaml
apiVersion: v1
kind: Secret
metadata:
  name: family-hobbies-secrets
  namespace: family-hobbies-dev
  labels:
    app.kubernetes.io/part-of: family-hobbies-manager
type: Opaque
data:
  # Base64-encoded placeholders -- replace with real values before deployment
  # echo -n 'CHANGE_ME' | base64 = Q0hBTkdFX01F
  jwt-secret: Q0hBTkdFX01F
  helloasso-client-id: Q0hBTkdFX01F
  helloasso-client-secret: Q0hBTkdFX01F
  db-user-password: Q0hBTkdFX01F
  db-association-password: Q0hBTkdFX01F
  db-payment-password: Q0hBTkdFX01F
  db-notification-password: Q0hBTkdFX01F
  db-root-password: Q0hBTkdFX01F
  smtp-password: Q0hBTkdFX01F
```

- **Verify**: `oc apply --dry-run=client -f k8s/base/secrets/secrets.yaml` -> `secret/family-hobbies-secrets created (dry run)`

---

## Task 4 Detail: Discovery-Service Manifests

- **What**: Eureka discovery-service Deployment, Service, and ConfigMap. The discovery-service is the first service to start and has no database dependency.
- **Where**: `k8s/base/discovery-service/deployment.yaml`, `service.yaml`, `configmap.yaml`
- **Why**: All other services register with Eureka. It must be running and healthy before any dependent service starts.

### deployment.yaml

```yaml
# k8s/base/discovery-service/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: discovery-service
  labels:
    app: discovery-service
    app.kubernetes.io/part-of: family-hobbies-manager
spec:
  replicas: 1
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxUnavailable: 0
      maxSurge: 1
  selector:
    matchLabels:
      app: discovery-service
  template:
    metadata:
      labels:
        app: discovery-service
    spec:
      containers:
        - name: discovery-service
          image: familyhobbies/discovery-service:latest
          ports:
            - containerPort: 8761
              protocol: TCP
          envFrom:
            - configMapRef:
                name: discovery-service-config
          resources:
            requests:
              memory: "256Mi"
              cpu: "250m"
            limits:
              memory: "512Mi"
              cpu: "500m"
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8761
            initialDelaySeconds: 30
            periodSeconds: 10
            timeoutSeconds: 5
            failureThreshold: 3
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8761
            initialDelaySeconds: 60
            periodSeconds: 15
            timeoutSeconds: 5
            failureThreshold: 3
      restartPolicy: Always
```

### service.yaml

```yaml
# k8s/base/discovery-service/service.yaml
apiVersion: v1
kind: Service
metadata:
  name: discovery-service
  labels:
    app: discovery-service
    app.kubernetes.io/part-of: family-hobbies-manager
spec:
  type: ClusterIP
  selector:
    app: discovery-service
  ports:
    - name: http
      port: 8761
      targetPort: 8761
      protocol: TCP
```

### configmap.yaml

```yaml
# k8s/base/discovery-service/configmap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: discovery-service-config
  labels:
    app: discovery-service
    app.kubernetes.io/part-of: family-hobbies-manager
data:
  SPRING_PROFILES_ACTIVE: "k8s"
  SERVER_PORT: "8761"
  EUREKA_CLIENT_REGISTER_WITH_EUREKA: "false"
  EUREKA_CLIENT_FETCH_REGISTRY: "false"
  EUREKA_SERVER_ENABLE_SELF_PRESERVATION: "false"
  MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE: "health,info,metrics"
  MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED: "true"
```

- **Verify**: `oc apply --dry-run=client -f k8s/base/discovery-service/` -> all 3 resources created (dry run)

---

## Task 5 Detail: API Gateway Manifests

- **What**: API Gateway Deployment, Service, Route (edge TLS), and ConfigMap. The gateway is the single entry point for all external traffic.
- **Where**: `k8s/base/api-gateway/deployment.yaml`, `service.yaml`, `route.yaml`, `configmap.yaml`
- **Why**: External clients (Angular frontend, mobile apps) connect to the gateway, which routes requests to internal services via Eureka. The Route exposes it with TLS termination.

### deployment.yaml

```yaml
# k8s/base/api-gateway/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: api-gateway
  labels:
    app: api-gateway
    app.kubernetes.io/part-of: family-hobbies-manager
spec:
  replicas: 1
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxUnavailable: 0
      maxSurge: 1
  selector:
    matchLabels:
      app: api-gateway
  template:
    metadata:
      labels:
        app: api-gateway
    spec:
      containers:
        - name: api-gateway
          image: familyhobbies/api-gateway:latest
          ports:
            - containerPort: 8080
              protocol: TCP
          envFrom:
            - configMapRef:
                name: api-gateway-config
          env:
            - name: JWT_SECRET
              valueFrom:
                secretKeyRef:
                  name: family-hobbies-secrets
                  key: jwt-secret
          resources:
            requests:
              memory: "256Mi"
              cpu: "250m"
            limits:
              memory: "512Mi"
              cpu: "500m"
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 10
            timeoutSeconds: 5
            failureThreshold: 3
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            initialDelaySeconds: 60
            periodSeconds: 15
            timeoutSeconds: 5
            failureThreshold: 3
      restartPolicy: Always
```

### service.yaml

```yaml
# k8s/base/api-gateway/service.yaml
apiVersion: v1
kind: Service
metadata:
  name: api-gateway
  labels:
    app: api-gateway
    app.kubernetes.io/part-of: family-hobbies-manager
spec:
  type: ClusterIP
  selector:
    app: api-gateway
  ports:
    - name: http
      port: 8080
      targetPort: 8080
      protocol: TCP
```

### route.yaml

```yaml
# k8s/base/api-gateway/route.yaml
apiVersion: route.openshift.io/v1
kind: Route
metadata:
  name: api-gateway
  labels:
    app: api-gateway
    app.kubernetes.io/part-of: family-hobbies-manager
spec:
  host: api.family-hobbies.dev.example.com
  port:
    targetPort: http
  tls:
    termination: edge
    insecureEdgeTerminationPolicy: Redirect
  to:
    kind: Service
    name: api-gateway
    weight: 100
  wildcardPolicy: None
```

### configmap.yaml

```yaml
# k8s/base/api-gateway/configmap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: api-gateway-config
  labels:
    app: api-gateway
    app.kubernetes.io/part-of: family-hobbies-manager
data:
  SPRING_PROFILES_ACTIVE: "k8s"
  SERVER_PORT: "8080"
  EUREKA_CLIENT_SERVICE_URL_DEFAULTZONE: "http://discovery-service:8761/eureka/"
  MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE: "health,info,metrics"
  MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED: "true"
```

- **Verify**: `oc apply --dry-run=client -f k8s/base/api-gateway/` -> all 4 resources created (dry run)

---

## Task 6 Detail: User-Service Manifests

- **What**: User-service Deployment, Service, and ConfigMap. Manages authentication, users, families, and RGPD compliance.
- **Where**: `k8s/base/user-service/deployment.yaml`, `service.yaml`, `configmap.yaml`
- **Why**: The user-service is the auth provider for the entire platform. It connects to its own PostgreSQL database and publishes Kafka events on user registration.

### deployment.yaml

```yaml
# k8s/base/user-service/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: user-service
  labels:
    app: user-service
    app.kubernetes.io/part-of: family-hobbies-manager
spec:
  replicas: 1
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxUnavailable: 0
      maxSurge: 1
  selector:
    matchLabels:
      app: user-service
  template:
    metadata:
      labels:
        app: user-service
    spec:
      containers:
        - name: user-service
          image: familyhobbies/user-service:latest
          ports:
            - containerPort: 8081
              protocol: TCP
          envFrom:
            - configMapRef:
                name: user-service-config
          env:
            - name: JWT_SECRET
              valueFrom:
                secretKeyRef:
                  name: family-hobbies-secrets
                  key: jwt-secret
            - name: SPRING_DATASOURCE_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: family-hobbies-secrets
                  key: db-user-password
          resources:
            requests:
              memory: "256Mi"
              cpu: "250m"
            limits:
              memory: "512Mi"
              cpu: "500m"
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8081
            initialDelaySeconds: 30
            periodSeconds: 10
            timeoutSeconds: 5
            failureThreshold: 3
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8081
            initialDelaySeconds: 60
            periodSeconds: 15
            timeoutSeconds: 5
            failureThreshold: 3
      restartPolicy: Always
```

### service.yaml

```yaml
# k8s/base/user-service/service.yaml
apiVersion: v1
kind: Service
metadata:
  name: user-service
  labels:
    app: user-service
    app.kubernetes.io/part-of: family-hobbies-manager
spec:
  type: ClusterIP
  selector:
    app: user-service
  ports:
    - name: http
      port: 8081
      targetPort: 8081
      protocol: TCP
```

### configmap.yaml

```yaml
# k8s/base/user-service/configmap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: user-service-config
  labels:
    app: user-service
    app.kubernetes.io/part-of: family-hobbies-manager
data:
  SPRING_PROFILES_ACTIVE: "k8s"
  SERVER_PORT: "8081"
  EUREKA_CLIENT_SERVICE_URL_DEFAULTZONE: "http://discovery-service:8761/eureka/"
  SPRING_DATASOURCE_URL: "jdbc:postgresql://postgresql:5432/familyhobbies_users"
  SPRING_DATASOURCE_USERNAME: "familyhobbies_user"
  SPRING_KAFKA_BOOTSTRAP_SERVERS: "kafka:9092"
  MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE: "health,info,metrics"
  MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED: "true"
```

- **Verify**: `oc apply --dry-run=client -f k8s/base/user-service/` -> all 3 resources created (dry run)

---

## Task 7 Detail: Association-Service Manifests

- **What**: Association-service Deployment, Service, and ConfigMap. Manages HelloAsso directory proxy, activities, sessions, subscriptions, and attendance.
- **Where**: `k8s/base/association-service/deployment.yaml`, `service.yaml`, `configmap.yaml`
- **Why**: The association-service integrates with the HelloAsso API for directory data. It needs HelloAsso credentials from secrets and its own PostgreSQL database.

### deployment.yaml

```yaml
# k8s/base/association-service/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: association-service
  labels:
    app: association-service
    app.kubernetes.io/part-of: family-hobbies-manager
spec:
  replicas: 1
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxUnavailable: 0
      maxSurge: 1
  selector:
    matchLabels:
      app: association-service
  template:
    metadata:
      labels:
        app: association-service
    spec:
      containers:
        - name: association-service
          image: familyhobbies/association-service:latest
          ports:
            - containerPort: 8082
              protocol: TCP
          envFrom:
            - configMapRef:
                name: association-service-config
          env:
            - name: SPRING_DATASOURCE_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: family-hobbies-secrets
                  key: db-association-password
            - name: HELLOASSO_CLIENT_ID
              valueFrom:
                secretKeyRef:
                  name: family-hobbies-secrets
                  key: helloasso-client-id
            - name: HELLOASSO_CLIENT_SECRET
              valueFrom:
                secretKeyRef:
                  name: family-hobbies-secrets
                  key: helloasso-client-secret
          resources:
            requests:
              memory: "256Mi"
              cpu: "250m"
            limits:
              memory: "512Mi"
              cpu: "500m"
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8082
            initialDelaySeconds: 30
            periodSeconds: 10
            timeoutSeconds: 5
            failureThreshold: 3
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8082
            initialDelaySeconds: 60
            periodSeconds: 15
            timeoutSeconds: 5
            failureThreshold: 3
      restartPolicy: Always
```

### service.yaml

```yaml
# k8s/base/association-service/service.yaml
apiVersion: v1
kind: Service
metadata:
  name: association-service
  labels:
    app: association-service
    app.kubernetes.io/part-of: family-hobbies-manager
spec:
  type: ClusterIP
  selector:
    app: association-service
  ports:
    - name: http
      port: 8082
      targetPort: 8082
      protocol: TCP
```

### configmap.yaml

```yaml
# k8s/base/association-service/configmap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: association-service-config
  labels:
    app: association-service
    app.kubernetes.io/part-of: family-hobbies-manager
data:
  SPRING_PROFILES_ACTIVE: "k8s"
  SERVER_PORT: "8082"
  EUREKA_CLIENT_SERVICE_URL_DEFAULTZONE: "http://discovery-service:8761/eureka/"
  SPRING_DATASOURCE_URL: "jdbc:postgresql://postgresql:5432/familyhobbies_associations"
  SPRING_DATASOURCE_USERNAME: "familyhobbies_assoc"
  SPRING_KAFKA_BOOTSTRAP_SERVERS: "kafka:9092"
  HELLOASSO_API_BASE_URL: "https://api.helloasso-sandbox.com/v5"
  MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE: "health,info,metrics"
  MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED: "true"
```

- **Verify**: `oc apply --dry-run=client -f k8s/base/association-service/` -> all 3 resources created (dry run)

---

## Task 8 Detail: Payment-Service Manifests

- **What**: Payment-service Deployment, Service, and ConfigMap. Manages HelloAsso Checkout integration, payment webhooks, and invoices.
- **Where**: `k8s/base/payment-service/deployment.yaml`, `service.yaml`, `configmap.yaml`
- **Why**: The payment-service orchestrates payment flows through HelloAsso Checkout and processes webhooks. It needs HelloAsso credentials and publishes Kafka events for payment lifecycle.

### deployment.yaml

```yaml
# k8s/base/payment-service/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: payment-service
  labels:
    app: payment-service
    app.kubernetes.io/part-of: family-hobbies-manager
spec:
  replicas: 1
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxUnavailable: 0
      maxSurge: 1
  selector:
    matchLabels:
      app: payment-service
  template:
    metadata:
      labels:
        app: payment-service
    spec:
      containers:
        - name: payment-service
          image: familyhobbies/payment-service:latest
          ports:
            - containerPort: 8083
              protocol: TCP
          envFrom:
            - configMapRef:
                name: payment-service-config
          env:
            - name: SPRING_DATASOURCE_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: family-hobbies-secrets
                  key: db-payment-password
            - name: HELLOASSO_CLIENT_ID
              valueFrom:
                secretKeyRef:
                  name: family-hobbies-secrets
                  key: helloasso-client-id
            - name: HELLOASSO_CLIENT_SECRET
              valueFrom:
                secretKeyRef:
                  name: family-hobbies-secrets
                  key: helloasso-client-secret
          resources:
            requests:
              memory: "256Mi"
              cpu: "250m"
            limits:
              memory: "512Mi"
              cpu: "500m"
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8083
            initialDelaySeconds: 30
            periodSeconds: 10
            timeoutSeconds: 5
            failureThreshold: 3
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8083
            initialDelaySeconds: 60
            periodSeconds: 15
            timeoutSeconds: 5
            failureThreshold: 3
      restartPolicy: Always
```

### service.yaml

```yaml
# k8s/base/payment-service/service.yaml
apiVersion: v1
kind: Service
metadata:
  name: payment-service
  labels:
    app: payment-service
    app.kubernetes.io/part-of: family-hobbies-manager
spec:
  type: ClusterIP
  selector:
    app: payment-service
  ports:
    - name: http
      port: 8083
      targetPort: 8083
      protocol: TCP
```

### configmap.yaml

```yaml
# k8s/base/payment-service/configmap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: payment-service-config
  labels:
    app: payment-service
    app.kubernetes.io/part-of: family-hobbies-manager
data:
  SPRING_PROFILES_ACTIVE: "k8s"
  SERVER_PORT: "8083"
  EUREKA_CLIENT_SERVICE_URL_DEFAULTZONE: "http://discovery-service:8761/eureka/"
  SPRING_DATASOURCE_URL: "jdbc:postgresql://postgresql:5432/familyhobbies_payments"
  SPRING_DATASOURCE_USERNAME: "familyhobbies_pay"
  SPRING_KAFKA_BOOTSTRAP_SERVERS: "kafka:9092"
  HELLOASSO_API_BASE_URL: "https://api.helloasso-sandbox.com/v5"
  MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE: "health,info,metrics"
  MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED: "true"
```

- **Verify**: `oc apply --dry-run=client -f k8s/base/payment-service/` -> all 3 resources created (dry run)

---

## Task 9 Detail: Notification-Service Manifests

- **What**: Notification-service Deployment, Service, and ConfigMap. Manages notifications, email templates, and Kafka listeners for cross-service events.
- **Where**: `k8s/base/notification-service/deployment.yaml`, `service.yaml`, `configmap.yaml`
- **Why**: The notification-service consumes Kafka events (UserRegistered, SubscriptionCreated, PaymentCompleted, PaymentFailed) and dispatches email/in-app notifications. It needs SMTP credentials and its own database.

### deployment.yaml

```yaml
# k8s/base/notification-service/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: notification-service
  labels:
    app: notification-service
    app.kubernetes.io/part-of: family-hobbies-manager
spec:
  replicas: 1
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxUnavailable: 0
      maxSurge: 1
  selector:
    matchLabels:
      app: notification-service
  template:
    metadata:
      labels:
        app: notification-service
    spec:
      containers:
        - name: notification-service
          image: familyhobbies/notification-service:latest
          ports:
            - containerPort: 8084
              protocol: TCP
          envFrom:
            - configMapRef:
                name: notification-service-config
          env:
            - name: SPRING_DATASOURCE_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: family-hobbies-secrets
                  key: db-notification-password
            - name: SPRING_MAIL_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: family-hobbies-secrets
                  key: smtp-password
          resources:
            requests:
              memory: "256Mi"
              cpu: "250m"
            limits:
              memory: "512Mi"
              cpu: "500m"
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8084
            initialDelaySeconds: 30
            periodSeconds: 10
            timeoutSeconds: 5
            failureThreshold: 3
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8084
            initialDelaySeconds: 60
            periodSeconds: 15
            timeoutSeconds: 5
            failureThreshold: 3
      restartPolicy: Always
```

### service.yaml

```yaml
# k8s/base/notification-service/service.yaml
apiVersion: v1
kind: Service
metadata:
  name: notification-service
  labels:
    app: notification-service
    app.kubernetes.io/part-of: family-hobbies-manager
spec:
  type: ClusterIP
  selector:
    app: notification-service
  ports:
    - name: http
      port: 8084
      targetPort: 8084
      protocol: TCP
```

### configmap.yaml

```yaml
# k8s/base/notification-service/configmap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: notification-service-config
  labels:
    app: notification-service
    app.kubernetes.io/part-of: family-hobbies-manager
data:
  SPRING_PROFILES_ACTIVE: "k8s"
  SERVER_PORT: "8084"
  EUREKA_CLIENT_SERVICE_URL_DEFAULTZONE: "http://discovery-service:8761/eureka/"
  SPRING_DATASOURCE_URL: "jdbc:postgresql://postgresql:5432/familyhobbies_notifications"
  SPRING_DATASOURCE_USERNAME: "familyhobbies_notif"
  SPRING_KAFKA_BOOTSTRAP_SERVERS: "kafka:9092"
  SPRING_MAIL_HOST: "smtp.example.com"
  SPRING_MAIL_PORT: "587"
  SPRING_MAIL_USERNAME: "noreply@family-hobbies.fr"
  MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE: "health,info,metrics"
  MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED: "true"
```

- **Verify**: `oc apply --dry-run=client -f k8s/base/notification-service/` -> all 3 resources created (dry run)

---

## Task 10 Detail: Frontend Manifests

- **What**: Angular frontend Deployment, Service, and Route (edge TLS). Serves the compiled Angular SPA via nginx.
- **Where**: `k8s/base/frontend/deployment.yaml`, `service.yaml`, `route.yaml`
- **Why**: The frontend is the user-facing application. It runs nginx serving static files. No ConfigMap needed because environment-specific API URLs are baked into Angular's `environment.ts` at build time (or injected via `env.js` at container startup).

### deployment.yaml

```yaml
# k8s/base/frontend/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: frontend
  labels:
    app: frontend
    app.kubernetes.io/part-of: family-hobbies-manager
spec:
  replicas: 1
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxUnavailable: 0
      maxSurge: 1
  selector:
    matchLabels:
      app: frontend
  template:
    metadata:
      labels:
        app: frontend
    spec:
      containers:
        - name: frontend
          image: familyhobbies/frontend:latest
          ports:
            - containerPort: 80
              protocol: TCP
          resources:
            requests:
              memory: "64Mi"
              cpu: "50m"
            limits:
              memory: "128Mi"
              cpu: "200m"
          readinessProbe:
            httpGet:
              path: /health
              port: 80
            initialDelaySeconds: 5
            periodSeconds: 10
            timeoutSeconds: 3
            failureThreshold: 3
          livenessProbe:
            httpGet:
              path: /health
              port: 80
            initialDelaySeconds: 10
            periodSeconds: 15
            timeoutSeconds: 3
            failureThreshold: 3
      restartPolicy: Always
```

### service.yaml

```yaml
# k8s/base/frontend/service.yaml
apiVersion: v1
kind: Service
metadata:
  name: frontend
  labels:
    app: frontend
    app.kubernetes.io/part-of: family-hobbies-manager
spec:
  type: ClusterIP
  selector:
    app: frontend
  ports:
    - name: http
      port: 80
      targetPort: 80
      protocol: TCP
```

### route.yaml

```yaml
# k8s/base/frontend/route.yaml
apiVersion: route.openshift.io/v1
kind: Route
metadata:
  name: frontend
  labels:
    app: frontend
    app.kubernetes.io/part-of: family-hobbies-manager
spec:
  host: family-hobbies.dev.example.com
  port:
    targetPort: http
  tls:
    termination: edge
    insecureEdgeTerminationPolicy: Redirect
  to:
    kind: Service
    name: frontend
    weight: 100
  wildcardPolicy: None
```

- **Verify**: `oc apply --dry-run=client -f k8s/base/frontend/` -> all 3 resources created (dry run)

---

## Task 11 Detail: PostgreSQL StatefulSet

- **What**: PostgreSQL 16 StatefulSet with a headless Service and PersistentVolumeClaim (5Gi). Runs all four service databases in one instance using an init script for database creation.
- **Where**: `k8s/base/postgresql/statefulset.yaml`, `service.yaml`, `pvc.yaml`
- **Why**: StatefulSet guarantees stable network identity and persistent storage across pod restarts. A single PostgreSQL instance with multiple databases simplifies the dev/demo deployment. Production could split into per-service instances.

### statefulset.yaml

```yaml
# k8s/base/postgresql/statefulset.yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: postgresql
  labels:
    app: postgresql
    app.kubernetes.io/part-of: family-hobbies-manager
spec:
  serviceName: postgresql
  replicas: 1
  selector:
    matchLabels:
      app: postgresql
  template:
    metadata:
      labels:
        app: postgresql
    spec:
      containers:
        - name: postgresql
          image: postgres:16-alpine
          ports:
            - containerPort: 5432
              protocol: TCP
          env:
            - name: POSTGRES_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: family-hobbies-secrets
                  key: db-root-password
            - name: POSTGRES_USER
              value: "postgres"
            - name: POSTGRES_MULTIPLE_DATABASES
              value: "familyhobbies_users,familyhobbies_associations,familyhobbies_payments,familyhobbies_notifications"
          volumeMounts:
            - name: postgresql-data
              mountPath: /var/lib/postgresql/data
              subPath: pgdata
            - name: init-scripts
              mountPath: /docker-entrypoint-initdb.d
          resources:
            requests:
              memory: "256Mi"
              cpu: "250m"
            limits:
              memory: "512Mi"
              cpu: "500m"
          readinessProbe:
            exec:
              command:
                - pg_isready
                - -U
                - postgres
            initialDelaySeconds: 10
            periodSeconds: 10
            timeoutSeconds: 5
            failureThreshold: 3
          livenessProbe:
            exec:
              command:
                - pg_isready
                - -U
                - postgres
            initialDelaySeconds: 30
            periodSeconds: 15
            timeoutSeconds: 5
            failureThreshold: 3
      volumes:
        - name: postgresql-data
          persistentVolumeClaim:
            claimName: postgresql-pvc
        - name: init-scripts
          configMap:
            name: postgresql-init-scripts
  volumeClaimTemplates: []
```

### service.yaml

```yaml
# k8s/base/postgresql/service.yaml
apiVersion: v1
kind: Service
metadata:
  name: postgresql
  labels:
    app: postgresql
    app.kubernetes.io/part-of: family-hobbies-manager
spec:
  type: ClusterIP
  clusterIP: None
  selector:
    app: postgresql
  ports:
    - name: postgresql
      port: 5432
      targetPort: 5432
      protocol: TCP
```

### pvc.yaml

```yaml
# k8s/base/postgresql/pvc.yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: postgresql-pvc
  labels:
    app: postgresql
    app.kubernetes.io/part-of: family-hobbies-manager
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 5Gi
```

- **Verify**: `oc apply --dry-run=client -f k8s/base/postgresql/` -> all 3 resources created (dry run)

---

## Task 12 Detail: Kafka StatefulSet

- **What**: Apache Kafka StatefulSet (KRaft mode, no Zookeeper) with a headless Service. Single-node configuration for dev/demo.
- **Where**: `k8s/base/kafka/statefulset.yaml`, `service.yaml`
- **Why**: Kafka is the event bus for inter-service communication (UserRegisteredEvent, SubscriptionCreatedEvent, PaymentCompletedEvent, PaymentFailedEvent). KRaft mode eliminates Zookeeper dependency, simplifying the deployment.

### statefulset.yaml

```yaml
# k8s/base/kafka/statefulset.yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: kafka
  labels:
    app: kafka
    app.kubernetes.io/part-of: family-hobbies-manager
spec:
  serviceName: kafka
  replicas: 1
  selector:
    matchLabels:
      app: kafka
  template:
    metadata:
      labels:
        app: kafka
    spec:
      containers:
        - name: kafka
          image: bitnami/kafka:3.7
          ports:
            - containerPort: 9092
              name: client
              protocol: TCP
            - containerPort: 9093
              name: controller
              protocol: TCP
          env:
            - name: KAFKA_CFG_NODE_ID
              value: "0"
            - name: KAFKA_CFG_PROCESS_ROLES
              value: "controller,broker"
            - name: KAFKA_CFG_LISTENERS
              value: "PLAINTEXT://:9092,CONTROLLER://:9093"
            - name: KAFKA_CFG_ADVERTISED_LISTENERS
              value: "PLAINTEXT://kafka:9092"
            - name: KAFKA_CFG_LISTENER_SECURITY_PROTOCOL_MAP
              value: "PLAINTEXT:PLAINTEXT,CONTROLLER:PLAINTEXT"
            - name: KAFKA_CFG_CONTROLLER_QUORUM_VOTERS
              value: "0@kafka:9093"
            - name: KAFKA_CFG_CONTROLLER_LISTENER_NAMES
              value: "CONTROLLER"
            - name: KAFKA_CFG_AUTO_CREATE_TOPICS_ENABLE
              value: "true"
            - name: KAFKA_CFG_OFFSETS_TOPIC_REPLICATION_FACTOR
              value: "1"
          resources:
            requests:
              memory: "512Mi"
              cpu: "250m"
            limits:
              memory: "1Gi"
              cpu: "500m"
          readinessProbe:
            tcpSocket:
              port: 9092
            initialDelaySeconds: 30
            periodSeconds: 10
            timeoutSeconds: 5
            failureThreshold: 3
          livenessProbe:
            tcpSocket:
              port: 9092
            initialDelaySeconds: 60
            periodSeconds: 15
            timeoutSeconds: 5
            failureThreshold: 3
```

### service.yaml

```yaml
# k8s/base/kafka/service.yaml
apiVersion: v1
kind: Service
metadata:
  name: kafka
  labels:
    app: kafka
    app.kubernetes.io/part-of: family-hobbies-manager
spec:
  type: ClusterIP
  clusterIP: None
  selector:
    app: kafka
  ports:
    - name: client
      port: 9092
      targetPort: 9092
      protocol: TCP
    - name: controller
      port: 9093
      targetPort: 9093
      protocol: TCP
```

- **Verify**: `oc apply --dry-run=client -f k8s/base/kafka/` -> both resources created (dry run)

---

## Task 13 Detail: Dev Overlay

- **What**: Kustomize overlay for the `family-hobbies-dev` namespace. Keeps replicas at 1, uses `:latest` image tags, and applies lower resource limits suitable for a development cluster.
- **Where**: `k8s/overlays/dev/kustomization.yaml`, `k8s/overlays/dev/patches/`
- **Why**: Dev overlay inherits everything from base and applies environment-specific tweaks. This avoids duplicating manifests across environments.

### kustomization.yaml

```yaml
# k8s/overlays/dev/kustomization.yaml
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization

namespace: family-hobbies-dev

namePrefix: ""

commonLabels:
  environment: dev

resources:
  - ../../base

patches:
  - path: patches/resource-limits.yaml
    target:
      kind: Deployment
  - path: patches/route-hosts.yaml
    target:
      kind: Route

images:
  - name: familyhobbies/discovery-service
    newTag: latest
  - name: familyhobbies/api-gateway
    newTag: latest
  - name: familyhobbies/user-service
    newTag: latest
  - name: familyhobbies/association-service
    newTag: latest
  - name: familyhobbies/payment-service
    newTag: latest
  - name: familyhobbies/notification-service
    newTag: latest
  - name: familyhobbies/frontend
    newTag: latest
```

### patches/resource-limits.yaml

```yaml
# k8s/overlays/dev/patches/resource-limits.yaml
# Dev: lower resource limits to save cluster capacity
apiVersion: apps/v1
kind: Deployment
metadata:
  name: not-important
spec:
  replicas: 1
  template:
    spec:
      containers:
        - name: not-important
          resources:
            requests:
              memory: "128Mi"
              cpu: "100m"
            limits:
              memory: "384Mi"
              cpu: "300m"
```

### patches/route-hosts.yaml

```yaml
# k8s/overlays/dev/patches/route-hosts.yaml
# Dev: use dev subdomain for routes
apiVersion: route.openshift.io/v1
kind: Route
metadata:
  name: not-important
spec:
  host: ""
```

- **Verify**: `kustomize build k8s/overlays/dev` -> outputs combined YAML with `family-hobbies-dev` namespace and dev labels

---

## Task 14 Detail: Prod Overlay

- **What**: Kustomize overlay for the `family-hobbies-prod` namespace. Increases replicas to 2 for backend services, uses specific version tags, and applies higher resource limits.
- **Where**: `k8s/overlays/prod/kustomization.yaml`, `k8s/overlays/prod/patches/`
- **Why**: Production needs higher availability (2 replicas), pinned image versions (not `:latest`), and more generous resource allocations. Route hosts point to the production domain.

### kustomization.yaml

```yaml
# k8s/overlays/prod/kustomization.yaml
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization

namespace: family-hobbies-prod

commonLabels:
  environment: prod

resources:
  - ../../base

patches:
  - path: patches/replicas.yaml
    target:
      kind: Deployment
      labelSelector: "app notin (frontend)"
  - path: patches/resource-limits.yaml
    target:
      kind: Deployment
  - path: patches/route-hosts-api.yaml
    target:
      kind: Route
      name: api-gateway
  - path: patches/route-hosts-frontend.yaml
    target:
      kind: Route
      name: frontend
  - path: patches/namespace-override.yaml
    target:
      kind: Namespace

images:
  - name: familyhobbies/discovery-service
    newTag: "1.0.0"
  - name: familyhobbies/api-gateway
    newTag: "1.0.0"
  - name: familyhobbies/user-service
    newTag: "1.0.0"
  - name: familyhobbies/association-service
    newTag: "1.0.0"
  - name: familyhobbies/payment-service
    newTag: "1.0.0"
  - name: familyhobbies/notification-service
    newTag: "1.0.0"
  - name: familyhobbies/frontend
    newTag: "1.0.0"
```

### patches/replicas.yaml

```yaml
# k8s/overlays/prod/patches/replicas.yaml
# Prod: 2 replicas for high availability
apiVersion: apps/v1
kind: Deployment
metadata:
  name: not-important
spec:
  replicas: 2
```

### patches/resource-limits.yaml

```yaml
# k8s/overlays/prod/patches/resource-limits.yaml
# Prod: higher resource limits for production workloads
apiVersion: apps/v1
kind: Deployment
metadata:
  name: not-important
spec:
  template:
    spec:
      containers:
        - name: not-important
          resources:
            requests:
              memory: "256Mi"
              cpu: "250m"
            limits:
              memory: "512Mi"
              cpu: "500m"
```

### patches/route-hosts-api.yaml

```yaml
# k8s/overlays/prod/patches/route-hosts-api.yaml
apiVersion: route.openshift.io/v1
kind: Route
metadata:
  name: api-gateway
spec:
  host: api.family-hobbies.fr
```

### patches/route-hosts-frontend.yaml

```yaml
# k8s/overlays/prod/patches/route-hosts-frontend.yaml
apiVersion: route.openshift.io/v1
kind: Route
metadata:
  name: frontend
spec:
  host: family-hobbies.fr
```

### patches/namespace-override.yaml

```yaml
# k8s/overlays/prod/patches/namespace-override.yaml
apiVersion: v1
kind: Namespace
metadata:
  name: family-hobbies-prod
  labels:
    environment: prod
```

- **Verify**: `kustomize build k8s/overlays/prod` -> outputs combined YAML with `family-hobbies-prod` namespace, 2 replicas, prod labels, and prod domain routes

---

## Task 15 Detail: Deployment Guide

### Prerequisites

- OpenShift CLI (`oc`) 4.12+ installed and authenticated to the target cluster
- `kustomize` CLI v5+ installed (or use `oc kustomize` / `kubectl kustomize`)
- Docker images built and pushed to an accessible registry (see S8-004)
- Secrets prepared with real values (replace `CHANGE_ME` placeholders)

### Deploy to Dev

```bash
# Step 1: Login to OpenShift cluster
oc login https://api.your-cluster.example.com:6443 --token=YOUR_TOKEN

# Step 2: Create the dev namespace (if it does not exist)
oc apply -f k8s/base/namespace.yaml

# Step 3: Create secrets with real values (do this ONCE, not from the template)
oc create secret generic family-hobbies-secrets \
  --namespace=family-hobbies-dev \
  --from-literal=jwt-secret='your-256-bit-jwt-secret-key-here' \
  --from-literal=helloasso-client-id='your-sandbox-client-id' \
  --from-literal=helloasso-client-secret='your-sandbox-client-secret' \
  --from-literal=db-user-password='secure-password-users' \
  --from-literal=db-association-password='secure-password-associations' \
  --from-literal=db-payment-password='secure-password-payments' \
  --from-literal=db-notification-password='secure-password-notifications' \
  --from-literal=db-root-password='secure-root-password' \
  --from-literal=smtp-password='smtp-password-here'

# Step 4: Apply all manifests via Kustomize dev overlay
oc apply -k k8s/overlays/dev

# Step 5: Verify all pods are running
oc get pods -n family-hobbies-dev -w

# Step 6: Verify routes
oc get routes -n family-hobbies-dev
```

### Deploy to Prod

```bash
# Step 1: Create the prod namespace
oc create namespace family-hobbies-prod

# Step 2: Create secrets (same structure, production values)
oc create secret generic family-hobbies-secrets \
  --namespace=family-hobbies-prod \
  --from-literal=jwt-secret='production-jwt-secret' \
  --from-literal=helloasso-client-id='production-client-id' \
  --from-literal=helloasso-client-secret='production-client-secret' \
  --from-literal=db-user-password='prod-password-users' \
  --from-literal=db-association-password='prod-password-associations' \
  --from-literal=db-payment-password='prod-password-payments' \
  --from-literal=db-notification-password='prod-password-notifications' \
  --from-literal=db-root-password='prod-root-password' \
  --from-literal=smtp-password='prod-smtp-password'

# Step 3: Apply prod overlay
oc apply -k k8s/overlays/prod

# Step 4: Verify
oc get pods -n family-hobbies-prod -w
oc get routes -n family-hobbies-prod
```

### Verify Health

```bash
# Check all pods are Ready
oc get pods -n family-hobbies-dev

# Check individual service health
oc port-forward svc/discovery-service 8761:8761 -n family-hobbies-dev &
curl http://localhost:8761/actuator/health

# Check routes are accessible
curl -k https://$(oc get route api-gateway -n family-hobbies-dev -o jsonpath='{.spec.host}')/actuator/health
curl -k https://$(oc get route frontend -n family-hobbies-dev -o jsonpath='{.spec.host}')/
```

### Rollback

```bash
# Rollback a specific deployment to the previous revision
oc rollout undo deployment/user-service -n family-hobbies-dev

# Check rollout status
oc rollout status deployment/user-service -n family-hobbies-dev
```

---

## Design Decisions

### Why Kustomize Over Helm

Kustomize is built into `kubectl` and `oc`, requires no extra tooling, and uses plain YAML patches rather than Go templates. For a portfolio project with clear base/overlay patterns, Kustomize provides sufficient flexibility without the complexity of Helm charts. Helm would be preferred at scale with many configurable values, but Kustomize keeps the manifests readable and auditable.

### Why a Single PostgreSQL Instance

In production, each microservice ideally has its own PostgreSQL instance for true isolation. For the dev/demo deployment, a single instance with multiple databases is simpler, uses fewer cluster resources, and still demonstrates the one-DB-per-service pattern at the logical level.

### Why KRaft Mode for Kafka

Apache Kafka 3.7 supports KRaft (Kafka Raft) mode, eliminating the need for a separate Zookeeper deployment. This reduces the number of pods, simplifies configuration, and aligns with Kafka's recommended production architecture going forward.

### Why Edge TLS on Routes

Edge TLS termination at the OpenShift Router means the Router handles SSL certificates (which can be auto-provisioned via cert-manager or OpenShift's built-in certificate management), and traffic within the cluster travels over plain HTTP. This is the standard pattern for most web applications on OpenShift.

### Why Resource Requests Differ From Limits

Setting `requests` lower than `limits` allows Kubernetes to bin-pack pods more efficiently on shared nodes. The pod is guaranteed `requests` and can burst up to `limits` when the node has spare capacity. This is appropriate for a demo/portfolio deployment where burst performance matters more than strict isolation.

---

## File Structure Summary

```
k8s/
  +-- base/
  |   +-- kustomization.yaml
  |   +-- namespace.yaml
  |   +-- discovery-service/
  |   |   +-- deployment.yaml, service.yaml, configmap.yaml
  |   +-- api-gateway/
  |   |   +-- deployment.yaml, service.yaml, route.yaml, configmap.yaml
  |   +-- user-service/
  |   |   +-- deployment.yaml, service.yaml, configmap.yaml
  |   +-- association-service/
  |   |   +-- deployment.yaml, service.yaml, configmap.yaml
  |   +-- payment-service/
  |   |   +-- deployment.yaml, service.yaml, configmap.yaml
  |   +-- notification-service/
  |   |   +-- deployment.yaml, service.yaml, configmap.yaml
  |   +-- frontend/
  |   |   +-- deployment.yaml, service.yaml, route.yaml
  |   +-- postgresql/
  |   |   +-- statefulset.yaml, service.yaml, pvc.yaml
  |   +-- kafka/
  |   |   +-- statefulset.yaml, service.yaml
  |   +-- secrets/
  |       +-- secrets.yaml
  +-- overlays/
      +-- dev/
      |   +-- kustomization.yaml
      |   +-- patches/
      |       +-- resource-limits.yaml
      |       +-- route-hosts.yaml
      +-- prod/
          +-- kustomization.yaml
          +-- patches/
              +-- replicas.yaml
              +-- resource-limits.yaml
              +-- route-hosts-api.yaml
              +-- route-hosts-frontend.yaml
              +-- namespace-override.yaml
```

---

## Acceptance Criteria Checklist

- [ ] All YAML manifests are valid and well-formed
- [ ] `oc apply --dry-run=client -k k8s/base` succeeds without errors
- [ ] `kustomize build k8s/overlays/dev` produces valid combined YAML
- [ ] `kustomize build k8s/overlays/prod` produces valid combined YAML
- [ ] Every backend Deployment has resource limits (512Mi memory / 500m CPU)
- [ ] Every backend Deployment has readiness and liveness probes pointing at `/actuator/health/*`
- [ ] Frontend Deployment has readiness and liveness probes pointing at `/health`
- [ ] API Gateway and Frontend have Route resources with edge TLS termination
- [ ] Secrets use base64-encoded placeholder values (no real credentials)
- [ ] PostgreSQL uses a PersistentVolumeClaim (5Gi)
- [ ] Dev overlay: 1 replica, `:latest` tags, lower resource limits
- [ ] Prod overlay: 2 replicas, pinned version tags (`1.0.0`), production domain routes
- [ ] Deployment guide includes step-by-step `oc` commands
- [ ] Rollback procedure documented
