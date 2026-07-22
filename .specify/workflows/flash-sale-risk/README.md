# Flash Sale Risk Workflow

`flash-sale-risk` is the project-local orchestration for a newly created feature with meaningful
distributed-system or business-correctness risk. It complements the `flash-sale-risk-profile`
preset; it does not replace the Constitution or human approval.

## What it runs

```text
specify → clarify → requirements checklist → spec gate
        → plan → plan checklist → plan gate
        → tasks → analyze → implementation gate
        → implement → converge → convergence gate
        → implement remaining approved tasks → final converge gate
        → final analyze → evidence gate
```

Rejected gates abort the run so the governing artifact can be corrected and re-approved. The
workflow deliberately has no generic shell-based “verify” step: validation commands differ by
feature and must be generated as tasks from the approved plan, executed by implementation, and
checked at the final evidence gate.

If final convergence adds work, reject the gate and start another reviewed implementation iteration.
One invocation is intentionally not allowed to hide unfinished work just to reach `completed`.

## Prerequisite

Install/enable the project preset before creating the next feature:

```powershell
specify preset add --dev .\presets\flash-sale-risk-profile --priority 5
specify preset info flash-sale-risk-profile
```

The Specify CLI is not currently available on this machine's `PATH`; these commands are activation
instructions for the next feature after the compatible official CLI is installed.

## Run directly from the repository

```powershell
specify workflow run .specify\workflows\flash-sale-risk\workflow.yml `
  -i spec="<business outcome, users, scope, and known constraints>" `
  -i integration=codex
```

Use the workflow only after selecting a new feature description. It must not regenerate or amend
`specs/001-scaffold-maven-services/`.

## Gate ownership

| Gate | Minimum reviewer decision |
|------|---------------------------|
| Spec | Business/domain owner accepts scope, invariants, failure outcomes, and open-decision status |
| Plan | Technical owner accepts ownership, compatibility, risk controls, migration, rollback, and evidence plan |
| Tasks | Implementers/reviewers accept traceability, ordering, exact paths, and executable validation |
| Convergence | Reviewer confirms appended work is inside approved scope |
| Final convergence | Reviewer confirms the last implementation introduced no remaining required work |
| Final evidence | Reviewer verifies passing required evidence and no blocking analysis finding |

The workflow stores orchestration state according to Specify CLI behavior, but the canonical feature
artifacts remain under `specs/<feature>/`.
