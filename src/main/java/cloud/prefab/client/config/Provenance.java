package cloud.prefab.client.config;

import cloud.prefab.client.ConfigClient;
import com.google.gson.JsonElement;

public class Provenance {

  private ConfigClient.Source source;
  private String sourceLocation;

  public Provenance(ConfigClient.Source source, String sourceLocation) {
    this.source = source;
    this.sourceLocation = sourceLocation;
    JsonElement jsonElement = null;
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
