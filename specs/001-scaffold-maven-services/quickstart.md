# Quickstart: Maven Multi-Module Microservice Skeleton

## Prerequisites

- JDK 21 with `JAVA_HOME` pointing to the JDK.
- Internet access for the initial wrapper/dependency download.
- Apache Maven available only for the one-time wrapper generation step.

Confirm the toolchain:

```powershell
mvn -version
```

The Maven output must report Java 21.

## Generate the shared Maven Wrapper

From the repository root, run once:

```powershell
mvn wrapper:wrapper -Dtype=bin
```

Expected root files:

- `.mvn/wrapper/maven-wrapper.properties`
- `.mvn/wrapper/maven-wrapper.jar`
- `mvnw`
- `mvnw.cmd`

No wrapper file may exist below `services/`.

## Verify the full reactor

```powershell
.\mvnw.cmd clean verify
```

On a Unix-like environment, use the shared shell entry point:

```bash
./mvnw clean verify
```

Expected result:

- All nine service modules appear in the reactor summary.
- Nine context-load tests run and pass.
- The final result is `BUILD SUCCESS`.

## Verify one service in isolation

Replace the module path to repeat for each affected service:

```powershell
.\mvnw.cmd -pl services/order-service -am verify
```

The selected service and required parent project must build without another service dependency.

## Run and inspect a service

Start one module on a local port:

```powershell
$env:SERVER_PORT = '18080'
.\mvnw.cmd -pl services/order-service spring-boot:run
```

In another terminal, verify the operational contract:

```powershell
Invoke-WebRequest http://localhost:18080/actuator/health -UseBasicParsing
Invoke-WebRequest http://localhost:18080/actuator/info -UseBasicParsing
Invoke-WebRequest http://localhost:18080/actuator/health/liveness -UseBasicParsing
Invoke-WebRequest http://localhost:18080/actuator/health/readiness -UseBasicParsing
Invoke-WebRequest http://localhost:18080/actuator/prometheus -UseBasicParsing
```

Repeat sequentially for all nine modules. Each request must return HTTP 200; the health and probe
responses must report `UP` within 60 seconds, `/actuator/info` must report
`app.name=order-service` (or the selected module name), and the Prometheus response must be non-empty.

To confirm the default-port edge cases, stop the service, remove `SERVER_PORT`, and start it again;
then repeat with an empty value. Both runs must listen on port 8080:

```powershell
Remove-Item Env:SERVER_PORT -ErrorAction SilentlyContinue
.\mvnw.cmd -pl services/order-service spring-boot:run

$env:SERVER_PORT = ''
.\mvnw.cmd -pl services/order-service spring-boot:run
```

## Out-of-scope validation

No Kubernetes dry-run, database integration, Kafka, Redis, contract-to-business-API, or load-test
command applies because this feature creates none of those artifacts or behaviors.
