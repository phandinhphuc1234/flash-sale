# Feature Specifications

Each numbered directory is a delivery ledger for one feature. The canonical artifact roles are:

- `spec.md` — WHAT and WHY;
- `plan.md` — HOW, architecture, dependencies, and rollout;
- `tasks.md` — dependency-ordered implementation work;
- `contracts/` and `data-model.md` — approved external/data boundaries when applicable;
- `validation.md` — commands, results, environment, commit, and unresolved live gates.

Use `.specify/feature.json` to identify the active feature. Never infer approval from file existence,
and never mark a task complete merely because code was written. See
[`docs/spec-driven-development/`](../docs/spec-driven-development/README.md).
