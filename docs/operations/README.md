# Operations

Current operational procedures are split between:

- [`../runbooks/`](../runbooks/) for business recovery;
- [`../../infra/scripts/gitops/`](../../infra/scripts/gitops/README.md) for guarded cloud operations;
- feature `validation.md` files for dated evidence;
- service READMEs for focused health and troubleshooting.

The EKS cluster is currently absent. Do not treat absent Pods or endpoints as an application
incident until the infrastructure has intentionally been recreated.
