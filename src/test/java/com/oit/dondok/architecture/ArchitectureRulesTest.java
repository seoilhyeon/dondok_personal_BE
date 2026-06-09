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
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ArchitectureRulesTest {

  // docs/convention/code-convention.md 중 정적 구조로 확인 가능한 규칙만 ArchUnit에서 검증한다.
  // HTTP 응답 body 모양, timestamp 같은 런타임 API 계약은 MVC/API 테스트에서 다룬다.
  private static final String ENTITY = "jakarta.persistence.Entity";
  private static final String BUILDER = "lombok.Builder";
  private static final String DATA = "lombok.Data";
  private static final String SETTER = "lombok.Setter";
  private static final String GENERATED = "lombok.Generated";
  private static final String TRANSACTIONAL =
      "org.springframework.transaction.annotation.Transactional";
  private static final Pattern SNAKE_CASE = Pattern.compile("^[a-z][a-z0-9]*(_[a-z0-9]+)*$");
  private static final Pattern INDEX_ORDER_SUFFIX = Pattern.compile("\\s+(?i:ASC|DESC)$");
  private static final Pattern DOMAIN_REQUEST_DTO_PACKAGE =
      Pattern.compile(".*\\.domain\\.[^.]+\\.dto\\.request(\\..*)?");
  private static final Pattern DOMAIN_RESPONSE_DTO_PACKAGE =
      Pattern.compile(".*\\.domain\\.[^.]+\\.dto\\.response(\\..*)?");
  private static final Pattern MEMBER_RESPONSE_DTO_PACKAGE =
      Pattern.compile(".*\\.domain\\.member\\.dto\\.response(\\..*)?");
  private static final Pattern DISALLOWED_RESPONSE_MEMBER_IDENTIFIER =
      Pattern.compile("^.*(?:Member|User)(?:Id|ID|No|Pk|Seq|Number|Identifier)$");
  private static final Set<String> ALLOWED_DOMAIN_LAYERS =
      Set.of(
          "controller", "service", "repository", "entity", "dto", "exception", "port", "scheduler");
  private static final List<String> MAPPING_ANNOTATIONS =
      List.of(
          "org.springframework.web.bind.annotation.RequestMapping",
          "org.springframework.web.bind.annotation.GetMapping",
          "org.springframework.web.bind.annotation.PostMapping",
          "org.springframework.web.bind.annotation.PutMapping",
          "org.springframework.web.bind.annotation.PatchMapping",
          "org.springframework.web.bind.annotation.DeleteMapping");
  private static final List<String> MONEY_TERMS =
      List.of(
          "amount",
          "price",
          "fee",
          "cost",
          "balance",
          "settlement",
          "refund",
          "deposit",
          "point",
          "share",
          "ratio",
          "locked",
          "reserved",
          "available");
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
    // Controller는 요청/응답 조립까지만 담당하고 DB 접근은 Service를 통해 수행한다.
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
    // Service는 HTTP, Servlet, Controller 타입을 모르는 순수 애플리케이션 경계로 유지한다.
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
    // Repository는 영속성 어댑터다. 비즈니스 흐름을 가진 Service를 역참조하지 않는다.
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
    // JPA Entity는 도메인별 entity 레이어 아래에만 둔다.
    ArchRule rule =
        classes()
            .that()
            .areAnnotatedWith("jakarta.persistence.Entity")
            .should()
            .resideInAPackage("..domain..entity..");

    rule.allowEmptyShould(true).check(productionClasses);
  }

  @Test
  void entitiesShouldNotDependOnWebConfigSecurityOrApiLayers() {
    // Entity/순수 도메인 모델은 API 입출력, Web, Config, Security 경계를 의존하지 않는다.
    // Domain Service까지 막는 규칙이 아니므로 범위를 entity/순수 모델로 좁혀 둔다.
    ArchRule rule =
        noClasses()
            .that()
            .areAnnotatedWith(ENTITY)
            .or()
            .resideInAPackage("..domain..entity..")
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
  void domainExceptionsShouldNotDependOnDomainImplementationLayers() {
    // Domain Exception은 여러 레이어에서 던질 수 있는 공용 신호이므로 구현 레이어를 의존하지 않는다.
    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("..domain..exception..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "..domain..controller..",
                "..domain..service..",
                "..domain..repository..",
                "..domain..entity..",
                "..domain..dto..",
                "..config..",
                "..security..",
                "..web..",
                "org.springframework.web..",
                "jakarta.servlet..",
                "javax.servlet..");

    rule.allowEmptyShould(true).check(productionClasses);
  }

  @Test
  void domainClassesShouldUseDocumentedLayerPackages() {
    // domain/{domain}/{layer}의 layer는 컨벤션 문서에 적힌 목록만 허용한다.
    ArchRule rule =
        classes().that().resideInAPackage("..domain..").should(useDocumentedDomainLayerPackages());

    rule.allowEmptyShould(true).check(productionClasses);
  }

  @Test
  void springComponentsShouldFollowLayerPackageAndNamingConventions() {
    // Spring stereotype과 패키지/접미사를 맞춰 레이어 위치를 눈으로도 찾기 쉽게 유지한다.
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
    // DTO 루트나 임의 하위 패키지를 막고 request/response 방향을 suffix로도 드러낸다.
    ArchRule documentedDtoPackages =
        classes()
            .that()
            .resideInAPackage("..domain..dto..")
            .should(resideUnderDocumentedDomainDtoPackages());

    ArchRule requestPackages =
        classes().that(domainRequestDtoClasses()).should().haveSimpleNameEndingWith("Request");

    ArchRule responsePackages =
        classes().that(domainResponseDtoClasses()).should().haveSimpleNameEndingWith("Response");

    documentedDtoPackages.allowEmptyShould(true).check(productionClasses);
    requestPackages.allowEmptyShould(true).check(productionClasses);
    responsePackages.allowEmptyShould(true).check(productionClasses);
  }

  @Test
  void controllerEndpointsShouldNotReturnEntitiesAsResponseBodies() {
    // Controller 내부에서 Entity를 DTO로 매핑하는 의존은 허용한다.
    // 금지 대상은 endpoint 시그니처가 Entity를 Response Body 타입으로 노출하는 경우다.
    ArchRule rule =
        classes()
            .that()
            .haveSimpleNameEndingWith("Controller")
            .should(notReturnEntitiesAsResponseBodies());

    rule.allowEmptyShould(true).check(productionClasses);
  }

  @Test
  void requestMappingPathsShouldUseLowercaseCharactersOutsidePathVariables() {
    // URL path는 lowercase를 기본으로 하되, {crewId} 같은 path variable 이름은 예외로 둔다.
    ArchRule rule =
        classes()
            .that()
            .haveSimpleNameEndingWith("Controller")
            .should(useLowercaseRequestMappingPaths());

    rule.allowEmptyShould(true).check(productionClasses);
  }

  @Test
  void lombokDataShouldNotBeUsed() {
    // @Data는 setter/equals/toString 등을 한 번에 열어 도메인 불변식이 흐려지므로 금지한다.
    ArchRule rule = noClasses().should().beAnnotatedWith(DATA);

    rule.check(productionClasses);
  }

  @Test
  void entitiesShouldNotUseBuilderAndShouldPreferNonPublicConstructors() {
    // Entity 생성 경로는 정적 팩토리/도메인 메서드로 모으고, public 생성자와 @Builder를 피한다.
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

    ArchRule noEntitySetters =
        classes()
            .that()
            .areAnnotatedWith(ENTITY)
            .or()
            .resideInAPackage("..domain..entity..")
            .should(notUseLombokSetters());

    noEntityBuilders.allowEmptyShould(true).check(productionClasses);
    noEntitySetters.allowEmptyShould(true).check(productionClasses);
    noPublicEntityConstructors.allowEmptyShould(true).check(productionClasses);
  }

  @Test
  void entityPublicInstanceMethodsShouldNotExposeJavaBeanSetters() {
    // Entity는 의미 있는 도메인 command 메서드는 허용하되 JavaBean setter로 상태를 열지 않는다.
    ArchRule rule =
        classes().that().areAnnotatedWith(ENTITY).should(notExposePublicJavaBeanSetters());

    rule.allowEmptyShould(true).check(productionClasses);
  }

  @Test
  void databaseMappingNamesShouldUseSnakeCaseWhenExplicitlyDeclared() {
    // Hibernate naming strategy에 맡기는 것이 기본이고, 명시 매핑이 필요할 때도 snake_case만 쓴다.
    ArchRule rule =
        classes().that().areAnnotatedWith(ENTITY).should(useSnakeCaseDatabaseMappingNames());

    rule.allowEmptyShould(true).check(productionClasses);
  }

  @Test
  void serviceQueryMethodsShouldBeReadOnlyTransactional() {
    // public 조회 메서드는 메서드 또는 클래스 수준에서 readOnly 트랜잭션 경계를 선언한다.
    // infra 어댑터(S3, FCM 등)는 DB 트랜잭션 경계를 다루지 않으므로 제외한다.
    ArchRule rule =
        classes()
            .that()
            .haveSimpleNameEndingWith("Service")
            .and()
            .resideOutsideOfPackage("..infra..")
            .should(declareReadOnlyTransactionsForQueryMethods());

    rule.allowEmptyShould(true).check(productionClasses);
  }

  @Test
  void serviceCommandMethodsShouldDeclareWriteTransactions() {
    // public command 메서드는 쓰기 트랜잭션 경계가 필요하다.
    // findOrCreate처럼 이름은 조회 prefix여도 쓰기 marker가 있으면 command로 취급한다.
    // infra 어댑터(S3, FCM 등)는 DB 트랜잭션 경계를 다루지 않으므로 제외한다.
    ArchRule rule =
        classes()
            .that()
            .haveSimpleNameEndingWith("Service")
            .and()
            .resideOutsideOfPackage("..infra..")
            .should(declareWriteTransactionsForCommandMethods());

    rule.allowEmptyShould(true).check(productionClasses);
  }

  @Test
  void responseDtosShouldExposeOnlyExternalMemberUuidIdentifiers() {
    // API 응답의 사용자 식별자는 memberUuid/member_uuid 계열만 허용하고 내부 DB id는 숨긴다.
    ArchRule rule =
        classes()
            .that(domainResponseDtoClassesIncludingNestedClasses())
            .should(exposeOnlyExternalMemberUuidIdentifiers());

    rule.allowEmptyShould(true).check(productionClasses);
  }

  @Test
  void moneyLikeMembersShouldNotUseFloatingPointTypes() {
    // 금액/포인트/정산/지분율 계열 이름에는 double/float 대신 BigDecimal을 사용한다.
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

  private static DescribedPredicate<JavaClass> domainResponseDtoClassesIncludingNestedClasses() {
    return new DescribedPredicate<>("domain response DTO classes including nested classes") {
      @Override
      public boolean test(JavaClass javaClass) {
        return !javaClass.isAnnotatedWith(GENERATED)
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

  private static ArchCondition<JavaClass> resideUnderDocumentedDomainDtoPackages() {
    return new ArchCondition<>("reside under documented domain DTO packages") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        String packageName = javaClass.getPackageName();
        boolean valid =
            DOMAIN_REQUEST_DTO_PACKAGE.matcher(packageName).matches()
                || DOMAIN_RESPONSE_DTO_PACKAGE.matcher(packageName).matches();
        if (!valid) {
          events.add(
              SimpleConditionEvent.violated(
                  javaClass,
                  javaClass.getName()
                      + " should reside under domain/{domain}/dto/request or"
                      + " domain/{domain}/dto/response"));
        }
      }
    };
  }

  private static ArchCondition<JavaClass> notReturnEntitiesAsResponseBodies() {
    return new ArchCondition<>("not return entities as response bodies") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        for (JavaMethod method : javaClass.getMethods()) {
          if (!isRequestMappingMethod(method)) {
            continue;
          }

          List<String> entityReturnTypes =
              method.getReturnType().getAllInvolvedRawTypes().stream()
                  .filter(ArchitectureRulesTest::isEntityType)
                  .map(JavaClass::getName)
                  .toList();
          if (!entityReturnTypes.isEmpty()) {
            events.add(
                SimpleConditionEvent.violated(
                    method,
                    method.getFullName()
                        + " should return response DTOs instead of entity response body types "
                        + entityReturnTypes));
          }
        }
      }
    };
  }

  private static boolean isRequestMappingMethod(JavaMethod method) {
    return method.getAnnotations().stream()
        .map(annotation -> annotation.getRawType().getName())
        .anyMatch(MAPPING_ANNOTATIONS::contains);
  }

  private static boolean isEntityType(JavaClass javaClass) {
    return javaClass.isAnnotatedWith(ENTITY);
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

  private static ArchCondition<JavaClass> notExposePublicJavaBeanSetters() {
    return new ArchCondition<>("not expose public JavaBean setters") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        for (JavaMethod method : javaClass.getMethods()) {
          if (!method.getModifiers().contains(JavaModifier.PUBLIC)
              || method.getModifiers().contains(JavaModifier.STATIC)
              || !isJavaBeanSetter(method)) {
            continue;
          }

          events.add(
              SimpleConditionEvent.violated(
                  method,
                  method.getFullName()
                      + " should use an intentional domain command method instead of a public setter"));
        }
      }
    };
  }

  private static ArchCondition<JavaClass> notUseLombokSetters() {
    return new ArchCondition<>("not use Lombok setters") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        if (javaClass.isAnnotatedWith(SETTER)) {
          events.add(
              SimpleConditionEvent.violated(
                  javaClass, javaClass.getName() + " should not use Lombok @Setter"));
        }

        for (JavaField field : javaClass.getFields()) {
          if (field.isAnnotatedWith(SETTER)) {
            events.add(
                SimpleConditionEvent.violated(
                    field, field.getFullName() + " should not use Lombok @Setter"));
          }
        }
      }
    };
  }

  private static boolean isAccessor(JavaMethod method) {
    String name = method.getName();
    return method.getRawParameterTypes().isEmpty()
        && (name.startsWith("get") || name.startsWith("is"));
  }

  private static boolean isJavaBeanSetter(JavaMethod method) {
    String name = method.getName();
    return name.startsWith("set")
        && name.length() > 3
        && Character.isUpperCase(name.charAt(3))
        && method.getRawParameterTypes().size() == 1;
  }

  private static ArchCondition<JavaClass> useSnakeCaseDatabaseMappingNames() {
    return new ArchCondition<>("use snake_case database mapping names") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        javaClass
            .tryGetAnnotationOfType(jakarta.persistence.Table.class)
            .ifPresent(
                table -> {
                  checkSnakeCase(javaClass, table.name(), "table", events);
                  for (jakarta.persistence.Index index : table.indexes()) {
                    checkColumnList(javaClass, index.columnList(), "index column", events);
                  }
                  for (jakarta.persistence.UniqueConstraint uniqueConstraint :
                      table.uniqueConstraints()) {
                    for (String columnName : uniqueConstraint.columnNames()) {
                      checkSnakeCase(javaClass, columnName, "unique constraint column", events);
                    }
                  }
                });

        for (JavaField field : javaClass.getFields()) {
          field
              .tryGetAnnotationOfType(jakarta.persistence.Column.class)
              .ifPresent(column -> checkSnakeCase(field, column.name(), "column", events));

          field
              .tryGetAnnotationOfType(jakarta.persistence.JoinColumn.class)
              .ifPresent(
                  joinColumn -> checkSnakeCase(field, joinColumn.name(), "join column", events));
        }
      }
    };
  }

  private static void checkColumnList(
      JavaClass javaClass, String columnList, String mappingType, ConditionEvents events) {
    for (String columnName : columnList.split(",")) {
      String cleanedColumnName = INDEX_ORDER_SUFFIX.matcher(columnName.trim()).replaceFirst("");
      checkSnakeCase(javaClass, cleanedColumnName, mappingType, events);
    }
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

  private static ArchCondition<JavaClass> declareWriteTransactionsForCommandMethods() {
    return new ArchCondition<>("declare write transactions for command methods") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        for (JavaMethod method : javaClass.getMethods()) {
          if (method.getModifiers().contains(JavaModifier.STATIC)
              || !method.getModifiers().contains(JavaModifier.PUBLIC)
              || isQueryMethod(method)) {
            continue;
          }
          if (!hasWriteTransactional(method) && !hasWriteTransactional(javaClass)) {
            events.add(
                SimpleConditionEvent.violated(
                    method, method.getFullName() + " should declare write @Transactional"));
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

  private static boolean hasWriteTransactional(JavaMethod method) {
    return method
        .tryGetAnnotationOfType(TRANSACTIONAL)
        .filter(ArchitectureRulesTest::isWrite)
        .isPresent();
  }

  private static boolean hasWriteTransactional(JavaClass javaClass) {
    return javaClass
        .tryGetAnnotationOfType(TRANSACTIONAL)
        .filter(ArchitectureRulesTest::isWrite)
        .isPresent();
  }

  private static boolean isWrite(JavaAnnotation<?> annotation) {
    return annotation.get("readOnly").filter(Boolean.TRUE::equals).isEmpty();
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
    if (!isMoneyLike(codeUnit.getName()) && !isMoneyLike(codeUnit.getOwner().getSimpleName())) {
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

  private static ArchCondition<JavaClass> exposeOnlyExternalMemberUuidIdentifiers() {
    return new ArchCondition<>("expose only external memberUuid identifiers") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        for (JavaField field : javaClass.getFields()) {
          if (!field.getModifiers().contains(JavaModifier.PUBLIC)
              || !isDisallowedResponseMemberIdentifier(javaClass, field.getName())) {
            continue;
          }

          events.add(
              SimpleConditionEvent.violated(
                  field,
                  field.getFullName()
                      + " should expose memberUuid (serialized as member_uuid)"
                      + " instead of an internal member/user identifier"));
        }

        for (JavaMethod method : javaClass.getMethods()) {
          if (!method.getModifiers().contains(JavaModifier.PUBLIC)
              || !isAccessor(method)
              || !isDisallowedResponseMemberIdentifier(javaClass, method.getName())) {
            continue;
          }

          events.add(
              SimpleConditionEvent.violated(
                  method,
                  method.getFullName()
                      + " should expose memberUuid (serialized as member_uuid)"
                      + " instead of an internal member/user identifier"));
        }
      }
    };
  }

  private static boolean isDisallowedResponseMemberIdentifier(String name) {
    String propertyName = toPropertyName(name);
    return DISALLOWED_RESPONSE_MEMBER_IDENTIFIER.matcher(capitalize(propertyName)).matches();
  }

  private static boolean isDisallowedResponseMemberIdentifier(
      JavaClass responseClass, String memberName) {
    String propertyName = toPropertyName(memberName);
    return isDisallowedResponseMemberIdentifier(memberName)
        || (MEMBER_RESPONSE_DTO_PACKAGE.matcher(responseClass.getPackageName()).matches()
            && propertyName.equals("id"));
  }

  private static String toPropertyName(String name) {
    if (name.startsWith("get") && name.length() > 3 && Character.isUpperCase(name.charAt(3))) {
      return decapitalize(name.substring(3));
    }
    return name;
  }

  private static String capitalize(String value) {
    if (value.isEmpty()) {
      return value;
    }
    return Character.toUpperCase(value.charAt(0)) + value.substring(1);
  }

  private static String decapitalize(String value) {
    if (value.isEmpty()) {
      return value;
    }
    return Character.toLowerCase(value.charAt(0)) + value.substring(1);
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
