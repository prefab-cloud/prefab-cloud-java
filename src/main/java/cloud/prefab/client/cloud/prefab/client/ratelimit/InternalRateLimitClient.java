package cloud.prefab.client.cloud.prefab.client.ratelimit;

import cloud.prefab.client.PrefabCloudClient;
import cloud.prefab.domain.Prefab;
import cloud.prefab.domain.RateLimitServiceGrpc;

public class InternalRateLimitClient {

  private final PrefabCloudClient baseClient;

  public InternalRateLimitClient(PrefabCloudClient baseClient) {
    this.baseClient = baseClient;
  }

  public Prefab.LimitResponse limitCheck(Prefab.LimitRequest limitRequest) {
    final RateLimitServiceGrpc.RateLimitServiceBlockingStub rateLimitServiceBlockingStub = RateLimitServiceGrpc.newBlockingStub(baseClient.getChannel());

    return rateLimitServiceBlockingStub.limitCheck(limitRequest);
  }

  public Prefab.BasicResponse upsert(Prefab.LimitDefinition ld) {
    final RateLimitServiceGrpc.RateLimitServiceBlockingStub rateLimitServiceBlockingStub = RateLimitServiceGrpc.newBlockingStub(baseClient.getChannel());
    return rateLimitServiceBlockingStub.upsertLimitDefinition(ld);
  }
}
