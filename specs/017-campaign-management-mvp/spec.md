# Feature Specification: Campaign Management MVP

**Feature Branch**: `017-campaign-management-mvp`

**Created**: 2026-07-29

**Status**: Approved — Avro/Schema Registry adoption confirmed (2026-08-03)

**Input**: `C:\Users\MSi\Downloads\campaign-service-minimal-mvp-spec.md`

**Linked Business Requirements**: Technical and business enabler for preparing a validated,
stock-backed campaign snapshot before the flash-sale purchase hot path is introduced.

**Business Owner**: Project owner

**Required Reviewers**: Project owner; architecture/security reviewer

## Problem and Scope

### Problem Statement

The system has Product and Inventory capabilities but no owner for defining a flash-sale campaign,
freezing its sellable configuration, coordinating campaign stock allocation, or exposing the stable
snapshot needed by the future Flash Sale Service. Without this control-plane capability, the hot
purchase path cannot distinguish a draft configuration from a campaign that is validated,
stock-backed, and ready to activate.

### In Scope

- create a campaign in `DRAFT` status;
- update draft campaign metadata;
- replace the single campaign item while the campaign remains draft;
- retrieve administrative campaign details;
- schedule a draft campaign by validating the selected Product variant and allocating Inventory;
- make schedule commands safe to retry and safe under concurrent requests;
- freeze campaign and variant snapshot data after scheduling;
- recover a schedule operation after Inventory allocation succeeds but local finalization is
  interrupted;
- automatically activate a due scheduled campaign and allow a manual recovery activation;
- automatically end an active campaign at its end time;
- expose an internal campaign snapshot for Flash Sale Service recovery and cache rebuilding;
- publish versioned scheduled and activated lifecycle notifications reliably;
- publish those lifecycle notifications as schema-first Avro records governed by Confluent Schema
  Registry, with compatibility validation before rollout;
- authorize campaign administration through the existing Gateway and revalidate authorization in
  Campaign Service;
- add the minimal OAuth2 Client Credentials capability required for Campaign Service to obtain a
  machine identity from Authentication Service;
- authorize Campaign-to-Product and Campaign-to-Inventory calls with endpoint-specific service
  scopes and a service-token audience distinct from administrator tokens.

### Out of Scope

- more than one Product variant in a campaign;
- campaign cancellation or release workflow;
- a campaign-ended integration event;
- public campaign listing, search, or advanced filtering;
- customer purchase attempts, carts, orders, or payments;
- customer purchase limits enforced on the hot path;
- runtime stock deduction, Redis state, or Redis scripts;
- dynamic pricing rules, coupons, tiers, or overlapping-campaign resolution;
- a dead-letter topic or change-data-capture publisher.
- a full OAuth2/OIDC authorization server, browser authorization flow, consent, dynamic client
  registration, or service identities other than the approved Campaign and Flash Sale clients;
- implementation of campaign cancellation/release behavior, even though the service client may be
  registered for the future Inventory release scope.

### Non-goals

- Campaign Service does not become the authority for Product data or physical Inventory.
- Campaign scheduling does not represent a completed sale and does not deduct sold physical stock.
- The internal snapshot is a recovery/cache-building contract, not a dependency for every purchase.

## Clarifications

### Session 2026-07-29

- Q: Should campaign-code uniqueness be case-sensitive? → A: No. Normalize campaign codes to
  uppercase and enforce case-insensitive uniqueness.
- Q: How long is a schedule idempotency key retained, and may it be reused? → A: Retain it
  indefinitely and never allow it to represent a new command.
- Q: How is an outbox event recovered after ten failed publication attempts? → A: An authenticated
  and authorized campaign operator explicitly requeues the same durable event.

### Session 2026-07-30

- Q: Which identity must Campaign Service use when it calls Product and Inventory, including from a
  background recovery job? → A: OAuth2 Client Credentials. Administrator tokens are accepted only
  at the Campaign administration boundary and are never relayed, stored, or reused for downstream
  service calls. Campaign Service obtains a short-lived service JWT from Authentication Service and
  downstream services authorize it with narrow endpoint-specific scopes.
- Q: Which identity may call the internal Campaign snapshot? → A: `flashsale-service` obtains its
  own OAuth2 Client Credentials service JWT with subject `flashsale-service`, audience
  `flash-sale-internal-api`, and scope `campaign.snapshot.read`. Campaign Service independently
  validates that identity and exposes no public shopper access to the snapshot.

### Session 2026-08-03 — Avro/Schema Registry amendment

- Q: Which serialization format should the Campaign lifecycle contract use? → A: Avro
  schema-first with generated `SpecificRecord` classes and Confluent Schema Registry. The existing
  JSON contract is replaced only after this amendment is approved and rolled out according to the
  compatibility plan.
