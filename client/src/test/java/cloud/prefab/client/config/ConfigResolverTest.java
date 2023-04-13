package cloud.prefab.client.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cloud.prefab.client.internal.ConfigClientImpl;
import cloud.prefab.domain.Prefab;
import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ConfigResolverTest {

  private ConfigResolver resolver;

  final ConfigStoreImpl mockConfigStoreImpl = mock(ConfigStoreImpl.class);

  @BeforeEach
  public void setup() {
    resolver = new ConfigResolver(mockConfigStoreImpl);
  }

  @Test
  public void testNamespaceMatch() {
    assertThat(resolver.hierarchicalMatch("a.b.c", "a.b.c")).isEqualTo(true);
    assertThat(resolver.hierarchicalMatch("a.b.c", "a.b.c.d.e")).isEqualTo(false);
    assertThat(resolver.hierarchicalMatch("a.b.c.d.e", "a.b.c")).isEqualTo(true);
    assertThat(resolver.hierarchicalMatch("a.z.c", "a.b.c")).isEqualTo(false);
    assertThat(resolver.hierarchicalMatch("a.b", "a")).isEqualTo(true);
  }

  @Test
  public void testPct() {
    Prefab.Config flag = getTrueFalseConfig(500, 500);

    assertThat(
      resolver
        .evalConfigElementMatch(
          new ConfigElement(
            flag,
            new Provenance(ConfigClientImpl.Source.LOCAL_ONLY, "unit test")
          ),
          keyOnlyLookupContext("very high hash")
        )
        .get()
        .getConfigValue()
    )
      .isEqualTo(Prefab.ConfigValue.newBuilder().setBool(false).build());

    assertThat(
      resolver
        .evalConfigElementMatch(
          new ConfigElement(
            flag,
            new Provenance(ConfigClientImpl.Source.LOCAL_ONLY, "unit test")
          ),
          keyOnlyLookupContext("hashes low")
        )
        .get()
        .getConfigValue()
    )
      .isEqualTo(Prefab.ConfigValue.newBuilder().setBool(true).build());
  }

  @Test
  public void testOff() {
    Prefab.Config flag = getTrueFalseConfig(0, 1000);

    assertThat(
      resolver
        .evalConfigElementMatch(
          new ConfigElement(
            flag,
            new Provenance(ConfigClientImpl.Source.LOCAL_ONLY, "unit test")
          ),
          keyOnlyLookupContext("very high hash")
        )
        .get()
        .getConfigValue()
    )
      .isEqualTo(Prefab.ConfigValue.newBuilder().setBool(false).build());

    assertThat(
      resolver
        .evalConfigElementMatch(
          new ConfigElement(
            flag,
            new Provenance(ConfigClientImpl.Source.LOCAL_ONLY, "unit test")
          ),
          keyOnlyLookupContext("hashes low")
        )
        .get()
        .getConfigValue()
    )
      .isEqualTo(Prefab.ConfigValue.newBuilder().setBool(false).build());
  }

  @Test
  public void testOn() {
    Prefab.Config flag = getTrueFalseConfig(1000, 0);

    assertThat(
      resolver
        .evalConfigElementMatch(
          new ConfigElement(
            flag,
            new Provenance(ConfigClientImpl.Source.LOCAL_ONLY, "unit test")
          ),
          keyOnlyLookupContext("very high hash")
        )
        .get()
        .getConfigValue()
    )
      .isEqualTo(Prefab.ConfigValue.newBuilder().setBool(true).build());

    assertThat(
      resolver
        .evalConfigElementMatch(
          new ConfigElement(
            flag,
            new Provenance(ConfigClientImpl.Source.LOCAL_ONLY, "unit test")
          ),
          keyOnlyLookupContext("hashes low")
        )
        .get()
        .getConfigValue()
    )
      .isEqualTo(Prefab.ConfigValue.newBuilder().setBool(true).build());
  }

  private static Prefab.Config getTrueFalseConfig(int trueWeight, int falseWeight) {
    Prefab.WeightedValues wvs = Prefab.WeightedValues
      .newBuilder()
      .addWeightedValues(
        Prefab.WeightedValue
          .newBuilder()
          .setWeight(trueWeight)
          .setValue(Prefab.ConfigValue.newBuilder().setBool(true).build())
          .build()
      )
      .addWeightedValues(
        Prefab.WeightedValue
          .newBuilder()
          .setWeight(falseWeight)
          .setValue(Prefab.ConfigValue.newBuilder().setBool(false).build())
          .build()
      )
      .build();

    Prefab.Config flag = Prefab.Config
      .newBuilder()
      .setKey("FlagName")
      .addAllowableValues(Prefab.ConfigValue.newBuilder().setBool(true))
      .addAllowableValues(Prefab.ConfigValue.newBuilder().setBool(false))
      .addRows(
        Prefab.ConfigRow
          .newBuilder()
          .addValues(
            Prefab.ConditionalValue
              .newBuilder()
              .setValue(Prefab.ConfigValue.newBuilder().setWeightedValues(wvs))
              .build()
          )
      )
      .build();
    return flag;
  }

  @Test
  public void testEndsWith() {
    final Prefab.Criterion socialEmailCritieria = Prefab.Criterion
      .newBuilder()
      .setPropertyName("email")
      .setValueToMatch(
        Prefab.ConfigValue
          .newBuilder()
          .setStringList(
            Prefab.StringList
              .newBuilder()
              .addValues("gmail.com")
              .addValues("yahoo.com")
              .build()
          )
      )
      .setOperator(Prefab.Criterion.CriterionOperator.PROP_ENDS_WITH_ONE_OF)
      .build();

    final EvaluatedCriterion bobEval = resolver
      .evaluateCriterionMatch(
        socialEmailCritieria,
        singleValueLookupContext("email", sv("bob@example.com"))
      )
      .stream()
      .findFirst()
      .get();
    assertThat(bobEval.isMatch()).isFalse();
    assertThat(bobEval.getEvaluatedProperty().get().getString())
      .isEqualTo("bob@example.com");

    final EvaluatedCriterion yahooEval = resolver
      .evaluateCriterionMatch(
        socialEmailCritieria,
        singleValueLookupContext("email", sv("alice@yahoo.com"))
      )
      .stream()
      .findFirst()
      .get();
    assertThat(yahooEval.isMatch()).isTrue();
    assertThat(yahooEval.getEvaluatedProperty().get().getString())
      .isEqualTo("alice@yahoo.com");

    final EvaluatedCriterion gmailEval = resolver
      .evaluateCriterionMatch(
        socialEmailCritieria,
        singleValueLookupContext("email", sv("alice@gmail.com"))
      )
      .stream()
      .findFirst()
      .get();
    assertThat(gmailEval.isMatch()).isTrue();
    assertThat(gmailEval.getEvaluatedProperty().get().getString())
      .isEqualTo("alice@gmail.com");
  }

  @Test
  public void testDoesNotEndWith() {
    final Prefab.Criterion socialEmailCritieria = Prefab.Criterion
      .newBuilder()
      .setPropertyName("email")
      .setValueToMatch(
        Prefab.ConfigValue
          .newBuilder()
          .setStringList(
            Prefab.StringList
              .newBuilder()
              .addValues("gmail.com")
              .addValues("yahoo.com")
              .build()
          )
      )
      .setOperator(Prefab.Criterion.CriterionOperator.PROP_DOES_NOT_END_WITH_ONE_OF)
      .build();

    final EvaluatedCriterion bobEval = resolver
      .evaluateCriterionMatch(
        socialEmailCritieria,
        singleValueLookupContext("email", sv("bob@example.com"))
      )
      .stream()
      .findFirst()
      .get();
    assertThat(bobEval.isMatch()).isTrue();
    assertThat(bobEval.getEvaluatedProperty().get().getString())
      .isEqualTo("bob@example.com");

    final EvaluatedCriterion yahooEval = resolver
      .evaluateCriterionMatch(
        socialEmailCritieria,
        singleValueLookupContext("email", sv("alice@yahoo.com"))
      )
      .stream()
      .findFirst()
      .get();
    assertThat(yahooEval.isMatch()).isFalse();
    assertThat(yahooEval.getEvaluatedProperty().get().getString())
      .isEqualTo("alice@yahoo.com");

    final EvaluatedCriterion gmailEval = resolver
      .evaluateCriterionMatch(
        socialEmailCritieria,
        singleValueLookupContext("email", sv("alice@gmail.com"))
      )
      .stream()
      .findFirst()
      .get();
    assertThat(gmailEval.isMatch()).isFalse();
    assertThat(gmailEval.getEvaluatedProperty().get().getString())
      .isEqualTo("alice@gmail.com");
  }

  @Test
  public void testSegment() {
    when(mockConfigStoreImpl.containsKey("segment")).thenReturn(true);
    when(mockConfigStoreImpl.getElement("segment")).thenReturn(segmentTestData());

    final Prefab.Criterion segmentCriteria = Prefab.Criterion
      .newBuilder()
      .setValueToMatch(Prefab.ConfigValue.newBuilder().setString("segment").build())
      .setOperator(Prefab.Criterion.CriterionOperator.IN_SEG)
      .build();

    final EvaluatedCriterion betaEval = resolver
      .evaluateCriterionMatch(
        segmentCriteria,
        singleValueLookupContext("group", sv("beta"))
      )
      .stream()
      .findFirst()
      .get();
    assertThat(betaEval.getEvaluatedProperty().get().getString()).isEqualTo("beta");
    assertThat(betaEval.isMatch()).isTrue();
    final List<EvaluatedCriterion> alphaEval = resolver.evaluateCriterionMatch(
      segmentCriteria,
      singleValueLookupContext("group", sv("alpha"))
    );
    System.out.println(alphaEval);
    assertThat(alphaEval).hasSize(1);
    assertThat(alphaEval.get(0).isMatch()).isFalse();
  }

  private Prefab.ConfigValue sv(String s) {
    return Prefab.ConfigValue.newBuilder().setString(s).build();
  }

  private ConfigElement segmentTestData() {
    Prefab.Config segment = Prefab.Config
      .newBuilder()
      .setKey("segment")
      .addRows(
        Prefab.ConfigRow
          .newBuilder()
          .addValues(
            Prefab.ConditionalValue
              .newBuilder()
              .setValue(Prefab.ConfigValue.newBuilder().setBool(true).build())
              .addCriteria(
                Prefab.Criterion
                  .newBuilder()
                  .setPropertyName("group")
                  .setOperator(Prefab.Criterion.CriterionOperator.PROP_IS_ONE_OF)
                  .setValueToMatch(
                    Prefab.ConfigValue
                      .newBuilder()
                      .setStringList(
                        Prefab.StringList.newBuilder().addValues("beta").build()
                      )
                      .build()
                  )
              )
          )
          .addValues(
            Prefab.ConditionalValue
              .newBuilder()
              .setValue(Prefab.ConfigValue.newBuilder().setBool(false).build())
          )
          .build()
      )
      .build();

    return new ConfigElement(
      segment,
      new Provenance(ConfigClientImpl.Source.LOCAL_ONLY, "unit test")
    );
  }

  public static LookupContext keyOnlyLookupContext(String key) {
    return new LookupContext(Optional.of(key), Optional.empty(), Collections.emptyMap());
  }

  public static LookupContext singleValueLookupContext(
    String propName,
    Prefab.ConfigValue configValue
  ) {
    return new LookupContext(
      Optional.empty(),
      Optional.empty(),
      Map.of(propName, configValue)
    );
  }
}
