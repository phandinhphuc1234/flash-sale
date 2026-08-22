# GitOps Phase 5–20 helper scripts

These PowerShell scripts preserve the manual workflow used for the AWS EKS GitOps exercise.
Run them from any directory. They resolve the repository root from their own location.

## Safety rules

- Phase 5 runs Terraform plan by default. Add -Apply only after reviewing the plan.
- Phase 7 only renders and runs Kubernetes client-side dry-run. It never creates resources.
- Phase 8 image publishing requires -Push.
- Phase 8 pilot deployment requires -Apply and prompts for a PostgreSQL password at runtime.
- Phase 11 Product delivery builds locally by default; ECR publishing requires -Push and desired-state
  editing requires -UpdateOverlay. The helper never commits or pushes Git changes.
- Phase 15 validates local ignored inputs by default; Secret creation requires -Apply and never prints
  values. Stripe remains deferred unless explicitly enabled in a later phase.
- Phase 16 validates the platform, ConfigMaps, Secrets, and seven migration Jobs by default; migration
  Job creation requires -Apply. Existing Jobs require -ForceRerun, preserving failure evidence.
- Phase 17 validates the cloud application prerequisites and eight Deployments by default; application
  rollout requires -Apply. A failed rollout preserves Pods for inspection.
- Phase 18 validates the internal API Gateway by default; -Run creates only a temporary local
  port-forward and checks readiness, public catalog routing, and protected admin routing. It rejects
  occupied local ports and verifies the listener belongs to its kubectl child process.
- Phase 19 validates the full-cloud Argo CD ownership handoff by default. -Apply is accepted only
  after the reviewed Phase 19 desired state exists on origin/develop. It suspends the Product pilot,
  waits for the full-cloud owner to become Synced/Healthy, verifies eight Deployments and the Product
  image, and then removes only the finalizer-free historical Application object.
- Phase 20 validates the exact cloud Kafka contract inventory by default. -Apply is accepted only
  after the reviewed Phase 20 implementation exists on origin/develop. It disables implicit topic
  creation, provisions seven topics with three partitions and replication factor one, and registers
  nine exact Git-owned Avro subjects with BACKWARD_TRANSITIVE compatibility. It never deletes or
  recreates a topic or subject, and it expands a one-partition topic only while its total end offset
  is still zero.
- Phase 21 verifies the cloud release artifact as the staging-equivalent gate. It checks the canonical
  Argo Application, all eight Deployment/ECR/Pod image digests, disabled Payment flags, and reuses the
  localhost-only Gateway smoke. It is read-only: it does not build, push, reconcile, update images, or
  read Kubernetes Secrets.
- Phase 22 verifies cloud ownership and configuration boundaries. It checks the EKS context, cloud
  overlay, Argo source/policy, application ConfigMap/Secret references, platform Secret references,
  private Services, Kafka safety settings, and disabled Payment flags. It is read-only and queries
  Secret names only; it never reads Secret values, `.env` values, or JWT contents.
- Passwords, tfvars, kubeconfig files, and .env files are never written by these scripts.
- Do not run the Phase 7 full dev overlay against EKS yet; it contains all eight services.

## Sequence