- Q: Which subject and compatibility policies apply? → A: Use
  `TopicRecordNameStrategy`, `BACKWARD_TRANSITIVE`, and `auto.register.schemas=false` outside local
  experiments. Consumers are deployed before producers that emit a compatible schema revision.
- Q: Where does trace context live? → A: W3C `traceparent`/`tracestate` Kafka headers; do not
  require a `traceId` field in the Avro business payload. No JWT, cookie, secret, or raw
  Authorization header may be placed in the record or headers.

## Baseline References

- `campaign-service` is currently a runnable scaffold without business schema or business APIs.
- Product Service owns Product and Variant truth and must provide an approved internal validation
  contract before Campaign scheduling is implemented.
- Inventory Service owns physical stock and campaign allocation and already defines the allocation
  behavior that Campaign scheduling must use.
- Authentication Service currently issues trusted administrator tokens but has no Client
  Credentials token endpoint or service-client registry; those minimal capabilities are part of
  this feature amendment.
- Inventory currently protects its internal allocation API with the broad
  `SCOPE_INVENTORY_WRITE`; Feature 017 introduces the narrower
  `SCOPE_inventory.campaign.allocate` compatibility contract.
- Product currently has no internal campaign-validation API; Feature 017 introduces that contract
  protected by `SCOPE_catalog.read`.
- The local platform provides PostgreSQL, Kafka, and Confluent Schema Registry. Campaign Service
  currently has no running publisher/consumer; this amendment promotes the lifecycle Avro contract
  only after its artifacts and implementation tasks are approved.

## User Scenarios & Testing

### User Story 1 - Prepare a draft campaign (Priority: P1)

As a campaign administrator, I want to create and revise a draft campaign containing one sellable
variant so I can prepare a promotion without exposing incomplete configuration to shoppers.

**Why this priority**: A durable, editable draft is the minimum independently useful campaign
capability and is the prerequisite for all later lifecycle operations.

**Independent Test**: Create a draft, update its metadata, replace its item, and retrieve the result
without calling Product or Inventory during the local draft operations.

#### Use Case UC-CAM-001: Manage a draft campaign

**Trigger**: An authorized campaign administrator creates or edits a campaign.

**Preconditions**:

- The caller is authenticated and has campaign-administration authority.
- Updates and item replacement target a campaign in `DRAFT` status and provide its expected version.

**Main Flow**:

1. The administrator creates a campaign with a unique code, name, start time, and end time.
2. The campaign is stored as `DRAFT`.
3. The administrator may update draft metadata.
4. The administrator may set or replace exactly one campaign item with a variant identifier,
   campaign price, requested quantity, and optional per-user purchase limit.
5. Each successful mutation advances the campaign version and returns the current campaign detail.

**Exceptions**:

- Invalid time range, price, quantity, or purchase limit is rejected without a mutation.
- A duplicate campaign code is rejected.
- A stale expected version is rejected without overwriting newer data.
- Any mutation after the draft stage is rejected.

**Postconditions**:

- Success: The latest draft and its optional single item are durable and auditable by actor/time.
- Failure: No partial draft mutation is visible.

**Acceptance Scenarios**:

1. **UC-CAM-001/AC-01** — **Given** valid campaign data, **When** an authorized administrator creates
   it, **Then** one `DRAFT` campaign with version zero is available for retrieval.
2. **UC-CAM-001/AC-02** — **Given** a draft at the expected version, **When** its metadata is updated,
   **Then** the new values are returned and the version advances once.
3. **UC-CAM-001/AC-03** — **Given** a draft campaign, **When** an item is set and later replaced,
   **Then** the campaign contains exactly the latest one item.
4. **UC-CAM-001/AC-04** — **Given** a stale version or a non-draft campaign, **When** a mutation is
   attempted, **Then** it is rejected and the stored campaign remains unchanged.

---

### User Story 2 - Schedule a validated, stock-backed campaign (Priority: P1)

As a campaign administrator, I want to schedule a fully prepared campaign so its Product facts and
Inventory quota are confirmed exactly once before it can become active.

**Why this priority**: Scheduling establishes the correctness boundary between editable setup and
the future high-contention purchase path.

**Independent Test**: Schedule one valid draft against controlled Product and Inventory contracts,
then exercise duplicate, conflicting, concurrent, insufficient-stock, and crash-recovery cases.

#### Use Case UC-CAM-002: Schedule a campaign

**Trigger**: An authorized administrator submits a schedule command with an expected campaign
version and an idempotency key.

**Preconditions**:

