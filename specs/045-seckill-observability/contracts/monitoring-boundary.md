# Contract: Internal Monitoring Boundary

## Prometheus scrape contract

- Protocol: HTTP inside the Kubernetes cluster.
- Path: `/actuator/prometheus`.
- Port: `8080` through each application ClusterIP Service.
- Targets: api-gateway, authentication-service, product-service, campaign-service,
  flash-sale-service, inventory-service, order-service, payment-service.
- Interval: 15 seconds.
- Public exposure: prohibited.

## Grafana datasource contract

- URL: `http://prometheus.monitoring.svc.cluster.local:9090`.
- Access mode: server/proxy.
- Default datasource: yes.
- Provisioning owner: Git; UI edits are non-durable.

## Operator access contract

```powershell
kubectl -n monitoring port-forward service/grafana 3000:3000
```

The operator opens `http://127.0.0.1:3000`. A LoadBalancer, NodePort, ingress, public DNS record, or
public certificate for monitoring is outside the approved scope.

## Secret contract

- Secret: `monitoring/grafana-admin`.
- Keys: `admin-user`, `admin-password`.
- The provisioning script may read credentials from memory and send them directly to Kubernetes.
- The script, Git, logs, validation evidence, and generated files must not contain the values.

## Failure-isolation contract

- Application Deployments do not reference Prometheus or Grafana.
- Application liveness/readiness does not query monitoring.
- Prometheus target failures do not mutate service state.
- `flash-sale-observability` does not own `infra/k8s/overlays/cloud`.
- Monitoring rollback must not delete or restart application resources.
