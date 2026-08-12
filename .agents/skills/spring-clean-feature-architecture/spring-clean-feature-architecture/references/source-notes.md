# Original source notes

This reference preserves the detailed source material supplied by the user. Treat it as historical
input, not current normative guidance. Use the curated references first: some examples below retain
the original `shared/bootstrap` layout and direct save-then-publish flow that the curated guidance
has since refined for this repository.

2. Cấu trúc bên trong một microservice

Lấy order-service làm ví dụ:

com.philia.flashsale.order
├── OrderServiceApplication.java
│
├── order/
│   ├── domain/
│   │   ├── model/
│   │   │   ├── Order.java
│   │   │   ├── OrderId.java
│   │   │   ├── OrderItem.java
│   │   │   ├── OrderStatus.java
│   │   │   └── MoneyVnd.java
│   │   │
│   │   ├── event/
│   │   │   ├── OrderCreated.java
│   │   │   └── OrderCancelled.java
│   │   │
│   │   ├── exception/
│   │   │   ├── InvalidOrderStateException.java
│   │   │   └── InvalidOrderItemException.java
│   │   │
│   │   ├── policy/
│   │   │   └── OrderCancellationPolicy.java
│   │   │
│   │   └── service/
│   │       └── OrderPricingService.java
│   │
│   ├── application/
│   │   ├── port/
│   │   │   ├── in/
│   │   │   │   ├── CreateOrderUseCase.java
│   │   │   │   ├── CancelOrderUseCase.java
│   │   │   │   └── GetOrderUseCase.java
│   │   │   │
│   │   │   └── out/
│   │   │       ├── LoadOrderPort.java
│   │   │       ├── SaveOrderPort.java
│   │   │       ├── ReserveStockPort.java
│   │   │       └── PublishOrderEventPort.java
│   │   │
│   │   ├── usecase/
│   │   │   ├── CreateOrderService.java
│   │   │   ├── CancelOrderService.java
│   │   │   └── GetOrderService.java
│   │   │
│   │   ├── command/
│   │   │   ├── CreateOrderCommand.java
│   │   │   └── CancelOrderCommand.java
│   │   │
│   │   ├── result/
│   │   │   └── OrderResult.java
│   │   │
│   │   └── exception/
│   │       ├── OrderNotFoundException.java
│   │       └── DuplicateOrderRequestException.java
│   │
│   └── adapter/
│       ├── in/
│       │   ├── web/
│       │   │   ├── OrderController.java
│       │   │   ├── request/
│       │   │   │   ├── CreateOrderRequest.java
│       │   │   │   └── CancelOrderRequest.java
│       │   │   ├── response/
│       │   │   │   └── OrderResponse.java
│       │   │   ├── mapper/
│       │   │   │   └── OrderWebMapper.java
│       │   │   └── error/
│       │   │       └── OrderApiErrorCode.java
│       │   │
│       │   └── messaging/
│       │       └── PaymentConfirmedConsumer.java
│       │
│       └── out/
│           ├── persistence/
│           │   └── jpa/
│           │       ├── entity/
│           │       │   ├── OrderJpaEntity.java
│           │       │   └── OrderItemJpaEntity.java
│           │       ├── repository/
│           │       │   └── SpringDataOrderRepository.java
│           │       ├── mapper/
│           │       │   └── OrderPersistenceMapper.java
│           │       └── OrderPersistenceAdapter.java
│           │
│           ├── messaging/
│           │   └── kafka/
│           │       └── KafkaOrderEventPublisher.java
│           │
│           └── client/
│               └── inventory/
│                   ├── InventoryClient.java
│                   ├── InventoryClientAdapter.java
│                   └── InventoryClientMapper.java
│
├── shared/
│   ├── web/
│   │   ├── ApiResponse.java
│   │   ├── ApiErrorResponse.java
│   │   ├── FieldViolation.java
│   │   └── GlobalExceptionHandler.java
│   │
│   ├── security/
│   │   ├── SecurityConfiguration.java
│   │   ├── CurrentActor.java
│   │   └── JwtAuthenticationConverter.java
│   │
│   ├── observability/
│   │   ├── CorrelationIdFilter.java
│   │   ├── RequestLoggingFilter.java
│   │   └── LogContext.java
│   │
│   └── config/
│       ├── JacksonConfiguration.java
│       ├── JpaConfiguration.java
│       └── ClockConfiguration.java
│
└── bootstrap/
    └── OrderModuleConfiguration.java

