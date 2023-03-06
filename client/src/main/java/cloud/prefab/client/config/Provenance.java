package cloud.prefab.client.config;

import cloud.prefab.client.ConfigClient;
import java.util.Optional;

public class Provenance {

  private final ConfigClient.Source source;
  private final Optional<String> sourceLocationMaybe;

  public Provenance(ConfigClient.Source source) {
    this.source = source;
    this.sourceLocationMaybe = Optional.empty();
  }

  public Provenance(ConfigClient.Source source, String sourceLocation) {
    this.source = source;
    this.sourceLocationMaybe = Optional.of(sourceLocation);
  }

  @Override
  public String toString() {
    StringBuilder stringBuilder = new StringBuilder().append(source.name());

    sourceLocationMaybe.ifPresent(sourceLocation ->
      stringBuilder.append(":").append(sourceLocation)
    );
    return stringBuilder.toString();
  }
}
