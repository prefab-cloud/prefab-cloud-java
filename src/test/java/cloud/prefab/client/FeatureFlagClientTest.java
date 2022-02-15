package cloud.prefab.client;

import cloud.prefab.domain.Prefab;
import org.assertj.core.util.Lists;
import org.assertj.core.util.Maps;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
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
    when(mockBaseClient.getProjectId()).thenReturn(1L);
    featureFlagClient = new FeatureFlagClient(mockBaseClient);
  }

  @Test
  public void testPct() {
    Prefab.FeatureFlag flag = Prefab.FeatureFlag.newBuilder()
        .setActive(true)
        .addVariants(Prefab.FeatureFlagVariant.newBuilder().setBool(false).build())
        .addVariants(Prefab.FeatureFlagVariant.newBuilder().setBool(true).build())
        .setInactiveVariantIdx(0)
        .setDefault(Prefab.VariantDistribution.newBuilder()
            .setVariantWeights(Prefab.VariantWeights.newBuilder()
                .addWeights(Prefab.VariantWeight.newBuilder().setVariantIdx(1).setWeight(500))
                .addWeights(Prefab.VariantWeight.newBuilder().setVariantIdx(0).setWeight(500))
                .build()))
        .build();
    String feature = "FlagName";

    assertThat(featureFlagClient.isOnFor(flag, feature, Optional.of("hashes high"), Maps.newHashMap())).isFalse();
    assertThat(featureFlagClient.isOnFor(flag, feature, Optional.of("hashes low"), Maps.newHashMap())).isTrue();
  }


  @Test
  public void testOff() {
    Prefab.FeatureFlag flag = Prefab.FeatureFlag.newBuilder()
        .setActive(true)
        .addVariants(Prefab.FeatureFlagVariant.newBuilder().setBool(false).build())
        .addVariants(Prefab.FeatureFlagVariant.newBuilder().setBool(true).build())
        .setInactiveVariantIdx(0)
        .setDefault(Prefab.VariantDistribution.newBuilder()
            .setVariantWeights(Prefab.VariantWeights.newBuilder()
                .addWeights(Prefab.VariantWeight.newBuilder().setVariantIdx(0).setWeight(1000))
                .addWeights(Prefab.VariantWeight.newBuilder().setVariantIdx(1).setWeight(0))
                .build()))
        .build();
    String feature = "FlagName";

    assertThat(featureFlagClient.isOnFor(flag, feature, Optional.of("hashes high"), Maps.newHashMap())).isFalse();
    assertThat(featureFlagClient.isOnFor(flag, feature, Optional.of("hashes low"), Maps.newHashMap())).isFalse();

  }

  @Test
  public void testOn() {
    Prefab.FeatureFlag flag = Prefab.FeatureFlag.newBuilder()
        .setActive(true)
        .addVariants(Prefab.FeatureFlagVariant.newBuilder().setBool(false).build())
        .addVariants(Prefab.FeatureFlagVariant.newBuilder().setBool(true).build())
        .setInactiveVariantIdx(0)
        .setDefault(Prefab.VariantDistribution.newBuilder()
            .setVariantWeights(Prefab.VariantWeights.newBuilder()
                .addWeights(Prefab.VariantWeight.newBuilder().setVariantIdx(0).setWeight(0))
                .addWeights(Prefab.VariantWeight.newBuilder().setVariantIdx(1).setWeight(1000))
                .build()))
        .build();
    String feature = "FlagName";

    assertThat(featureFlagClient.isOnFor(flag, feature, Optional.of("hashes high"), Maps.newHashMap())).isTrue();
    assertThat(featureFlagClient.isOnFor(flag, feature, Optional.of("hashes low"), Maps.newHashMap())).isTrue();

  }

  @Test
  public void testTargeting() {
    Prefab.FeatureFlag flag = Prefab.FeatureFlag.newBuilder()
        .setActive(true)
        .addVariants(Prefab.FeatureFlagVariant.newBuilder().setBool(false).build())
        .addVariants(Prefab.FeatureFlagVariant.newBuilder().setBool(true).build())
        .addUserTargets(Prefab.UserTarget.newBuilder()
            .setVariantIdx(1)
            .addIdentifiers("beta")
            .addIdentifiers("user:1")
            .addIdentifiers("user:3")
            .build())
        .build();
    String feature = "FlagName";



//    assertThat(featureFlagClient.isOnFor(flag, feature, Optional.of("anything"), new ArrayList<>())).isFalse();
//    assertThat(featureFlagClient.isOnFor(flag, feature, Optional.of("anything"), Lists.newArrayList("beta"))).isTrue();
//    assertThat(featureFlagClient.isOnFor(flag, feature, Optional.of("anything"), Lists.newArrayList("alpha", "beta"))).isTrue();
//    assertThat(featureFlagClient.isOnFor(flag, feature, Optional.of("anything"), Lists.newArrayList("alpha", "user:1"))).isTrue();
//    assertThat(featureFlagClient.isOnFor(flag, feature, Optional.of("anything"), Lists.newArrayList("alpha", "user:2"))).isFalse();
  }

}
