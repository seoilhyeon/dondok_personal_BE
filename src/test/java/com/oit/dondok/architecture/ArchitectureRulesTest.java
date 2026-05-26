package com.oit.dondok.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

class ArchitectureRulesTest {

  private final JavaClasses productionClasses =
      new ClassFileImporter()
          .withImportOption(new ImportOption.DoNotIncludeTests())
          .importPackages("com.oit.dondok");

  @Test
  void controllersShouldNotDependOnRepositories() {
    ArchRule rule =
        noClasses()
            .that()
            .haveSimpleNameEndingWith("Controller")
            .should()
            .dependOnClassesThat()
            .haveSimpleNameEndingWith("Repository");

    rule.allowEmptyShould(true).check(productionClasses);
  }

  @Test
  void servicesShouldNotDependOnWebLayer() {
    ArchRule rule =
        noClasses()
            .that()
            .haveSimpleNameEndingWith("Service")
            .should()
            .dependOnClassesThat()
            .haveSimpleNameEndingWith("Controller");

    rule.allowEmptyShould(true).check(productionClasses);
  }

  @Test
  void repositoriesShouldNotDependOnServices() {
    ArchRule rule =
        noClasses()
            .that()
            .haveSimpleNameEndingWith("Repository")
            .should()
            .dependOnClassesThat()
            .haveSimpleNameEndingWith("Service");

    rule.allowEmptyShould(true).check(productionClasses);
  }

  @Test
  void entitiesShouldLiveUnderDomainPackage() {
    ArchRule rule =
        classes()
            .that()
            .areAnnotatedWith("jakarta.persistence.Entity")
            .should()
            .resideInAPackage("..domain..");

    rule.allowEmptyShould(true).check(productionClasses);
  }

  @Test
  void domainShouldNotDependOnWebConfigOrSecurityLayers() {
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..controller..", "..config..", "..security..");

    rule.allowEmptyShould(true).check(productionClasses);
  }
}
