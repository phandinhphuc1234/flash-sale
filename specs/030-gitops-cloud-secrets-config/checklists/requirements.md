# Specification Quality Checklist: Cloud Secrets and Service Configuration

**Purpose**: Validate specification completeness before planning
**Created**: 2026-08-21
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] Secret values are explicitly excluded
- [x] Manual and deferred inputs are distinguished
- [x] Service and platform boundaries are explicit
- [x] Mandatory sections are complete

## Requirement Completeness

- [x] No unresolved clarification markers remain
- [x] Required secret names are testable
- [x] ConfigMap and Secret ownership is defined
- [x] Acceptance scenarios cover missing input, disabled Stripe, and opt-in apply
- [x] Edge cases cover legacy Secret reuse and missing JWT files

## Feature Readiness

- [x] Eight cloud application services are covered
- [x] Cart and notification scope is explicitly excluded
- [x] Migration and public HTTPS are explicitly deferred
- [x] Validation and secret-safety gates are defined

## Notes

- The operator must run validation-only first. `-Apply` is intentionally never run by the agent.