Đây là mô hình:

Package by feature ở ngoài
+
Clean Architecture ở trong
3. Luật dependency

Luồng compile-time phải là:

adapter.in.web
       │
       ▼
application
       │
       ▼
domain

Persistence adapter:

adapter.out.persistence
       │
       ├────────► application.port.out
       └────────► domain

Domain không được đi ngược ra ngoài:

domain ─X─► Spring MVC
domain ─X─► JpaRepository
domain ─X─► Hibernate
domain ─X─► KafkaTemplate
domain ─X─► MapStruct
domain ─X─► HTTP DTO

ArchUnit mô tả Onion/Hexagonal Architecture theo đúng nguyên tắc: application được dùng domain, adapters được dùng application và domain; domain/application không được phụ thuộc adapters, và các adapter không nên phụ thuộc trực tiếp lẫn nhau.

4. Domain layer
Domain chứa gì?
domain/
├── model
├── value object
├── aggregate
├── domain service
├── domain policy
├── domain event
└── domain exception

Ví dụ Order:

public class Order {

    private final OrderId id;
    private final CustomerId customerId;
    private final List<OrderItem> items;
    private OrderStatus status;

    public void cancel() {
        if (status != OrderStatus.PENDING) {
            throw new InvalidOrderStateException(
                    "Only pending orders can be cancelled"
            );
        }

        status = OrderStatus.CANCELLED;
    }
}

Domain không biết:

Request HTTP đến từ đâu.
Database là PostgreSQL hay MongoDB.
Object được lưu bằng JPA hay JDBC.
Response trả JSON như thế nào.
Kafka topic tên gì.
Spring đang chạy hay không.
Domain entity khác JPA entity

Nên đặt tên rõ:

Order
→ domain aggregate

OrderJpaEntity
→ persistence representation

Không nên đặt cả hai đều là OrderEntity.

Domain model
public class Order {
    private OrderId id;
    private List<OrderItem> items;
    private OrderStatus status;
}
Persistence model
@Entity
@Table(name = "orders")
public class OrderJpaEntity {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    @OneToMany(
        mappedBy = "order",
        cascade = CascadeType.ALL,
        orphanRemoval = true
    )
    private List<OrderItemJpaEntity> items;
}

Tách hai model giúp domain không bị Hibernate annotation, lazy loading, proxy và cấu trúc bảng chi phối.

Tuy nhiên, không phải feature nào cũng cần mức tách này. Repository ddd-by-examples/library nhấn mạnh rằng mỗi bounded context có thể sử dụng kiến trúc cục bộ phù hợp với độ phức tạp của bài toán; phần có business logic thực sự mới sử dụng domain model và Hexagonal Architecture đầy đủ.

Quy tắc thực tế:

Business logic phức tạp
→ tách domain model và JPA model.

CRUD đơn giản, bảng lookup
→ có thể dùng một model để giảm boilerplate.

Đó là pragmatic Clean Architecture, không phải phá kiến trúc.

5. Application layer

Application layer chứa các use case của hệ thống.

Không nên tạo một class khổng lồ:

OrderService.java

với 30 method:

createOrder()
cancelOrder()
payOrder()
expireOrder()
findOrder()
findOrders()
updateOrder()
...

Nên tách theo use case:

CreateOrderUseCase
CreateOrderService

CancelOrderUseCase
CancelOrderService

GetOrderUseCase
GetOrderService

Ví dụ inbound port:

public interface CreateOrderUseCase {

    OrderResult create(CreateOrderCommand command);
}

Implementation:

@Service
@Transactional
@RequiredArgsConstructor
class CreateOrderService implements CreateOrderUseCase {

    private final SaveOrderPort saveOrderPort;
    private final ReserveStockPort reserveStockPort;
    private final PublishOrderEventPort eventPort;

    @Override
    public OrderResult create(CreateOrderCommand command) {
        Order order = Order.create(
                command.customerId(),
                command.items()
        );

        reserveStockPort.reserve(order);

        Order savedOrder = saveOrderPort.save(order);

        eventPort.publish(new OrderCreated(savedOrder.getId()));

        return OrderResult.from(savedOrder);
    }
}
Đặt transaction ở đâu?

Transaction boundary thường nên trùng với use case:

@Transactional
class CreateOrderService

Không nên đặt chính ở:

Controller
JpaRepository
Domain entity