- The campaign is `DRAFT`, has exactly one item, and its start time is still in the future.
- Campaign price and quantity rules are valid.
- The selected Product variant is eligible for campaigning.
- The initiating administrator token was validated at the Campaign boundary and Campaign Service
  can obtain a service token for the required downstream scopes.

**Main Flow**:

1. The system records the initiating administrator identity and records or resumes the schedule
   operation for the campaign and idempotency key.
2. Campaign Service obtains or reuses an unexpired in-memory service token and the Product owner
   validates the variant under `SCOPE_catalog.read`, returning the authoritative sellable snapshot.
3. Campaign price is checked against the authoritative base price and currency.
4. The Inventory owner allocates the requested quantity under
   `SCOPE_inventory.campaign.allocate` using one stable allocation request identity.
5. The Campaign stores the validated Product snapshot and Inventory allocation result.
6. The Campaign transitions once from `DRAFT` to `SCHEDULED`.
7. Exactly one scheduled lifecycle notification is made durable with that transition.

**Alternative Flows**:

- Retrying the same command with the same key and same request identity returns or resumes the same
  result and reuses the same Inventory allocation request identity.
- If Inventory allocation succeeded but Campaign finalization was interrupted, a client retry or
  recovery process resumes the same operation and completes local scheduling without allocating
  again.

**Exceptions**:

- Ineligible or unavailable Product data leaves the campaign in `DRAFT` and the schedule operation
  resumable with the same stable identities.
- Insufficient Inventory leaves the campaign in `DRAFT` and creates no Campaign lifecycle event.
- Reusing a key for a different campaign configuration is rejected as a conflict and does not call
  Inventory with a changed payload.
- Concurrent schedule commands cannot create two active operations, two allocations, or two
  scheduled lifecycle notifications for one campaign version.

**Postconditions**:

- Success: The campaign is `SCHEDULED`, immutable, fully snapshotted, linked to one allocation, and
  has one durable scheduled notification.
- Failure: The campaign does not appear scheduled unless all local scheduling guarantees are durable;
  an already successful allocation remains recoverable through the stable operation identity.

**Acceptance Scenarios**:

1. **UC-CAM-002/AC-01** — **Given** an eligible variant, a lower positive campaign price, and enough
   Inventory, **When** the campaign is scheduled, **Then** the full snapshot is frozen, the complete
   requested quantity is allocated, and the campaign becomes `SCHEDULED` exactly once.
2. **UC-CAM-002/AC-02** — **Given** insufficient Inventory, **When** scheduling is attempted, **Then**
   the campaign stays `DRAFT` and no scheduled lifecycle notification exists.
3. **UC-CAM-002/AC-03** — **Given** Inventory allocation succeeded before an interruption, **When**
   the same command is retried, **Then** the same allocation identity is reused and scheduling
   completes without a second allocation.
4. **UC-CAM-002/AC-04** — **Given** the same idempotency key was used for another request identity,
   **When** it is reused, **Then** the command is rejected without a changed Inventory call.
5. **UC-CAM-002/AC-05** — **Given** concurrent schedule requests for one draft version, **When** they
   execute, **Then** at most one schedule operation produces the allocation and scheduled result.
6. **UC-CAM-002/AC-06** — **Given** a valid administrator request, **When** Campaign Service calls
   Product or Inventory, **Then** it uses its own service token and never forwards the administrator
   bearer token.
7. **UC-CAM-002/AC-07** — **Given** an interrupted schedule operation and no administrator online,
   **When** recovery resumes, **Then** Campaign Service obtains a service token, reuses the stable
   Inventory request identity, and can complete the approved recovery flow.

---

### User Story 3 - Progress the campaign lifecycle safely (Priority: P1)

As an operator, I want scheduled campaigns to activate and active campaigns to end at their defined
times so the future purchase runtime receives an unambiguous sale window.

**Why this priority**: A scheduled campaign has no customer value unless its valid operating window
is entered and exited safely across multiple service instances.

**Independent Test**: Advance a test clock across start and end boundaries while two workers race,
and verify the lifecycle changes at most once with the required notification ordering.

#### Use Case UC-CAM-003: Activate and end a campaign

**Trigger**: A lifecycle worker observes a due campaign, or an authorized administrator requests a
manual recovery activation.

**Preconditions**:

- Activation requires `SCHEDULED`, `startAt <= now < endAt`, a complete snapshot, and a complete
  Inventory allocation.
- Ending requires `ACTIVE` and `now >= endAt`.

**Main Flow**:

1. A due scheduled campaign transitions once to `ACTIVE`.
2. Exactly one activated lifecycle notification is made durable with the successful transition.
3. An active campaign transitions once to `ENDED` when its end boundary is reached.

