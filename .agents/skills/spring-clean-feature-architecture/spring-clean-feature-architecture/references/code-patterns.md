# Code patterns

Use these as patterns, not mandatory boilerplate.

## Inbound port and use case

```java
public interface CreateOrderUseCase {
    OrderResult create(CreateOrderCommand command);
}
```

```java
@Service
@Transactional
@RequiredArgsConstructor
class CreateOrderService implements CreateOrderUseCase {

    private final PersistOrderWithOutboxPort persistOrderWithOutboxPort;

    @Override
    public OrderResult create(CreateOrderCommand command) {
        Order order = Order.create(command.customerId(), command.items());
        OrderCreated event = new OrderCreated(order.id());
        Order saved = persistOrderWithOutboxPort.persist(order, event);
        return OrderResult.from(saved);
    }
}
```

## Outbound persistence port

```java
public interface PersistOrderWithOutboxPort {
    Order persist(Order order, OrderCreated event);
}

public interface LoadOrderPort {
    Optional<Order> findById(OrderId orderId);
}
```

## Spring Data repository and adapter

```java
interface SpringDataOrderRepository
        extends JpaRepository<OrderJpaEntity, UUID> {
}
```

```java
@Component
@RequiredArgsConstructor
class OrderPersistenceAdapter implements PersistOrderWithOutboxPort, LoadOrderPort {

    private final SpringDataOrderRepository repository;
    private final SpringDataOutboxRepository outboxRepository;
    private final OrderPersistenceMapper mapper;
    private final OrderEventMapper eventMapper;

    @Override
    public Order persist(Order order, OrderCreated event) {
        OrderJpaEntity saved = repository.save(mapper.toJpaEntity(order));
        outboxRepository.save(eventMapper.toOutboxEntity(event));
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Order> findById(OrderId orderId) {
        return repository.findById(orderId.value()).map(mapper::toDomain);
    }
}
```

The application transaction commits the Order and outbox row together. A separate outbox publisher
later maps the approved record to the versioned Kafka contract. Do not replace this with an
uncoordinated database save followed by a required direct Kafka publish.

Translate implementation exceptions at this boundary:

```java
try {
    return persist(order, event);
} catch (DataIntegrityViolationException exception) {
    throw new DuplicateOrderRequestException(idempotencyKey, exception);
}
```

## HTTP mapping flow

```text
CreateOrderRequest
        │ web mapper
        ▼
CreateOrderCommand
        │
        ▼
CreateOrderUseCase
        │
        ▼
OrderResult
        │ web mapper
        ▼
OrderResponse
```

```java
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface OrderWebMapper {
    CreateOrderCommand toCommand(CreateOrderRequest request, CustomerId customerId);
    OrderResponse toResponse(OrderResult result);
}
```

```java
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface OrderPersistenceMapper {
    OrderJpaEntity toJpaEntity(Order order);
    Order toDomain(OrderJpaEntity entity);
}
```

## Domain invariant

```java
public record Quantity(int value) {
    public Quantity {
        if (value <= 0) {
            throw new InvalidQuantityException(value);
        }
    }
}
```

## Controller

```java
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
class OrderController {

    private final CreateOrderUseCase createOrderUseCase;
    private final OrderWebMapper mapper;

    @PostMapping
    ResponseEntity<OrderResponse> create(
            @Valid @RequestBody CreateOrderRequest request,
            Authentication authentication) {

        CustomerId customerId = CustomerId.from(authentication.getName());
        OrderResult result = createOrderUseCase.create(mapper.toCommand(request, customerId));
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(result));
    }
}
```

Do not pass `Authentication`, `Jwt`, `HttpServletRequest`, or `ResponseEntity` into application/domain code.

## Exception-to-HTTP translation

```java
@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(OrderNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleOrderNotFound(
            OrderNotFoundException exception,
            HttpServletRequest request) {

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(
                        "ORDER_NOT_FOUND",
                        exception.getMessage(),
                        request.getRequestURI()));
    }
}
```

## ArchUnit

```java
@AnalyzeClasses(packages = "com.philia.flashsale.order")
class CleanArchitectureTest {

    @ArchTest
    static final ArchRule domainMustNotDependOnFrameworks =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "org.springframework..",
                            "jakarta.persistence..",
                            "org.mapstruct..");
}
```

Add rules for adapter separation and feature-to-feature access when the package layout is stable.
