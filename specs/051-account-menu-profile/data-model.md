# Data Model: Account Profile and Session Menu

## Existing durable model reused

Authentication's existing `users` table remains the source of truth. A backward-compatible
Liquibase migration adds nullable `full_name`, `phone`, and `address` columns.

## Application read model

`AccountSummary` is an application/web read model, not a new persistence entity:

| Field | Source | Exposure |
|---|---|---|
| `id` | `users.id` | authenticated account owner |
| `username` | `users.username` | authenticated account owner; nullable for legacy accounts |
| `login` | username when present, otherwise email | authenticated account owner |
| `email` | `users.email` | authenticated account owner |
| `displayName` | full name when present, otherwise username/email fallback | authenticated account owner |
| `fullName` | `users.full_name` | authenticated account owner; nullable |
| `phone` | `users.phone` | authenticated account owner; nullable |
| `address` | `users.address` | authenticated account owner; nullable |
| `status` | `users.status` | authenticated account owner |
| `authorities` | `users.role.authorities()` | authenticated account owner |

The model excludes password hash, access token, refresh credential, cookie value, session ID, IP
address, user-agent, and persistence timestamps.

## Profile update command

`UpdateAccountProfileCommand` contains only the authenticated subject UUID and the requested
`username` for the compatibility endpoint. `UpdateAccountDetailsCommand` contains the subject UUID
and the optional `username`, `fullName`, `phone`, and `address` values. The email is deliberately
absent from both commands because changing it requires a separate verified-email flow. The existing
`users.username_normalized` unique constraint remains the authoritative conflict rule.
