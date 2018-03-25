package cloud.prefab.client;

import cloud.prefab.domain.ConfigServiceGrpc;
import cloud.prefab.domain.Prefab;

import java.util.Optional;

public class ConfigClient {

  private final PrefabCloudClient baseClient;
  private final String namespace;

  public ConfigClient(String namespace, PrefabCloudClient baseClient) {
    this.baseClient = baseClient;
    this.namespace = namespace;
  }

  public Optional<Prefab.ConfigValue> get(String key) {


    return Optional.empty();
  }

  public void upsert(Prefab.ConfigDelta configDelta) {

    configServiceStub().upsert(configDelta);

  }


  private ConfigServiceGrpc.ConfigServiceBlockingStub configServiceStub() {
    return ConfigServiceGrpc.newBlockingStub(baseClient.getChannel());
  }


}
