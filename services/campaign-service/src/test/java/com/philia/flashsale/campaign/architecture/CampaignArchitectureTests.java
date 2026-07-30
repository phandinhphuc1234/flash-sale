package com.philia.flashsale.campaign.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Keeps the package-by-feature boundaries executable without adding an architecture dependency.
 *
 * <p>These checks intentionally scan imports rather than class names: the rule is about source
 * dependency direction, not about forcing every future feature to use every possible package.</p>
 */
class CampaignArchitectureTests {

    private static final Path JAVA_ROOT = Path.of(
            "src", "main", "java", "com", "philia", "flashsale", "campaign");

    @Test
    void applicationCodeDoesNotDependOnAdaptersOrFrameworkStorage() throws IOException {
        List<String> violations = findForbiddenImports("application", List.of(
                "com.philia.flashsale.campaign.campaign.adapter.",
                "com.philia.flashsale.campaign.scheduleoperation.adapter.",
                "com.philia.flashsale.campaign.outbox.adapter.",
                "org.springframework.data.",
                "jakarta.persistence.",
                "org.hibernate."));

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
                "org.apache.kafka.",
                "com.fasterxml.jackson.",
                "jakarta.servlet."));

        assertTrue(violations.isEmpty(), () -> "Domain dependency violations:\n"
                + String.join("\n", violations));
    }

    @Test
    void featureAdaptersDoNotImportAnotherFeatureAdapter() throws IOException {
        List<String> violations = new ArrayList<>();
        violations.addAll(findForbiddenImportsUnder("campaign/adapter", List.of(
                "com.philia.flashsale.campaign.scheduleoperation.adapter.",
                "com.philia.flashsale.campaign.outbox.adapter.")));
        violations.addAll(findForbiddenImportsUnder("scheduleoperation/adapter", List.of(
                "com.philia.flashsale.campaign.campaign.adapter.",
                "com.philia.flashsale.campaign.outbox.adapter.")));
        violations.addAll(findForbiddenImportsUnder("outbox/adapter", List.of(
                "com.philia.flashsale.campaign.campaign.adapter.",
                "com.philia.flashsale.campaign.scheduleoperation.adapter.")));

        assertTrue(violations.isEmpty(), () -> "Cross-feature adapter violations:\n"
                + String.join("\n", violations));
    }

    private List<String> findForbiddenImports(String boundary, List<String> forbiddenImports)
            throws IOException {
        return findForbiddenImportsUnder(boundary, forbiddenImports);
    }

    private List<String> findForbiddenImportsUnder(
            String pathFragment, List<String> forbiddenImports) throws IOException {
        List<String> violations = new ArrayList<>();
        if (!Files.exists(JAVA_ROOT)) {
            return violations;
        }
        try (var paths = Files.walk(JAVA_ROOT)) {
            for (Path path : paths.filter(this::isJavaSource).toList()) {
                String normalizedPath = path.toString().replace('\\', '/');
                String normalizedRoot = JAVA_ROOT.toString().replace('\\', '/');
                String relativePath = normalizedPath.startsWith(normalizedRoot)
                        ? normalizedPath.substring(normalizedRoot.length() + 1)
                        : normalizedPath;
                if (!relativePath.contains(pathFragment)) {
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
