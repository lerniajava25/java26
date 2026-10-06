package org.example.jakartaee;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

public class CleanArchitectureTest {

    @Test
    @DisplayName("Clean Architecture verification")
    void cleanArchitectureLayersAreRespected() {

        ArchRule rule = layeredArchitecture()
                .consideringAllDependencies()
                .layer("Presentation").definedBy("..presentation..")
                .layer("Domain").definedBy("..domain..")
                .layer("Application").definedBy("..application..")
                .layer("Infrastructure").definedBy("..infrastructure..")

                .whereLayer("Presentation").mayNotBeAccessedByAnyLayer()
                .whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer()
                .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure")
                .whereLayer("Application").mayOnlyBeAccessedByLayers("Presentation", "Infrastructure");

        JavaClasses importedClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("org.example.jakartaee");

        rule.check(importedClasses);
    }
}
