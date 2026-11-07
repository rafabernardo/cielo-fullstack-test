package com.portabilidade.backend;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

// Enforces the dependency rules documented in AGENTS.md.
@AnalyzeClasses(packages = "com.portabilidade.backend", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

	private static final String BASE = "com.portabilidade.backend";

	@ArchTest
	static final ArchRule layerDependencies = layeredArchitecture()
			.consideringOnlyDependenciesInLayers()
			.withOptionalLayers(true)
			.layer("Controller").definedBy(BASE + ".controller..")
			.layer("Service").definedBy(BASE + ".service..")
			.layer("Domain").definedBy(BASE + ".domain..")
			.layer("Repository").definedBy(BASE + ".repository..")
			.layer("Dto").definedBy(BASE + ".dto..")
			.layer("Integration").definedBy(BASE + ".integration..")
			.layer("Config").definedBy(BASE + ".config..")
			.layer("Exception").definedBy(BASE + ".exception..")
			.whereLayer("Controller").mayOnlyAccessLayers("Service", "Dto", "Domain", "Exception")
			.whereLayer("Service").mayOnlyAccessLayers("Domain", "Repository", "Exception", "Integration")
			.whereLayer("Domain").mayOnlyAccessLayers("Exception")
			.whereLayer("Repository").mayOnlyAccessLayers("Domain")
			.whereLayer("Dto").mayOnlyAccessLayers("Domain")
			.whereLayer("Integration").mayOnlyAccessLayers("Config", "Exception")
			.whereLayer("Config").mayNotAccessAnyLayer()
			.whereLayer("Exception").mayNotAccessAnyLayer();

	@ArchTest
	static final ArchRule servicesDependOnGatewayInterfacesOnly = noClasses()
			.that().resideInAPackage("..service..")
			.should().dependOnClassesThat().haveSimpleNameEndingWith("HttpGateway")
			.orShould().dependOnClassesThat().resideInAPackage("org.springframework.web.client..");

	@ArchTest
	static final ArchRule domainIsFrameworkFree = noClasses()
			.that().resideInAPackage("..domain..")
			.should().dependOnClassesThat().resideInAPackage("org.springframework..");

	@ArchTest
	static final ArchRule noPackageCycles = slices()
			.matching(BASE + ".(*)..")
			.should().beFreeOfCycles();

	@ArchTest
	static final ArchRule noFieldInjection = NO_CLASSES_SHOULD_USE_FIELD_INJECTION;

}
