# Phase 23 Gate Data Model

This operational feature has no business persistence.

| Record | Fields | Meaning |
|---|---|---|
| `TerraformInput` | directory, profile, region, cidrProvided | Non-secret operator inputs used by the plan |
| `AwsIdentity` | account, arn, region | Caller metadata checked before Terraform |
| `PlanResult` | exitCode, classification, readOnly | Terraform detailed plan outcome |
| `GateResult` | checkName, status, diagnostic | Bounded validation evidence |

Terraform state, AWS credentials, `.env` values, and CIDR values are not emitted as data fields.