**Alternative Flows**:

- Manual activation may recover a missed scheduled activation but may not activate early.
- A worker that loses a race observes that no second transition is required.

**Exceptions**:

- Invalid status, early activation, expired activation window, or stale version is rejected without
  a transition or duplicate notification.

**Postconditions**:

- The campaign lifecycle is monotonic: `DRAFT -> SCHEDULED -> ACTIVE -> ENDED`.
- `ENDED` is terminal in this MVP.

**Acceptance Scenarios**:

1. **UC-CAM-003/AC-01** — **Given** a due scheduled campaign, **When** two workers attempt activation,
   **Then** exactly one transition succeeds and exactly one activated notification is created.
2. **UC-CAM-003/AC-02** — **Given** a scheduled campaign before its start time, **When** manual
   activation is requested, **Then** it is rejected and remains `SCHEDULED`.
3. **UC-CAM-003/AC-03** — **Given** an active campaign at or beyond its end time, **When** lifecycle
   processing runs, **Then** it becomes `ENDED` once.
4. **UC-CAM-003/AC-04** — **Given** a successfully scheduled campaign, **When** it later activates,
   **Then** the scheduled notification precedes the activated notification for that campaign.

---

### User Story 4 - Recover downstream campaign runtime state (Priority: P2)

As Flash Sale Service, I want a stable internal snapshot and reliable lifecycle notifications so I
can build or rebuild campaign runtime state without querying Campaign Service during each purchase.

**Why this priority**: This prepares the next Flash Sale feature while keeping the current feature
independently operable through administration and lifecycle management.

**Independent Test**: Retrieve a non-draft campaign snapshot and verify that scheduled/activated
notifications carry the corresponding campaign version and trace context without exposing internal
workflow metadata.

**Acceptance Scenarios**:

1. **Given** a `SCHEDULED`, `ACTIVE`, or `ENDED` campaign, **When** `flashsale-service` presents a
   valid internal-audience service token with `SCOPE_campaign.snapshot.read`, **Then** it receives
   the frozen sellable data and lifecycle window.
2. **Given** a `DRAFT` or missing campaign, **When** an internal snapshot is requested, **Then** no
   sellable snapshot is returned.
3. **Given** an interrupted notification publication, **When** publication is retried, **Then** the
   same durable event identity is reused and no new business transition is created.
4. **Given** a lifecycle notification has reached its terminal failed state, **When** an authorized
   campaign operator requeues it, **Then** publication resumes for the same durable event identity
   without recreating the campaign transition.
5. **Given** an administrator token, a Campaign service token, or a Flash Sale service token without
   the snapshot scope, **When** the internal snapshot is requested, **Then** Campaign Service denies
   the request without returning campaign data.

### Edge Cases

- The start and end instants are equal or reversed.
- A campaign is created with a past start time but is scheduled only after that start time has passed.
- The campaign has no item, more than one item is attempted, or the item quantity/price/limit is invalid.
- Product validation reports a missing, inactive, unpublished, or mismatched variant.
- Campaign price is not lower than the current base price or uses a mismatched currency.
- Inventory returns insufficient stock, an incompatible allocation result, or is unavailable.
- The campaign changes between schedule-operation creation and finalization.
- A concurrent draft mutation races with scheduling.
- A service interruption occurs immediately before or after Inventory allocation.
- Multiple workers discover the same due campaign.
- Lifecycle publication repeatedly fails and reaches the terminal retry state.
- A request lacks authentication, lacks campaign authority, or carries a stale expected version.
- A downstream call presents an administrator token, the wrong service-token audience, the wrong
  service subject, or a token without the endpoint's required scope.
- Authentication Service is unavailable while Campaign Service has no unexpired cached service
  token.

## Requirements

### Functional Requirements

- **FR-001**: The system MUST create each new campaign in `DRAFT` with a required code no longer than
  64 characters, a required name no longer than 200 characters, and `startAt < endAt`.
- **FR-002**: Campaign codes MUST be normalized to uppercase before storage and comparison, and
  uniqueness MUST be enforced without allowing case-only duplicates.
- **FR-003**: The system MUST allow campaign metadata and the single item to be changed only while
  the campaign is `DRAFT` and no schedule operation is active.
- **FR-004**: Every campaign mutation MUST require an expected version and MUST reject stale versions
  without overwriting newer state.
- **FR-005**: A campaign MUST contain exactly one item before scheduling; multiple variants per
  campaign are prohibited in this MVP.
- **FR-006**: The item MUST identify one Product variant, have a positive campaign price, a positive
  requested quantity, and an optional positive per-user purchase limit that does not exceed the
  requested quantity.