Spring hỗ trợ declarative transaction và cho phép khai báo transaction ở class hoặc method. Tài liệu Spring cũng lưu ý transaction context không truyền qua remote call và thông thường không nên kéo một database transaction xuyên qua nhiều dịch vụ từ xa.

Hai mức độ Clean Architecture

Strict:

Application không import Spring.
TransactionRunner được định nghĩa thành port.

Pragmatic:

Application service được dùng @Service và @Transactional.
Domain vẫn hoàn toàn thuần Java.

Với đồ án thực tập, mình khuyên dùng mức pragmatic. Bạn vẫn giữ được domain sạch nhưng không tạo quá nhiều abstraction chỉ để che một annotation.

6. Repository và Spring Data JPA

Application chỉ định nghĩa nhu cầu nghiệp vụ:

public interface SaveOrderPort {

    Order save(Order order);
}
public interface LoadOrderPort {

    Optional<Order> findById(OrderId orderId);
}

Không viết:

public interface OrderRepository
        extends JpaRepository<OrderJpaEntity, UUID> {
}

ở domain hoặc application.

JpaRepository phải nằm trong persistence adapter:

interface SpringDataOrderRepository
        extends JpaRepository<OrderJpaEntity, UUID> {

    Optional<OrderJpaEntity> findByIdAndCustomerId(
            UUID orderId,
            UUID customerId
    );
}

Spring Data định nghĩa repository abstraction dựa trên domain class được quản lý và identifier type; đó là abstraction của persistence framework nên nên được giữ ở adapter ngoài.

Adapter:

