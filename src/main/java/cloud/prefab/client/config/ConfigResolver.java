package cloud.prefab.client.config;

import cloud.prefab.client.ConfigStore;
import cloud.prefab.domain.Prefab;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigResolver {

  public static final String NAMESPACE_KEY = "NAMESPACE";
  public static final String LOOKUP_KEY = "LOOKUP";

  private static final Logger LOG = LoggerFactory.getLogger(ConfigResolver.class);

  private final ConfigStore configStore;
  private final WeightedValueEvaluator weightedValueEvaluator;

  private long projectEnvId = 0;

  public ConfigResolver(ConfigStore configStoreImpl) {
    this(configStoreImpl, 0L);
  }

  public ConfigResolver(ConfigStore configStoreImpl, long projectEnvId) {
    this.weightedValueEvaluator = new WeightedValueEvaluator();
    this.projectEnvId = projectEnvId;
    this.configStore = configStoreImpl;
  }

  public Optional<Prefab.ConfigValue> getConfigValue(String key) {
    return getConfigValue(key, new HashMap<>());
  }

  public Optional<Match> getMatch(
    String key,
    Map<String, Prefab.ConfigValue> properties
  ) {
    if (!configStore.containsKey(key)) {
      return Optional.empty();
    }
    final ConfigElement configElement = configStore.getElement(key);

    return evalConfigElementMatch(configElement, properties);
  }

  public Optional<Prefab.ConfigValue> getConfigValue(String key,
      Map<String, Prefab.ConfigValue> properties){

    return getMatch(key, properties).map(Match::getConfigValue);
  }

  /**
   * find if we have a match for the given properties
   *
   * @param configElement
   * @param properties
   * @return
   */
  Optional<Match> evalConfigElementMatch(
    ConfigElement configElement,
    Map<String, Prefab.ConfigValue> properties
  ) {
    // Prefer rows that have a projEnvId to ones that don't
    // There will be 0-1 rows with projenv and 0-1 rows without (the default row)
    final Optional<Match> match = configElement
      .getRowsProjEnvFirst(projectEnvId)
      .map(configRow -> {
        Map<String, Prefab.ConfigValue> rowProperties = new HashMap<>(
          properties.size() + configRow.getPropertiesMap().size()
        );
        rowProperties.putAll(properties);

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
      .flatMap(criterion -> evaluateCriterionMatch(criterion, rowProperties).stream())
      .collect(Collectors.toList());

    if (evaluatedCriterionStream.stream().allMatch(EvaluatedCriterion::isMatch)) {
      Prefab.ConfigValue simplified = simplify(
        conditionalValue,
        configElement.getConfig().getKey(),
        rowProperties
      );

      return Optional.of(new Match(simplified, configElement, evaluatedCriterionStream));
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
  List<EvaluatedCriterion> evaluateCriterionMatch(
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
        return List.of(new EvaluatedCriterion(criterion, true));
      case LOOKUP_KEY_IN:
        if (!lookupKey.isPresent()) {
          return List.of(new EvaluatedCriterion(criterion, false));
        }
        boolean match = criterion
          .getValueToMatch()
          .getStringList()
          .getValuesList()
          .contains(lookupKey.get());
        return List.of(new EvaluatedCriterion(criterion, lookupKey.get(), match));
      case LOOKUP_KEY_NOT_IN:
        if (!lookupKey.isPresent()) {
          return List.of(new EvaluatedCriterion(criterion, false));
        }
        boolean notMatch = !criterion
          .getValueToMatch()
          .getStringList()
          .getValuesList()
          .contains(lookupKey.get());
        return List.of(new EvaluatedCriterion(criterion, lookupKey.get(), notMatch));
      case HIERARCHICAL_MATCH:
        if (prop.isPresent()) {
          if (prop.get().hasString() && criterion.getValueToMatch().hasString()) {
            final String propertyString = attributes
              .get(criterion.getPropertyName())
              .getString();
            return List.of(new EvaluatedCriterion(
              criterion,
              criterion.getValueToMatch(),
              hierarchicalMatch(propertyString, criterion.getValueToMatch().getString()))
            );
          }
        }
        return List.of(new EvaluatedCriterion(criterion, criterion.getValueToMatch(), false));
      // The string here is the key of the Segment
      case IN_SEG:
        final Optional<Match> evaluatedSegment = getMatch(
          criterion.getValueToMatch().getString(),
          attributes
        );

        if (evaluatedSegment.isPresent() && evaluatedSegment.get().getConfigValue().hasBool()) {
          return evaluatedSegment.get().getEvaluatedCriterion();
        } else {
          return List.of(new EvaluatedCriterion(
            criterion,
            "Missing Segment " + criterion.getValueToMatch().getString(),
            false
          ));
        }
      case NOT_IN_SEG:
        final Optional<Prefab.ConfigValue> evaluatedNotSegment = getConfigValue(
          criterion.getValueToMatch().getString(),
          attributes
        );

        if (evaluatedNotSegment.isPresent() && evaluatedNotSegment.get().hasBool()) {
          return List.of(new EvaluatedCriterion(
            criterion,
            criterion.getValueToMatch(),
            !evaluatedNotSegment.get().getBool()
          ));
        } else {
          return List.of(new EvaluatedCriterion(
            criterion,
            "Missing Segment " + criterion.getValueToMatch().getString(),
            true
          ));
        }
      case PROP_IS_ONE_OF:
        if (!prop.isPresent()) {
          return List.of(new EvaluatedCriterion(criterion, false));
        }
        // assumption that property is a String
        return List.of(new EvaluatedCriterion(
          criterion,
          prop.get().getString(),
          criterion
            .getValueToMatch()
            .getStringList()
            .getValuesList()
            .contains(prop.get().getString())
        ));
      case PROP_IS_NOT_ONE_OF:
        if (!prop.isPresent()) {
          return List.of(new EvaluatedCriterion(criterion, false));
        }
        return List.of(new EvaluatedCriterion(
          criterion,
          prop.get().getString(),
          !criterion
            .getValueToMatch()
            .getStringList()
            .getValuesList()
            .contains(prop.get().getString())
        ));
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

  public String contentsString() {
    StringBuilder sb = new StringBuilder("\n");
    List<String> sortedKeys = new ArrayList(getKeys());
    Collections.sort(sortedKeys);
    for (String key : sortedKeys) {
      ConfigElement configElement = configStore.getElement(key);
      final Optional<Match> match = evalConfigElementMatch(configElement, new HashMap<>());

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
