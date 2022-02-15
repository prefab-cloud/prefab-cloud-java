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
    Prefab.FeatureFlag flagObj = Prefab.FeatureFlag.newBuilder()
        .setActive(true)
        .addVariants(Prefab.FeatureFlagVariant.newBuilder().setBool(false).build())
        .addVariants(Prefab.FeatureFlagVariant.newBuilder().setBool(true).build())
        .setInactiveVariantIdx(0)
        .setDefault(Prefab.VariantDistribution.newBuilder().setVariantIdx(0).build())
        .addUserTargets(Prefab.UserTarget.newBuilder()
            .setVariantIdx(1)
            .addIdentifiers("beta")
            .addIdentifiers("user:1")
            .addIdentifiers("user:3")
            .build())
        .build();

    String featureName = "FlagName";

    assertThat(featureFlagClient.isOnFor(flagObj, featureName, Optional.of("user:1"), Maps.newHashMap())).isTrue();
    assertThat(featureFlagClient.isOnFor(flagObj, featureName, Optional.of("user:2"), Maps.newHashMap())).isFalse();
    assertThat(featureFlagClient.isOnFor(flagObj, featureName, Optional.of("user:3"), Maps.newHashMap())).isTrue();
  }

  @Test
  public void testSegments() {
    Prefab.FeatureFlag flagObj = Prefab.FeatureFlag.newBuilder()
        .setActive(true)
        .addVariants(Prefab.FeatureFlagVariant.newBuilder().setBool(false).build())
        .addVariants(Prefab.FeatureFlagVariant.newBuilder().setBool(true).build())
        .setInactiveVariantIdx(0)
        .setDefault(Prefab.VariantDistribution.newBuilder().setVariantIdx(0).build())
        .addRules(Prefab.Rule.newBuilder()
            .setCriteria(Prefab.Criteria.newBuilder()
                .setOperator(Prefab.Criteria.CriteriaOperator.IN_SEG)
                .addValues("beta-segment")
                .build())
            .setDistribution(Prefab.VariantDistribution.newBuilder()
                .setVariantIdx(1)
                .build())
            .build())
        .build();

    String featureName = "FlagName";

    ConfigClient mockConfigClient = mock(ConfigClient.class);
    when(mockBaseClient.configClient()).thenReturn(mockConfigClient);

    when(mockConfigClient.get("beta-segment")).thenReturn(Optional.of(Prefab.ConfigValue.newBuilder()
            .setSegment(Prefab.Segment.newBuilder()
                .addIncludes("user:1")
                .addIncludes("user:5")
                .addExcludes("user:1")
                .addExcludes("user:2")
                .build())
        .build()));

    assertThat(featureFlagClient.isOnFor(flagObj, featureName, Optional.of("user:0"), Maps.newHashMap())).isFalse();
    assertThat(featureFlagClient.isOnFor(flagObj, featureName, Optional.of("user:1"), Maps.newHashMap())).isFalse();
    assertThat(featureFlagClient.isOnFor(flagObj, featureName, Optional.of("user:2"), Maps.newHashMap())).isFalse();
    assertThat(featureFlagClient.isOnFor(flagObj, featureName, Optional.of("user:5"), Maps.newHashMap())).isTrue();
  }

  @Test
  public void testRules() {
    Prefab.FeatureFlag flagObj = Prefab.FeatureFlag.newBuilder()
        .setActive(true)
        .addVariants(Prefab.FeatureFlagVariant.newBuilder().setBool(false).build())
        .addVariants(Prefab.FeatureFlagVariant.newBuilder().setBool(true).build())
        .setInactiveVariantIdx(0)
        .setDefault(Prefab.VariantDistribution.newBuilder().setVariantIdx(0).build())
        .addRules(Prefab.Rule.newBuilder()
            .setCriteria(Prefab.Criteria.newBuilder()
                .setOperator(Prefab.Criteria.CriteriaOperator.IN)
                .addValues("user:1")
                .build())
            .setDistribution(Prefab.VariantDistribution.newBuilder()
                .setVariantIdx(1)
                .build())
            .build())
        .build();

    String featureName = "FlagName";

    assertThat(featureFlagClient.isOnFor(flagObj, featureName, Optional.of("user:0"), Maps.newHashMap())).isFalse();
    assertThat(featureFlagClient.isOnFor(flagObj, featureName, Optional.of("user:1"), Maps.newHashMap())).isTrue();
    assertThat(featureFlagClient.isOnFor(flagObj, featureName, Optional.of("user:2"), Maps.newHashMap())).isFalse();
  }

}
