# Research: GitOps Stateful Product Pilot

## Decisions

1. **Adopt existing EBS CSI state instead of recreating it**
   - The cluster already has the EBS CSI managed add-on and an IAM role created during the manual
     pilot. Terraform must import both identities before apply.
   - This avoids duplicate IAM roles, add-ons, or accidental storage interruption.

2. **Use the AWS managed EBS CSI policy V2**
   - The canonical policy ARN is
     `arn:aws:iam::aws:policy/AmazonEBSCSIDriverPolicyV2`.
   - No custom policy is required for this training pilot.

3. **Use a dedicated pilot overlay**
   - The existing `dev` overlay describes eight services and remains source-only. A separate overlay
     prevents Product pilot work from starting unrelated services.

4. **Keep PostgreSQL data below the EBS mount root**
   - EBS filesystems contain `lost+found`; PostgreSQL is configured with `PGDATA` under a clean child
     directory to make initialization deterministic.

5. **Keep credentials external**
   - Kubernetes Secret names are referenced by manifests, while values are entered/provisioned by
     the operator outside Git. The Phase 9 script never writes credentials to disk.

## Alternatives Rejected

- **Delete and recreate the PVC**: rejected because it is destructive and unnecessary; the PGDATA
  correction works while preserving the claim.
- **Deploy all eight services**: rejected because backing services and secret keys are not approved
  for the remaining services.
- **Install Argo CD in the same phase**: rejected to keep storage adoption and application desired
  state reviewable before introducing a reconciliation controller.
- **Use RDS now**: deferred; it changes the training topology and cost model beyond the pilot.
