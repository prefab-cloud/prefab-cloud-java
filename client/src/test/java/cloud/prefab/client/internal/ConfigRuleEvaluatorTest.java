package cloud.prefab.client.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cloud.prefab.client.ConfigClient;
import cloud.prefab.client.config.ConfigElement;
import cloud.prefab.client.config.ConfigValueUtils;
import cloud.prefab.client.config.EvaluatedCriterion;
import cloud.prefab.client.config.Provenance;
import cloud.prefab.context.PrefabContext;
import cloud.prefab.context.PrefabContextSet;
import cloud.prefab.context.PrefabContextSetReadable;
import cloud.prefab.domain.Prefab;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.checkerframework.checker.units.qual.N;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.shadow.com.univocity.parsers.annotations.Nested;

public class ConfigRuleEvaluatorTest {

  final ConfigStoreImpl mockConfigStoreImpl = mock(ConfigStoreImpl.class);

  private ConfigRuleEvaluator evaluator;

  @BeforeEach
  public void setup() {
    evaluator =
      new ConfigRuleEvaluator(mockConfigStoreImpl, new WeightedValueEvaluator());

    when(mockConfigStoreImpl.getConfigIncludedContext())
      .thenReturn(PrefabContextSetReadable.EMPTY);

    when(mockConfigStoreImpl.getGlobalContext())
      .thenReturn(PrefabContextSetReadable.EMPTY);
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
  void itRespectsCaseForContextPropertyLookups() {
    final Prefab.Criterion socialEmailCriteria = Prefab.Criterion
      .newBuilder()
      .setPropertyName("eMail")
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

    final EvaluatedCriterion bobExampleEvalCaseMismatch = evaluator
      .evaluateCriterionMatch(
        socialEmailCriteria,
        singleValueLookupContext("eMaiL", sv("bob@gmail.com"))
      )
      .stream()
      .findFirst()
      .get();
    assertThat(bobExampleEvalCaseMismatch.isMatch()).isFalse();

    final EvaluatedCriterion bobExampleEvalCaseMatch = evaluator
      .evaluateCriterionMatch(
        socialEmailCriteria,
        singleValueLookupContext("eMail", sv("bob@gmail.com"))
      )
      .stream()
      .findFirst()
      .get();
    assertThat(bobExampleEvalCaseMatch.isMatch()).isTrue();
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

  @ParameterizedTest
  @MethodSource("stringDoesNotContainArgs")
  public void testStringDoesNotContain(
    boolean isMatch,
    @Nullable String value,
    List<String> listContents
  ) {
    final String propertyName = "foo";

    final Prefab.Criterion stringContainsCriterion = Prefab.Criterion
      .newBuilder()
      .setPropertyName(propertyName)
      .setValueToMatch(ConfigValueUtils.from(listContents))
      .setOperator(Prefab.Criterion.CriterionOperator.PROP_DOES_NOT_CONTAIN_ONE_OF)
      .build();

    var lookupContext = value != null
      ? singleValueLookupContext(propertyName, sv(value))
      : LookupContext.EMPTY;

    testStringOperation(isMatch, stringContainsCriterion, lookupContext, value);
  }

  static Stream<Arguments> stringDoesNotContainArgs() {
    List<String> emptyList = Collections.emptyList();
    List<String> singleElementList = List.of("two");

    return Stream.of(
      Arguments.of(true, null, singleElementList),
      Arguments.of(false, "one two three", singleElementList),
      Arguments.of(false, "two three", singleElementList),
      Arguments.of(false, "two", singleElementList),
      Arguments.of(true, "Two", singleElementList),
      Arguments.of(true, "foobar", singleElementList),
      Arguments.of(true, "two", emptyList)
    );
  }

  @ParameterizedTest
  @MethodSource("stringContainsArgs")
  public void testStringContains(
    boolean isMatch,
    @Nullable String value,
    List<String> listContents
  ) {
    final String propertyName = "foo";

    final Prefab.Criterion stringContainsCriterion = Prefab.Criterion
      .newBuilder()
      .setPropertyName(propertyName)
      .setValueToMatch(ConfigValueUtils.from(listContents))
      .setOperator(Prefab.Criterion.CriterionOperator.PROP_CONTAINS_ONE_OF)
      .build();

    var lookupContext = value != null
      ? singleValueLookupContext(propertyName, sv(value))
      : LookupContext.EMPTY;

    testStringOperation(isMatch, stringContainsCriterion, lookupContext, value);
  }

  static Stream<Arguments> stringContainsArgs() {
    List<String> emptyList = Collections.emptyList();
    List<String> singleElementList = List.of("two");

    return Stream.of(
      Arguments.of(false, null, singleElementList),
      Arguments.of(true, "one two three", singleElementList),
      Arguments.of(true, "two three", singleElementList),
      Arguments.of(true, "two", singleElementList),
      Arguments.of(false, "Two", singleElementList),
      Arguments.of(false, "foobar", singleElementList),
      Arguments.of(false, "two", emptyList)
    );
  }

  @ParameterizedTest
  @MethodSource("stringDoesNotStartWithArgs")
  public void testStringDoesNotStartWith(
    boolean isMatch,
    @Nullable String value,
    List<String> listContents
  ) {
    final String propertyName = "foo";

    final Prefab.Criterion stringContainsCriterion = Prefab.Criterion
      .newBuilder()
      .setPropertyName(propertyName)
      .setValueToMatch(ConfigValueUtils.from(listContents))
      .setOperator(Prefab.Criterion.CriterionOperator.PROP_DOES_NOT_START_WITH_ONE_OF)
      .build();

    var lookupContext = value != null
      ? singleValueLookupContext(propertyName, sv(value))
      : LookupContext.EMPTY;

    testStringOperation(isMatch, stringContainsCriterion, lookupContext, value);
  }

  static Stream<Arguments> stringDoesNotStartWithArgs() {
    List<String> emptyList = Collections.emptyList();
    List<String> singleElementList = List.of("two");
    List<String> twoElementList = List.of("two", "one");

    return Stream.of(
      Arguments.of(true, null, singleElementList),
      Arguments.of(true, "one two three", singleElementList),
      Arguments.of(false, "one two three", twoElementList),
      Arguments.of(false, "two three", singleElementList),
      Arguments.of(false, "two", singleElementList),
      Arguments.of(true, "Two", singleElementList),
      Arguments.of(true, "foobar", singleElementList),
      Arguments.of(true, "two", emptyList)
    );
  }

  @ParameterizedTest
  @MethodSource("stringStartsWithArgs")
  public void testStringStartsWith(
    boolean isMatch,
    @Nullable String value,
    List<String> listContents
  ) {
    final String propertyName = "foo";

    final Prefab.Criterion stringContainsCriterion = Prefab.Criterion
      .newBuilder()
      .setPropertyName(propertyName)
      .setValueToMatch(ConfigValueUtils.from(listContents))
      .setOperator(Prefab.Criterion.CriterionOperator.PROP_STARTS_WITH_ONE_OF)
      .build();

    var lookupContext = value != null
      ? singleValueLookupContext(propertyName, sv(value))
      : LookupContext.EMPTY;

    testStringOperation(isMatch, stringContainsCriterion, lookupContext, value);
  }

  static Stream<Arguments> stringStartsWithArgs() {
    List<String> emptyList = Collections.emptyList();
    List<String> singleElementList = List.of("two");
    List<String> twoElementList = List.of("two", "one");

    return Stream.of(
      Arguments.of(false, null, singleElementList),
      Arguments.of(false, "one two three", singleElementList),
      Arguments.of(true, "one two three", twoElementList),
      Arguments.of(true, "two three", singleElementList),
      Arguments.of(true, "two", singleElementList),
      Arguments.of(false, "Two", singleElementList),
      Arguments.of(false, "foobar", singleElementList),
      Arguments.of(false, "two", emptyList)
    );
  }

  void testStringOperation(
    boolean isMatch,
    Prefab.Criterion criterion,
    LookupContext lookupContext,
    @Nullable String value
  ) {
    var eval = evaluator
      .evaluateCriterionMatch(criterion, lookupContext)
      .stream()
      .findFirst()
      .orElseThrow();
    assertThat(eval.isMatch()).isEqualTo(isMatch);
    if (isMatch) {
      if (value != null) {
        assertThat(eval.getEvaluatedProperty().orElseThrow().getString())
          .isEqualTo(value);
      } else {
        assertThat(eval.getEvaluatedProperty()).isEmpty();
      }
    }
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

  public static Stream<Arguments> comparisonArguments() {
    return Stream.of(
      Arguments.of(
        ConfigValueUtils.from(2),
        Prefab.Criterion.CriterionOperator.PROP_GREATER_THAN_OR_EQUAL,
        ConfigValueUtils.from(1),
        true
      ),
      Arguments.of(
        ConfigValueUtils.from(2),
        Prefab.Criterion.CriterionOperator.PROP_GREATER_THAN,
        ConfigValueUtils.from(1),
        true
      ),
      Arguments.of(
        ConfigValueUtils.from(2),
        Prefab.Criterion.CriterionOperator.PROP_GREATER_THAN_OR_EQUAL,
        ConfigValueUtils.from(2),
        true
      ),
      Arguments.of(
        ConfigValueUtils.from(2),
        Prefab.Criterion.CriterionOperator.PROP_GREATER_THAN,
        ConfigValueUtils.from(2),
        false
      ),
      Arguments.of(
        ConfigValueUtils.from(1),
        Prefab.Criterion.CriterionOperator.PROP_GREATER_THAN_OR_EQUAL,
        ConfigValueUtils.from(2),
        false
      ),
      Arguments.of(
        ConfigValueUtils.from(1),
        Prefab.Criterion.CriterionOperator.PROP_GREATER_THAN,
        ConfigValueUtils.from(2),
        false
      ),
      Arguments.of(
        ConfigValueUtils.from(2),
        Prefab.Criterion.CriterionOperator.PROP_LESS_THAN_OR_EQUAL,
        ConfigValueUtils.from(1),
        false
      ),
      Arguments.of(
        ConfigValueUtils.from(2),
        Prefab.Criterion.CriterionOperator.PROP_LESS_THAN,
        ConfigValueUtils.from(1),
        false
      ),
      Arguments.of(
        ConfigValueUtils.from(2),
        Prefab.Criterion.CriterionOperator.PROP_LESS_THAN_OR_EQUAL,
        ConfigValueUtils.from(2),
        true
      ),
      Arguments.of(
        ConfigValueUtils.from(2),
        Prefab.Criterion.CriterionOperator.PROP_LESS_THAN,
        ConfigValueUtils.from(2),
        false
      ),
      Arguments.of(
        ConfigValueUtils.from(1),
        Prefab.Criterion.CriterionOperator.PROP_LESS_THAN_OR_EQUAL,
        ConfigValueUtils.from(2),
        true
      ),
      Arguments.of(
        ConfigValueUtils.from(1),
        Prefab.Criterion.CriterionOperator.PROP_LESS_THAN,
        ConfigValueUtils.from(2),
        true
      ),
      Arguments.of(
        ConfigValueUtils.from(2.1),
        Prefab.Criterion.CriterionOperator.PROP_GREATER_THAN_OR_EQUAL,
        ConfigValueUtils.from(1),
        true
      ),
      Arguments.of(
        ConfigValueUtils.from(2),
        Prefab.Criterion.CriterionOperator.PROP_GREATER_THAN_OR_EQUAL,
        ConfigValueUtils.from(1.1),
        true
      ),
      Arguments.of(
        ConfigValueUtils.from(2.1),
        Prefab.Criterion.CriterionOperator.PROP_GREATER_THAN_OR_EQUAL,
        ConfigValueUtils.from(1.1),
        true
      ),
      Arguments.of(
        ConfigValueUtils.from("2.1"),
        Prefab.Criterion.CriterionOperator.PROP_GREATER_THAN_OR_EQUAL,
        ConfigValueUtils.from(1),
        false
      ),
      Arguments.of(
        ConfigValueUtils.from(2.1),
        Prefab.Criterion.CriterionOperator.PROP_GREATER_THAN_OR_EQUAL,
        ConfigValueUtils.from("1"),
        false
      ),
      Arguments.of(
        ConfigValueUtils.from("2024-01-01T00:00:00z"),
        Prefab.Criterion.CriterionOperator.PROP_AFTER,
        ConfigValueUtils.from("2024-01-01T00:00:00z"),
        false
      ),
      Arguments.of(
        ConfigValueUtils.from("2024-01-02T00:00:00z"),
        Prefab.Criterion.CriterionOperator.PROP_AFTER,
        ConfigValueUtils.from("2024-01-01T00:00:00z"),
        true
      ),
      Arguments.of(
        ConfigValueUtils.from("2024-01-01T00:00:00z"),
        Prefab.Criterion.CriterionOperator.PROP_AFTER,
        ConfigValueUtils.from("2024-01-01T00:00:00z"),
        false
      ),
      Arguments.of(
        ConfigValueUtils.from("2024-01-01T00:00:00z"),
        Prefab.Criterion.CriterionOperator.PROP_AFTER,
        ConfigValueUtils.from("2024-01-01T00:00:00z"),
        false
      ),
      Arguments.of(
        ConfigValueUtils.from("2024-01-01T00:00:00z"),
        Prefab.Criterion.CriterionOperator.PROP_BEFORE,
        ConfigValueUtils.from("2024-01-02T00:00:00z"),
        true
      ),
      Arguments.of(
        ConfigValueUtils.from("2024-01-02T00:00:00z"),
        Prefab.Criterion.CriterionOperator.PROP_BEFORE,
        ConfigValueUtils.from("2024-01-01T00:00:00z"),
        false
      ),
      Arguments.of(
        ConfigValueUtils.from(
          ZonedDateTime.parse("2024-01-02T00:00:00z").toInstant().toEpochMilli()
        ),
        Prefab.Criterion.CriterionOperator.PROP_AFTER,
        ConfigValueUtils.from("2024-01-01T00:00:00z"),
        true
      )
    );
  }

  @MethodSource("comparisonArguments")
  @ParameterizedTest
  void testComparison(
    Prefab.ConfigValue propertyValue,
    Prefab.Criterion.CriterionOperator operator,
    Prefab.ConfigValue criterionValue,
    boolean expectedMatch
  ) {
    String contextName = "user";
    String propertyName = "fooCount";
    String fullPropertyName = contextName + "." + propertyName;
    Prefab.Criterion criterion = Prefab.Criterion
      .newBuilder()
      .setPropertyName(fullPropertyName)
      .setOperator(operator)
      .setValueToMatch(criterionValue)
      .build();
    LookupContext lookupContext = new LookupContext(
      new PrefabContextSet()
        .addContext(
          PrefabContext.newBuilder(contextName).put(propertyName, propertyValue).build()
        )
    );
    assertThat(
      evaluator
        .evaluateCriterionMatch(criterion, lookupContext)
        .stream()
        .findFirst()
        .orElseThrow()
        .isMatch()
    )
      .isEqualTo(expectedMatch);
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

  private static LookupContext singleValueLookupContext(
    String propName,
    @Nullable Prefab.ConfigValue configValue
  ) {
    if (configValue != null) {
      return new LookupContext(
        PrefabContext.unnamedFromMap(Map.of(propName, configValue))
      );
    }
    return LookupContext.EMPTY;
  }
}
