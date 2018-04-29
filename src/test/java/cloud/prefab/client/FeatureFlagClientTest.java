package cloud.prefab.client;

import cloud.prefab.domain.Prefab;
import org.assertj.core.util.Lists;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class FeatureFlagClientTest {

  private PrefabCloudClient mockBaseClient;
  private FeatureFlagClient featureFlagClient;

  @Before
  public void setup() {
    mockBaseClient = mock(PrefabCloudClient.class);
    when(mockBaseClient.getAccountId()).thenReturn(1L);
    featureFlagClient = new FeatureFlagClient(mockBaseClient);
  }

  @Test
  public void testPct() {

    Prefab.FeatureFlag flag = Prefab.FeatureFlag.newBuilder()
        .setPct(0.5)
        .build();
    String feature = "FlagName";

    assertThat(featureFlagClient.isOnFor(flag, feature, Optional.of("hashes high"), new ArrayList<>())).isFalse();
    assertThat(featureFlagClient.isOnFor(flag, feature, Optional.of("hashes low"), new ArrayList<>())).isTrue();
  }


  @Test
  public void testOff() {
    Prefab.FeatureFlag flag = Prefab.FeatureFlag.newBuilder()
        .setPct(0)
        .build();
    String feature = "FlagName";

    assertThat(featureFlagClient.isOnFor(flag, feature, Optional.of("hashes high"), new ArrayList<>())).isFalse();
    assertThat(featureFlagClient.isOnFor(flag, feature, Optional.of("hashes low"), new ArrayList<>())).isFalse();

  }

  @Test
  public void testOn() {
    Prefab.FeatureFlag flag = Prefab.FeatureFlag.newBuilder()
        .setPct(1)
        .build();
    String feature = "FlagName";

    assertThat(featureFlagClient.isOnFor(flag, feature, Optional.of("hashes high"), new ArrayList<>())).isTrue();
    assertThat(featureFlagClient.isOnFor(flag, feature, Optional.of("hashes low"), new ArrayList<>())).isTrue();

  }

  @Test
  public void testWhitelist() {
    Prefab.FeatureFlag flag = Prefab.FeatureFlag.newBuilder()
        .setPct(0)
        .addWhitelisted("beta")
        .addWhitelisted("user:1")
        .addWhitelisted("user:3")
        .build();
    String feature = "FlagName";

    assertThat(featureFlagClient.isOnFor(flag, feature, Optional.of("anything"), new ArrayList<>())).isFalse();
    assertThat(featureFlagClient.isOnFor(flag, feature, Optional.of("anything"), Lists.newArrayList("beta"))).isTrue();
    assertThat(featureFlagClient.isOnFor(flag, feature, Optional.of("anything"), Lists.newArrayList("alpha", "beta"))).isTrue();
    assertThat(featureFlagClient.isOnFor(flag, feature, Optional.of("anything"), Lists.newArrayList("alpha", "user:1"))).isTrue();
    assertThat(featureFlagClient.isOnFor(flag, feature, Optional.of("anything"), Lists.newArrayList("alpha", "user:2"))).isFalse();
  }
}
