package cloud.prefab.client.config;

import cloud.prefab.client.PrefabCloudClient;
import cloud.prefab.domain.Prefab;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigResolver {

  public static final String NAMESPACE_KEY = "NAMESPACE";
  public static final String LOOKUP_KEY = "lookup_key";

  private static final Logger LOG = LoggerFactory.getLogger(ConfigResolver.class);

  private static final String NAMESPACE_DELIMITER = "\\.";

  private final PrefabCloudClient baseClient;
  private final ConfigLoader configLoader;
  private final AtomicReference<ImmutableMap<String, ConfigElement>> localMap = new AtomicReference<>(
    ImmutableMap.of()
  );
  private final WeightedValueEvaluator weightedValueEvaluator;

  private long projectEnvId = 0;

  public ConfigResolver(PrefabCloudClient baseClient, ConfigLoader configLoader) {
    this.baseClient = baseClient;
    this.configLoader = configLoader;
    this.weightedValueEvaluator = new WeightedValueEvaluator();
  }

  public Optional<Prefab.ConfigValue> getConfigValue(String key) {
    return getConfigValue(key, new HashMap<>());
  }

  public Optional<Prefab.ConfigValue> getConfigValue(
    String key,
    Map<String, Prefab.ConfigValue> properties
  ) {
    if (!localMap.get().containsKey(key)) {
      return Optional.empty();
    }
    final ConfigElement configElement = localMap.get().get(key);

    final Optional<Match> match = findMatch(configElement, properties);

    if (match.isPresent()) {
      return Optional.of(match.get().getConfigValue());
    } else {
      return Optional.empty();
    }
  }

  /**
   * find if we have a match for the given properties
   *
   * @param configElement
   * @param properties
   * @return
   */
  Optional<Match> findMatch(
    ConfigElement configElement,
    Map<String, Prefab.ConfigValue> properties
  ) {
    if (baseClient.getOptions().getNamespace().isPresent()) properties.put(
      NAMESPACE_KEY,
      Prefab.ConfigValue
        .newBuilder()
        .setString(baseClient.getOptions().getNamespace().get())
        .build()
    );

    // Prefer rows that have a projEnvId to ones that don't
    // There will be 0-1 rows with projenv and 0-1 rows without (the default row)
    final Optional<Match> match = configElement
      .getRowsProjEnvFirst(projectEnvId)
      .map(configRow -> {
        Map<String, Prefab.ConfigValue> rowProperties = new HashMap<>(properties);

        // Add row properties like "active"
        rowProperties.putAll(configRow.getPropertiesMap());
        // Return the value of the first matching set of criteria
        for (Prefab.ConditionalValue conditionalValue : configRow.getValuesList()) {
          Optional<Match> optionalMatch = evaluateConditionalValue(
            conditionalValue,
            rowProperties,
            configElement
          );
          if (optionalMatch.isPresent()) {
            return optionalMatch.get();
          }
        }
        return null;
      })
      .filter(Objects::nonNull)
      .findFirst();

    return match;
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
    Map<String, Prefab.ConfigValue> rowProperties,
    ConfigElement configElement
  ) {
    final List<EvaluatedCriterion> evaluatedCriterionStream = conditionalValue
      .getCriteriaList()
      .stream()
      .map((criterion -> evaluateCriterionMatch(criterion, rowProperties)))
      .collect(Collectors.toList());

    if (evaluatedCriterionStream.stream().allMatch(EvaluatedCriterion::isMatch)) {
      Prefab.ConfigValue simplified = simplify(
        conditionalValue,
        configElement.getConfig().getKey(),
        rowProperties
      );

      return Optional.of(
        new Match(
          simplified,
          configElement,
          evaluatedCriterionStream.stream().collect(Collectors.toList())
        )
      );
    } else {
      return Optional.empty();
    }
  }

  /**
   * A ConfigValue may be a WeightedValue. If so break it down so we can return a simpler form.
   */
  private Prefab.ConfigValue simplify(
    Prefab.ConditionalValue conditionalValue,
    String key,
    Map<String, Prefab.ConfigValue> rowProperties
  ) {
    if (conditionalValue.getValue().hasWeightedValues()) {
      return weightedValueEvaluator.toValue(
        conditionalValue.getValue().getWeightedValues(),
        key,
        lookupKey(rowProperties)
      );
    } else {
      return conditionalValue.getValue();
    }
  }

  private Optional<String> lookupKey(Map<String, Prefab.ConfigValue> attributes) {
    if (attributes.containsKey(LOOKUP_KEY)) {
      return Optional.of(attributes.get(LOOKUP_KEY).getString());
    } else {
      return Optional.empty();
    }
  }

  private Optional<Prefab.ConfigValue> prop(
    String key,
    Map<String, Prefab.ConfigValue> attributes
  ) {
    if (attributes.containsKey(key)) {
      return Optional.of(attributes.get(key));
    } else {
      return Optional.empty();
    }
  }

  /**
   * Does this criterion match?
   *
   * @param criterion
   * @param attributes
   * @return
   */
  EvaluatedCriterion evaluateCriterionMatch(
    Prefab.Criterion criterion,
    Map<String, Prefab.ConfigValue> attributes
  ) {
    Optional<String> lookupKey = lookupKey(attributes);
    final Optional<Prefab.ConfigValue> prop = prop(
      criterion.getPropertyName(),
      attributes
    );

    switch (criterion.getOperator()) {
      case ALWAYS_TRUE:
        return new EvaluatedCriterion(criterion, true);
      case LOOKUP_KEY_IN:
        if (!lookupKey.isPresent()) {
          return new EvaluatedCriterion(criterion, false);
        }
        boolean match = criterion
          .getValueToMatch()
          .getStringList()
          .getValuesList()
          .contains(lookupKey.get());
        return new EvaluatedCriterion(criterion, lookupKey.get(), match);
      case LOOKUP_KEY_NOT_IN:
        if (!lookupKey.isPresent()) {
          return new EvaluatedCriterion(criterion, false);
        }
        boolean notMatch = !criterion
          .getValueToMatch()
          .getStringList()
          .getValuesList()
          .contains(lookupKey.get());
        return new EvaluatedCriterion(criterion, lookupKey.get(), notMatch);
      case HIERARCHICAL_MATCH:
        if (prop.isPresent()) {
          if (prop.get().hasString() && criterion.getValueToMatch().hasString()) {
            final String propertyString = attributes
              .get(criterion.getPropertyName())
              .getString();
            return new EvaluatedCriterion(
              criterion,
              criterion.getValueToMatch(),
              hierarchicalMatch(propertyString, criterion.getValueToMatch().getString())
            );
          }
        }
        return new EvaluatedCriterion(criterion, criterion.getValueToMatch(), false);
      // The string here is the key of the Segment
      case IN_SEG:
        final Optional<Prefab.ConfigValue> optionalSegment = getConfigValue(
          criterion.getValueToMatch().getString()
        );

        if (optionalSegment.isPresent() && optionalSegment.get().hasSegment()) {
          //NOTE This only supports a single set of criteria
          return evaluateCriterionMatch(
            optionalSegment
              .get()
              .getSegment()
              .getCriteriaList()
              .stream()
              .findFirst()
              .get(),
            attributes
          );
        } else {
          return new EvaluatedCriterion(
            criterion,
            "Missing Segment " + criterion.getValueToMatch().getString(),
            false
          );
        }
      case NOT_IN_SEG:
        final Optional<Prefab.ConfigValue> optionalNotSegment = getConfigValue(
          criterion.getValueToMatch().getString()
        );

        if (optionalNotSegment.isPresent() && optionalNotSegment.get().hasSegment()) {
          return evaluateCriterionMatch(
            optionalNotSegment
              .get()
              .getSegment()
              .getCriteriaList()
              .stream()
              .findFirst()
              .get(),
            attributes
          )
            .negated();
        } else {
          return new EvaluatedCriterion(
            criterion,
            "Missing Segment " + criterion.getValueToMatch().getString(),
            true
          );
        }
      case PROP_IS_ONE_OF:
        // assumption that property is a String
        return new EvaluatedCriterion(
          criterion,
          prop.get().getString(),
          criterion
            .getValueToMatch()
            .getStringList()
            .getValuesList()
            .contains(prop.get().getString())
        );
      case PROP_IS_NOT_ONE_OF:
        return new EvaluatedCriterion(
          criterion,
          prop.get().getString(),
          !criterion
            .getValueToMatch()
            .getStringList()
            .getValuesList()
            .contains(prop.get().getString())
        );
      case PROP_ENDS_WITH_ONE_OF:
        if (prop.isPresent() && prop.get().hasString()) {
          final boolean matched = criterion
            .getValueToMatch()
            .getStringList()
            .getValuesList()
            .stream()
            .anyMatch(value -> prop.get().getString().endsWith(value));

          return new EvaluatedCriterion(criterion, prop.get(), matched);
        } else {
          return new EvaluatedCriterion(criterion, false);
        }
      case PROP_DOES_NOT_END_WITH_ONE_OF:
        if (prop.isPresent() && prop.get().hasString()) {
          final boolean matched = criterion
            .getValueToMatch()
            .getStringList()
            .getValuesList()
            .stream()
            .anyMatch(value -> prop.get().getString().endsWith(value));

          return new EvaluatedCriterion(criterion, prop.get(), !matched);
        } else {
          return new EvaluatedCriterion(criterion, true);
        }
    }
    // Unknown Operator
    return new EvaluatedCriterion(criterion, false);
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

  /**
   * Return the changed config values since last update()
   */
  public synchronized List<ConfigChangeEvent> update() {
    // store the old map
    final Map<String, Optional<Prefab.ConfigValue>> before = localMap
      .get()
      .entrySet()
      .stream()
      .collect(Collectors.toMap(Entry::getKey, e -> getConfigValue(e.getKey())));

    // load the new map
    makeLocal();

    // build the new map
    final Map<String, Optional<Prefab.ConfigValue>> after = localMap
      .get()
      .entrySet()
      .stream()
      .collect(Collectors.toMap(Entry::getKey, e -> getConfigValue(e.getKey())));

    MapDifference<String, Optional<Prefab.ConfigValue>> delta = Maps.difference(
      before,
      after
    );
    if (delta.areEqual()) {
      return ImmutableList.of();
    } else {
      ImmutableList.Builder<ConfigChangeEvent> changeEvents = ImmutableList.builder();

      // removed config values
      delta
        .entriesOnlyOnLeft()
        .forEach((key, value) ->
          changeEvents.add(new ConfigChangeEvent(key, value, Optional.empty()))
        );

      // added config values
      delta
        .entriesOnlyOnRight()
        .forEach((key, value) ->
          changeEvents.add(new ConfigChangeEvent(key, Optional.empty(), value))
        );

      // changed config values
      delta
        .entriesDiffering()
        .forEach((key, values) ->
          changeEvents.add(
            new ConfigChangeEvent(key, values.leftValue(), values.rightValue())
          )
        );

      return changeEvents.build();
    }
  }

  public boolean setProjectEnvId(Prefab.Configs configs) {
    if (configs.hasConfigServicePointer()) {
      this.projectEnvId = configs.getConfigServicePointer().getProjectEnvId();
      return true;
    }
    return false;
  }

  /**
   * set the localMap
   */
  private void makeLocal() {
    ImmutableMap.Builder<String, ConfigElement> store = ImmutableMap.builder();

    configLoader
      .calcConfig()
      .forEach((key, configElement) -> {
        store.put(key, configElement);
      });

    localMap.set(store.buildKeepingLast());
  }

  public Collection<String> getKeys() {
    return localMap.get().keySet();
  }

  public String contentsString() {
    StringBuilder sb = new StringBuilder("\n");
    List<String> sortedKeys = new ArrayList(localMap.get().keySet());
    Collections.sort(sortedKeys);
    for (String key : sortedKeys) {
      ConfigElement configElement = localMap.get().get(key);
      final Optional<Match> match = findMatch(configElement, new HashMap<>());

      if (match.isPresent()) {
        sb.append(padded(key, 30));
        sb.append(padded(toS(match.get().getConfigValue()), 40));
        sb.append(padded(configElement.getProvenance().toString(), 40));
        sb.append(padded(match.get().getReason(), 40));
      }
      sb.append("\n");
    }
    System.out.println(sb.toString());
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
    } else if (configValue.getTypeCase() == Prefab.ConfigValue.TypeCase.SEGMENT) {
      return "Segment";
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