@Component
@RequiredArgsConstructor
class OrderPersistenceAdapter
        implements SaveOrderPort, LoadOrderPort {

    private final SpringDataOrderRepository repository;
    private final OrderPersistenceMapper mapper;

    @Override
    public Order save(Order order) {
        OrderJpaEntity entity = mapper.toJpaEntity(order);
        OrderJpaEntity saved = repository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Order> findById(OrderId orderId) {
        return repository.findById(orderId.value())
                .map(mapper::toDomain);
    }
}

Luồng runtime:

CreateOrderService
    │ gọi interface
    ▼
SaveOrderPort
    ▲ được implement bởi
    │
OrderPersistenceAdapter
    │
    ▼
SpringDataOrderRepository
    │
    ▼
PostgreSQL

Application biết SaveOrderPort, nhưng không biết JPA.

Đây chính là Dependency Inversion.

7. DTO request, response và command

Không đưa CreateOrderRequest thẳng vào application service.

Sai:

public OrderResponse create(CreateOrderRequest request);

Vì application lúc này phụ thuộc HTTP DTO.

Đúng:

CreateOrderRequest
       │ Web mapper
       ▼
CreateOrderCommand
       │
       ▼
CreateOrderUseCase
       │
       ▼
OrderResult
       │ Web mapper
       ▼
OrderResponse
Request DTO
public record CreateOrderRequest(
        @NotEmpty List<OrderItemRequest> items
) {
}
Application command
public record CreateOrderCommand(
        CustomerId customerId,
        List<CreateOrderItemCommand> items
) {
}
Response DTO
public record OrderResponse(
        UUID id,
        String status,
        BigDecimal totalAmount
) {
}

Clean Architecture khuyến nghị dữ liệu đi qua boundary dưới dạng các cấu trúc đơn giản và không truyền database row hoặc framework-specific model vào trong.

8. Validation nên có ba tầng
Tầng 1: HTTP validation

Kiểm tra hình dạng request:

public record CreateOrderRequest(

        @NotEmpty
        List<@Valid OrderItemRequest> items
) {
}
public record OrderItemRequest(

        @NotNull
        UUID variantId,

        @Positive
        int quantity
) {
}

Controller:

@PostMapping
ResponseEntity<OrderResponse> create(
        @Valid @RequestBody CreateOrderRequest request
) {
}

Spring MVC tích hợp Bean Validation cho @RequestBody, @ModelAttribute và các tham số được đánh dấu @Valid hoặc @Validated.

Tầng 2: Domain invariant

Ví dụ:

public record Quantity(int value) {

    public Quantity {
        if (value <= 0) {
            throw new InvalidQuantityException(value);
        }
    }
}

Domain invariant phải luôn đúng, dù object được tạo từ HTTP, Kafka consumer, batch job hay unit test.

Tầng 3: Business validation

Ví dụ:

Mỗi khách chỉ được mua tối đa hai sản phẩm flash sale.
Không được cancel order đã thanh toán.
Không được tạo order ngoài campaign window.

Các kiểm tra này nằm trong domain policy hoặc application use case, không phải annotation DTO.

9. MapStruct nên đặt ở đâu?

MapStruct là annotation processor sinh code mapping type-safe từ mapper interface. Code được sinh sử dụng lời gọi method Java thông thường.

Nên có hai mapper riêng.

Web mapper
adapter/in/web/mapper/OrderWebMapper
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface OrderWebMapper {

    CreateOrderCommand toCommand(
            CreateOrderRequest request,
            CustomerId customerId
    );

    OrderResponse toResponse(OrderResult result);
}
Persistence mapper
adapter/out/persistence/jpa/mapper/OrderPersistenceMapper
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface OrderPersistenceMapper {

    OrderJpaEntity toJpaEntity(Order order);

    Order toDomain(OrderJpaEntity entity);
}

Không nên có:

shared/mapper/GlobalMapper.java

hoặc:

OrderMapper

vừa map HTTP DTO, JPA entity, Kafka event và domain model.

Mỗi mapper nên thuộc về một boundary:

Web boundary        → OrderWebMapper
Persistence boundary → OrderPersistenceMapper
Kafka boundary       → OrderEventMapper
Client boundary      → InventoryClientMapper
10. Exception và error code

Nên chia exception theo nơi phát sinh.

Domain exception
order/domain/exception/
├── InvalidOrderStateException
├── InvalidOrderItemException
└── PurchaseLimitExceededException

Ý nghĩa:

Business invariant bị vi phạm.

Domain exception không biết:

HTTP status.
JSON response.
ResponseEntity.
ProblemDetail.

Không viết:

throw new ResponseStatusException(
        HttpStatus.CONFLICT,
        "Order cannot be cancelled"
);

trong domain.

Application exception
order/application/exception/
├── OrderNotFoundException
├── DuplicateOrderRequestException
└── StockReservationFailedException

Ý nghĩa:

Use case không thể hoàn thành.
Infrastructure exception

Infrastructure exception nên được translate trước khi đi vào application:

try {
    repository.save(entity);
} catch (DataIntegrityViolationException exception) {
    throw new DuplicateOrderRequestException(idempotencyKey);
}

Không để controller nhận trực tiếp:

ConstraintViolationException
PSQLException
HibernateException
FeignException
API error code

Error code thuộc API boundary:

public enum OrderApiErrorCode {

    ORDER_NOT_FOUND,
    ORDER_ALREADY_CANCELLED,
    ORDER_CANNOT_BE_CANCELLED,
    DUPLICATE_ORDER_REQUEST,
    STOCK_RESERVATION_FAILED
}

Không tạo một enum duy nhất cho toàn monorepo:

GlobalErrorCode {
    AUTH_001,
    PRODUCT_001,
    ORDER_001,
    PAYMENT_001,
    ...
}

Mỗi service sở hữu error code của nó.

Global exception handler

Global ở đây nghĩa là global trong một microservice, không phải dùng chung runtime cho tất cả service.

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(OrderNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleOrderNotFound(
            OrderNotFoundException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(
                        "ORDER_NOT_FOUND",
                        exception.getMessage(),
                        request.getRequestURI()
                ));
    }
}

Spring MVC hỗ trợ @ExceptionHandler thông qua exception resolution của DispatcherServlet; @ControllerAdvice cho phép đăng ký handler dùng chung qua component scanning.

Error response có thể thống nhất:

{
  "timestamp": "2026-07-27T23:30:00Z",
  "status": 404,
  "code": "ORDER_NOT_FOUND",
  "message": "Order was not found",
  "path": "/api/v1/orders/123",
  "traceId": "4f9be0...",
  "violations": []
}

Validation error:

{
  "status": 400,
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "violations": [
    {
      "field": "items[0].quantity",
      "message": "must be greater than 0"
    }
  ]
}
11. Logging và observability

Không tạo package:

utils/LoggerUtils.java

Nên tạo:

shared/observability/
├── CorrelationIdFilter.java
├── RequestLoggingFilter.java
├── LogContext.java
└── ObservabilityConfiguration.java
Log ở đâu?

Nên log tại:

Request boundary.
Use-case bắt đầu/kết thúc nếu quan trọng.
Business state transition.
Outbound call.
Kafka publish/consume.
Retry/failure.
Global exception handler.

