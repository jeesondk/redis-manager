# redis-manager Helm Chart

This chart deploys the Redis Manager application (Quarkus + React UI) to Kubernetes.

## Features
- Configurable image repository/tag
- Kubernetes Service + optional Ingress
- Probes (startup, liveness, readiness) with configurable paths and timing
- Quarkus configuration overrides via ConfigMap (application.properties)
- Optional OIDC authentication configuration
- Resources/affinity/tolerations hooks

## Quick start

1. Push an image for the app (or let the included GitHub Action build & push to GHCR):
   - ghcr.io/<org>/redis-manager:latest

2. Install the chart:

```bash
helm repo add oci-ghcr oci://ghcr.io/<org>/charts
helm install redis-manager oci-ghcr/redis-manager \
  --set image.repository=ghcr.io/<org>/redis-manager \
  --set image.tag=latest
```

Or from sources:

```bash
helm install redis-manager ./charts/redis-manager \
  --set image.repository=ghcr.io/<org>/redis-manager \
  --set image.tag=latest
```

3. Optional: Enable ingress

```bash
helm upgrade --install redis-manager ./charts/redis-manager \
  --set ingress.enabled=true \
  --set ingress.className=nginx \
  --set ingress.hosts[0].host=redis.example.com \
  --set ingress.hosts[0].paths[0].path=/ \
  --set ingress.hosts[0].paths[0].pathType=Prefix
```

## Values

### IP restrictions

You can restrict which client IPs/CIDRs can access the application through the Ingress. This is configured via `ipRestrictions` in `values.yaml` and rendered as controller-specific annotations.

- NGINX Ingress (default when `ingress.className` is empty or contains `nginx`): uses `nginx.ingress.kubernetes.io/whitelist-source-range`.
- AWS ALB Ingress (when `ingress.className` contains `alb` or `aws`): uses `alb.ingress.kubernetes.io/inbound-cidrs`.
- For other controllers, use `ingress.annotations` directly to apply your own mechanism.

Examples:

NGINX:

```bash
auth_host=redis.example.com
helm upgrade --install redis-manager ./charts/redis-manager \
  --set ingress.enabled=true \
  --set ingress.className=nginx \
  --set ingress.hosts[0].host=$auth_host \
  --set ingress.hosts[0].paths[0].path=/ \
  --set ingress.hosts[0].paths[0].pathType=Prefix \
  --set ipRestrictions.enabled=true \
  --set ipRestrictions.allowList[0]=203.0.113.0/24 \
  --set ipRestrictions.allowList[1]=198.51.100.23/32
```

AWS ALB:

```bash
helm upgrade --install redis-manager ./charts/redis-manager \
  --set ingress.enabled=true \
  --set ingress.className=alb \
  --set ingress.hosts[0].host=redis.example.com \
  --set ingress.hosts[0].paths[0].path=/ \
  --set ingress.hosts[0].paths[0].pathType=Prefix \
  --set ipRestrictions.enabled=true \
  --set ipRestrictions.allowList[0]=10.0.0.0/8 \
  --set ipRestrictions.allowList[1]=192.168.0.0/16
```

If you use a different ingress controller, you can configure the appropriate annotations directly:

```yaml
ingress:
  enabled: true
  className: traefik
  annotations:
    traefik.ingress.kubernetes.io/router.middlewares: myns-allowlist@kubernetescrd
```

- replicaCount: number of pods (default 1)
- image:
  - repository: container registry (default ghcr.io/your-org/redis-manager)
  - tag: image tag (default Chart appVersion)
  - pullPolicy: IfNotPresent
  - pullSecrets: list
- service:
  - type: ClusterIP
  - port: 80
  - targetPort: 8080
  - annotations: {}
- management:
  - enabled: true
  - port: 9000
  - healthPath: /management/health
  - livenessPath: /management/health/live
  - readinessPath: /management/health/ready
- ingress:
  - enabled: false
  - className: ""
  - annotations: {}
  - hosts: [{ host, paths: [{ path, pathType }] }]
  - tls: []
- probes:
  - startup/liveness/readiness with timings
- extraEnv: []
- extraEnvFrom: []
- appProperties: {}
- auth:
  - type: none|oidc
  - oidc:
    - authServerUrl, clientId, clientSecret, applicationType, additional: {}

## Authentication

By default, authentication is disabled (auth.type=none). To use OIDC:

```bash
helm upgrade --install redis-manager ./charts/redis-manager \
  --set auth.type=oidc \
  --set auth.oidc.authServerUrl=https://keycloak.example.com/realms/myrealm \
  --set auth.oidc.clientId=redis-manager \
  --set auth.oidc.clientSecret=<secret> \
  --set appProperties."quarkus.oidc.application-type"=web
```

Notes:
- Client secret is stored in a Secret named <release>-secrets.
- Additional quarkus.oidc.* keys can be provided via auth.oidc.additional or appProperties.

## Quarkus configuration overrides

Any custom Quarkus properties can be provided under `appProperties` and will be written to an application.properties file mounted into the container. The chart sets `QUARKUS_CONFIG_LOCATIONS` to load it.

Example:

```yaml
appProperties:
  quarkus.otel.exporter.otlp.endpoint: http://otel-collector:4317
  quarkus.log.level: INFO
```

## Probes

By default, the chart points probes to the Quarkus management port (9000) and standard SmallRye Health endpoints under `/management/*`. Adjust paths/port to match your build if needed.

## GitHub Actions

A workflow at `.github/workflows/docker-helm.yml` is provided to:
- Build the application with Maven
- Build and push the Docker image to GHCR
- Package the Helm chart and push it to GHCR as an OCI artifact

The workflow uses the built-in `GITHUB_TOKEN` for authentication to GHCR.
