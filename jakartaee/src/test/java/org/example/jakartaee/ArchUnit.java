package org.example.jakartaee;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

public class ArchUnit {

    @Test
    void resourcesShouldDependOnServices() {

        JavaClasses importedClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("org.example.jakartaee");

        ArchRule rule = classes()
                .that().haveSimpleNameEndingWith("Resource")
                .should().onlyHaveDependentClassesThat()
                .haveSimpleNameEndingWith("Service")
                .because("Resources should depend on services");

        rule.check(importedClasses);
    }

    @Test
    void serviceShouldNotDependOnResources() {
        JavaClasses importedClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("org.example.jakartaee");

        ArchRule rule = noClasses()
                .that().haveSimpleNameEndingWith("Service")
                .should()
                .dependOnClassesThat()
                .haveSimpleNameEndingWith("Resource")
                .because("Services should not depend on resources");

        rule.check(importedClasses);
    }
}