- **FR-007**: Scheduling MUST require a `DRAFT` campaign, exactly one valid item, and a future start
  time.
- **FR-008**: Scheduling MUST validate that Product and Variant are eligible, obtain authoritative
  Product/Variant/SKU/base-price/currency snapshots, and require campaign price to be lower than the
  authoritative base price in the same currency.
- **FR-009**: Scheduling MUST allocate the full requested quantity through Inventory; partial or
  insufficient allocation MUST NOT produce a `SCHEDULED` campaign.
- **FR-010**: Inventory allocation is campaign quota, not a completed physical-stock sale.
- **FR-011**: Each schedule command MUST use an idempotency key scoped to one campaign and retain a
  stable request identity containing the expected campaign version and current campaign
  configuration.
- **FR-012**: The same idempotency key and same request identity MUST replay or resume the same
  schedule result with the same Inventory allocation request identity.
- **FR-013**: Reusing an idempotency key with a different request identity MUST be rejected as a
  conflict before Inventory is invoked with changed data.
- **FR-014**: At most one schedule operation may be active for a campaign, and concurrent scheduling
  MUST NOT create duplicate allocations, transitions, or lifecycle notifications.
- **FR-015**: After Inventory allocation succeeds but local finalization is interrupted, retry or
  recovery MUST reuse the original operation and allocation identities and complete the local
  transition without releasing or allocating again.
- **FR-016**: Schedule-operation idempotency records MUST be retained indefinitely, and a key MUST
  never be reused to represent a new schedule command.
- **FR-017**: A successful schedule MUST atomically make both the `SCHEDULED` state and exactly one
  `CampaignScheduled.v1` notification durable.
- **FR-018**: Campaign configuration and sellable snapshot fields MUST become immutable after the
  campaign is scheduled.
- **FR-019**: A campaign MUST follow only `DRAFT -> SCHEDULED -> ACTIVE -> ENDED`; no cancellation
  transition exists in this MVP.
- **FR-020**: Activation MUST require `SCHEDULED`, `startAt <= now < endAt`, a complete snapshot, and
  a complete allocation.
- **FR-021**: The system MUST activate due campaigns automatically and MUST allow an authorized
  manual activation only to recover a missed due activation; early activation is prohibited.
- **FR-022**: A successful activation MUST atomically make both the `ACTIVE` state and exactly one
  `CampaignActivated.v1` notification durable.
- **FR-023**: The system MUST transition `ACTIVE` campaigns to `ENDED` when `now >= endAt`; an ended
  event is not produced in this MVP.
- **FR-024**: Concurrent lifecycle workers MUST produce at most one successful state transition and
  one corresponding lifecycle notification per campaign version.
- **FR-025**: Scheduled notifications MUST precede activated notifications for the same campaign.
- **FR-026**: Durable notification publication MUST retry failed attempts with the approved bounded
  policy and mark an event terminally failed after ten attempts. An authenticated and authorized
  operator with `SCOPE_CAMPAIGN_ADMIN` MUST be able to requeue that same durable event for publication;
  requeue MUST NOT recreate the campaign transition or create a new business-event identity.
- **FR-027**: The internal snapshot MUST be available only for `SCHEDULED`, `ACTIVE`, or `ENDED`
  campaigns, MUST exclude schedule-operation identities and publication metadata, and MUST require
  a valid `flashsale-service` token with audience `flash-sale-internal-api` and authority
  `SCOPE_campaign.snapshot.read`.
- **FR-028**: Administrative operations MUST require `SCOPE_CAMPAIGN_ADMIN` at Gateway and Campaign
  Service; missing/invalid authentication and missing authority MUST remain distinguishable.
- **FR-029**: Synchronous Product and Inventory interactions MUST use approved internal contracts and
  MUST NOT read either service's database.
- **FR-030**: Campaign Service MUST use OAuth2 Client Credentials for every synchronous or background
  Product and Inventory call. It MUST NOT relay, persist, or reuse an administrator bearer token as
  downstream authorization.
- **FR-031**: Important HTTP calls, schedule operations, lifecycle processing, and lifecycle
  notifications MUST propagate one trace/correlation identity through downstream calls and logs.
- **FR-032**: Validation, not-found, state/version/idempotency conflict, authentication,
  authorization, downstream-unavailable, and unexpected-failure outcomes MUST be exposed through
  documented and consistent contracts.
- **FR-033**: Authentication Service MUST expose `POST /oauth2/token` for
  `grant_type=client_credentials`, authenticate the client with HTTP Basic, and issue a token only
  when the client exists, its secret matches, its status is `ACTIVE`, the grant is allowed, and all
  requested scopes are registered for that client. Client secrets MUST be stored only as
  non-reversible hashes by Authentication Service.
