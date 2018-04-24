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

  public ConfigClient(String namespace, PrefabCloudClient baseClient) {
    this.baseClient = baseClient;
    this.namespace = namespace;

    liveConfig = LiveConfig.builder()
        .usingEnvironmentVariables()
        .usingSystemProperties()
        .usingResolver(new ConfigResolver(baseClient))
        .build();
  }

  public LiveConfig getLiveConfig() {
    return liveConfig;
  }


  public void upsert(Prefab.UpsertRequest upsertRequest) {
    configServiceStub().upsert(upsertRequest);
  }


  private ConfigServiceGrpc.ConfigServiceBlockingStub configServiceStub() {
    return ConfigServiceGrpc.newBlockingStub(baseClient.getChannel());
  }
}
