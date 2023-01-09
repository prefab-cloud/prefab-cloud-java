package cloud.prefab.client.config;

import cloud.prefab.client.ConfigClient;
import cloud.prefab.domain.Prefab;
import com.google.common.base.MoreObjects;

public class Provenance {

  private ConfigClient.Source source;
  private String sourceLocation;

  public Provenance(ConfigClient.Source source, String sourceLocation) {
    this.source = source;
    this.sourceLocation = sourceLocation;
  }

  @Override
  public String toString() {
    return new StringBuilder()
      .append(source.name())
      .append(":")
      .append(sourceLocation)
      .toString();
  }
}