- **FR-034**: A Campaign service token MUST be RS256-signed and verifiable through the existing JWKS
  trust mechanism, identify `campaign-service` as its subject, use
  `flash-sale-internal-api` as its audience, expire no later than 300 seconds after issuance, and
  contain only approved requested scopes.
- **FR-035**: Product campaign validation MUST require `SCOPE_catalog.read`; Inventory campaign
  allocation MUST require `SCOPE_inventory.campaign.allocate`; the approved client registration MAY
  include `inventory.campaign.release` for the future release feature, but Feature 017 MUST NOT invoke
  a release operation.
- **FR-036**: Product and Inventory MUST independently validate service-token signature, issuer,
  audience, expiration, subject, and required endpoint scope. An administrator token, wrong-audience
  token, or insufficient-scope token MUST NOT authorize these internal endpoints.
- **FR-037**: Campaign client credentials MUST enter Campaign Service through runtime secret
  configuration, MUST NOT be committed, logged, persisted in Campaign data, or baked into an image,
  and MUST be independently replaceable. Access tokens MAY be cached only in memory and MUST be
  renewed before expiry without logging or persisting their value.
- **FR-038**: Schedule recovery and lifecycle background processing MUST be able to acquire a new
  Campaign service token without an administrator session; recovery MUST still reuse the original
  idempotency and Inventory request identities.
- **FR-039**: Campaign audit data MUST distinguish the initiating administrator identity from the
  calling service identity and trace identity. A service token authorizes downstream work but MUST
  NOT replace the initiating administrator in business audit fields.
- **FR-040**: Authentication Service MUST register `flashsale-service` as a separate Client
  Credentials client allowed to request `campaign.snapshot.read`; its credentials and token MUST
  remain independent from the Campaign client. Campaign Service MUST independently validate the
  token signature, issuer, internal audience, expiration, `flashsale-service` subject, and snapshot
  authority before returning internal snapshot data.
- **FR-041**: `CampaignScheduled.v1` and `CampaignActivated.v1` MUST be Avro records with generated
  protocol types, registered and compatibility-checked under their Schema Registry subjects before
  a producer rollout.
- **FR-042**: The lifecycle producer MUST use the approved subject naming and compatibility policy;
  an incompatible schema change MUST be rejected before publication and MUST NOT mutate Campaign
  state.
- **FR-043**: If Kafka or Schema Registry is unavailable, the already committed Campaign transition
  and its stable outbox event identity MUST remain durable and retryable; the system MUST NOT create
  a second Campaign transition or event identity.
- **FR-044**: Lifecycle records MUST carry stable event identity and Campaign key as defined by the
  contract, while W3C trace context is propagated through Kafka headers and sensitive credentials
  are absent from payloads and headers.

### Non-Functional Requirements

- **NFR-COR-001**: In concurrency tests with at least two simultaneous schedule attempts against one
  campaign version, no run may create more than one Inventory allocation or one scheduled business
  result.
- **NFR-COR-002**: In multi-instance lifecycle race tests, no run may create more than one activated
  business result for the same campaign version.
- **NFR-REL-001**: A simulated interruption after successful Inventory allocation MUST be recoverable
  without manual data repair and without duplicate allocation.
- **NFR-REL-002**: A simulated Kafka or Schema Registry outage after a successful Campaign transition
  MUST preserve one pending lifecycle event that can later be published with its original identity.
- **NFR-SEC-001**: All tested unauthorized administrative calls MUST be denied at both ingress and
  owning-service boundaries.
- **NFR-SEC-002**: Contract tests MUST demonstrate that administrator/service token substitution,
  wrong audience, wrong subject, expired tokens, and missing endpoint scopes are denied without a
  downstream business mutation.
- **NFR-SEC-003**: Internal snapshot contract tests MUST demonstrate that only the approved
  `flashsale-service` identity with `SCOPE_campaign.snapshot.read` receives snapshot data.
- **NFR-OBS-001**: Operators MUST be able to determine a campaign's current lifecycle state, schedule
  operation state, notification publication state, and trace identity from supported operational
  signals without reading another service's data store.

### Key Entities

- **Campaign**: The lifecycle owner for one promotional sale window. Its identity, unique code,
  name, time window, status, version, actors, and lifecycle timestamps remain owned by Campaign
  Service.
- **Campaign Item**: The single configured Product variant and its campaign price, requested and
  allocated quantities, optional per-user purchase limit, immutable Product snapshot, and Inventory
  allocation reference.
- **Schedule Operation**: The durable identity and progress of one idempotent cross-service schedule
  attempt, including the stable Inventory request identity and request fingerprint.