Không nên log cùng một exception ở mọi layer:

Repository log error
Application log error
Controller log error
Global handler log error

Điều đó tạo bốn log cho cùng một lỗi.

Quy tắc:

Layer xử lý hoặc kết thúc exception
→ chịu trách nhiệm log.

Layer chỉ throw tiếp
→ không log lại.

Structured log:

{
  "timestamp": "...",
  "level": "INFO",
  "service": "order-service",
  "event": "order.created",
  "orderId": "...",
  "customerId": "...",
  "traceId": "...",
  "spanId": "...",
  "correlationId": "...",
  "outcome": "SUCCESS"
}

Spring Boot hỗ trợ structured logging theo các format JSON phổ biến như ECS, GELF và Logstash. Spring Boot Actuator và Micrometer cung cấp nền tảng metrics, observations và tracing.

Không log:

Access token.
Refresh token.
Password.
Card information.
Toàn bộ request có dữ liệu nhạy cảm.
12. Security nằm ở đâu?
shared/security/
├── SecurityConfiguration.java
├── JwtAuthenticationConverter.java
├── CurrentActor.java
└── CurrentActorProvider.java

Application không nên nhận Jwt của Spring Security:

Sai:

OrderResult create(CreateOrderRequest request, Jwt jwt);

Đúng:

OrderResult create(CreateOrderCommand command);

Trong đó:

public record CreateOrderCommand(
        CustomerId customerId,
        List<CreateOrderItemCommand> items
) {
}

Web/security adapter lấy sub từ JWT rồi chuyển thành CustomerId.

JWT
→ Security adapter
→ CustomerId
→ Application command

Nhờ vậy application không phụ thuộc Spring Security.

13. Cross-feature communication

Giả sử trong một service có:

order/
promotion/
shipping/

Không cho phép:

order.application
    │
    └── gọi thẳng promotion.adapter.out.persistence

Feature chỉ nên dùng public API của feature khác:

order.application
    │
    ▼
promotion.api.PromotionQuery

Hoặc application event:

OrderCreated
    │
    ▼
Shipping listener

Spring Modulith có thể kiểm tra:

Không có dependency cycle.
Module chỉ truy cập public API của module khác.
Chỉ được dùng dependency đã khai báo.

Trong microservice, Spring Modulith không bắt buộc. Bạn có thể chỉ dùng ArchUnit nếu muốn nhẹ hơn.

14. Architecture test

Nên có:

src/test/java/com/philia/flashsale/order/architecture/
└── CleanArchitectureTest.java

Ví dụ:

@AnalyzeClasses(packages = "com.philia.flashsale.order")
class CleanArchitectureTest {

    @ArchTest
    static final ArchRule architecture =
            onionArchitecture()
                    .domainModels("..domain.model..")
                    .domainServices(
                            "..domain.service..",
                            "..domain.policy.."
                    )
                    .applicationServices("..application..")
                    .adapter("web", "..adapter.in.web..")
                    .adapter(
                            "persistence",
                            "..adapter.out.persistence.."
                    )
                    .adapter(
                            "messaging",
                            "..adapter..messaging.."
                    );
}

Thêm rule:

@ArchTest
static final ArchRule domainMustNotDependOnSpring =
        noClasses()
                .that()
                .resideInAPackage("..domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "org.mapstruct.."
                );

ArchUnit được thiết kế để kiểm tra dependency giữa package/class, layer, cycle và Onion Architecture bằng unit test Java thông thường.

15. Chiến lược testing
domain/
→ pure unit test, không Spring, không mock nếu có thể.

application/
→ unit test với mock outbound ports.

adapter/in/web/
→ @WebMvcTest.

adapter/out/persistence/
→ @DataJpaTest + Testcontainers.

toàn service/
→ @SpringBootTest cho critical flow.

architecture/
→ ArchUnit.

Spring Boot cung cấp test slices để chỉ khởi động phần framework cần thiết thay vì luôn chạy toàn bộ application context.

Cây test:

src/test/java/com/philia/flashsale/order/
├── order/
│   ├── domain/
│   │   └── OrderTest.java
│   ├── application/
│   │   └── CreateOrderServiceTest.java
│   └── adapter/
│       ├── in/web/
│       │   └── OrderControllerTest.java
│       └── out/persistence/
│           └── OrderPersistenceAdapterTest.java
│
├── architecture/
│   └── CleanArchitectureTest.java
│
└── integration/
    └── CreateOrderIntegrationTest.java
