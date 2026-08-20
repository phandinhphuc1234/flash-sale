# Feature Specification: EKS GitOps Bootstrap Hardening

**Feature Branch**: `codex/eks-gitops-bootstrap`

**Created**: 2026-08-20

**Status**: Approved by project owner on 2026-08-20 for Terraform implementation and read-only
validation; AWS resource creation remains a separate explicit approval.

**Input**: User description: "Review and correct the existing Terraform configuration before the
first EKS apply, keeping the GitOps internship topology simple: eight services, three worker nodes,
no application replicas or advanced platform components in this group."

## Problem and Scope

The repository already has an applied development VPC and eight ECR repositories, plus an unapplied
EKS module. The configuration validates, but it selects an EKS version in extended support, allows
the public cluster endpoint from every IPv4 address by default, leaves the ECR deployment identity
mutable, exposes few reusable inputs/outputs, and is not formatted cleanly. Applying this baseline
unchanged would create avoidable security, support, and reproducibility debt before GitOps work
begins.

### In Scope

- Preserve the existing S3 backend, VPC topology, single development NAT gateway, eight ECR
  repositories, and three-node managed node-group intent.
- Select an EKS version in standard support for a new cluster.
- Require the operator to supply explicit public endpoint CIDRs instead of inheriting a global
  allow-list.
- Keep worker nodes in existing private subnets and make the development capacity type explicit.
- Make Git-SHA image tags resistant to overwrites.
- Add consistent project/environment ownership tags and useful operator outputs.
- Pin remote module selections used by the reviewed plan and commit the provider lock file.
- Format, validate, and generate a refreshed plan without applying it.

### Out of Scope

- `terraform apply`, destroy, import, state mutation, or changing the remote backend object.
- Kubernetes manifests, Kustomize overlays, Argo CD, GitHub release workflows, load balancers, DNS,
  TLS, monitoring, or application deployment.
- EBS CSI, persistent volumes, PostgreSQL, Redis, Kafka, Schema Registry, or other stateful
  workloads. Those belong to the next approved infrastructure group.
- Adding `cart-service` or `notification-service` to the initial eight-service deployment scope.
- Production high availability, autoscaling controllers, Spot capacity, multiple NAT gateways, or
  multi-environment Terraform composition.

## User Scenarios & Testing

### User Story 1 - Review a safe EKS creation plan (Priority: P1)

As the project operator, I can plan the first EKS cluster creation with an explicitly authorized
administrative network range and a supported Kubernetes version, so I understand the exact AWS
changes before accepting cost or external state changes.

**Why this priority**: The cluster has not been created, so safety and supportability must be fixed
before the first apply.

**Independent Test**: Supply a valid `/32` administrative CIDR and run format check, validation, and
a refreshed plan using the `flash-sale-terraform` profile; the plan succeeds without replacement or
destruction.

**Acceptance Scenarios**:

1. **Given** the existing VPC and ECR state, **When** the operator runs a refreshed plan, **Then**
   Terraform proposes the EKS foundation and only intentional in-place hardening changes.
2. **Given** no endpoint CIDR value, **When** the operator plans, **Then** Terraform refuses to plan
   rather than silently exposing the EKS API to all IPv4 addresses.
3. **Given** a valid administrator CIDR, **When** the EKS configuration is evaluated, **Then** both
   private endpoint access and CIDR-restricted public endpoint access are selected.

---

### User Story 2 - Reproduce infrastructure configuration from Git (Priority: P2)

As a contributor, I can initialize the root Terraform module from version-controlled sources and
receive stable provider/module selections, ownership tags, and operator outputs without relying on
local cache contents.

**Why this priority**: GitOps infrastructure must be reviewable and reproducible before it is
automated.

**Independent Test**: A clean initialization selects the reviewed module families and provider
lock, then exposes the cluster, subnet, and repository identifiers required by later workflows.

**Acceptance Scenarios**:

1. **Given** a clean Terraform working directory, **When** initialization runs, **Then** source and
   lock files are sufficient and local cache/state/plan files remain excluded from Git.
2. **Given** the eight repository resources, **When** an existing Git-SHA tag is pushed again,
   **Then** the registry rejects the overwrite.

### Edge Cases

- An operator uses the wrong AWS CLI profile: the quickstart requires identity verification before
  plan or apply.