- **Campaign Lifecycle Notification**: A versioned fact for one successful campaign transition,
  represented by an approved Avro record and retained durably until publication succeeds or reaches
  its approved recovery state.
- **OAuth Client Registration**: Authentication-owned machine-client identity containing a hashed
  credential, client status, allowed grant, access-token TTL, and least-privilege scopes.

### Domain Model Delta

- Added lifecycle: `DRAFT -> SCHEDULED -> ACTIVE -> ENDED`.
- Added invariant: only a draft campaign is editable.
- Added invariant: exactly one campaign item is required before scheduling.
- Added invariant: Product snapshot, price, quantity, allocation, and time window are immutable after
  scheduling.
- Added invariant: scheduling succeeds only with full Inventory allocation and creates one scheduled
  lifecycle notification.
- Added invariant: activation occurs only inside `[startAt, endAt)` and creates one activated
  lifecycle notification.
- Added distinction: campaign allocation reserves quota; it is not a completed sale or hot-path
  stock deduction.
- Added identity boundary: administrator JWTs authorize Campaign administration, while a distinct
  `campaign-service` Client Credentials JWT authorizes internal Product and Inventory calls.

## Success Criteria

### Measurable Outcomes

- **SC-001**: An authorized administrator can create, edit, populate, retrieve, and schedule a valid
  single-variant campaign through supported contracts without direct database access.
- **SC-002**: Across duplicate and simultaneous schedule tests, exactly one Inventory
  allocation and one scheduled campaign result exist for a campaign version.
- **SC-003**: Every tested interruption after successful Inventory allocation completes on retry
  with the original allocation and exactly one scheduled lifecycle notification.
- **SC-004**: Across concurrent activation tests, exactly one activation and one activated lifecycle
  notification exist for the campaign version.
- **SC-005**: A campaign is never active before its start time or at/after its end time, and every
  due active campaign reaches `ENDED` through lifecycle processing.
- **SC-006**: Flash Sale Service can reconstruct the sellable campaign snapshot from the internal
  snapshot and ordered lifecycle notifications without accessing Campaign, Product, or Inventory
  databases.
- **SC-007**: All acceptance tests preserve the rule that Campaign Service owns campaign state while
  Product and Inventory remain authoritative for their own data.
- **SC-008**: Every tested lifecycle notification that reaches terminal failure can be explicitly
  requeued by an authorized campaign operator and published using its original event identity.
- **SC-009**: Every tested Campaign-to-Product/Inventory request uses a `campaign-service` token with
  the internal audience and only the endpoint-required scope; no administrator bearer token reaches
  either downstream service.
- **SC-010**: Every tested internal snapshot request from an identity other than
  `flashsale-service`, or without `SCOPE_campaign.snapshot.read`, is denied without returning
  campaign data.
- **SC-011**: Every tested lifecycle schema passes the approved compatibility check, can be
  serialized/deserialized by producer and consumer contract tests, and remains publishable with
  the original event identity after a Kafka or Schema Registry outage.

## Dependencies and Compatibility

- Gateway requires a documented campaign-administration route and authority policy.
- Authentication role/authority mappings require `SCOPE_CAMPAIGN_ADMIN` compatibility without
  weakening existing Product or Inventory authorization.
- Authentication Service requires a narrowly scoped Client Credentials token endpoint and durable
  `campaign-service` and `flashsale-service` client/scope registrations. This extends the existing
  first-party trust model; it does not introduce browser OAuth2/OIDC flows.
- Administrator tokens continue to use the existing `flash-sale-api` audience. Service tokens use
  `flash-sale-internal-api`, and resource servers must select the correct validation policy for each
  endpoint without accepting the audiences interchangeably.
- Product Service requires an approved internal variant-validation contract; this is a cross-module
  contract change and must be planned and tested before Campaign scheduling uses it.
- Campaign scheduling must use the existing Inventory allocation behavior and reconcile any
  transport-field naming difference in its boundary adapter rather than creating a duplicate
  Inventory endpoint. Its authorization is narrowed from `SCOPE_INVENTORY_WRITE` to
  `SCOPE_inventory.campaign.allocate` through an approved compatibility change.
- `CampaignScheduled.v1` and `CampaignActivated.v1` are new versioned integration contracts whose
  Avro record schema, subject naming, compatibility, partition key, headers, and consumer
  expectations must be approved before implementation.
- The root protocol-only `contracts/kafka-avro-contracts` module is a proposed build artifact; it
  must not contain JPA entities, domain models, or service business logic.
- Schema Registry is a runtime contract service, not durable business truth. PostgreSQL outbox rows
  remain authoritative for Campaign transitions and pending publication.
