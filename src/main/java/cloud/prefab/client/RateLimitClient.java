package cloud.prefab.client;

import cloud.prefab.domain.Prefab;
import cloud.prefab.domain.RateLimitServiceGrpc;

public class RateLimitClient {
  private Prefab.OnFailure defaultOnFailure = Prefab.OnFailure.LOG_AND_PASS;

  private final PrefabCloudClient baseClient;

  public RateLimitClient(PrefabCloudClient baseClient) {
    this.baseClient = baseClient;
  }

  public boolean isPass(Prefab.LimitRequest limitRequest) {
    return acquire(limitRequest, defaultOnFailure).getPassed();
  }

  public Prefab.LimitResponse acquire(Prefab.LimitRequest limitRequest, Prefab.OnFailure onFailure) {
    limitRequest = limitRequest.toBuilder()
        .setAccountId(baseClient.getAccountId()).build();
    final RateLimitServiceGrpc.RateLimitServiceBlockingStub rateLimitServiceBlockingStub = RateLimitServiceGrpc.newBlockingStub(baseClient.getChannel());

    try {
      final Prefab.LimitResponse limitResponse = rateLimitServiceBlockingStub.limitCheck(limitRequest);
      return limitResponse;
    } catch (Exception e) {
      String message = String.format("ratelimit for %s error: %s", limitRequest.getGroupsList(), e.getMessage());

      switch (onFailure) {
        case THROW:
          throw e;
        case LOG_AND_FAIL:
          baseClient.logInternal(message);
          return Prefab.LimitResponse.newBuilder()
              .setAmount(0)
              .setPassed(false)
              .build();
        default:
          baseClient.logInternal(message);
          return Prefab.LimitResponse.newBuilder()
              .setAmount(limitRequest.getAcquireAmount())
              .setPassed(true)
              .build();

      }
    }
  }

  public void upsert(Prefab.LimitDefinition limitDefinition) {
    final RateLimitServiceGrpc.RateLimitServiceBlockingStub rateLimitServiceBlockingStub = RateLimitServiceGrpc.newBlockingStub(baseClient.getChannel());
    final Prefab.BasicResponse basicResponse = rateLimitServiceBlockingStub.upsertLimitDefinition(limitDefinition);
  }
}
