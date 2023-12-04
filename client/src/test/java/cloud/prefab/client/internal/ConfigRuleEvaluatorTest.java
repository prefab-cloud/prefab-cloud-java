package cloud.prefab.client.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cloud.prefab.client.ConfigClient;
import cloud.prefab.client.config.ConfigElement;
import cloud.prefab.client.config.EvaluatedCriterion;
import cloud.prefab.client.config.Provenance;
import cloud.prefab.context.PrefabContext;
import cloud.prefab.domain.Prefab;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ConfigRuleEvaluatorTest {

  final ConfigStoreImpl mockConfigStoreImpl = mock(ConfigStoreImpl.class);

  private ConfigRuleEvaluator evaluator;

  @BeforeEach
  public void setup() {
    evaluator =
      new ConfigRuleEvaluator(mockConfigStoreImpl, new WeightedValueEvaluator());

    when(mockConfigStoreImpl.getDefaultContext())
      .thenReturn(DefaultContextWrapper.empty());
  }

  @Test
  public void testNamespaceMatch() {
    assertThat(evaluator.hierarchicalMatch("a.b.c", "a.b.c")).isEqualTo(true);
    assertThat(evaluator.hierarchicalMatch("a.b.c", "a.b.c.d.e")).isEqualTo(false);
    assertThat(evaluator.hierarchicalMatch("a.b.c.d.e", "a.b.c")).isEqualTo(true);
    assertThat(evaluator.hierarchicalMatch("a.z.c", "a.b.c")).isEqualTo(false);
    assertThat(evaluator.hierarchicalMatch("a.b", "a")).isEqualTo(true);
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

    final EvaluatedCriterion bobEval = evaluator
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

    final EvaluatedCriterion yahooEval = evaluator
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

    final EvaluatedCriterion gmailEval = evaluator
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

    final EvaluatedCriterion bobEval = evaluator
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

    final EvaluatedCriterion yahooEval = evaluator
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

    final EvaluatedCriterion gmailEval = evaluator
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

    final EvaluatedCriterion betaEval = evaluator
      .evaluateCriterionMatch(
        segmentCriteria,
        singleValueLookupContext("group", sv("beta"))
      )
      .stream()
      .findFirst()
      .get();
    assertThat(betaEval.getEvaluatedProperty().get().getString()).isEqualTo("beta");
    assertThat(betaEval.isMatch()).isTrue();
    final List<EvaluatedCriterion> alphaEval = evaluator.evaluateCriterionMatch(
      segmentCriteria,
      singleValueLookupContext("group", sv("alpha"))
    );
    System.out.println(alphaEval);
    assertThat(alphaEval).hasSize(1);
    assertThat(alphaEval.get(0).isMatch()).isFalse();
  }

  @Test
  public void testIntInStringListMatches() {
    final Prefab.Criterion numberCriterion = Prefab.Criterion
      .newBuilder()
      .setPropertyName("number")
      .setValueToMatch(
        Prefab.ConfigValue
          .newBuilder()
          .setStringList(
            Prefab.StringList.newBuilder().addValues("10").addValues("11").build()
          )
      )
      .setOperator(Prefab.Criterion.CriterionOperator.PROP_IS_ONE_OF)
      .build();

    final EvaluatedCriterion positiveEval = evaluator
      .evaluateCriterionMatch(
        numberCriterion,
        singleValueLookupContext(
          "number",
          Prefab.ConfigValue.newBuilder().setInt(10).build()
        )
      )
      .stream()
      .findFirst()
      .get();
    assertThat(positiveEval.isMatch()).isTrue();
    assertThat(positiveEval.getEvaluatedProperty().get().getString()).isEqualTo("10");

    final EvaluatedCriterion negativeEval = evaluator
      .evaluateCriterionMatch(
        numberCriterion,
        singleValueLookupContext(
          "number",
          Prefab.ConfigValue.newBuilder().setInt(13).build()
        )
      )
      .stream()
      .findFirst()
      .get();
    assertThat(negativeEval.isMatch()).isFalse();
    assertThat(negativeEval.getEvaluatedProperty().get().getString()).isEqualTo("13");
  }

  @Test
  public void testIntNotInStringListMatches() {
    final Prefab.Criterion numberCriteria = Prefab.Criterion
      .newBuilder()
      .setPropertyName("number")
      .setValueToMatch(
        Prefab.ConfigValue
          .newBuilder()
          .setStringList(
            Prefab.StringList.newBuilder().addValues("10").addValues("11").build()
          )
      )
      .setOperator(Prefab.Criterion.CriterionOperator.PROP_IS_NOT_ONE_OF)
      .build();

    final EvaluatedCriterion positiveEval = evaluator
      .evaluateCriterionMatch(
        numberCriteria,
        singleValueLookupContext(
          "number",
          Prefab.ConfigValue.newBuilder().setInt(13).build()
        )
      )
      .stream()
      .findFirst()
      .get();
    assertThat(positiveEval.isMatch()).isTrue();
    assertThat(positiveEval.getEvaluatedProperty().get().getString()).isEqualTo("13");

    final EvaluatedCriterion negativeEval = evaluator
      .evaluateCriterionMatch(
        numberCriteria,
        singleValueLookupContext(
          "number",
          Prefab.ConfigValue.newBuilder().setInt(10).build()
        )
      )
      .stream()
      .findFirst()
      .get();
    assertThat(negativeEval.isMatch()).isFalse();
    assertThat(negativeEval.getEvaluatedProperty().get().getString()).isEqualTo("10");
  }

  @Test
  void itFiltersKeysByConfigType() {
    ConfigElement configTypedElement = new ConfigElement(
      Prefab.Config
        .newBuilder()
        .setConfigType(Prefab.ConfigType.CONFIG)
        .setKey("key1")
        .buildPartial(),
      new Provenance(ConfigClient.Source.STREAMING)
    );
    ConfigElement featureFlagTypedElement = new ConfigElement(
      Prefab.Config
        .newBuilder()
        .setConfigType(Prefab.ConfigType.FEATURE_FLAG)
        .setKey("key2")
        .buildPartial(),
      new Provenance(ConfigClient.Source.STREAMING)
    );

    when(mockConfigStoreImpl.getElements())
      .thenReturn(List.of(configTypedElement, featureFlagTypedElement));
    assertThat(evaluator.getKeysOfConfigType(Prefab.ConfigType.CONFIG))
      .containsExactlyInAnyOrder("key1");
    assertThat(evaluator.getKeysOfConfigType(Prefab.ConfigType.FEATURE_FLAG))
      .containsExactlyInAnyOrder("key2");
    assertThat(evaluator.getKeysOfConfigType(Prefab.ConfigType.SEGMENT)).isEmpty();
  }

  @Test
  public void testTimeInRange() {
    // note this relies on Configevaluator on-demand adding the current time
    final Prefab.Criterion intRangeCriterion = Prefab.Criterion
      .newBuilder()
      .setPropertyName(ConfigRuleEvaluator.CURRENT_TIME_KEY)
      .setValueToMatch(
        Prefab.ConfigValue.newBuilder().setIntRange(Prefab.IntRange.newBuilder().build())
      )
      .setOperator(Prefab.Criterion.CriterionOperator.IN_INT_RANGE)
      .build();

    final EvaluatedCriterion positiveEval = evaluator
      .evaluateCriterionMatch(intRangeCriterion, LookupContext.EMPTY)
      .stream()
      .findFirst()
      .get();

    assertThat(positiveEval.isMatch()).isTrue();
  }

  @Test
  public void testTimeAfterRange() {
    // note this relies on ConfigRuleEvaluator on-demand adding the current time
    long currentTime = System.currentTimeMillis();

    final Prefab.Criterion intRangeCriterion = Prefab.Criterion
      .newBuilder()
      .setPropertyName(ConfigRuleEvaluator.CURRENT_TIME_KEY)
      .setValueToMatch(
        Prefab.ConfigValue
          .newBuilder()
          .setIntRange(
            Prefab.IntRange
              .newBuilder()
              .setEnd(currentTime - TimeUnit.MINUTES.toMillis(2))
              .build()
          )
      )
      .setOperator(Prefab.Criterion.CriterionOperator.IN_INT_RANGE)
      .build();

    final EvaluatedCriterion eval = evaluator
      .evaluateCriterionMatch(intRangeCriterion, LookupContext.EMPTY)
      .stream()
      .findFirst()
      .get();

    assertThat(eval.isMatch()).isFalse();
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

  public static LookupContext singleValueLookupContext(
    String propName,
    Prefab.ConfigValue configValue
  ) {
    return new LookupContext(
      Optional.empty(),
      PrefabContext.unnamedFromMap(Map.of(propName, configValue))
    );
  }
}
