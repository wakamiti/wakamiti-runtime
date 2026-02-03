/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package es.wakamiti.service.test;


import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.assertj.core.api.Assertions.assertThat;


/**
 * Architectural tests for the wakamiti-service project using ArchUnit.
 * Ensures the project follows Hexagonal Architecture principles and naming conventions.
 */
@AnalyzeClasses(
        packages = "es.wakamiti.service",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class ArchitectureTest {

    /**
     * Verifies the layered architecture of the project.
     */
    @ArchTest
    static final ArchRule packages = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .optionalLayer("adapters").definedBy("..adapters..")
            .layer("application").definedBy("..application..")
            .optionalLayer("domain.api").definedBy("..domain.api..")
            .optionalLayer("domain.spi").definedBy("..domain.spi..")
            .optionalLayer("domain.model").definedBy("..domain.model..")
            .optionalLayer("domain.logic").definedBy("..domain.service..")
            .layer("infrastructure").definedBy("..infrastructure..")
            .whereLayer("application").mayNotBeAccessedByAnyLayer()
            .whereLayer("domain.api").mayOnlyBeAccessedByLayers(
                    "domain.logic", "adapters", "application", "infrastructure")
            .whereLayer("domain.spi").mayOnlyBeAccessedByLayers(
                    "domain.logic", "adapters", "application", "infrastructure")
            .whereLayer("domain.model").mayOnlyBeAccessedByLayers(
                    "domain.api", "domain.spi", "domain.logic", "adapters", "application", "infrastructure")
            .whereLayer("domain.logic").mayNotBeAccessedByAnyLayer()
            .whereLayer("infrastructure").mayNotBeAccessedByAnyLayer();

    /**
     * Domain layer should be independent of application and infrastructure layers.
     */
    @ArchTest
    static final ArchRule domainShouldBeIndependent = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAPackage("..application..")
            .orShould().dependOnClassesThat()
            .resideInAPackage("..infrastructure..");

    /**
     * Domain layer should not depend on external frameworks to remain pure business logic.
     */
    @ArchTest
    static final ArchRule domainShouldNotDependOnExternalFrameworks = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                    "io.helidon..",
                    "jakarta..",
                    "org.slf4j..",
                    "org.eclipse.microprofile.."
            )
            .as("Domain should not depend on external frameworks like Helidon, Jakarta, SLF4J or MicroProfile");

    @ArchTest
    void archunit_must_import_classes(JavaClasses importedClasses) {
        assertFalse(importedClasses.isEmpty(),
                    "ArchUnit did not import any production classes. Check the “es.wakamiti.service” " +
                            "package and the Surefire configuration (useModulePath=false). ");
    }

    /**
     * Rule 1: The application layer can only depend on the domain and itself.
     * This ensures that the application logic is not coupled to anything external, except for the business core.
     * Dependencies on standard Java libraries and jakarta/io.helidon/slf4j (allowed for this project) are allowed.
     *
     * Note: Dependency on infrastructure or domain implementations is forbidden by the layeredArchitecture rule.
     */
    @ArchTest
    static final ArchRule applicationShouldOnlyDependOnDomain = classes()
            .that().resideInAPackage("..application..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage(
                    "..application..",
                    "..domain..",
                    "java..",
                    "jakarta..",
                    "io.helidon..",
                    "org.slf4j.."
            );

    /**
     * Rule 2: Naming convention for application services.
     * Ensures that classes implementing use cases follow a consistent pattern
     * and are in the correct package.
     */
    @ArchTest
    static final ArchRule applicationServicesShouldBeNamedCorrectly = classes()
            .that().resideInAPackage("..application.service..")
            .and().areNotInterfaces()
            .should().haveSimpleNameEndingWith("ServiceImpl")
            .as("Application service implementations should end with 'ServiceImpl' and be in the 'service' package");

    /**
     * Rule 3: Domain API (Input ports) and SPI (Output ports) must be interfaces.
     */
    @ArchTest
    static final ArchRule domainPortsShouldBeInterfaces = classes()
            .that().resideInAPackage("..domain.api..")
            .or().resideInAPackage("..domain.spi..")
            .should().beInterfaces()
            .as("Domain API and SPI must be interfaces");

    /**
     * Rule 4: Input adapters should not directly access output adapters.
     * In this project we use infrastructure.webservice as input and others as output.
     */
    @ArchTest
    static final ArchRule webAdaptersShouldNotAccessOtherInfrastructure = noClasses()
            .that().resideInAPackage("..infrastructure.webservice..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..infrastructure.exec..", "..infrastructure.logging..");

    /**
     * Rule 5: Convention for REST resources.
     */
    @ArchTest
    static final ArchRule resourcesShouldFollowConvention = classes()
            .that().resideInAPackage("..infrastructure.webservice.http..")
            .should().haveSimpleNameEndingWith("Resource")
            .as("REST resources must end with 'Resource'");

    /**
     * Rule 6: CDI configuration or internal infrastructure classes.
     */
    @ArchTest
    static final ArchRule configClassesShouldBeInConfigPackage = classes()
            .that().resideInAPackage("..infrastructure.config..")
            .should().haveSimpleNameEndingWith("Configurator")
            .orShould().haveSimpleNameEndingWith("Provider")
            .as("Configuration classes must be in the 'config' package and follow naming conventions");

    /**
     * Rule 7: Prevent multiple entry points.
     */
    @Test
    void aSingleApplicationShouldExist() {
        JavaClasses importedClasses = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("es.wakamiti.service");

        List<JavaClass> applicationClasses = importedClasses.stream()
                .filter(javaClass -> javaClass.getSimpleName().endsWith("Application"))
                .toList();

        assertThat(applicationClasses).hasSize(1);

        JavaClass mainClass = applicationClasses.getFirst();
        assertThat(mainClass.getSimpleName()).isEqualTo("WakamitiServiceApplication");
        assertThat(mainClass.getPackageName()).isEqualTo("es.wakamiti.service");
    }

}
