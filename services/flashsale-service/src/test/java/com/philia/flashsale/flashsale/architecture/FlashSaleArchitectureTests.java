package com.philia.flashsale.flashsale.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Executable import-direction checks for Feature 019's package-by-feature foundation. */
class FlashSaleArchitectureTests {
    private static final Path JAVA_ROOT = Path.of(
            "src", "main", "java", "com", "philia", "flashsale", "flashsale");

    @Test
    void domainAndApplicationRemainIndependentOfAdaptersAndProviders() throws IOException {
        List<String> violations = new ArrayList<>();
        violations.addAll(findForbiddenImportsUnder("domain", List.of(
                "org.springframework.", "jakarta.persistence.", "org.hibernate.",
                "org.apache.kafka.", "org.springframework.data.redis.", "org.springframework.cloud.openfeign.",
                "feign.", "com.fasterxml.jackson.")));
        violations.addAll(findForbiddenImportsUnder("application", List.of(
                ".adapter.", "org.springframework.data.", "jakarta.persistence.", "org.apache.kafka.",
                "org.springframework.data.redis.", "org.springframework.cloud.openfeign.", "feign.")));
        assertTrue(violations.isEmpty(), () -> "Clean/Hex violations:\n" + String.join("\n", violations));
    }

    @Test
    void providerImportsStayAtConfigurationOrAdapterBoundaries() throws IOException {
        List<String> violations = new ArrayList<>();
        try (var paths = Files.walk(JAVA_ROOT)) {
            for (Path path : paths.filter(this::isJavaSource).toList()) {
                String source = Files.readString(path);
                String normalized = path.toString().replace('\\', '/');
                if (source.contains("org.apache.kafka.") && !normalized.contains("/adapter/")
                        && !normalized.contains("/configuration/")) {
                    violations.add(normalized + " imports Kafka outside an adapter/configuration boundary");
                }
                if (source.contains("org.springframework.data.redis.") && !normalized.contains("/adapter/")
                        && !normalized.contains("/configuration/")) {
                    violations.add(normalized + " imports Redis outside an adapter/configuration boundary");
                }
            }
        }
        assertTrue(violations.isEmpty(), () -> "Provider boundary violations:\n" + String.join("\n", violations));
    }

    private List<String> findForbiddenImportsUnder(String boundary, List<String> forbidden) throws IOException {
        List<String> violations = new ArrayList<>();
        if (!Files.exists(JAVA_ROOT)) {
            return violations;
        }
        try (var paths = Files.walk(JAVA_ROOT)) {
            for (Path path : paths.filter(this::isJavaSource).toList()) {
                String normalized = path.toString().replace('\\', '/');
                if (!normalized.contains("/" + boundary + "/")) {
                    continue;
                }
                String source = Files.readString(path);
                forbidden.stream().filter(source::contains)
                        .map(item -> normalized + " imports " + item).forEach(violations::add);
            }
        }
        return violations;
    }

    private boolean isJavaSource(Path path) {
        return Files.isRegularFile(path) && path.toString().endsWith(".java");
    }
}
