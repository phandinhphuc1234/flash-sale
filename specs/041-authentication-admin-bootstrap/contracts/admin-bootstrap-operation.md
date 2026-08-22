# Operational Contract: Authentication Admin Bootstrap

This is an operator-only Job contract. It is not a public HTTP endpoint and is not invoked by Argo
automatically.

## Inputs

The launcher creates a temporary Secret named `authentication-admin-bootstrap` with:

| Key | Meaning | Handling |
|---|---|---|
| `AUTH_ADMIN_BOOTSTRAP_EMAIL` | Account email | In-memory/operator input; not committed |
| `AUTH_ADMIN_BOOTSTRAP_USERNAME` | Account username | In-memory/operator input; not committed |
| `AUTH_ADMIN_BOOTSTRAP_PASSWORD` | Initial password | `Read-Host -AsSecureString`; never printed |

The Job also sets `AUTH_ADMIN_BOOTSTRAP_ENABLED=true` and
`SPRING_MAIN_WEB_APPLICATION_TYPE=none`. The long-running Authentication Deployment remains
disabled.

## Outcomes

- Exit `0`: `created=true` or `created=false` for the same existing `ROLE_ADMIN` identity.
- Non-zero exit: validation failure, collision, identity mismatch, persistence failure, or missing
  runtime prerequisite. Existing rows remain unchanged on collision.

## Cleanup

The launcher deletes the temporary Secret after the Job finishes. It deletes the Job by default after
capturing sanitized status; `-KeepJob` is reserved for troubleshooting and must not retain the Secret.
