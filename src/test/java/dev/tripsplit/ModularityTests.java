package dev.tripsplit;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/** Spring Modulith: no cycles, and modules only use each other's published API. */
class ModularityTests {

  private final ApplicationModules modules = ApplicationModules.of(TripSplitApplication.class);

  @Test
  void modulesRespectTheirBoundaries() {
    modules.verify();
  }

  @Test
  void writesModuleDiagrams() {
    new Documenter(modules).writeModulesAsPlantUml().writeIndividualModulesAsPlantUml();
  }
}
