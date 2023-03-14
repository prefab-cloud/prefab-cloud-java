package cloud.prefab.client.config;

import cloud.prefab.domain.Prefab;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ConfigElement {

  private Prefab.Config config;
  private Provenance provenance;

  private ConcurrentHashMap<Long, List<Prefab.ConfigRow>> preProcessedRows = new ConcurrentHashMap<>();

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
    return preProcessedRows
      .computeIfAbsent(
        projectEnvId,
        key ->
          config
            .getRowsList()
            .stream()
            .filter(cr -> !cr.hasProjectEnvId() || cr.getProjectEnvId() == projectEnvId)
            .sorted((o1, o2) -> o1.hasProjectEnvId() ? -1 : 1)
            .collect(Collectors.toList())
      )
      .stream();
  }
}
