package com.philia.flashsale.order.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class OrderInfrastructureContractTests {

    @Test
    void composeAndDefaultsExposeTheOrderRuntimeTopology() throws IOException {
        Path root = repositoryRoot();
        String compose = Files.readString(root.resolve("infra/docker/compose.yml"));
        String defaults = Files.readString(root.resolve("infra/docker/.env.example"));

        assertThat(compose).contains("order-service:", "order-migration:", "order_db",
                "ORDER_EVENTS_TOPIC", "schema-registry:", "condition: service_healthy");
        assertThat(defaults).contains("ORDER_SERVICE_URL=http://order-service:8080",
                "ORDER_EVENTS_TOPIC=flashsale.order.events.v1",
                "ORDER_PURCHASE_ACCEPTED_DLT_TOPIC=flashsale.order.purchase-accepted.dlt.v1");
    }

    @Test
    void topicAndSchemaProvisioningScriptsRemainIdempotentAndControlled() throws IOException {
        Path root = repositoryRoot();
        String topics = Files.readString(root.resolve("infra/docker/kafka/init-order-topics.sh"));
        String schema = Files.readString(root.resolve("infra/docker/schema-registry/register-order-schemas.ps1"));

        assertThat(topics).contains("flashsale.order.events.v1", "flashsale.order.purchase-accepted.dlt.v1",
                "--if-not-exists", "PartitionCount", "ReplicationFactor");
        assertThat(schema).contains("OrderCreatedV1.avsc", "OrderCreatedV1",
                "BACKWARD_TRANSITIVE", "TopicRecordNameStrategy", "CheckOnly");
    }

    @Test
    void powershellProvisioningScriptParsesWhenPowerShellIsAvailable() throws Exception {
        Path root = repositoryRoot();
        String executable = executable(List.of("pwsh", "powershell"));
        assumeTrue(executable != null, "PowerShell is not installed in this environment");
        Path script = root.resolve("infra/docker/schema-registry/register-order-schemas.ps1");
        Path parser = Files.createTempFile("order-schema-parser-", ".ps1");
        Files.writeString(parser, "$tokens=$null\n$errors=$null\n"
                + "[System.Management.Automation.Language.Parser]::ParseFile($args[0],[ref]$tokens,[ref]$errors) | Out-Null\n"
                + "if($errors.Count -gt 0){ exit 1 }\n");
        Process process = new ProcessBuilder(executable, "-NoProfile", "-File", parser.toString(), script.toString())
                .directory(root.toFile()).redirectErrorStream(true).start();
        process.getInputStream().readAllBytes();
        assertThat(process.waitFor(20, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).isZero();
        Files.deleteIfExists(parser);
    }

    @Test
    void renderedComposeContainsTheOrderServiceWhenDockerIsAvailable() throws Exception {
        String executable = executable(List.of("docker"));
        assumeTrue(executable != null, "Docker is not installed in this environment");
        Path root = repositoryRoot();
        Process process = new ProcessBuilder(executable, "compose", "--env-file", "infra/docker/.env.example",
                "-f", "infra/docker/compose.yml", "--profile", "apps", "--profile", "migrations", "config")
                .directory(root.toFile()).redirectErrorStream(true).start();
        byte[] output = process.getInputStream().readAllBytes();
        assertThat(process.waitFor(60, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).isZero();
        assertThat(new String(output)).contains("order-service:", "order-migration:", "order_db");
    }

    private static String executable(List<String> candidates) {
        for (String candidate : candidates) {
            try {
                Process process = new ProcessBuilder(candidate, "--version")
                        .redirectErrorStream(true).start();
                if (process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0) {
                    return candidate;
                }
            } catch (IOException | InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    private static Path repositoryRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null && !Files.exists(current.resolve("infra/docker/compose.yml"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("Cannot locate repository root");
        }
        return current;
    }
}
