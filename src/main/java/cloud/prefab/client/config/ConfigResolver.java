package cloud.prefab.client.config;

import cloud.prefab.client.PrefabCloudClient;
import cloud.prefab.domain.ConfigServiceGrpc;
import cloud.prefab.domain.Prefab;
import com.google.common.base.Optional;
import com.hubspot.liveconfig.resolver.Resolver;
import io.grpc.stub.StreamObserver;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ConfigResolver implements Resolver {

  private final PrefabCloudClient baseClient;

  private ConcurrentMap<String, Prefab.ConfigDelta> map = new ConcurrentHashMap<>();

  public ConfigResolver(PrefabCloudClient baseClient) {
    this.baseClient = baseClient;

    Prefab.ConfigServicePointer pointer = Prefab.ConfigServicePointer.newBuilder().setStartAtId(0)
        .setAccountId(baseClient.getAccountId())
        .build();

    configServiceStub().getConfig(pointer, new StreamObserver<Prefab.ConfigDeltas>() {
      @Override
      public void onNext(Prefab.ConfigDeltas configDeltas) {
        for (Prefab.ConfigDelta configDelta : configDeltas.getDeltasList()) {
          final Prefab.ConfigDelta currentVal = map.get(configDelta.getKey());
          if (currentVal == null || currentVal.getId() < configDelta.getId()) {
            map.put(configDelta.getKey(), configDelta);
          }
        }
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


  @Override
  public Optional<String> get(String s) {
    final Prefab.ConfigDelta configDelta = map.get(s);
    if (configDelta != null) {
      return Optional.of(configDelta.getValue().toString());
    }
    return Optional.absent();
  }

  @Override
  public Set<String> keySet() {
    return map.keySet();
  }

  private ConfigServiceGrpc.ConfigServiceStub configServiceStub() {
    return ConfigServiceGrpc.newStub(baseClient.getChannel());
  }
}
