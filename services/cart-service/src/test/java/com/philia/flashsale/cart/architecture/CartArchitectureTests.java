package com.philia.flashsale.cart.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Keeps the Cart core independent from transport, framework, and persistence details.
 *
 * <p>The rules intentionally cover the domain and application packages together: both layers are
 * inward-facing and adapters/configuration are the only places allowed to depend on providers.
 */
@AnalyzeClasses(packages = "com.philia.flashsale.cart")
class CartArchitectureTests {

    @ArchTest
    static final ArchRule core_must_not_depend_on_adapters_or_frameworks = noClasses()
            .that()
            .resideInAnyPackage("..cart.domain..", "..cart.application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "..cart.adapter..",
                    "org.springframework..",
                    "jakarta.persistence..",
                    "jakarta.servlet..",
                    "feign..",
                    "org.springframework.cloud.openfeign..")
            .because("Cart domain and application code must depend on business ports, not adapters or providers")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule domain_must_remain_framework_free = noClasses()
            .that()
            .resideInAnyPackage("..cart.domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "org.springframework..",
                    "jakarta..",
                    "com.fasterxml.jackson..",
                    "feign..")
            .because("Cart domain models and invariants must remain plain Java")
            .allowEmptyShould(true);
}