- An operator supplies `0.0.0.0/0`: variable validation rejects the unsafe development CIDR.
- A module/provider upgrade appears during initialization: exact module pins and the provider lock
  make the change visible for review.
- A plan proposes destruction or replacement: validation is considered failed and no apply may
  proceed.

## Requirements

### Functional Requirements

- **FR-001**: The EKS cluster MUST use Kubernetes `1.36` for the first creation plan.
- **FR-002**: EKS worker nodes MUST remain in the two existing private subnets.
- **FR-003**: The managed node group MUST retain `m7i-flex.large`, minimum two, desired three, and
  maximum four nodes, with on-demand capacity explicitly selected.
- **FR-004**: The cluster endpoint MUST enable private access and MUST restrict public access to a
  non-empty operator-supplied IPv4 CIDR list that excludes `0.0.0.0/0`.
- **FR-005**: The eight approved ECR repositories MUST use immutable image tags and retain scan on
  push.
- **FR-006**: Existing VPC CIDRs, Availability Zones, subnet-role tags, routing, and the single NAT
  gateway MUST remain unchanged.
- **FR-007**: Shared AWS resources MUST receive project, environment, and management ownership tags.
- **FR-008**: Terraform MUST expose the cluster name, cluster endpoint, region, private subnet IDs,
  and ECR repository URLs as outputs.
- **FR-009**: Local Terraform cache, state, variable, crash, and saved-plan files MUST remain outside
  version control; `.terraform.lock.hcl` MUST remain eligible for commit.
- **FR-010**: The committed configuration MUST pass formatting and validation and generate a
  refreshed plan with no destroy or replacement action.
- **FR-011**: No AWS resource mutation may occur in this feature implementation turn.

### Key Entities

- **EKS bootstrap configuration**: The desired cluster version, endpoint policy, VPC placement,
  managed-node capacity, and administrative access entry.
- **Container repository catalog**: The eight GitOps image destinations and their immutability and
  scanning behavior.
- **Operator input**: Local region and authorized EKS public endpoint CIDRs; credential values and
  AWS profiles are never committed.
- **Operator output**: Stable identifiers used to connect `kubectl` and configure later CI/CD work.

## Success Criteria

### Measurable Outcomes

- **SC-001**: Formatting and Terraform validation complete with exit status zero.
- **SC-002**: A refreshed plan completes with zero destroy and zero replacement actions.
- **SC-003**: The planned EKS API public allow-list contains only operator-approved CIDRs and never
  `0.0.0.0/0`.
- **SC-004**: All eight planned repositories reject reuse of an existing immutable image tag.
- **SC-005**: A contributor can identify the cluster, region, private subnets, and all eight image
  repositories from Terraform outputs after apply.
- **SC-006**: No AWS resource is created, changed, or destroyed during implementation validation.

## Assumptions

- The operator uses the existing `flash-sale-terraform` profile with administrative bootstrap
  permissions and verifies its identity before running Terraform.
- The initial GitOps demonstration deploys eight completed services; cart and notification remain
  outside this feature.
- Three on-demand workers are an accepted development sizing baseline; workload resource tuning is
  deferred until Kubernetes manifests exist.
- EBS CSI and stateful backing services are intentionally deferred to avoid coupling cluster
  creation with workload storage design.

## Constitutional Constraints

- **Service ownership**: No service source, database, schema, or migration is changed.
- **External ingress**: No public application route is created; future public traffic remains owned
  by `api-gateway`.
- **API/event contracts**: No HTTP or Kafka contract changes.
- **Durable and hot-path data**: No PostgreSQL or Redis behavior changes.
- **Messaging reliability**: No Kafka runtime or consumer behavior changes.
- **Root infrastructure ownership**: All changes remain under root `infra/terraform` plus Spec Kit
  governance artifacts; no service owns shared infrastructure.
- **Observability**: Application endpoints and trace paths are unchanged; EKS control-plane logging
  remains module-managed.
- **Verification**: Terraform format, validate, refreshed plan, Git hygiene, and diff checks apply;
  Maven, contract, load, and Kubernetes overlay tests are omitted because no Java, contract, or
  manifest changes occur.
- **Architecture decisions**: Existing ADR 0001 already assigns shared infrastructure to root
  `infra/`; this hardening does not alter service boundaries, ingress ownership, discovery, or data
  ownership, so no new ADR is required.
