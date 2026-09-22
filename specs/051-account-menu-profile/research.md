# Research: Account Profile and Session Menu

## Findings

1. Authentication already owns the `users` table with `id`, `email`, optional `username`, `role`, and `status`; no migration is needed for a read-only summary.
2. Access tokens currently expose `sub` and `authorities`, but not email or username. A protected `/api/v1/auth/me` query is safer than adding identity fields to every short-lived JWT.
3. Current logout endpoints already revoke the current refresh-token chain or all sessions owned by the verified JWT subject and clear the cookie. The feature composes those endpoints rather than redesigning revocation.
4. The API Gateway already routes `/api/v1/auth/**`, so `/api/v1/auth/me` needs no route or infrastructure change.
5. OWASP guidance requires logout to terminate the associated session and remain accessible from authenticated UI. The account menu therefore keeps current logout visible and puts all-session logout behind an explicit security confirmation.
6. Material menu guidance treats menus as temporary action surfaces rather than primary navigation; the admin console remains a separate `/seller` shell and is only linked from the account menu for `ROLE_ADMIN`.

## Decision

Use an Authentication-owned application query and persistence output port for the summary. Keep the
frontend contract stable (`AccountSummary`) so a future User Service can replace the source without
redesigning navigation. Do not add a new dependency, table, event, or token claim.

## Alternatives Rejected

- Put email/username in JWT claims: increases token exposure and requires coordinated signer/consumer compatibility without adding value over `/me`.
- Create a User Service now: expands scope and ownership before the requested profile behavior exists.
- Keep logout buttons in the navbar: fails the requested separation between storefront browsing and account/security actions.
