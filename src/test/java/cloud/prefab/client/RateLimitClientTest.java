package cloud.prefab.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import cloud.prefab.client.cloud.prefab.client.ratelimit.InternalRateLimitClient;
import cloud.prefab.client.util.Cache;
import cloud.prefab.domain.Prefab;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.Before;
import org.junit.Test;

public class RateLimitClientTest {

  private RateLimitClient rateLimitClient;
  private PrefabCloudClient mockBaseClient;
  private InternalRateLimitClient mockInternalRateLimitClient;

  @Before
  public void setup() throws IOException {
    mockBaseClient = mock(PrefabCloudClient.class);

    mockInternalRateLimitClient = mock(InternalRateLimitClient.class);

    when(mockBaseClient.getDistributedCache())
      .thenReturn(
        new Cache() {
          private final Map<String, byte[]> cache = new ConcurrentHashMap<>();

          @Override
          public byte[] get(String s) {
            return cache.get(s);
          }

          @Override
          public void set(String key, int expiryInSeconds, byte[] bytes) {
            cache.put(key, bytes);
          }
        }
      );

    when(mockBaseClient.getProjectId()).thenReturn(54321L);

    rateLimitClient = new RateLimitClient(mockBaseClient);
    rateLimitClient.setInternalRateLimitClient(mockInternalRateLimitClient);
  }

  @Test
  public void isPassAddsAccountId() {
    when(mockInternalRateLimitClient.limitCheck(any()))
      .thenReturn(Prefab.LimitResponse.newBuilder().setPassed(true).setAmount(1).build());

    rateLimitClient.isPass(
      Prefab.LimitRequest.newBuilder().setAcquireAmount(1).addGroups("test.group").build()
    );

    Prefab.LimitRequest expectedRemoteCall = Prefab.LimitRequest
      .newBuilder()
      .setAcquireAmount(1)
      .setAccountId(54321L)
      .addGroups("test.group")
      .build();
    verify(mockInternalRateLimitClient).limitCheck(expectedRemoteCall);
  }

  @Test
  public void acquireCalledTwiceCaches() {
    final long fiveSecondsFromNow = System.currentTimeMillis() + 5000;

    when(mockInternalRateLimitClient.limitCheck(any()))
      .thenReturn(
        Prefab.LimitResponse
          .newBuilder()
          .setPassed(true)
          .setLimitResetAt(fiveSecondsFromNow)
          .setAmount(1)
          .build()
      );

    boolean pass = rateLimitClient.isPass(
      Prefab.LimitRequest.newBuilder().setAcquireAmount(1).addGroups("test.group").build()
    );
    assertThat(pass).isTrue();

    pass =
      rateLimitClient.isPass(
        Prefab.LimitRequest
          .newBuilder()
          .setAcquireAmount(1)
          .addGroups("test.group")
          .build()
      );

    assertThat(pass).isFalse();

    Prefab.LimitRequest expectedRemoteCall = Prefab.LimitRequest
      .newBuilder()
      .setAcquireAmount(1)
      .setAccountId(54321L)
      .addGroups("test.group")
      .build();

    verify(mockInternalRateLimitClient, times(1)).limitCheck(expectedRemoteCall);
  }

  @Test
  public void upsert() {}
}
