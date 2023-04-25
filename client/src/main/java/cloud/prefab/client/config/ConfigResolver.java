package cloud.prefab.client.config;

import cloud.prefab.client.ConfigStore;
import cloud.prefab.client.config.logging.AbstractLoggingListener;
import cloud.prefab.domain.Prefab;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigResolver {

  public static final String NAMESPACE_KEY = "NAMESPACE";

  private static final Logger LOG = LoggerFactory.getLogger(ConfigResolver.class);

  private final ConfigStore configStore;
  private final WeightedValueEvaluator weightedValueEvaluator;

  private long projectEnvId = 0;

  public ConfigResolver(
    ConfigStore configStoreImpl,
    WeightedValueEvaluator weightedValueEvaluator
  ) {
    this(configStoreImpl, 0L, weightedValueEvaluator);
  }

  public ConfigResolver(
    ConfigStore configStoreImpl,
    long projectEnvId,
    WeightedValueEvaluator weightedValueEvaluator
  ) {
    this.weightedValueEvaluator = weightedValueEvaluator;
    this.projectEnvId = projectEnvId;
    this.configStore = configStoreImpl;
  }

  public Optional<Prefab.ConfigValue> getConfigValue(String key) {
    return getConfigValue(key, LookupContext.EMPTY);
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

    return evalConfigElementMatch(configElement, lookupContext);
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

    return evalConfigElementMatch(configElement, lookupContext, rowPropertiesStack);
  }

  public Optional<Prefab.ConfigValue> getConfigValue(
    String key,
    LookupContext lookupContext
  ) {
    return getMatch(key, lookupContext).map(Match::getConfigValue);
  }

  /**
   * Get all currently known parameter-less config values.
   * ConfigValues that are not visible unless passed an appropriate map of parameters are not here
   * Note: these values ARE NOT LIVE and will not change values
   * @return
   */
  public Map<String, Prefab.ConfigValue> getAllCurrentValues() {
    ImmutableMap.Builder<String, Prefab.ConfigValue> allValues = ImmutableMap.builder();
    for (String key : getKeys()) {
      getConfigValue(key).ifPresent(configValue -> allValues.put(key, configValue));
    }
    return allValues.buildKeepingLast();
  }

  private Optional<Match> evalConfigElementMatch(
    ConfigElement configElement,
    LookupContext lookupContext,
    Deque<Map<String, Prefab.ConfigValue>> rowPropertiesStack
  ) {
    // Prefer rows that have a projEnvId to ones that don't
    // There will be 0-1 rows with projenv and 0-1 rows without (the default row)

    final Optional<Match> match = configElement
      .getRowsProjEnvFirst(projectEnvId)
      .map(configRow -> {
        if (!configRow.getPropertiesMap().isEmpty()) {
          rowPropertiesStack.push(configRow.getPropertiesMap());
        }
        // Return the value of the first matching set of criteria
        for (Prefab.ConditionalValue conditionalValue : configRow.getValuesList()) {
          Optional<Match> optionalMatch = evaluateConditionalValue(
            conditionalValue,
            lookupContext,
            rowPropertiesStack,
            configElement
          );

          if (optionalMatch.isPresent()) {
            return optionalMatch.get();
          }
        }
        if (!configRow.getPropertiesMap().isEmpty()) {
          rowPropertiesStack.pop();
        }
        return null;
      })
      .filter(Objects::nonNull)
      .findFirst();

    return match;
  }

  /**
   * find if we have a match for the given properties
   *
   * @param configElement
   * @param lookupContext
   * @return
   */
  Optional<Match> evalConfigElementMatch(
    ConfigElement configElement,
    LookupContext lookupContext
  ) {
    return evalConfigElementMatch(configElement, lookupContext, new LinkedList<>());
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
    Prefab.ConditionalValue conditionalValue,
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

    Prefab.ConfigValue simplified = simplify(
      conditionalValue,
      configElement.getConfig().getKey(),
      lookupContext
    );

    return Optional.of(new Match(simplified, configElement, evaluatedCriteria));
  }

  /**
   * A ConfigValue may be a WeightedValue. If so break it down so we can return a simpler form.
   */
  private Prefab.ConfigValue simplify(
    Prefab.ConditionalValue conditionalValue,
    String key,
    LookupContext lookupContext
  ) {
    if (conditionalValue.getValue().hasWeightedValues()) {
      return weightedValueEvaluator.toValue(
        conditionalValue.getValue().getWeightedValues(),
        key,
        lookupContext
      );
    } else {
      return conditionalValue.getValue();
    }
  }

  private Optional<Prefab.ConfigValue> prop(
    String key,
    LookupContext lookupContext,
    Deque<Map<String, Prefab.ConfigValue>> rowPropertiesStack
  ) {
    for (Map<String, Prefab.ConfigValue> rowProperties : rowPropertiesStack) {
      if (rowProperties.containsKey(key)) {
        return Optional.of(rowProperties.get(key));
      }
    }
    Prefab.ConfigValue valueFromLookupContext = lookupContext
      .getExpandedProperties()
      .get(key);
    if (valueFromLookupContext != null) {
      return Optional.of(valueFromLookupContext);
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
      case IN_TIME_RANGE:
        if (prop.isEmpty() || !criterion.getValueToMatch().hasTimeRange()) {
          return List.of(new EvaluatedCriterion(criterion, false));
        }
        long currentTime = prop.get().getInt();
        Prefab.TimeRange timeRange = criterion.getValueToMatch().getTimeRange();
        if (timeRange.hasStart() && currentTime < timeRange.getStart()) {
          return List.of(new EvaluatedCriterion(criterion, false));
        }
        if (timeRange.hasEnd() && currentTime > timeRange.getEnd()) {
          return List.of(new EvaluatedCriterion(criterion, false));
        }
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
        final Optional<Prefab.ConfigValue> evaluatedNotSegment = getConfigValue(
          criterion.getValueToMatch().getString(),
          lookupContext
        );

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
        if (!propStringValue.isPresent()) {
          return List.of(new EvaluatedCriterion(criterion, false));
        }
        // assumption that property is a String
        return List.of(
          new EvaluatedCriterion(
            criterion,
            propStringValue.get(),
            criterion
              .getValueToMatch()
              .getStringList()
              .getValuesList()
              .contains(propStringValue.get())
          )
        );
      case PROP_IS_NOT_ONE_OF:
        if (!propStringValue.isPresent()) {
          return List.of(new EvaluatedCriterion(criterion, false));
        }

        return List.of(
          new EvaluatedCriterion(
            criterion,
            propStringValue.get(),
            !criterion
              .getValueToMatch()
              .getStringList()
              .getValuesList()
              .contains(propStringValue.get())
          )
        );
      case PROP_ENDS_WITH_ONE_OF:
        if (prop.isPresent() && prop.get().hasString()) {
          final boolean matched = criterion
            .getValueToMatch()
            .getStringList()
            .getValuesList()
            .stream()
            .anyMatch(value -> prop.get().getString().endsWith(value));

          return List.of(new EvaluatedCriterion(criterion, prop.get(), matched));
        } else {
          return List.of(new EvaluatedCriterion(criterion, false));
        }
      case PROP_DOES_NOT_END_WITH_ONE_OF:
        if (prop.isPresent() && prop.get().hasString()) {
          final boolean matched = criterion
            .getValueToMatch()
            .getStringList()
            .getValuesList()
            .stream()
            .anyMatch(value -> prop.get().getString().endsWith(value));

          return List.of(new EvaluatedCriterion(criterion, prop.get(), !matched));
        } else {
          return List.of(new EvaluatedCriterion(criterion, true));
        }
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

  public boolean setProjectEnvId(Prefab.Configs configs) {
    if (configs.hasConfigServicePointer()) {
      this.projectEnvId = configs.getConfigServicePointer().getProjectEnvId();
      return true;
    }
    return false;
  }

  public Collection<String> getKeys() {
    return configStore.getKeys();
  }

  public boolean containsKey(String key) {
    return configStore.containsKey(key);
  }

  public String contentsString() {
    StringBuilder sb = new StringBuilder();
    List<String> sortedKeys = new ArrayList(getKeys());
    Collections.sort(sortedKeys);
    for (String key : sortedKeys) {
      ConfigElement configElement = configStore.getElement(key);
      final Optional<Match> match = evalConfigElementMatch(
        configElement,
        LookupContext.EMPTY
      );
      if (match.isPresent()) {
        sb.append(padded(key, 45));
        sb.append(padded(toS(match.get().getConfigValue()), 40));
        sb.append(padded(configElement.getProvenance().toString(), 40));
        sb.append(padded(match.get().getReason(), 40));
        sb.append("\n");
      }
    }
    return sb.toString();
  }

  private String toS(Prefab.ConfigValue configValue) {
    if (configValue.getTypeCase() == Prefab.ConfigValue.TypeCase.STRING) {
      return configValue.getString();
    } else if (configValue.getTypeCase() == Prefab.ConfigValue.TypeCase.INT) {
      return Long.toString(configValue.getInt());
    } else if (configValue.getTypeCase() == Prefab.ConfigValue.TypeCase.BOOL) {
      return Boolean.toString(configValue.getBool());
    } else if (configValue.getTypeCase() == Prefab.ConfigValue.TypeCase.BYTES) {
      return "Bytes";
    } else if (configValue.getTypeCase() == Prefab.ConfigValue.TypeCase.DOUBLE) {
      return Double.toString(configValue.getDouble());
    } else if (configValue.getTypeCase() == Prefab.ConfigValue.TypeCase.LOG_LEVEL) {
      return configValue.getLogLevel().toString();
    } else {
      return "Unknown";
    }
  }

  private String padded(String s, int size) {
    return String.format(
      "%-" + size + "s",
      s.substring(0, Math.min(s.length(), size - 1))
    );
  }
}
