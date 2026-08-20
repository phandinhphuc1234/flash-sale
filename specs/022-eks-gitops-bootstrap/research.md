# Research: EKS GitOps Bootstrap Hardening

## EKS version

**Decision**: Use EKS `1.36` for the first cluster creation.

**Rationale**: AWS reports `1.36` as the default version under standard support in
`ap-southeast-2`; `1.33` is already in extended support. There is no existing cluster upgrade or
compatibility migration to preserve.

**Alternatives considered**: Keep `1.33` and pay/accept extended-support lifecycle debt; use `1.35`
for a slightly older standard-support release. Both are unnecessary for a cluster that does not yet
exist.

## Public endpoint policy

**Decision**: Enable private access and require one or more operator-supplied public IPv4 CIDRs;
reject `0.0.0.0/0`.

**Rationale**: The operator needs direct `kubectl` access from a laptop, while a global public
allow-list creates unnecessary exposure. A local variable keeps changing IP values out of Git.

**Alternatives considered**: Private-only access requires a VPN/bastion and exceeds the internship
scope; globally open public access is simpler but not an acceptable security default.

## Module/provider selection

**Decision**: Pin EKS module `20.37.2` and VPC module `5.21.0`; keep AWS provider `5.100.0` through
the committed dependency lock and existing major-version constraint.

**Rationale**: These versions already validate and produce the reviewed plan. The dependency lock
tracks providers but not remote modules, so exact module pins make clean initialization
reproducible. Moving to EKS module v21 and AWS provider v6 is a separate migration with no value for
this bootstrap.

**Alternatives considered**: Floating module minor versions can change fresh-init behavior;
upgrading to the latest module/provider family widens risk immediately before first apply.

## Node sizing

**Decision**: Keep one on-demand managed node group with `m7i-flex.large`, minimum two, desired
three, maximum four.

**Rationale**: The type is available in both configured AZs and provides the accepted development
baseline for eight single-Pod services and later GitOps tooling. Autoscaling automation and workload
resource sizing do not exist yet.

**Alternatives considered**: Two nodes reduce cost but leave less Java workload headroom; larger
instances or multiple node groups are unnecessary for the internship bootstrap.

## ECR mutability

**Decision**: Make all eight repositories immutable and keep scan-on-push.

**Rationale**: GitOps image identity will be the Git commit SHA; overwriting that tag would make Git
stop representing the deployed artifact.

**Alternatives considered**: Mutable tags are convenient for `latest`, but the approved release flow
does not use `latest`. Lifecycle deletion is deferred because retention has not been approved.

## Stateful workload support

**Decision**: Defer EBS CSI and persistent data workloads.

**Rationale**: AWS EBS-backed PVCs require an EKS add-on plus IAM permissions. Adding that design
before the database/Kafka topology is approved would couple two task groups and make cluster
bootstrap harder to diagnose.

**Alternatives considered**: Install EBS CSI immediately; run stateful workloads without persistent
volumes. The former expands scope and the latter risks data loss.

## Existing backend and VPC

**Decision**: Preserve the backend and VPC topology.

**Rationale**: Read-only checks confirm S3 versioning, AES256 server-side encryption, and all public
access blocks; native lockfile support is configured. VPC state and AWS agree on two public/two
private subnets, correct role tags, one Internet Gateway, and one development NAT gateway.

**Alternatives considered**: A NAT per AZ improves availability but adds cost; VPC endpoints reduce
NAT dependence but add complexity. Both remain later production-hardening options.
