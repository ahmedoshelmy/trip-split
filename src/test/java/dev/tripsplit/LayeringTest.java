package dev.tripsplit;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Hexagonal layering inside every module: domain is pure, adapters sit on the outside. */
@AnalyzeClasses(packages = "dev.tripsplit", importOptions = ImportOption.DoNotIncludeTests.class)
class LayeringTest {

  @ArchTest
  static final ArchRule domainIsPureJava =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .and()
          .doNotHaveSimpleName("package-info")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework..",
              "tools.jackson..",
              "com.fasterxml..",
              "java.sql..",
              "jakarta..")
          .because("domain code is plain Java: records, sealed types and rules");

  @ArchTest
  static final ArchRule domainDoesNotReachOutwards =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..application..", "..adapter..");

  @ArchTest
  static final ArchRule applicationDoesNotKnowAdapters =
      noClasses()
          .that()
          .resideInAPackage("..application..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..adapter..");

  @ArchTest
  static final ArchRule onlyAdaptersTouchJdbcAndHttp =
      noClasses()
          .that()
          .resideInAnyPackage("..domain..", "..application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework.jdbc..",
              "org.springframework.web..",
              "org.springframework.http..");
}
