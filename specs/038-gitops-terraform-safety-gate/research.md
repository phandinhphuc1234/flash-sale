# Phase 23 Research

## Decision 1: Plan only, never apply

Terraform plan can refresh remote state and show proposed changes without mutating AWS. Apply is kept
outside the script so the operator must review an explicit plan before a later phase can authorize it.

## Decision 2: Pass the CIDR through an environment variable

The endpoint allow-list is machine-specific and must not be committed. `TF_VAR_cluster_endpoint_public_access_cidrs`
keeps the value local and lets Terraform validate the list expression. The helper never prints it.

An explicit `-AutoDetectPublicIp` switch may populate the same process-local variable from a bounded
IPv4 lookup. Auto-detection is opt-in so an offline workstation or VPN policy never causes a hidden
network dependency.

## Decision 3: Preserve Terraform's detailed exit codes

Exit `0` means no changes, exit `2` means changes, and exit `1` means failure. Changes are reported as
review-required rather than silently accepted or applied.

## Decision 4: Fail closed on missing tooling or identity mismatch

Terraform, AWS CLI, profile, region, and remote backend are prerequisites. A missing or wrong caller
must stop before a plan can be mistaken for approval.
