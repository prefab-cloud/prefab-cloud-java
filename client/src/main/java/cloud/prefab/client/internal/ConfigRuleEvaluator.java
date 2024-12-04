package cloud.prefab.client.internal;

import cloud.prefab.client.ConfigStore;
import cloud.prefab.client.config.ConfigElement;
import cloud.prefab.client.config.ConfigValueUtils;
import cloud.prefab.client.config.EvaluatedCriterion;
import cloud.prefab.client.config.Match;
import cloud.prefab.client.config.logging.AbstractLoggingListener;
import cloud.prefab.domain.Prefab;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigRuleEvaluator {

  public static final String CURRENT_TIME_KEY = "prefab.current-time";
  private static final Logger LOG = LoggerFactory.getLogger(ConfigRuleEvaluator.class);

  private final ConfigStore configStore;
  private final WeightedValueEvaluator weightedValueEvaluator;

  public ConfigRuleEvaluator(
    ConfigStore configStoreImpl,
    WeightedValueEvaluator weightedValueEvaluator
  ) {
    this.weightedValueEvaluator = weightedValueEvaluator;
    this.configStore = configStoreImpl;
  }

  public Optional<Match> getMatch(String key, LookupContext lookupContext) {
    final ConfigElement configElement = configStore.getElement(key);
    if (configElement == null) {
      // logging lookups generate a lot of misses so skip those
      if (!key.startsWith(AbstractLoggingListener.LOG_LEVEL_PREFIX)) {
        LOG.trace("No config value found for key {}", key);
      }
      return Optional.empty();
    }

    return getMatch(configElement, lookupContext);
  }

  /**
   * find if we have a match for the given properties
   *
   * @param configElement
   * @param lookupContext
   * @return
   */
  public Optional<Match> getMatch(
    ConfigElement configElement,
    LookupContext lookupContext
  ) {
    return getMatch(configElement, lookupContext, new LinkedList<>());
  }

  private Optional<Match> getMatch(
    String key,
    LookupContext lookupContext,
    Deque<Map<String, Prefab.ConfigValue>> rowPropertiesStack
  ) {
    if (!configStore.containsKey(key)) {
      // logging lookups generate a lot of misses so skip those
      if (!key.startsWith(AbstractLoggingListener.LOG_LEVEL_PREFIX)) {
        LOG.trace("No config value found for key {}", key);
      }
      return Optional.empty();
    }
    final ConfigElement configElement = configStore.getElement(key);

    return getMatch(configElement, lookupContext, rowPropertiesStack);
  }

  private Optional<Match> getMatch(
    ConfigElement configElement,
    LookupContext lookupContext,
    Deque<Map<String, Prefab.ConfigValue>> rowPropertiesStack
  ) {
    // Prefer rows that have a projEnvId to ones that don't
    // There will be 0-1 rows with projenv and 0-1 rows without (the default row)

    return Streams
      .mapWithIndex(
        configElement.getRowsProjEnvFirst(configStore.getProjectEnvironmentId()),
        (configRow, rowIndex) -> {
          if (!configRow.getPropertiesMap().isEmpty()) {
            rowPropertiesStack.push(configRow.getPropertiesMap());
          }
          // Return the value of the first matching set of criteria
          int conditionalValueIndex = 0;
          for (Prefab.ConditionalValue conditionalValue : configRow.getValuesList()) {
            Optional<Match> optionalMatch = evaluateConditionalValue(
              rowIndex,
              conditionalValue,
              conditionalValueIndex,
              lookupContext,
              rowPropertiesStack,
              configElement
            );

            if (optionalMatch.isPresent()) {
              return optionalMatch.get();
            }
            conditionalValueIndex++;
          }
          if (!configRow.getPropertiesMap().isEmpty()) {
            rowPropertiesStack.pop();
          }
          return null;
        }
      )
      .filter(Objects::nonNull)
      .findFirst();
  }

  /**
   * If all of the conditions match, return a true match
   *
   * @param conditionalValue
   * @param rowProperties
   * @param configElement
   * @return
   */
  private Optional<Match> evaluateConditionalValue(
    long rowIndex,
    Prefab.ConditionalValue conditionalValue,
    int conditionalValueIndex,
    LookupContext lookupContext,
    Deque<Map<String, Prefab.ConfigValue>> rowProperties,
    ConfigElement configElement
  ) {
    List<EvaluatedCriterion> evaluatedCriteria = new ArrayList<>();
    for (Prefab.Criterion criterion : conditionalValue.getCriteriaList()) {
      for (EvaluatedCriterion evaluateCriterion : evaluateCriterionMatch(
        criterion,
        lookupContext,
        rowProperties
      )) {
        if (!evaluateCriterion.isMatch()) {
          return Optional.empty();
        }
        evaluatedCriteria.add(evaluateCriterion);
      }
    }
    return Optional.of(
      simplifyToMatch(
        rowIndex,
        conditionalValue,
        conditionalValueIndex,
        configElement,
        lookupContext,
        evaluatedCriteria
      )
    );
  }

  /**
   * A ConfigValue may be a WeightedValue. If so break it down so we can return a simpler form.
   */
  private Match simplifyToMatch(
    long rowIndex,
    Prefab.ConditionalValue selectedConditionalValue,
    int conditionalValueIndex,
    ConfigElement configElement,
    LookupContext lookupContext,
    List<EvaluatedCriterion> evaluatedCriteria
  ) {
    if (selectedConditionalValue.getValue().hasWeightedValues()) {
      WeightedValueEvaluator.Result result = weightedValueEvaluator.toResult(
        selectedConditionalValue.getValue().getWeightedValues(),
        configElement.getConfig().getKey(),
        lookupContext
      );
      return new Match(
        result.getValue(),
        configElement,
        evaluatedCriteria,
        (int) rowIndex,
        conditionalValueIndex,
        Optional.of(result.getIndex())
      );
    } else {
      return new Match(
        selectedConditionalValue.getValue(),
        configElement,
        evaluatedCriteria,
        (int) rowIndex,
        conditionalValueIndex,
        Optional.empty()
      );
    }
  }

  private List<String> keyAndLowerCasedKey(String key) {
    String lowerCased = key.toLowerCase();
    if (lowerCased.equals(key)) {
      return Collections.singletonList(key);
    }
    return List.of(key, lowerCased);
  }

  private Optional<Prefab.ConfigValue> prop(
    String key,
    LookupContext lookupContext,
    Deque<Map<String, Prefab.ConfigValue>> rowPropertiesStack
  ) {
    List<String> keysToLookup = keyAndLowerCasedKey(key);

    for (Map<String, Prefab.ConfigValue> rowProperties : rowPropertiesStack) {
      for (String keyToLookup : keysToLookup) {
        Prefab.ConfigValue rowPropValue = rowProperties.get(keyToLookup);
        if (rowPropValue != null) {
          return Optional.of(rowPropValue);
        }
      }
    }

    for (String keyToLookup : keysToLookup) {
      Prefab.ConfigValue valueFromLookupContext = lookupContext
        .getExpandedProperties()
        .get(keyToLookup);
      if (valueFromLookupContext != null) {
        return Optional.of(valueFromLookupContext);
      }
    }
    //TODO: move this current time injection into a ContextResolver class?
    if (CURRENT_TIME_KEY.equals(key)) {
      return Optional.of(
        Prefab.ConfigValue.newBuilder().setInt(System.currentTimeMillis()).build()
      );
    }
    return Optional.empty();
  }

  private Optional<Prefab.ConfigValue> getPropFromContextWrapper(
    List<String> keysToLookup,
    ContextWrapper contextWrapper
  ) {
    for (String keyToLookup : keysToLookup) {
      Prefab.ConfigValue configValue = contextWrapper
        .getConfigValueMap()
        .get(keyToLookup);
      if (configValue != null) {
        return Optional.of(configValue);
      }
    }
    return Optional.empty();
  }

  List<EvaluatedCriterion> evaluateCriterionMatch(
    Prefab.Criterion criterion,
    LookupContext lookupContext
  ) {
    return evaluateCriterionMatch(criterion, lookupContext, new LinkedList<>());
  }

  /**
   * Does this criterion match?
   *
   * @param criterion
   * @return
   */
  List<EvaluatedCriterion> evaluateCriterionMatch(
    Prefab.Criterion criterion,
    LookupContext lookupContext,
    Deque<Map<String, Prefab.ConfigValue>> rowPropertiesStack
  ) {
    final Optional<Prefab.ConfigValue> prop = prop(
      criterion.getPropertyName(),
      lookupContext,
      rowPropertiesStack
    );
    Optional<String> propStringValue = prop.flatMap(ConfigValueUtils::coerceToString);

    switch (criterion.getOperator()) {
      case ALWAYS_TRUE:
        return List.of(new EvaluatedCriterion(criterion, true));
      case HIERARCHICAL_MATCH:
        if (prop.isPresent()) {
          if (prop.get().hasString() && criterion.getValueToMatch().hasString()) {
            final String propertyString = prop.get().getString();
            return List.of(
              new EvaluatedCriterion(
                criterion,
                criterion.getValueToMatch(),
                hierarchicalMatch(propertyString, criterion.getValueToMatch().getString())
              )
            );
          }
        }
        return List.of(
          new EvaluatedCriterion(criterion, criterion.getValueToMatch(), false)
        );
      // The string here is the key of the Segment
      case IN_SEG:
        final Optional<Match> evaluatedSegment = getMatch(
          criterion.getValueToMatch().getString(),
          lookupContext,
          rowPropertiesStack
        );

        if (
          evaluatedSegment.isPresent() &&
          evaluatedSegment.get().getConfigValue().hasBool() &&
          evaluatedSegment.get().getConfigValue().getBool()
        ) {
          return evaluatedSegment.get().getEvaluatedCriterion();
        } else {
          return List.of(
            new EvaluatedCriterion(
              criterion,
              "Missing Segment " + criterion.getValueToMatch().getString(),
              false
            )
          );
        }
      case NOT_IN_SEG:
        final Optional<Prefab.ConfigValue> evaluatedNotSegment = getMatch(
          criterion.getValueToMatch().getString(),
          lookupContext
        )
          .map(Match::getConfigValue);

        if (evaluatedNotSegment.isPresent() && evaluatedNotSegment.get().hasBool()) {
          return List.of(
            new EvaluatedCriterion(
              criterion,
              criterion.getValueToMatch(),
              !evaluatedNotSegment.get().getBool()
            )
          );
        } else {
          return List.of(
            new EvaluatedCriterion(
              criterion,
              "Missing Segment " + criterion.getValueToMatch().getString(),
              true
            )
          );
        }
      case PROP_IS_ONE_OF:
      // fall through
      case PROP_IS_NOT_ONE_OF:
        // this is actually going to function as intersection -- true if there is non-empty overlap between the collection value on the left or on the right
        Set<String> contextValueSet = prop
          .map(ConfigRuleEvaluator::coerceToStringList)
          .orElseGet(Collections::emptySet);
        if (contextValueSet.isEmpty()) {
          return List.of(
            new EvaluatedCriterion(
              criterion,
              criterion.getOperator() ==
              Prefab.Criterion.CriterionOperator.PROP_IS_NOT_ONE_OF
            )
          );
        }
        boolean nonEmptyIntersection = !Sets
          .intersection(
            contextValueSet,
            Set.copyOf(criterion.getValueToMatch().getStringList().getValuesList())
          )
          .isEmpty();

        // assumption that property is a String
        return List.of(
          new EvaluatedCriterion(
            criterion,
            propStringValue.get(),
            nonEmptyIntersection ==
            (criterion.getOperator() == Prefab.Criterion.CriterionOperator.PROP_IS_ONE_OF)
          )
        );
      case PROP_ENDS_WITH_ONE_OF:
      // fall through
      case PROP_DOES_NOT_END_WITH_ONE_OF:
        return evaluateStringOperation(
          criterion,
          criterion.getOperator() ==
          Prefab.Criterion.CriterionOperator.PROP_DOES_NOT_END_WITH_ONE_OF,
          String::endsWith,
          prop
        );
      case PROP_CONTAINS_ONE_OF:
      // fall through
      case PROP_DOES_NOT_CONTAIN_ONE_OF:
        return evaluateStringOperation(
          criterion,
          criterion.getOperator() ==
          Prefab.Criterion.CriterionOperator.PROP_DOES_NOT_CONTAIN_ONE_OF,
          String::contains,
          prop
        );
      case PROP_STARTS_WITH_ONE_OF:
      // fall through
      case PROP_DOES_NOT_START_WITH_ONE_OF:
        return evaluateStringOperation(
          criterion,
          criterion.getOperator() ==
          Prefab.Criterion.CriterionOperator.PROP_DOES_NOT_START_WITH_ONE_OF,
          String::startsWith,
          prop
        );
      case IN_INT_RANGE:
        if (
          prop.isPresent() &&
          prop.get().hasInt() &&
          criterion.getValueToMatch().hasIntRange()
        ) {
          return List.of(
            new EvaluatedCriterion(
              criterion,
              IntRangeWrapper
                .of(criterion.getValueToMatch().getIntRange())
                .contains(prop.get().getInt())
            )
          );
        }
      case PROP_GREATER_THAN:
      // fall through
      case PROP_GREATER_THAN_OR_EQUAL:
      // fall through
      case PROP_LESS_THAN:
      // fall through
      case PROP_LESS_THAN_OR_EQUAL:
        return evaluateNumericComparison(criterion, prop);
      default:
        LOG.debug(
          "Unexpected operator {} found in criterion {}",
          criterion.getOperator(),
          criterion
        );
    }
    // Unknown Operator
    return List.of(new EvaluatedCriterion(criterion, false));
  }

  private List<EvaluatedCriterion> evaluateNumericComparison(
    Prefab.Criterion criterion,
    Optional<Prefab.ConfigValue> valueFromContext
  ) {
    boolean comparison = false;

    Optional<Number> numberToMatch = getCriterionValueToMatch(criterion)
      .flatMap(this::getNumber);
    Optional<Number> numberFromContext = valueFromContext.flatMap(this::getNumber);

    if (numberToMatch.isPresent() && numberFromContext.isPresent()) {
      comparison =
        Optional
          .ofNullable(NUMERIC_COMPARE_TO_EVAL.get(criterion.getOperator()))
          .orElse(v -> false)
          .test(compareTo(numberFromContext.get(), numberToMatch.get()));
    }

    return List.of(new EvaluatedCriterion(criterion, valueFromContext, comparison));
  }

  private int compareTo(Number number1, Number number2) {
    return Double.compare(number1.doubleValue(), number2.doubleValue());
  }

  Map<Prefab.Criterion.CriterionOperator, Predicate<Integer>> NUMERIC_COMPARE_TO_EVAL = ImmutableMap.of(
    Prefab.Criterion.CriterionOperator.PROP_GREATER_THAN,
    v -> v > 0,
    Prefab.Criterion.CriterionOperator.PROP_GREATER_THAN_OR_EQUAL,
    v -> v >= 0,
    Prefab.Criterion.CriterionOperator.PROP_LESS_THAN,
    v -> v < 0,
    Prefab.Criterion.CriterionOperator.PROP_LESS_THAN_OR_EQUAL,
    v -> v <= 0
  );

  private Optional<Prefab.ConfigValue> getCriterionValueToMatch(
    Prefab.Criterion criterion
  ) {
    if (criterion.hasValueToMatch()) {
      return Optional.of(criterion.getValueToMatch());
    }
    return Optional.empty();
  }

  private Optional<Number> getNumber(Prefab.ConfigValue configValue) {
    if (configValue.hasInt()) {
      return Optional.of(configValue.getInt());
    }
    if (configValue.hasDouble()) {
      return Optional.of(configValue.getDouble());
    }
    return Optional.empty();
  }

  static boolean negate(boolean result, boolean negate) {
    if (negate) {
      return !result;
    }
    return result;
  }

  List<EvaluatedCriterion> evaluateStringOperation(
    Prefab.Criterion criterion,
    boolean negated,
    BiPredicate<String, String> predicate,
    Optional<Prefab.ConfigValue> prop
  ) {
    if (prop.isPresent() && prop.get().hasString()) {
      final boolean matched = criterion
        .getValueToMatch()
        .getStringList()
        .getValuesList()
        .stream()
        .anyMatch(value -> predicate.test(prop.get().getString(), value));

      return List.of(
        new EvaluatedCriterion(criterion, prop.get(), negate(matched, negated))
      );
    } else {
      return List.of(new EvaluatedCriterion(criterion, negated));
    }
  }

  /**
   * a.b.c match a.b -> true
   * a.b match a.b.c -> false
   *
   * @param valueToMatch
   * @param propertyString
   * @return
   */
  boolean hierarchicalMatch(String propertyString, String valueToMatch) {
    return propertyString.startsWith(valueToMatch);
  }

  public Collection<String> getKeys() {
    return configStore.getKeys();
  }

  public Collection<String> getKeysOfConfigType(Prefab.ConfigType configType) {
    return configStore
      .getElements()
      .stream()
      .map(ConfigElement::getConfig)
      .filter(config -> config.getConfigType() == configType)
      .map(Prefab.Config::getKey)
      .collect(Collectors.toList());
  }

  public ConfigElement getRaw(String key) {
    return configStore.getElement(key);
  }

  public boolean containsKey(String key) {
    return configStore.containsKey(key);
  }

  private static Set<String> coerceToStringList(Prefab.ConfigValue configValue) {
    if (configValue.getTypeCase() == Prefab.ConfigValue.TypeCase.STRING_LIST) {
      return Set.copyOf(configValue.getStringList().getValuesList());
    }
    return ConfigValueUtils
      .coerceToString(configValue)
      .map(Collections::singleton)
      .orElseGet(Collections::emptySet);
  }
}