From the repository root:

    .\infra\scripts\gitops\phase5-terraform.ps1
    .\infra\scripts\gitops\phase5-terraform.ps1 -Apply

    .\infra\scripts\gitops\phase6-eks-health.ps1
    .\infra\scripts\gitops\phase7-kustomize-validate.ps1

    .\infra\scripts\gitops\phase8-product-image.ps1 -Push

    $image = "090814040069.dkr.ecr.ap-southeast-2.amazonaws.com/flash-sale/product-service:pilot-<git-sha>"
    .\infra\scripts\gitops\phase8-product-pilot.ps1 -Image $image -Apply

    .\infra\scripts\gitops\phase11-product-delivery.ps1
    .\infra\scripts\gitops\phase11-product-delivery.ps1 -Push -UpdateOverlay
    kubectl kustomize infra/k8s/overlays/dev-pilot

    .\infra\scripts\gitops\phase13-environment-contract.ps1
    .\infra\scripts\gitops\phase14-stateful-preflight.ps1
    .\infra\scripts\gitops\phase15-secrets.ps1
    .\infra\scripts\gitops\phase15-secrets.ps1 -Apply
    .\infra\scripts\gitops\phase16-migrations.ps1
    .\infra\scripts\gitops\phase16-migrations.ps1 -Apply
    .\infra\scripts\gitops\phase17-application-rollout.ps1
    .\infra\scripts\gitops\phase17-application-rollout.ps1 -Apply

    .\infra\scripts\gitops\phase18-gateway-smoke.ps1
    .\infra\scripts\gitops\phase18-gateway-smoke.ps1 -Run

    .\infra\scripts\gitops\phase19-argocd-cloud.ps1

    # Run only after the Phase 18 and Phase 19 pull requests are merged into develop.
    git fetch origin develop
    git switch develop
    git pull --ff-only origin develop
    .\infra\scripts\gitops\phase19-argocd-cloud.ps1 -Apply

    # Before merge: inventory only. Exit code 2 means the approved desired state is still pending.
    .\infra\scripts\gitops\phase20-kafka-contracts.ps1

    # After the Phase 20 pull request is reviewed and merged into develop:
    git fetch origin develop
    git switch develop
    git pull --ff-only origin develop
    .\infra\scripts\gitops\phase20-kafka-contracts.ps1 -Apply

    # Prove the resulting state and a second idempotent reconciliation.
    .\infra\scripts\gitops\phase20-kafka-contracts.ps1
    .\infra\scripts\gitops\phase20-kafka-contracts.ps1 -Apply

    # Phase 21: verify the cloud release artifact without mutation.
    pwsh -NoLogo -NoProfile -File .\infra\scripts\gitops\phase21-cloud-release-verify.ps1

    # Phase 22: verify cloud ownership/configuration boundaries without mutation.
    pwsh -NoLogo -NoProfile -File .\infra\scripts\gitops\phase22-cloud-guard.ps1

Phase 8 pilot creates one PostgreSQL StatefulSet with an 8 GiB gp2 PVC, creates runtime Secrets
from an interactive password prompt, and applies only the product-service base. It is intentionally
a manual pilot and is not yet the GitOps desired-state source for Argo CD.

Phase 11 adds the hosted `Product Pilot Delivery` workflow. It verifies Product Service, uses the
existing GitHub OIDC role to push an immutable ECR image, and opens a pull request that changes only
the Product pilot image tag. Argo CD reconciles after that pull request is reviewed and merged.

Phase 17 rolls out the eight cloud application Deployments after the platform, service Secrets,
ConfigMaps, and seven migration Jobs are complete. Payment acceptance and Stripe remain disabled.
Its health endpoints are still probeable without a bearer token, while Payment business paths remain
protected until a later enablement phase.

Phase 18 keeps the Gateway Service internal (ClusterIP). The smoke helper temporarily forwards the
Service to localhost, verifies readiness and public catalog routing, and confirms an admin route
still rejects unauthenticated access. It does not create an ingress, DNS record, TLS certificate, or
public AWS endpoint.

Phase 19 transfers desired-state ownership from the Product-only `dev-pilot` Application to the
canonical `flash-sale-cloud` Application. The new owner tracks `develop` and
`infra/k8s/overlays/cloud`, so the merge must happen before `-Apply`. Automatic pruning remains off;
the migration Jobs, Secret provisioning, platform PVC cleanup, ingress, and public exposure are not
part of this phase. If cutover fails, the helper removes the new finalizer-free Application object
and restores the committed Product pilot Application.

Phase 20 materializes only the approved runtime contract set: seven Kafka topics and nine
TopicRecordNameStrategy Schema Registry subjects. The choice of three partitions and replication
factor one is deliberate for this single-environment internship deployment; it is not presented as
a production high-availability topology. Existing one-partition topics are expanded only when their
combined end offset is zero, because changing partitions after records exist can change key-to-
partition routing. Unexpected topics, subjects, replication factors, or non-empty one-partition
topics stop the run for human review. The helper uses a temporary localhost-only Schema Registry
port-forward and terminates only the child process it created. Payment runtime and Stripe flags
remain disabled throughout this phase.

Phase 21 treats the cloud EKS environment as the staging-equivalent release target because this
project has no separate staging or production environment. It does not rebuild or publish images;
it resolves the image tags already selected by the cloud Deployment in ECR, compares those manifest
digests with running Pod image IDs, checks Argo Synced/Healthy and the seven disabled Payment flags,
then runs the existing Phase 18 Gateway smoke.

## Required local tools

AWS CLI, Terraform, kubectl, Docker Desktop, Maven wrapper, and an AWS profile with the EKS/ECR
permissions must be available in PATH. The default profile is flash-sale-terraform and the default
region is ap-southeast-2.
