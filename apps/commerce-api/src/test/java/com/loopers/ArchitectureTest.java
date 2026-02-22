package com.loopers;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

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
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure", "Interfaces", "Support")
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

    // — 4. Interfaces 계층은 Domain Entity에 직접 의존하지 않음 —
    // enum, VO 등 값 타입은 허용하되, @Entity 클래스는 차단
    @ArchTest
    static final ArchRule interfaces_should_not_depend_on_domain_entities = noClasses()
            .that().resideInAPackage("..interfaces..")
            .should().dependOnClassesThat()
            .areAnnotatedWith("jakarta.persistence.Entity");
}