- Campaign Service must remain backward compatible as an independently deployable scaffold while
  slices are introduced in dependency order.
- Flash Sale Service does not need purchase-path implementation in Feature 017, but its approved
  machine-client registration and Campaign snapshot compatibility contract are required to verify
  the protected recovery boundary.

## Assumptions

- The project owner is the business owner and final approver for this personal-project feature.
- One item per campaign is an intentional MVP limit, not a temporary database accident.
- Creating a draft does not require its start time to be in the future; scheduling does.
- Campaign currency comes from the authoritative Product variant snapshot and is not independently
  selected by the campaign administrator.
- A completed schedule operation replays the same campaign result; an in-progress operation resumes
  with the same stable downstream request identity.
- Recovery automation may obtain a Campaign service token and resume an approved schedule operation
  without the original administrator session, while preserving the original initiating user in
  audit data.
- Configuration remains locked while a schedule operation is active, so the crash-after-allocation
  recovery path completes scheduling rather than releasing the allocation.
- Kafka delivery is at least once; downstream consumers remain responsible for idempotent handling.
- Avro schemas are committed to Git, generated deterministically, compatibility-checked before
  release, and registered through controlled tooling rather than application startup.

## Constitutional Constraints

- **Service ownership**: `campaign-service` owns campaign state, schema, migrations, persistence
  mappings, and tests. It must not read Product or Inventory databases or share their domain/JPA
  models.
- **External ingress**: Public/admin campaign traffic enters through `api-gateway`. The internal
  snapshot is not a public shopper route.
- **API/event contracts**: Admin Campaign, Product validation, Inventory allocation compatibility,
  service-token issuance/trust, internal snapshot, `CampaignScheduled.v1`, and
  `CampaignActivated.v1` contracts must be documented and approved before implementation; the
  lifecycle contracts use approved Avro schemas and compatibility tests.
- **Durable and hot-path data**: Campaign state and operation history use Campaign Service's
  PostgreSQL database as durable truth. Redis and purchase hot-path behavior are out of scope.
- **Messaging reliability**: Scheduled/activated state changes require a transactional outbox;
  publication is at least once, consumers are idempotent, ordering is per campaign, and terminal
  failure is recovered by an authenticated `SCOPE_CAMPAIGN_ADMIN` requeue of the same durable event.
  Schema Registry or Kafka unavailability cannot erase the committed transition or replace its event
  identity.
- **Root infrastructure ownership**: Service-owned runtime configuration and schema migrations stay
  under `services/campaign-service`; shared Compose/Kubernetes/monitoring changes stay under root
  `infra/`.
- **Observability**: Campaign Service must expose liveness, readiness, and Prometheus-compatible
  metrics through Spring Boot auto-configuration and declarative configuration. Important requests,
  downstream calls, operations, events, and logs propagate trace identity; business code must not
  construct a Prometheus registry.
- **Verification**: Domain unit, PostgreSQL migration/integration, HTTP contract, security,
  cross-service compatibility, concurrency, outbox/event, recovery, scheduler race, observability,
  module-build, full-reactor, and local smoke tests apply. Load testing is limited to approved
  schedule/lifecycle concurrency profiles; purchase-path load testing is deferred because that path
  is out of scope.
- **Architecture decisions**: The feature has high correctness, stock-allocation, concurrency,
  security, data, and contract risk, so the repository risk workflow and pragmatic feature-local
  Clean/Hexagonal structure apply. No service-boundary change is proposed; any new cross-service
  authentication or communication-style decision requires an ADR before implementation. Avro and
  Schema Registry adoption is tracked by ADR 0016 and must be accepted before code changes.

## Approval and History

- 2026-07-29 — Draft created from the Campaign Service Minimal MVP design; awaiting three P1 policy
  decisions and human specification approval.
- 2026-07-29 — Clarified campaign-code normalization, indefinite idempotency retention/no key reuse,
  and authenticated operator requeue for terminally failed lifecycle notifications.
- 2026-07-29 — Specification approved by the project owner.
- 2026-07-30 — Project owner selected OAuth2 Client Credentials for Campaign service identity;
  specification amended to prohibit administrator-token relay and define downstream audience and
  scope boundaries.
- 2026-07-30 — Project owner approved a separate `flashsale-service` Client Credentials identity
  with `campaign.snapshot.read` for the internal Campaign snapshot; the last open specification
  security boundary was resolved.
- 2026-08-03 — Avro schema-first contracts, Confluent Schema Registry, TopicRecordNameStrategy,
  BACKWARD_TRANSITIVE compatibility, controlled registration, and W3C Kafka header tracing approved
  as the Feature 017 wire-format amendment.
