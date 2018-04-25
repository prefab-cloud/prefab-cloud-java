package cloud.prefab.client;

import cloud.prefab.client.config.ConfigResolver;
import cloud.prefab.domain.ConfigServiceGrpc;
import cloud.prefab.domain.Prefab;
import com.hubspot.liveconfig.LiveConfig;

import java.util.Optional;

public class ConfigClient {

  private final PrefabCloudClient baseClient;
  private final String namespace;
  private final LiveConfig liveConfig;
  private final ConfigResolver resolver;

  public ConfigClient(String namespace, PrefabCloudClient baseClient) {
    this.baseClient = baseClient;
    this.namespace = namespace;

    resolver = new ConfigResolver(baseClient);
    liveConfig = LiveConfig.builder()
        .usingEnvironmentVariables()
        .usingSystemProperties()
        .usingResolver(resolver)
        .build();
  }

  public LiveConfig getLiveConfig() {
    return liveConfig;
  }


  public Optional<Prefab.ConfigValue> get(String key) {
    return resolver.getConfigValue(key).transform(java.util.Optional::of).or(java.util.Optional.empty());
  }

  public void upsert(Prefab.UpsertRequest upsertRequest) {
    configServiceStub().upsert(upsertRequest);
  }


  private ConfigServiceGrpc.ConfigServiceBlockingStub configServiceStub() {
    return ConfigServiceGrpc.newBlockingStub(baseClient.getChannel());
  }

}
