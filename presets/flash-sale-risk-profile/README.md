# Flash Sale Risk Profile

Risk-aware Spec Kit preset for features involving stock correctness, money, security, concurrency,
distributed data, Kafka delivery, Redis atomicity, contract compatibility, migration, or recovery.

## Scope

The preset customizes feature artifacts and agent instructions. It does not amend the project
constitution, create a product baseline, decide business policy, or retroactively rewrite existing
feature folders.

Use the bundled core workflow for low-risk scaffolding or documentation. Use this preset together
with the `flash-sale-risk` workflow for a newly created feature whose risk classification warrants
the additional gates.

## Contents

```text
flash-sale-risk-profile/
├── preset.yml
├── README.md
├── templates/
│   ├── spec-template.md
│   ├── plan-template.md
│   ├── tasks-template.md
│   └── checklist-template.md
└── commands/
    ├── speckit.specify.md
    ├── speckit.clarify.md
    ├── speckit.plan.md
    ├── speckit.tasks.md
    ├── speckit.analyze.md
    └── speckit.implement.md
```

Artifact templates use `strategy: replace` because this repository's PowerShell generators resolve
whole template files. Command wrappers use `strategy: wrap`; the Specify CLI composes them with the
installed core commands when the preset is installed.

## Install for the next feature

The Specify CLI is not currently available on this machine's `PATH`. Once the official CLI version
compatible with `.specify/init-options.json` is installed, run from the repository root:

```powershell
specify preset add --dev .\presets\flash-sale-risk-profile --priority 5
specify preset list
specify preset info flash-sale-risk-profile
specify preset resolve spec-template
specify preset resolve plan-template
specify preset resolve tasks-template
```

Install the preset before creating the next feature. Installing it does not rewrite existing
`spec.md`, `plan.md`, or `tasks.md`; template resolution only affects newly generated artifacts.

## Run the risk workflow

```powershell
specify workflow run .specify\workflows\flash-sale-risk\workflow.yml `
  -i spec="<business outcome and scope>" `
  -i integration=codex
```

At every gate, inspect the generated artifact and checklist before resuming. The workflow does not
turn approval into an automatic decision.

## Disable or remove

```powershell
specify preset disable flash-sale-risk-profile
specify preset enable flash-sale-risk-profile
specify preset remove flash-sale-risk-profile
```

Project-local overrides under `.specify/templates/overrides/` have higher priority than this preset.
Review them if `preset resolve` does not point at the expected template.

## Profile rules

- `spec.md` owns WHAT/WHY; plan and code do not invent observable behavior.
- Unresolved high-risk behavior stays explicit with `[NEEDS CLARIFICATION: ...]` and decision
  metadata.
- User Story remains the Spec Kit delivery slice; UC/AC/NFR IDs are trace references.
- Hexagonal/DDD depth follows approved risk classification and plan.
- Test-first sequencing applies only where the approved plan/tasks select it.
- Required tests, contracts, migrations, rollback, observability, and validation evidence are never
  optional once selected by the plan.
- Completed historical features are not regenerated or reformatted.

