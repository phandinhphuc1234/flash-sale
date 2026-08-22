# Data Model: One-Time Authentication Admin Bootstrap

## Existing durable entity

The feature changes no schema and adds no table. It creates one row in the Authentication-owned
`auth_db.users` table through the existing JPA adapter.

| Field | Source | Rule |
|---|---|---|
| `id` | Authentication domain identity | New UUID only for a newly created account |
| `email`, `email_normalized` | operator input | Required; normalized with existing Account rules |
| `username`, `username_normalized` | operator input | Required for bootstrap; unique collision fails closed |
| `password_hash` | existing Argon2 adapter | Never logged or returned |
| `role` | bootstrap use case | `ROLE_ADMIN` only |
| `status` | bootstrap use case | `ACTIVE` only |
| timestamps | Authentication clock | Set on creation; never changed on idempotent rerun |

## Disposable operational entities

- **Bootstrap Secret**: Kubernetes Secret `authentication-admin-bootstrap`; contains only the three
  input keys for the duration of the Job and is deleted by the launcher.
- **Bootstrap Job**: Kubernetes Job `authentication-admin-bootstrap`; no durable application table;
  its completion and sanitized logs are the operational evidence.

## State rules

```text
missing identity + valid input -> create ACTIVE ROLE_ADMIN
same email + same username + ROLE_ADMIN -> idempotent success
ROLE_USER collision or identity mismatch -> fail closed, no mutation
```
