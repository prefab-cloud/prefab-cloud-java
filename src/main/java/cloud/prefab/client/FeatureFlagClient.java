package cloud.prefab.client;

import cloud.prefab.domain.ConfigServiceGrpc;
import cloud.prefab.domain.Prefab;
import cloud.prefab.domain.RateLimitServiceGrpc;
import io.grpc.stub.StreamObserver;

public class FeatureFlagClient {

  private final PrefabCloudClient baseClient;

  public FeatureFlagClient(PrefabCloudClient baseClient) {
    this.baseClient = baseClient;
  }

  public boolean isPass(Prefab.LimitRequest limitRequest) {
    return acquire(limitRequest).getPassed();
  }

  public Prefab.LimitResponse acquire(Prefab.LimitRequest limitRequest) {
    limitRequest = limitRequest.toBuilder()
        .setAccountId(baseClient.getAccountId()).build();
    final RateLimitServiceGrpc.RateLimitServiceBlockingStub rateLimitServiceBlockingStub = RateLimitServiceGrpc.newBlockingStub(baseClient.getChannel());
    final Prefab.LimitResponse limitResponse = rateLimitServiceBlockingStub.limitCheck(limitRequest);
    return limitResponse;
  }

  public void upsert(Prefab.ConfigDelta configDelta) {
    configServiceStub().upsert(configDelta, new StreamObserver<Prefab.ConfigServicePointer>() {
      @Override
      public void onNext(Prefab.ConfigServicePointer configServicePointer) {

      }

      @Override
      public void onError(Throwable throwable) {

      }

      @Override
      public void onCompleted() {

      }
    });
  }


  private ConfigServiceGrpc.ConfigServiceStub configServiceStub() {
    return ConfigServiceGrpc.newStub(baseClient.getChannel());
  }


}
