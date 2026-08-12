# Contract: Container Runtime and Local Orchestration

This contract defines the observable runtime conventions introduced by the feature.

## Service image contract

Every service module must expose this build shape:

```text
docker build -f services/<service>/Dockerfile -t flash-sale/<service>:local .
```

Required services:

- `api-gateway`
- `authentication-service`
- `product-service`
- `campaign-service`
- `flashsale-service`
- `order-service`
- `payment-service`
- `notification-service`
- `chatting-service`

Each image must run the service on container port `8080`.

## Compose validation contract

Baseline local topology must render successfully:

```powershell
docker compose --env-file infra/docker/.env.example -f infra/docker/compose.yml config
```

Development override topology must render successfully:

```powershell
docker compose --env-file infra/docker/.env.example -f infra/docker/compose.yml -f infra/docker/compose.dev.yml --profile apps config
```

## Compose runtime contract

Platform-only local runtime:

```powershell
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml up -d postgres redis kafka
```

Full local application runtime:

```powershell
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml --profile apps up --build
```

Development-debug runtime:

```powershell
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml -f infra/docker/compose.dev.yml --profile apps up --build
```

## Public port contract

Default app topology:

- `api-gateway`: host `8080` -> container `8080`
- Non-gateway services: no host port mapping

Development override topology may expose non-gateway services on host ports `18081` through `18088` for debugging only.

## Health endpoint contract

Every application container keeps these Spring Boot Actuator paths:

- `/actuator/health`
- `/actuator/health/liveness`
- `/actuator/health/readiness`
- `/actuator/info`
- `/actuator/prometheus`

Future Kubernetes probes should target:

- liveness: `/actuator/health/liveness`
- readiness: `/actuator/health/readiness`

## Kubernetes migration contract

Future Kubernetes manifests should use the same logical names:

```text
compose service name -> Kubernetes Service name
flash-sale/<service>:<tag> -> Deployment container image
non-secret env vars -> ConfigMap
secret env vars -> Secret or external secret provider
Liquibase migration execution -> Kubernetes Job
app runtime -> Deployment
api-gateway public access -> Ingress or Gateway API route to api-gateway
```
