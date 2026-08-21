# Research: Argo CD Dev Pilot Bootstrap

## Decisions

1. **Use the official non-HA install manifest**
   - Helm is not installed on the operator workstation.
   - A pinned upstream manifest avoids adding a Helm dependency to the first GitOps exercise.
   - The pinned version is `v3.4.2`; upgrades are explicit changes, not floating `stable` URLs.

2. **Keep Argo CD private**
   - The default `argocd-server` Service remains `ClusterIP`.
   - Operators use `kubectl port-forward`; no AWS LoadBalancer or DNS is needed.

3. **Use a single Application with no prune**
   - The Application points only to `infra/k8s/overlays/dev-pilot` on `develop`.
   - `selfHeal: true` demonstrates reconciliation; `prune: false` avoids accidental deletion during
     the first training rollout, especially around the PVC.

4. **Keep secrets outside Argo CD source**
   - Argo CD applies name-only Secret references already provisioned by the operator.

## Alternatives Rejected

- Floating `stable` manifest: rejected because the control plane version would change without review.
- Public LoadBalancer: rejected because port-forward is sufficient for a local internship.
- Full eight-service Application: rejected because Phase 9 only proves the Product pilot.
- Helm install in this turn: deferred because Helm is not installed and the official manifest is
  simpler for the first bootstrap.
