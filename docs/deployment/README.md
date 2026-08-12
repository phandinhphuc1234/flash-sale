# Deployment Documentation

This folder explains how the Flash Sale monorepo is packaged and run across local and future
cluster environments.

Start here:

- [Container Compose and Kubernetes strategy](container-compose-k8s-strategy.md)

## Ownership reminder

- Service image recipes belong to `services/<service>/Dockerfile`.
- Shared local orchestration belongs to `infra/docker/`.
- Future Kubernetes bases and overlays belong to `infra/k8s/`.
- Service runtime configuration and database migrations stay with the owning service.