16. Những gì được dùng chung trong monorepo
Có thể share
libs/
├── platform-bom
├── build-conventions
├── observability-starter
├── test-support
└── web-contract

Ví dụ:

platform-bom
Quản lý version thư viện.
Spring Boot dependency management.
MapStruct.
Testcontainers.
ArchUnit.
Micrometer.
observability-starter
Correlation ID.
Trace context.
Structured logging conventions.
Common metric names.
test-support
Testcontainers PostgreSQL.
Kafka test fixtures.
JWT test factory.
Common assertions.
web-contract

Có thể chứa:

ApiResponse
ApiErrorResponse
FieldViolation
PageResponse

Nhưng phải version cẩn thận vì mọi service phụ thuộc nó.

Không nên share
OrderJpaEntity
UserJpaEntity
ProductJpaEntity

OrderRepository
UserRepository

OrderNotFoundException
ProductNotFoundException

OrderStatus
PaymentStatus

OrderMapper
UserMapper

Business service

Không chia sẻ domain entity giữa service.

Nếu order-service muốn biết product:

Không import Product entity.
Không query product database.
Không dùng shared Product DTO jar.

Nó dùng:

HTTP contract
Kafka event contract
hoặc local snapshot cần thiết

Nếu shared library chứa quá nhiều business code, microservices sẽ trở thành một distributed monolith về mặt compile-time và release.

17. Các repository đáng nghiên cứu
thombergs/buckpal

Repo tập trung trực tiếp vào:

Implement use case.
Web adapter.
Persistence adapter.
Mapping giữa boundaries.
Assembly.
Enforce architecture boundaries.
Multiple bounded contexts.

Đây là repo sát nhất với cấu trúc Ports and Adapters cho Spring Boot.

ddd-by-examples/library

Điểm mạnh:

Bắt đầu từ business requirement.
Event Storming.
DDD tactical patterns.
Bounded context.
Hexagonal Architecture.
ArchUnit.
Không cố dùng cùng một kiến trúc cho mọi module.

Repo này tốt để học tư duy domain, không chỉ học cách xếp folder.

spring-petclinic-modulith

Repo tổ chức theo module nghiệp vụ. Mỗi module sở hữu domain logic, persistence và controller; cross-cutting concern được tách sang system. Nó cũng minh họa module test, structural verification và event communication.

Đây là ví dụ tốt cho tư duy:

package by business module
thay vì
package by technical layer
Spring Modulith và ArchUnit

Spring Modulith dùng để kiểm tra module-level API, cycles và allowed dependencies; ArchUnit dùng để kiểm tra dependency rule chi tiết bên trong package.

18. Cấu trúc mình chốt cho dự án của bạn

Trong từng microservice:

<service-root>/
├── <feature-one>/
│   ├── domain/
│   ├── application/
│   └── adapter/
│       ├── in/
│       └── out/
│
├── <feature-two>/
│   ├── domain/
│   ├── application/
│   └── adapter/
│       ├── in/
│       └── out/
│
├── shared/
│   ├── web/
│   ├── security/
│   ├── observability/
│   └── config/
│
└── bootstrap/

Các luật bắt buộc:

1. Domain thuần Java.

2. Controller chỉ xử lý HTTP và gọi use case.

3. Application service đại diện một use case.

4. Application phụ thuộc outbound port, không phụ thuộc JPA.

5. JpaRepository nằm trong persistence adapter.

6. JPA entity không đi ra khỏi persistence adapter.

7. Request/response DTO không đi vào domain.

8. MapStruct mapper thuộc từng boundary.

9. Domain exception không biết HTTP status.

10. GlobalExceptionHandler chỉ làm exception-to-HTTP translation.

11. Transaction đặt tại application use case.

12. Logging, security và framework configuration nằm ngoài domain.

13. Feature không truy cập persistence internals của feature khác.

14. Dùng ArchUnit để biến kiến trúc thành test, không chỉ ghi trong tài liệu.

15. Không áp dụng full ceremony cho mọi CRUD nhỏ.

Đây là cấu trúc vừa thể hiện được Clean Architecture, Hexagonal Architecture, DDD và package-by-feature, vừa không biến đồ án thực tập thành một hệ thống có quá nhiều abstraction vô ích.
