# Phase 18 Quickstart

Run from the repository root while kubectl points at flash-sale-dev:

    .\infra\scripts\gitops\phase18-gateway-smoke.ps1
    .\infra\scripts\gitops\phase18-gateway-smoke.ps1 -Run

The first command is validation-only. The second creates only a temporary local port-forward and
prints status codes; it does not apply manifests, create an ingress, or expose the Gateway
publicly. The default is port 28080 because local Docker Compose owns ports 18080–18089. The script
refuses to run if the selected port is already occupied; use another value with -LocalPort.
