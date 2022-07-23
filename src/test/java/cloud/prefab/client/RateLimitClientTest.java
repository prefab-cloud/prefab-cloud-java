package cloud.prefab.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import cloud.prefab.client.cloud.prefab.client.ratelimit.InternalRateLimitClient;
import cloud.prefab.client.util.MemcachedCache;
import cloud.prefab.domain.Prefab;
import java.io.IOException;
import java.net.InetSocketAddress;
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
    net.spy.memcached.MemcachedClient memcachedClient = new net.spy.memcached.MemcachedClient(
      new InetSocketAddress("localhost", 11211)
    );

    when(mockBaseClient.getDistributedCache())
      .thenReturn(new MemcachedCache(memcachedClient));

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
