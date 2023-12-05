package cloud.prefab.client.internal;

import cloud.prefab.client.ConfigStore;
import cloud.prefab.client.config.ConfigElement;
import cloud.prefab.client.config.ConfigValueUtils;
import cloud.prefab.client.config.EvaluatedCriterion;
import cloud.prefab.client.config.Match;
import cloud.prefab.client.config.logging.AbstractLoggingListener;
import cloud.prefab.context.PrefabContext;
import cloud.prefab.domain.Prefab;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Streams;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigResolver {

  public static final String NAMESPACE_KEY = "NAMESPACE";
  public static final String NEW_NAMESPACE_KEY = "prefab.namespace";

  public static final String CURRENT_TIME_KEY = "prefab.current-time";
  private static final Logger LOG = LoggerFactory.getLogger(ConfigResolver.class);

  private final ConfigStore configStore;
  private final WeightedValueEvaluator weightedValueEvaluator;

  private long projectEnvId = 0;

  private AtomicReference<Map<String, Prefab.ConfigValue>> defaultContext = new AtomicReference<>(
    Collections.emptyMap()
  );

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

    return Streams
      .mapWithIndex(
        configElement.getRowsProjEnvFirst(projectEnvId),
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
      Prefab.ConfigValue configFromDefaultContext = defaultContext.get().get(keyToLookup);
      if (configFromDefaultContext != null) {
        return Optional.of(configFromDefaultContext);
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

    //TODO: move this current time injection into a ContextResolver class
    if (CURRENT_TIME_KEY.equals(key)) {
      return Optional.of(
        Prefab.ConfigValue.newBuilder().setInt(System.currentTimeMillis()).build()
      );
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
        if (propStringValue.isEmpty()) {
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
        if (propStringValue.isEmpty()) {
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

  public void setDefaultContext(Prefab.Configs configs) {
    if (configs.getDefaultContext().getContextsCount() == 0) {
      defaultContext.set(Collections.emptyMap());
    }
    Map<String, Prefab.ConfigValue> mergedMap = new HashMap<>();
    configs
      .getDefaultContext()
      .getContextsList()
      .forEach(c ->
        mergedMap.putAll(PrefabContext.fromProto(c).getNameQualifiedProperties())
      );
    defaultContext.set(mergedMap);
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

  public String contentsString() {
    StringBuilder sb = new StringBuilder();
    List<String> sortedKeys = new ArrayList<>(getKeys());
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
