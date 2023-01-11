package cloud.prefab.client.config;

import cloud.prefab.domain.Prefab;
import java.util.stream.Stream;

public class ConfigElement {

  private Prefab.Config config;
  private Provenance provenance;

  public ConfigElement(Prefab.Config config, Provenance provenance) {
    this.config = config;
    this.provenance = provenance;
  }

  public Prefab.Config getConfig() {
    return config;
  }

  public Provenance getProvenance() {
    return provenance;
  }

  public Stream<Prefab.ConfigRow> getRowsProjEnvFirst(long projectEnvId) {
    return config
      .getRowsList()
      .stream()
      .filter(cr -> !cr.hasProjectEnvId() || cr.getProjectEnvId() == projectEnvId)
      .sorted((o1, o2) -> o1.hasProjectEnvId() ? -1 : 1);
  }
}
