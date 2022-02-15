package cloud.prefab.client;

import cloud.prefab.client.cloud.prefab.client.ratelimit.InternalRateLimitClient;
import cloud.prefab.domain.Prefab;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.primitives.Longs;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutionException;

public class RateLimitClient {
  private static final Logger LOG = LoggerFactory.getLogger(RateLimitClient.class);

  private Prefab.OnFailure defaultOnFailure = Prefab.OnFailure.LOG_AND_PASS;

  private final PrefabCloudClient baseClient;
  private InternalRateLimitClient internalRateLimitClient;

  public RateLimitClient(PrefabCloudClient baseClient) {
    internalRateLimitClient = new InternalRateLimitClient(baseClient);
    this.baseClient = baseClient;
  }

  public boolean isPass(Prefab.LimitRequest limitRequest) {
    return acquire(limitRequest, defaultOnFailure).getPassed();
  }

  public Prefab.LimitResponse acquire(Prefab.LimitRequest limitRequest, Prefab.OnFailure onFailure) {
    limitRequest = limitRequest.toBuilder()
        .setAccountId(baseClient.getProjectId()).build();

    String limitResetCacheKey = Joiner.on(":").join("prefab.java.ratelimit.limitReset:", limitRequest.getGroupsList());

    if (checkCache(limitResetCacheKey)) {
      return Prefab.LimitResponse.newBuilder()
          .setPassed(false)
          .setAmount(0)
          .build();
    }

    try {
      Prefab.LimitResponse limitResponse = internalRateLimitClient.limitCheck(limitRequest);

      saveLimitResetToCache(limitResetCacheKey, limitResponse);

      return limitResponse;

    } catch (Exception e) {
      String message = String.format("ratelimit for %s error: %s", limitRequest.getGroupsList(), e.getMessage());

      switch (onFailure) {
        case THROW:
          throw e;
        case LOG_AND_FAIL:
          LOG.warn(message);
          return Prefab.LimitResponse.newBuilder()
              .setAmount(0)
              .setPassed(false)
              .build();
        default:
          LOG.info(message);
          return Prefab.LimitResponse.newBuilder()
              .setAmount(limitRequest.getAcquireAmount())
              .setPassed(true)
              .build();

      }
    }
  }

  private void saveLimitResetToCache(String limitResetCacheKey, Prefab.LimitResponse limitResponse) {
    if (limitResponse.getLimitResetAt() > 0) { // protobuf default is 0
      baseClient.getDistributedCache().set(limitResetCacheKey, 0, Longs.toByteArray(limitResponse.getLimitResetAt()));
    }
  }

  private boolean checkCache(String limitResetCacheKey) {
    try {
      final byte[] bytes = baseClient.getDistributedCache().get(limitResetCacheKey);
      if (bytes != null) {
        final long expiry = Longs.fromByteArray(bytes);
        if (expiry > DateTime.now().getMillis()) {
          return true;
        }
      }
    } catch (InterruptedException | ExecutionException e) {
      e.printStackTrace();
    }
    return false;
  }

  @VisibleForTesting
  public RateLimitClient setInternalRateLimitClient(InternalRateLimitClient internalRateLimitClient) {
    this.internalRateLimitClient = internalRateLimitClient;
    return this;
  }
}
