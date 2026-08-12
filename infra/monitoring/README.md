# Monitoring Infrastructure

This directory is reserved for future Prometheus scrape infrastructure, Grafana provisioning and
dashboards, and alert rules.

The monitored applications remain responsible for their own Spring Boot Actuator dependency,
runtime `micrometer-registry-prometheus` dependency, and declarative `/actuator/prometheus`
exposure. Custom Prometheus registry construction in service Java code is not part of the baseline.

No Prometheus server, Grafana, dashboard, scrape target, or alert rule is implemented in the current
skeleton.
