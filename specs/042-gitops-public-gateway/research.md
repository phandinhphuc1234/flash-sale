# Phase 23 Research: Development Public Gateway

## Baseline

- `api-gateway` is currently a `ClusterIP` Service in `infra/k8s/base/api-gateway/service.yaml`.
- The cloud overlay is reconciled by Argo Application `flash-sale-cloud`.
- Phase 18 and Phase 22 use a loopback-only `kubectl port-forward`; no public endpoint exists.
- Terraform tags the VPC public subnets with `kubernetes.io/role/elb=1`, but the repository does not
  currently declare an AWS Load Balancer Controller or Helm provider.

## Decision candidates

| Candidate | Benefits | Costs / risks | Status |
|---|---|---|---|
| Kubernetes `Service type: LoadBalancer` with AWS NLB annotations | Smallest change; preserves Gateway as the only edge; no new application dependency | Controller/cloud-provider behavior must be verified on this EKS cluster; generated HTTP hostname is dev-only | Recommended pending operator confirmation |
| AWS Load Balancer Controller + Ingress/ALB | Rich routing, security-group and TLS integration | Adds controller, IAM/IRSA, CRDs, Helm ownership and another reconciliation lifecycle | Deferred unless required |
| NodePort or direct node IP | No managed LB dependency | Bypasses stable cloud edge and weakens security/operations | Rejected |

## Safety boundaries

1. Do not change the base Service until the exposure mechanism is approved.
2. Keep the public edge in the cloud overlay only; local overlays remain unchanged.
3. Inventory every Service after reconciliation and fail if any non-Gateway Service is public.
4. Use the generated endpoint only for bounded development smoke; defer TLS/domain/WAF.
5. Roll back through Git by restoring `ClusterIP`; do not delete PVCs, Secrets, topics, or data.
