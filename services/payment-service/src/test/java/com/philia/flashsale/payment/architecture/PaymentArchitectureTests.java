package com.philia.flashsale.payment.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.philia.flashsale.payment.PaymentServiceApplication;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/** Executable Clean/Hexagonal dependency guards for the Payment service. */
class PaymentArchitectureTests {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .importPackagesOf(PaymentServiceApplication.class);

    @Test
    void domainAndApplicationDoNotImportFrameworkOrProviderTypes() {
        ArchRule domainRule = noClasses()
                .that().resideInAPackage("..payment.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "org.apache.kafka..",
                        "org.apache.avro..",
                        "com.stripe..",
                        "io.micrometer..",
                        "org.mapstruct..");
        domainRule.allowEmptyShould(true).check(CLASSES);

        ArchRule applicationRule = noClasses()
                .that().resideInAPackage("..payment.application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..payment.adapter..",
                        "org.springframework..",
                        "jakarta.persistence..",
                        "org.apache.kafka..",
                        "org.apache.avro..",
                        "com.stripe..");
        applicationRule.allowEmptyShould(true).check(CLASSES);
    }

    @Test
    void inboundAndOutboundAdaptersDoNotDependOnEachOther() {
        noClasses().that().resideInAnyPackage("..payment.adapter.in..")
                .should().dependOnClassesThat().resideInAnyPackage("..payment.adapter.out..")
                .allowEmptyShould(true)
                .check(CLASSES);
        noClasses().that().resideInAnyPackage("..payment.adapter.out..")
                .should().dependOnClassesThat().resideInAnyPackage("..payment.adapter.in..")
                .allowEmptyShould(true)
                .check(CLASSES);
    }
}
