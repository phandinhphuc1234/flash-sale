package com.philia.flashsale.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Static ownership and optional local-tool parser checks for the shared Payment infrastructure.
 *
 * <p>The tests never open the owner-managed {@code infra/docker/.env}; only the tracked example and
 * declarative files are inspected.
 */
class PaymentInfrastructureContractTests {
    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void keepsPaymentRuntimeAndSharedInfrastructureInTheirApprovedOwners() throws Exception {
        String compose = read("../infra/docker/compose.yml");
        String composeDev = read("../infra/docker/compose.dev.yml");
        String example = read("../infra/docker/.env.example");
        String security = read("payment-service/src/main/java/com/philia/flashsale/payment/security/PaymentSecurityConfiguration.java");

        assertThat(compose).contains("payment_db", "schema-registry", "PAYMENT_ACCEPTANCE_ENABLED",
                "PAYMENT_WEBHOOK_PROCESSING_ENABLED", "networkaddress.cache.ttl=60");
        assertThat(composeDev).contains("PAYMENT_SERVICE_PORT");
        assertThat(example).contains("STRIPE_SECRET_KEY=sk_test_REPLACE_WITH_STRIPE_TEST_SECRET_KEY",
                "STRIPE_WEBHOOK_SECRET=whsec_REPLACE_WITH_STRIPE_TEST_WEBHOOK_SECRET");
        assertThat(security).contains("requestMatchers(\"/actuator/health/**\"")
                .contains("properties.webhookPath()");
        assertThat(Files.exists(ROOT.resolve("../infra/docker/kafka/init-payment-topics.sh"))).isTrue();
        assertThat(Files.exists(ROOT.resolve("../infra/docker/schema-registry/register-payment-schemas.ps1"))).isTrue();
        assertThat(Files.exists(ROOT.resolve("../infra/docker/smoke/feature-021-payment.ps1"))).isTrue();
    }

    @Test
    void smokeAndSchemaScriptsDeclareStrictModeAndVerifiedMarkers() throws Exception {
        String smoke = read("../infra/docker/smoke/feature-021-payment.ps1");
        String schema = read("../infra/docker/schema-registry/register-payment-schemas.ps1");
        String topics = read("../infra/docker/kafka/init-payment-topics.sh");

        assertThat(smoke).contains("Set-StrictMode -Version Latest", "FEATURE_021_SMOKE=PASS",
                "FEATURE_021_FAILURE_MATRIX=PASS");
        assertThat(schema).contains("Set-StrictMode -Version Latest", "PaymentRequestedV1");
        assertThat(topics).contains("set -euo pipefail", "flashsale.payment.commands.v1",
                "flashsale.payment.events.v1");
    }

    @Test
    void parsesPowerShellSmokeAndSchemaScriptsWhenPowerShellIsAvailable() throws Exception {
        assumeTrue(commandAvailable("pwsh"), "pwsh is not installed; static checks still run");
        for (String relative : List.of("../infra/docker/smoke/feature-021-payment.ps1",
                "../infra/docker/schema-registry/register-payment-schemas.ps1")) {
            Process process = new ProcessBuilder("pwsh", "-NoProfile", "-NonInteractive", "-Command",
                    "$null=[System.Management.Automation.Language.Parser]::ParseFile('"
                            + ROOT.resolve(relative).toString().replace("'", "''")
                            + "',[ref]$null,[ref]$null); if($null){exit 1}")
                    .redirectErrorStream(true).start();
            assertThat(process.waitFor(20, TimeUnit.SECONDS)).isTrue();
            assertThat(process.exitValue()).isZero();
        }
    }

    private static String read(String relative) throws IOException {
        return Files.readString(ROOT.resolve(relative));
    }

    private static boolean commandAvailable(String command) {
        try {
            Process process = new ProcessBuilder(command, "-NoProfile", "-Command", "exit 0")
                    .redirectErrorStream(true).start();
            return process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (Exception exception) {
            return false;
        }
    }
}
