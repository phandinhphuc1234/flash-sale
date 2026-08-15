package com.philia.flashsale.order.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.philia.flashsale.order.OrderServiceApplication;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/** Executable Clean/Hexagonal dependency guards for the Order service. */
class OrderArchitectureTests {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .importPackagesOf(OrderServiceApplication.class);

    @Test
    void domainAndApplicationDoNotImportFrameworkOrTransportTypes() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("..order.domain..", "..order.application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "org.hibernate..",
                        "org.apache.kafka..",
                        "org.apache.avro..",
                        "com.fasterxml.jackson..",
                        "com.philia.flashsale.contract..",
                        "..order.adapter..");
        rule.allowEmptyShould(true).check(CLASSES);
    }

    @Test
    void inboundAndOutboundAdaptersDoNotDependOnEachOther() {
        noClasses().that().resideInAnyPackage("..order.adapter.in..")
                .should().dependOnClassesThat().resideInAnyPackage("..order.adapter.out..")
                .allowEmptyShould(true)
                .check(CLASSES);
        noClasses().that().resideInAnyPackage("..order.adapter.out..")
                .should().dependOnClassesThat().resideInAnyPackage("..order.adapter.in..")
                .allowEmptyShould(true)
                .check(CLASSES);
    }
}
