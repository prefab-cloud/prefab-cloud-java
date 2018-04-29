package cloud.prefab.client;

import cloud.prefab.client.config.ConfigLoader;
import cloud.prefab.client.config.ConfigResolver;
import cloud.prefab.domain.ConfigServiceGrpc;
import cloud.prefab.domain.Prefab;
import com.hubspot.liveconfig.value.LiveValue;
import io.grpc.stub.StreamObserver;

import java.util.Optional;

public class ConfigClient {

  private final PrefabCloudClient baseClient;
  private final String namespace;
  private final ConfigResolver resolver;
  private final ConfigLoader configLoader;

  public ConfigClient(String namespace, PrefabCloudClient baseClient) {
    this.baseClient = baseClient;
    this.namespace = namespace;

    configLoader = new ConfigLoader();
    resolver = new ConfigResolver(baseClient, configLoader);
    startAPI();
  }

  public Optional<Prefab.ConfigValue> get(String key) {
    return resolver.getConfigValue(key);
  }

  public void upsert(Prefab.UpsertRequest upsertRequest) {
    configServiceBlockingStub().upsert(upsertRequest);
  }


  private void startAPI() {
    Prefab.ConfigServicePointer pointer = Prefab.ConfigServicePointer.newBuilder().setStartAtId(0)
        .setAccountId(baseClient.getAccountId())
        .build();

    configServiceStub().getConfig(pointer, new StreamObserver<Prefab.ConfigDeltas>() {
      @Override
      public void onNext(Prefab.ConfigDeltas configDeltas) {
        for (Prefab.ConfigDelta configDelta : configDeltas.getDeltasList()) {
          configLoader.set(configDelta);
        }
        resolver.update();
      }

      @Override
      public void onError(Throwable throwable) {
        throwable.printStackTrace();
      }

      @Override
      public void onCompleted() {
      }
    });
  }

  private ConfigServiceGrpc.ConfigServiceBlockingStub configServiceBlockingStub() {
    return ConfigServiceGrpc.newBlockingStub(baseClient.getChannel());
  }

  private ConfigServiceGrpc.ConfigServiceStub configServiceStub() {
    return ConfigServiceGrpc.newStub(baseClient.getChannel());
  }
}
