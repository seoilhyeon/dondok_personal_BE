package com.oit.dondok.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.constructors;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMember;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ArchitectureRulesTest {

  private static final String ENTITY = "jakarta.persistence.Entity";
  private static final String TABLE = "jakarta.persistence.Table";
  private static final String COLUMN = "jakarta.persistence.Column";
  private static final String BUILDER = "lombok.Builder";
  private static final String DATA = "lombok.Data";
  private static final String GENERATED = "lombok.Generated";
  private static final String TRANSACTIONAL =
      "org.springframework.transaction.annotation.Transactional";
  private static final Pattern SNAKE_CASE = Pattern.compile("^[a-z][a-z0-9]*(_[a-z0-9]+)*$");
  private static final Pattern DOMAIN_REQUEST_DTO_PACKAGE =
      Pattern.compile(".*\\.domain\\.[^.]+\\.dto\\.request(\\..*)?");
  private static final Pattern DOMAIN_RESPONSE_DTO_PACKAGE =
      Pattern.compile(".*\\.domain\\.[^.]+\\.dto\\.response(\\..*)?");
  private static final Set<String> ALLOWED_DOMAIN_LAYERS =
      Set.of("controller", "service", "repository", "entity", "dto");
  private static final List<String> MAPPING_ANNOTATIONS =
      List.of(
          "org.springframework.web.bind.annotation.RequestMapping",
          "org.springframework.web.bind.annotation.GetMapping",
          "org.springframework.web.bind.annotation.PostMapping",
          "org.springframework.web.bind.annotation.PutMapping",
          "org.springframework.web.bind.annotation.PatchMapping",
          "org.springframework.web.bind.annotation.DeleteMapping");
  private static final List<String> MONEY_TERMS =
      List.of("amount", "price", "fee", "cost", "balance", "settlement");
  private static final List<String> QUERY_METHOD_PREFIXES =
      List.of("find", "get", "read", "search", "count", "exists");
  private static final List<String> WRITE_INTENT_QUERY_MARKERS =
      List.of("orCreate", "andCreate", "orUpdate", "andUpdate", "orDelete", "andDelete");

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
            .resideInAnyPackage(
                "..controller..",
                "..web..",
                "org.springframework.web..",
                "jakarta.servlet..",
                "javax.servlet..");

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
            .resideInAPackage("..domain..entity..");

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
            .resideInAnyPackage(
                "..controller..",
                "..config..",
                "..security..",
                "..web..",
                "..dto.request..",
                "..dto.response..",
                "org.springframework.web..",
                "jakarta.servlet..",
                "javax.servlet..");

    rule.allowEmptyShould(true).check(productionClasses);
  }

  @Test
  void domainClassesShouldUseDocumentedLayerPackages() {
    ArchRule rule =
        classes().that().resideInAPackage("..domain..").should(useDocumentedDomainLayerPackages());

    rule.allowEmptyShould(true).check(productionClasses);
  }

  @Test
  void springComponentsShouldFollowLayerPackageAndNamingConventions() {
    ArchRule controllers =
        classes()
            .that()
            .areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
            .or()
            .areAnnotatedWith("org.springframework.stereotype.Controller")
            .should()
            .resideInAPackage("..controller..")
            .andShould()
            .haveSimpleNameEndingWith("Controller");

    ArchRule services =
        classes()
            .that()
            .areAnnotatedWith("org.springframework.stereotype.Service")
            .should()
            .resideInAPackage("..service..")
            .andShould()
            .haveSimpleNameEndingWith("Service");

    ArchRule repositories =
        classes()
            .that(repositoryClasses())
            .should()
            .resideInAPackage("..repository..")
            .andShould()
            .haveSimpleNameEndingWith("Repository");

    controllers.allowEmptyShould(true).check(productionClasses);
    services.allowEmptyShould(true).check(productionClasses);
    repositories.allowEmptyShould(true).check(productionClasses);
  }

  @Test
  void dtoClassesShouldFollowRequestAndResponsePackageNamingConventions() {
    ArchRule requestPackages =
        classes().that(domainRequestDtoClasses()).should().haveSimpleNameEndingWith("Request");

    ArchRule responsePackages =
        classes().that(domainResponseDtoClasses()).should().haveSimpleNameEndingWith("Response");

    requestPackages.allowEmptyShould(true).check(productionClasses);
    responsePackages.allowEmptyShould(true).check(productionClasses);
  }

  @Test
  void controllersShouldNotDependOnEntities() {
    ArchRule rule =
        noClasses()
            .that()
            .haveSimpleNameEndingWith("Controller")
            .should()
            .dependOnClassesThat()
            .areAnnotatedWith(ENTITY);

    rule.allowEmptyShould(true).check(productionClasses);
  }

  @Test
  void requestMappingPathsShouldUseLowercaseCharactersOutsidePathVariables() {
    ArchRule rule =
        classes()
            .that()
            .haveSimpleNameEndingWith("Controller")
            .should(useLowercaseRequestMappingPaths());

    rule.allowEmptyShould(true).check(productionClasses);
  }

  @Test
  void lombokDataShouldNotBeUsed() {
    ArchRule rule = noClasses().should().beAnnotatedWith(DATA);

    rule.check(productionClasses);
  }

  @Test
  void entitiesShouldNotUseBuilderAndShouldPreferNonPublicConstructors() {
    ArchRule noEntityBuilders =
        noClasses()
            .that()
            .areAnnotatedWith(ENTITY)
            .or()
            .resideInAPackage("..domain..entity..")
            .should()
            .beAnnotatedWith(BUILDER);

    ArchRule noPublicEntityConstructors =
        constructors()
            .that()
            .areDeclaredInClassesThat()
            .areAnnotatedWith(ENTITY)
            .should()
            .notBePublic();

    noEntityBuilders.allowEmptyShould(true).check(productionClasses);
    noPublicEntityConstructors.allowEmptyShould(true).check(productionClasses);
  }

  @Test
  void entityPublicInstanceMethodsShouldBeAccessorsOnly() {
    ArchRule rule =
        classes()
            .that()
            .areAnnotatedWith(ENTITY)
            .should(declareOnlyAccessorPublicInstanceMethods());

    rule.allowEmptyShould(true).check(productionClasses);
  }

  @Test
  void databaseMappingNamesShouldUseSnakeCaseWhenExplicitlyDeclared() {
    ArchRule rule =
        classes().that().areAnnotatedWith(ENTITY).should(useSnakeCaseTableAndColumnNames());

    rule.allowEmptyShould(true).check(productionClasses);
  }

  @Test
  void serviceQueryMethodsShouldBeReadOnlyTransactional() {
    ArchRule rule =
        classes()
            .that()
            .haveSimpleNameEndingWith("Service")
            .should(declareReadOnlyTransactionsForQueryMethods());

    rule.allowEmptyShould(true).check(productionClasses);
  }

  @Test
  void moneyLikeMembersShouldNotUseFloatingPointTypes() {
    ArchRule rule = classes().should(notUseFloatingPointTypesForMoneyLikeMembers());

    rule.allowEmptyShould(true).check(productionClasses);
  }

  private static DescribedPredicate<JavaClass> repositoryClasses() {
    return new DescribedPredicate<>("repository classes") {
      @Override
      public boolean test(JavaClass javaClass) {
        return javaClass.isAnnotatedWith("org.springframework.stereotype.Repository")
            || javaClass.isAssignableTo("org.springframework.data.repository.Repository");
      }
    };
  }

  private static DescribedPredicate<JavaClass> domainRequestDtoClasses() {
    return new DescribedPredicate<>("top-level domain request DTO classes") {
      @Override
      public boolean test(JavaClass javaClass) {
        return isTopLevelSourceClass(javaClass)
            && DOMAIN_REQUEST_DTO_PACKAGE.matcher(javaClass.getPackageName()).matches();
      }
    };
  }

  private static DescribedPredicate<JavaClass> domainResponseDtoClasses() {
    return new DescribedPredicate<>("top-level domain response DTO classes") {
      @Override
      public boolean test(JavaClass javaClass) {
        return isTopLevelSourceClass(javaClass)
            && DOMAIN_RESPONSE_DTO_PACKAGE.matcher(javaClass.getPackageName()).matches();
      }
    };
  }

  private static boolean isTopLevelSourceClass(JavaClass javaClass) {
    return !javaClass.getName().contains("$") && !javaClass.isAnnotatedWith(GENERATED);
  }

  private static ArchCondition<JavaClass> useDocumentedDomainLayerPackages() {
    return new ArchCondition<>("use documented domain layer packages") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        String packageName = javaClass.getPackageName();
        int domainIndex = packageName.indexOf(".domain.");
        if (domainIndex < 0) {
          return;
        }

        String afterDomain = packageName.substring(domainIndex + ".domain.".length());
        String[] parts = afterDomain.split("\\.");
        boolean valid = parts.length >= 2 && ALLOWED_DOMAIN_LAYERS.contains(parts[1]);
        if (!valid) {
          events.add(
              SimpleConditionEvent.violated(
                  javaClass,
                  javaClass.getName()
                      + " should reside under domain/{domain}/"
                      + ALLOWED_DOMAIN_LAYERS));
        }
      }
    };
  }

  private static ArchCondition<JavaClass> useLowercaseRequestMappingPaths() {
    return new ArchCondition<>("use lowercase request mapping paths") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        checkMappingAnnotations(javaClass, javaClass.getName(), javaClass.getAnnotations(), events);
        javaClass
            .getMethods()
            .forEach(
                method ->
                    checkMappingAnnotations(
                        method, method.getFullName(), method.getAnnotations(), events));
      }
    };
  }

  private static void checkMappingAnnotations(
      Object owner,
      String ownerName,
      Set<? extends JavaAnnotation<?>> annotations,
      ConditionEvents events) {
    for (JavaAnnotation<?> annotation : annotations) {
      if (!MAPPING_ANNOTATIONS.contains(annotation.getRawType().getName())) {
        continue;
      }

      for (String path : annotationValues(annotation, "value", "path")) {
        String pathWithoutVariables = path.replaceAll("\\{[^}]*}", "");
        if (!pathWithoutVariables.equals(pathWithoutVariables.toLowerCase(Locale.ROOT))) {
          events.add(
              SimpleConditionEvent.violated(
                  owner, ownerName + " has non-lowercase request path " + path));
        }
      }
    }
  }

  private static ArchCondition<JavaClass> declareOnlyAccessorPublicInstanceMethods() {
    return new ArchCondition<>("declare only accessor public instance methods") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        for (JavaMethod method : javaClass.getMethods()) {
          if (!method.getModifiers().contains(JavaModifier.PUBLIC)
              || method.getModifiers().contains(JavaModifier.STATIC)
              || isAccessor(method)) {
            continue;
          }

          events.add(
              SimpleConditionEvent.violated(
                  method,
                  method.getFullName()
                      + " is a public entity instance method that is not an accessor"));
        }
      }
    };
  }

  private static boolean isAccessor(JavaMethod method) {
    String name = method.getName();
    return method.getRawParameterTypes().isEmpty()
        && (name.startsWith("get") || name.startsWith("is"));
  }

  private static ArchCondition<JavaClass> useSnakeCaseTableAndColumnNames() {
    return new ArchCondition<>("use snake_case table and column names") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        javaClass
            .tryGetAnnotationOfType(TABLE)
            .ifPresent(
                table ->
                    annotationValues(table, "name")
                        .forEach(
                            tableName -> checkSnakeCase(javaClass, tableName, "table", events)));

        for (JavaField field : javaClass.getFields()) {
          Optional<JavaAnnotation<JavaField>> column = field.tryGetAnnotationOfType(COLUMN);
          if (column.isEmpty()) {
            continue;
          }
          for (String columnName : annotationValues(column.get(), "name")) {
            checkSnakeCase(field, columnName, "column", events);
          }
        }
      }
    };
  }

  private static void checkSnakeCase(
      JavaMember member, String value, String mappingType, ConditionEvents events) {
    if (!value.isBlank() && !SNAKE_CASE.matcher(value).matches()) {
      events.add(
          SimpleConditionEvent.violated(
              member,
              member.getFullName() + " has non-snake_case " + mappingType + " name " + value));
    }
  }

  private static void checkSnakeCase(
      JavaClass javaClass, String value, String mappingType, ConditionEvents events) {
    if (!value.isBlank() && !SNAKE_CASE.matcher(value).matches()) {
      events.add(
          SimpleConditionEvent.violated(
              javaClass,
              javaClass.getName() + " has non-snake_case " + mappingType + " name " + value));
    }
  }

  private static ArchCondition<JavaClass> declareReadOnlyTransactionsForQueryMethods() {
    return new ArchCondition<>("declare read-only transactions for query methods") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        for (JavaMethod method : javaClass.getMethods()) {
          if (method.getModifiers().contains(JavaModifier.STATIC)
              || !method.getModifiers().contains(JavaModifier.PUBLIC)
              || !isQueryMethod(method)) {
            continue;
          }
          if (!hasReadOnlyTransactional(method) && !hasReadOnlyTransactional(javaClass)) {
            events.add(
                SimpleConditionEvent.violated(
                    method,
                    method.getFullName() + " should declare @Transactional(readOnly = true)"));
          }
        }
      }
    };
  }

  private static boolean isQueryMethod(JavaMethod method) {
    String name = method.getName();
    String lowerName = name.toLowerCase();
    return QUERY_METHOD_PREFIXES.stream().anyMatch(name::startsWith)
        && WRITE_INTENT_QUERY_MARKERS.stream()
            .map(String::toLowerCase)
            .noneMatch(lowerName::contains);
  }

  private static boolean hasReadOnlyTransactional(JavaMethod method) {
    return method
        .tryGetAnnotationOfType(TRANSACTIONAL)
        .flatMap(annotation -> annotation.get("readOnly"))
        .filter(Boolean.TRUE::equals)
        .isPresent();
  }

  private static boolean hasReadOnlyTransactional(JavaClass javaClass) {
    return javaClass
        .tryGetAnnotationOfType(TRANSACTIONAL)
        .flatMap(annotation -> annotation.get("readOnly"))
        .filter(Boolean.TRUE::equals)
        .isPresent();
  }

  private static ArchCondition<JavaClass> notUseFloatingPointTypesForMoneyLikeMembers() {
    return new ArchCondition<>("not use floating point types for money-like members") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        for (JavaField field : javaClass.getFields()) {
          if (isMoneyLike(field.getName()) && isFloatingPoint(field.getRawType())) {
            events.add(
                SimpleConditionEvent.violated(
                    field,
                    field.getFullName() + " should use BigDecimal instead of floating point"));
          }
        }

        for (JavaMethod method : javaClass.getMethods()) {
          if (isMoneyLike(method.getName()) && isFloatingPoint(method.getRawReturnType())) {
            events.add(
                SimpleConditionEvent.violated(
                    method,
                    method.getFullName() + " should return BigDecimal instead of floating point"));
          }
          checkFloatingPointParameters(method, events);
        }

        for (JavaConstructor constructor : javaClass.getConstructors()) {
          checkFloatingPointParameters(constructor, events);
        }
      }
    };
  }

  private static void checkFloatingPointParameters(JavaCodeUnit codeUnit, ConditionEvents events) {
    if (!isMoneyLike(codeUnit.getName())) {
      return;
    }
    for (JavaClass parameterType : codeUnit.getRawParameterTypes()) {
      if (isFloatingPoint(parameterType)) {
        events.add(
            SimpleConditionEvent.violated(
                codeUnit,
                codeUnit.getFullName() + " should use BigDecimal instead of floating point"));
      }
    }
  }

  private static boolean isMoneyLike(String name) {
    String lowerName = name.toLowerCase(Locale.ROOT);
    return MONEY_TERMS.stream().anyMatch(lowerName::contains);
  }

  private static boolean isFloatingPoint(JavaClass type) {
    return type.isEquivalentTo(double.class)
        || type.isEquivalentTo(Double.class)
        || type.isEquivalentTo(float.class)
        || type.isEquivalentTo(Float.class);
  }

  private static List<String> annotationValues(JavaAnnotation<?> annotation, String... names) {
    return List.of(names).stream()
        .flatMap(name -> annotation.get(name).stream())
        .flatMap(ArchitectureRulesTest::flattenAnnotationValue)
        .map(String::trim)
        .filter(value -> !value.isEmpty())
        .toList();
  }

  private static java.util.stream.Stream<String> flattenAnnotationValue(Object value) {
    if (value instanceof String string) {
      return java.util.stream.Stream.of(string);
    }
    if (value instanceof Collection<?> collection) {
      return collection.stream().flatMap(ArchitectureRulesTest::flattenAnnotationValue);
    }
    if (value != null && value.getClass().isArray()) {
      return java.util.stream.IntStream.range(0, Array.getLength(value))
          .mapToObj(index -> Array.get(value, index))
          .flatMap(ArchitectureRulesTest::flattenAnnotationValue);
    }
    return java.util.stream.Stream.empty();
  }
}
