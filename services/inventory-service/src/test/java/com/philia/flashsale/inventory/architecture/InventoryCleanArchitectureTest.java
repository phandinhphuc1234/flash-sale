package com.philia.flashsale.inventory.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Guards the dependency rules selected by Feature 016 without adding an architecture library. */
class InventoryCleanArchitectureTest {

    private static final Path JAVA_ROOT = Path.of("src", "main", "java", "com", "philia",
            "flashsale", "inventory");

    @Test
    void applicationCodeDoesNotDependOnAdaptersOrSpringData() throws IOException {
        List<String> violations = findForbiddenImports("application", List.of(
                "com.philia.flashsale.inventory.stock.adapter.",
                "com.philia.flashsale.inventory.allocation.adapter.",
                "com.philia.flashsale.inventory.movement.adapter.",
                "com.philia.flashsale.inventory.outbox.adapter.",
                "com.philia.flashsale.inventory.regularhold.adapter.",
                "org.springframework.data.domain."
        ));

        assertTrue(violations.isEmpty(), () -> "Application dependency violations:\n"
                + String.join("\n", violations));
    }

    @Test
    void domainCodeRemainsFrameworkIndependent() throws IOException {
        List<String> violations = findForbiddenImports("domain", List.of(
                "org.springframework.",
                "jakarta.persistence.",
                "org.hibernate.",
                "org.mapstruct.",
                "org.apache.kafka."
        ));

        assertTrue(violations.isEmpty(), () -> "Domain dependency violations:\n"
                + String.join("\n", violations));
    }

    private List<String> findForbiddenImports(String boundary, List<String> forbiddenImports)
            throws IOException {
        List<String> violations = new ArrayList<>();
        try (var paths = Files.walk(JAVA_ROOT)) {
            for (Path path : paths.filter(this::isJavaSource).toList()) {
                String normalizedPath = path.toString().replace('\\', '/');
                if (!normalizedPath.contains("/" + boundary + "/")) {
                    continue;
                }
                String source = Files.readString(path);
                forbiddenImports.stream()
                        .filter(source::contains)
                        .map(forbidden -> normalizedPath + " imports " + forbidden)
                        .forEach(violations::add);
            }
        }
        return violations;
    }

    private boolean isJavaSource(Path path) {
        return Files.isRegularFile(path) && path.toString().endsWith(".java");
    }
}
