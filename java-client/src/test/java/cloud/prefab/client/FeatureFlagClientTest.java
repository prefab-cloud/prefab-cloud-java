package cloud.prefab.client;

import static org.mockito.Mockito.mock;

import cloud.prefab.client.internal.FeatureFlagClientImpl;
import org.junit.jupiter.api.BeforeEach;

public class FeatureFlagClientTest {

  private ConfigClient mockConfigClient;
  private FeatureFlagClient featureFlagClient;

  @BeforeEach
  public void setup() {
    mockConfigClient = mock(ConfigClient.class);
    featureFlagClient = new FeatureFlagClientImpl(mockConfigClient);
  }
  //

  //
  //  @Test
  //  public void testTargeting() {
  //    Prefab.FeatureFlag flagObj = Prefab.FeatureFlag.newBuilder()
  //        .setActive(true)
  //        .addVariants(Prefab.FeatureFlagVariant.newBuilder().setBool(false).build())
  //        .addVariants(Prefab.FeatureFlagVariant.newBuilder().setBool(true).build())
  //        .setInactiveVariantIdx(0)
  //        .setDefault(Prefab.VariantDistribution.newBuilder().setVariantIdx(0).build())
  //        .addUserTargets(Prefab.UserTarget.newBuilder()
  //            .setVariantIdx(1)
  //            .addIdentifiers("beta")
  //            .addIdentifiers("user:1")
  //            .addIdentifiers("user:3")
  //            .build())
  //        .build();
  //
  //    String featureName = "FlagName";
  //
  //    assertThat(featureFlagClient.isOnFor(flagObj, featureName, Optional.of("user:1"), Maps.newHashMap())).isTrue();
  //    assertThat(featureFlagClient.isOnFor(flagObj, featureName, Optional.of("user:2"), Maps.newHashMap())).isFalse();
  //    assertThat(featureFlagClient.isOnFor(flagObj, featureName, Optional.of("user:3"), Maps.newHashMap())).isTrue();
  //  }
  //
  //  @Test
  //  public void testSegments() {
  //    Prefab.FeatureFlag flagObj = Prefab.FeatureFlag.newBuilder()
  //        .setActive(true)
  //        .addVariants(Prefab.FeatureFlagVariant.newBuilder().setBool(false).build())
  //        .addVariants(Prefab.FeatureFlagVariant.newBuilder().setBool(true).build())
  //        .setInactiveVariantIdx(0)
  //        .setDefault(Prefab.VariantDistribution.newBuilder().setVariantIdx(0).build())
  //        .addRules(Prefab.Rule.newBuilder()
  //            .setCriteria(Prefab.Criteria.newBuilder()
  //                .setOperator(Prefab.Criteria.CriteriaOperator.IN_SEG)
  //                .addValues("beta-segment")
  //                .build())
  //            .setDistribution(Prefab.VariantDistribution.newBuilder()
  //                .setVariantIdx(1)
  //                .build())
  //            .build())
  //        .build();
  //
  //    String featureName = "FlagName";
  //
  //    ConfigClient mockConfigClient = mock(ConfigClient.class);
  //    when(mockBaseClient.configClient()).thenReturn(mockConfigClient);
  //
  //    when(mockConfigClient.get("beta-segment")).thenReturn(Optional.of(Prefab.ConfigValue.newBuilder()
  //        .setSegment(Prefab.Segment.newBuilder()
  //            .addIncludes("user:1")
  //            .addIncludes("user:5")
  //            .addExcludes("user:1")
  //            .addExcludes("user:2")
  //            .build())
  //        .build()));
  //
  //    assertThat(featureFlagClient.isOnFor(flagObj, featureName, Optional.of("user:0"), Maps.newHashMap())).isFalse();
  //    assertThat(featureFlagClient.isOnFor(flagObj, featureName, Optional.of("user:1"), Maps.newHashMap())).isFalse();
  //    assertThat(featureFlagClient.isOnFor(flagObj, featureName, Optional.of("user:2"), Maps.newHashMap())).isFalse();
  //    assertThat(featureFlagClient.isOnFor(flagObj, featureName, Optional.of("user:5"), Maps.newHashMap())).isTrue();
  //  }
  //
  //  @Test
  //  public void testRules() {
  //    Prefab.FeatureFlag flagObj = Prefab.FeatureFlag.newBuilder()
  //        .setActive(true)
  //        .addVariants(Prefab.FeatureFlagVariant.newBuilder().setBool(false).build())
  //        .addVariants(Prefab.FeatureFlagVariant.newBuilder().setBool(true).build())
  //        .setInactiveVariantIdx(0)
  //        .setDefault(Prefab.VariantDistribution.newBuilder().setVariantIdx(0).build())
  //        .addRules(Prefab.Rule.newBuilder()
  //            .setCriteria(Prefab.Criteria.newBuilder()
  //                .setOperator(Prefab.Criteria.CriteriaOperator.IN)
  //                .addValues("user:1")
  //                .build())
  //            .setDistribution(Prefab.VariantDistribution.newBuilder()
  //                .setVariantIdx(1)
  //                .build())
  //            .build())
  //        .build();
  //
  //    String featureName = "FlagName";
  //
  //    assertThat(featureFlagClient.isOnFor(flagObj, featureName, Optional.of("user:0"), Maps.newHashMap())).isFalse();
  //    assertThat(featureFlagClient.isOnFor(flagObj, featureName, Optional.of("user:1"), Maps.newHashMap())).isTrue();
  //    assertThat(featureFlagClient.isOnFor(flagObj, featureName, Optional.of("user:2"), Maps.newHashMap())).isFalse();
  //  }

}
