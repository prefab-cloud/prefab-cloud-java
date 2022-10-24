package cloud.prefab.client.config;

import cloud.prefab.client.ConfigClient;
import cloud.prefab.domain.Prefab;

public class ConfigElement {

  private Prefab.Config config;
  private ConfigClient.Source source;
  private String sourceLocation;

  public ConfigElement(
    Prefab.Config config,
    ConfigClient.Source source,
    String sourceLocation
  ) {
    this.config = config;
    this.source = source;
    this.sourceLocation = sourceLocation;
  }

  public Prefab.Config getConfig() {
    return config;
  }

  public ConfigClient.Source getSource() {
    return source;
  }

  public String getSourceLocation() {
    return sourceLocation;
  }
}
