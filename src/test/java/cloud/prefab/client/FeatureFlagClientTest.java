package cloud.prefab.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cloud.prefab.domain.Prefab;
import java.util.*;
import org.assertj.core.util.Lists;
import org.assertj.core.util.Maps;
import org.junit.Before;
import org.junit.Test;

public class FeatureFlagClientTest {

  private ConfigClient mockConfigClient;
  private FeatureFlagClient featureFlagClient;

  @Before
  public void setup() {
    mockConfigClient = mock(ConfigClient.class);
    when(mockConfigClient.getProjectId()).thenReturn(1L);
    featureFlagClient = new FeatureFlagClient(mockConfigClient);
  }

  @Test
  public void testPct() {
    List<Prefab.FeatureFlagVariant> variants = new ArrayList<>();
    variants.add(Prefab.FeatureFlagVariant.newBuilder().setBool(false).build());
    variants.add(Prefab.FeatureFlagVariant.newBuilder().setBool(true).build());
    Prefab.FeatureFlag flag = Prefab.FeatureFlag
      .newBuilder()
      .setActive(true)
      .setInactiveVariantIdx(0)
      .addRules(
        Prefab.Rule
          .newBuilder()
          .setCriteria(
            Prefab.Criteria
              .newBuilder()
              .setOperator(Prefab.Criteria.CriteriaOperator.ALWAYS_TRUE)
              .build()
          )
          .addVariantWeights(
            Prefab.VariantWeight.newBuilder().setVariantIdx(2).setWeight(500)
          )
          .addVariantWeights(
            Prefab.VariantWeight.newBuilder().setVariantIdx(1).setWeight(500)
          )
          .build()
      )
      .build();
    String feature = "FlagName";

    assertThat(
      featureFlagClient.isOnFor(
        flag,
        feature,
        Optional.of("hashes high"),
        Maps.newHashMap(),
        variants
      )
    )
      .isFalse();
    assertThat(
      featureFlagClient.isOnFor(
        flag,
        feature,
        Optional.of("hashes low"),
        Maps.newHashMap(),
        variants
      )
    )
      .isTrue();
  }

  @Test
  public void testOff() {
    List<Prefab.FeatureFlagVariant> variants = new ArrayList<>();
    variants.add(Prefab.FeatureFlagVariant.newBuilder().setBool(false).build());
    variants.add(Prefab.FeatureFlagVariant.newBuilder().setBool(true).build());
    Prefab.FeatureFlag flag = Prefab.FeatureFlag
      .newBuilder()
      .setActive(true)
      .setInactiveVariantIdx(0)
      .addRules(
        Prefab.Rule
          .newBuilder()
          .setCriteria(
            Prefab.Criteria
              .newBuilder()
              .setOperator(Prefab.Criteria.CriteriaOperator.ALWAYS_TRUE)
              .build()
          )
          .addVariantWeights(
            Prefab.VariantWeight.newBuilder().setVariantIdx(1).setWeight(1000)
          )
          .addVariantWeights(
            Prefab.VariantWeight.newBuilder().setVariantIdx(2).setWeight(0)
          )
          .build()
      )
      .build();

    String feature = "FlagName";

    assertThat(
      featureFlagClient.isOnFor(
        flag,
        feature,
        Optional.of("hashes high"),
        Maps.newHashMap(),
        variants
      )
    )
      .isFalse();
    assertThat(
      featureFlagClient.isOnFor(
        flag,
        feature,
        Optional.of("hashes low"),
        Maps.newHashMap(),
        variants
      )
    )
      .isFalse();
  }

  @Test
  public void testOn() {
    List<Prefab.FeatureFlagVariant> variants = new ArrayList<>();
    variants.add(Prefab.FeatureFlagVariant.newBuilder().setBool(false).build());
    variants.add(Prefab.FeatureFlagVariant.newBuilder().setBool(true).build());
    Prefab.FeatureFlag flag = Prefab.FeatureFlag
      .newBuilder()
      .setActive(true)
      .setInactiveVariantIdx(0)
      .addRules(
        Prefab.Rule
          .newBuilder()
          .setCriteria(
            Prefab.Criteria
              .newBuilder()
              .setOperator(Prefab.Criteria.CriteriaOperator.ALWAYS_TRUE)
              .build()
          )
          .addVariantWeights(
            Prefab.VariantWeight.newBuilder().setVariantIdx(1).setWeight(0)
          )
          .addVariantWeights(
            Prefab.VariantWeight.newBuilder().setVariantIdx(2).setWeight(1000)
          )
          .build()
      )
      .build();

    String feature = "FlagName";

    assertThat(
      featureFlagClient.isOnFor(
        flag,
        feature,
        Optional.of("hashes high"),
        Maps.newHashMap(),
        variants
      )
    )
      .isTrue();
    assertThat(
      featureFlagClient.isOnFor(
        flag,
        feature,
        Optional.of("hashes low"),
        Maps.newHashMap(),
        variants
      )
    )
      .isTrue();
  }
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
