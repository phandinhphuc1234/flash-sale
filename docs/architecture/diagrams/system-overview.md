# Flash Sale Engine System Overview

This is the first source-controlled overview diagram for the repository. It is a target architecture sketch, not proof that every component is implemented.

```mermaid
flowchart LR
  customer[Customer Web/Mobile]
  admin[Admin/Ops User]

  subgraph cluster[Kubernetes Cluster - Planned]
    ingress[Ingress Controller\nPlanned]
    gateway[api-gateway\nCurrent shell\nSpring Cloud Gateway WebFlux]

    auth[authentication-service\nCurrent shell]
    product[product-service\nCurrent shell]
    cart[cart-service\nCurrent shell]
    campaign[campaign-service\nCurrent shell]
    flashsale[flashsale-service\nCurrent shell\nHot path planned]
    order[order-service\nCurrent shell]
    payment[payment-service\nCurrent shell]
    notification[notification-service\nCurrent shell]
    chatting[chatting-service\nCurrent shell]

    subgraph data[Data and Messaging Layer - Planned]
      redis[(Redis\nLua atomic reserve/cache/rate limit\nPlanned)]
      kafka[(Kafka Event Bus\nVersioned events\nPlanned)]
      authdb[(auth-db\nPostgreSQL planned)]
      productdb[(product-db\nPostgreSQL planned)]
      cartdb[(cart-db\nPostgreSQL planned)]
      campaigndb[(campaign-db\nPostgreSQL planned)]
      flashsaledb[(flashsale-db\nPostgreSQL planned)]
      orderdb[(order-db\nPostgreSQL planned)]
      paymentdb[(payment-db\nPostgreSQL planned)]
      notificationdb[(notification-db\nPostgreSQL planned)]
      chattingdb[(chatting-db\nPostgreSQL planned)]
    end

    subgraph obs[Observability Stack - Planned]
      prometheus[Prometheus\nscrapes /actuator/prometheus\nPlanned infra]
      otel[OpenTelemetry Collector\nOTLP traces/logs/metrics\nPlanned]
      alloy[Grafana Alloy or OTel Collector\nlog collection\nPlanned]
      loki[(Loki\nlogs planned)]
      tempo[(Tempo\ntraces planned)]
      grafana[Grafana\ndashboards/explore\nPlanned]
    end
  end

  customer --> ingress
  admin --> ingress
  ingress --> gateway

  gateway --> auth
  gateway --> product
  gateway --> campaign
  gateway --> flashsale
  gateway --> order
  gateway --> payment
  gateway --> notification
  gateway --> chatting

  auth --> authdb
  product --> productdb
  cart -. planned persistence .-> cartdb
  campaign --> campaigndb
  flashsale --> flashsaledb
  order --> orderdb
  payment --> paymentdb
  notification --> notificationdb
  chatting --> chattingdb

  flashsale -. atomic reserve .-> redis
  gateway -. rate limit/session/cache planned .-> redis
  chatting -. presence/cache planned .-> redis

  campaign -. campaign.item.prepared.v1 .-> kafka
  flashsale -. order.requested.v1 .-> kafka
  order -. order.created.v1 / order.failed.v1 .-> kafka
  payment -. payment.succeeded.v1 / payment.failed.v1 .-> kafka
  notification -. notification.requested.v1 .-> kafka

  kafka -. consume .-> order
  kafka -. consume .-> payment
  kafka -. consume .-> notification

  gateway -. metrics .-> prometheus
  auth -. metrics .-> prometheus
  product -. metrics .-> prometheus
  cart -. metrics .-> prometheus
  campaign -. metrics .-> prometheus
  flashsale -. metrics .-> prometheus
  order -. metrics .-> prometheus
  payment -. metrics .-> prometheus
  notification -. metrics .-> prometheus
  chatting -. metrics .-> prometheus

  gateway -. OTLP traces .-> otel
  auth -. OTLP traces .-> otel
  product -. OTLP traces .-> otel
  cart -. OTLP traces .-> otel
  campaign -. OTLP traces .-> otel
  flashsale -. OTLP traces .-> otel
  order -. OTLP traces .-> otel
  payment -. OTLP traces .-> otel
  notification -. OTLP traces .-> otel
  chatting -. OTLP traces .-> otel

  otel --> tempo
  alloy --> loki
  prometheus --> grafana
  tempo --> grafana
  loki --> grafana

  classDef current fill:#e8f3ff,stroke:#1d4ed8,color:#0f172a;
  classDef planned fill:#fff7ed,stroke:#ea580c,color:#0f172a;
  classDef data fill:#ecfdf5,stroke:#059669,color:#0f172a;
  classDef obs fill:#f5f3ff,stroke:#7c3aed,color:#0f172a;
  classDef deferred fill:#f8fafc,stroke:#64748b,color:#0f172a;

  class gateway,auth,product,cart,campaign,flashsale,order,payment,notification,chatting current;
  class ingress planned;
  class redis,kafka,authdb,productdb,cartdb,campaigndb,flashsaledb,orderdb,paymentdb,notificationdb,chattingdb data;
  class prometheus,otel,alloy,loki,tempo,grafana obs;
```

## Legend

| Style | Meaning |
|---|---|
| Current shell | Build/runtime shell exists in this repository |
| Planned | Architecture direction, not implemented yet |
| Dashed edge | Future integration or telemetry flow |
| PostgreSQL per service | Each service owns its database and migrations |
| Redis | Hot-path/cache/rate-limit support only, not durable truth |
| Kafka | Versioned asynchronous events with idempotent consumers |
| Cart | Current service shell; only its dashed, planned database ownership edge is shown |
