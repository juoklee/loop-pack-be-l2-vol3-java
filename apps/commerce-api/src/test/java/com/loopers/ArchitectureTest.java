package com.loopers;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

@AnalyzeClasses(packages = "com.loopers", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    // — 1. 계층형 아키텍처 의존성 검증 —
    // Interfaces → Application → Domain ← Infrastructure
    // support 패키지는 cross-cutting concern (에러, 인증, 설정)이므로 레이어 검증에서 제외
    @ArchTest
    static final ArchRule layered_architecture_is_respected = layeredArchitecture()
            .consideringOnlyDependenciesInAnyPackage("com.loopers..")
            .layer("Interfaces").definedBy("..interfaces..")
            .layer("Application").definedBy("..application..")
            .layer("Domain").definedBy("..domain..")
            .layer("Infrastructure").definedBy("..infrastructure..")
            .optionalLayer("Support").definedBy("..support..", "..config..")

            .whereLayer("Interfaces").mayOnlyBeAccessedByLayers("Support")
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Interfaces")
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure", "Support")
            .whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer();

    // — 2. 도메인 간 순환 참조 방지 —
    // 각 도메인 (member, product, brand, like, order 등)이 서로 순환 의존하지 않아야 함
    @ArchTest
    static final ArchRule no_cycles_between_domains = slices()
            .matching("com.loopers.domain.(*)..")
            .should().beFreeOfCycles();

    // — 3. Application 계층은 인프라 기술에 직접 의존하지 않음 —
    @ArchTest
    static final ArchRule application_should_not_use_jpa_annotations = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "jakarta.persistence..",
                    "org.springframework.data.."
            );

    // — 4. Domain Repository 인터페이스는 순수 자바 (JPA/Spring Data 비의존) —
    // DIP: domainRepository는 특정 데이터베이스 기술에 종속되지 않은 순수한 자바 인터페이스여야 함
    @ArchTest
    static final ArchRule domain_repository_should_be_pure_java = noClasses()
            .that().resideInAPackage("..domain..")
            .and().haveSimpleNameEndingWith("Repository")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "jakarta.persistence..",
                    "org.springframework.data.."
            );

    // — 5. Domain Entity는 무분별한 setter를 노출하지 않음 —
    // 의미 있는 메서드명(예: changeShippingInfo())을 통해 상태를 변경하도록 제어
    @ArchTest
    static final ArchRule domain_should_not_expose_setters = methods()
            .that().haveNameMatching("set[A-Z].*")
            .and().areDeclaredInClassesThat().resideInAPackage("..domain..")
            .should().notBePublic()
            .allowEmptyShould(true);
}
