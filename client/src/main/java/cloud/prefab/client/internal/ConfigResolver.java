package cloud.prefab.client.internal;

import cloud.prefab.client.ConfigStore;
import cloud.prefab.client.config.ConfigElement;
import cloud.prefab.client.config.ConfigValueUtils;
import cloud.prefab.client.config.Match;
import cloud.prefab.domain.Prefab;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigResolver {

  public static final String NAMESPACE_KEY = "NAMESPACE";
  public static final String NEW_NAMESPACE_KEY = "prefab.namespace";
  private static final Logger LOG = LoggerFactory.getLogger(ConfigResolver.class);

  private final ConfigStore configStore;
  private final EnvironmentVariableLookup environmentVariableLookup;
  private final ConfigRuleEvaluator configRuleEvaluator;

  public ConfigResolver(
    ConfigStore configStoreImpl,
    ConfigRuleEvaluator configRuleEvaluator,
    EnvironmentVariableLookup environmentVariableLookup
  ) {
    this.configRuleEvaluator = configRuleEvaluator;
    this.configStore = configStoreImpl;
    this.environmentVariableLookup = environmentVariableLookup;
  }

  public Optional<Prefab.ConfigValue> getConfigValue(String key) {
    return getConfigValue(key, LookupContext.EMPTY);
  }

  public Optional<Prefab.ConfigValue> getConfigValue(
    String key,
    LookupContext lookupContext
  ) {
    return getMatch(key, lookupContext).map(Match::getConfigValue);
  }

  public Optional<Match> getMatch(String key, LookupContext lookupContext) {
    return getRawMatch(key, lookupContext).map(this::reify);
  }

  public Optional<Match> getRawMatch(String key, LookupContext lookupContext) {
    return configRuleEvaluator.getMatch(key, lookupContext);
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

  private Match reify(Match match) {
    Prefab.ConfigValue updatedConfigValue = reify(
      match.getConfigElement().getConfig(),
      match.getConfigValue()
    );
    return new Match(
      updatedConfigValue,
      match.getConfigElement(),
      match.getEvaluatedCriterion(),
      match.getRowIndex(),
      match.getConditionalValueIndex(),
      match.getWeightedValueIndex()
    );
  }

  private Prefab.ConfigValue reify(Prefab.Config config, Prefab.ConfigValue configValue) {
    if (configValue.hasProvided()) {
      switch (configValue.getProvided().getSource()) {
        case ENV_VAR:
          Optional<String> newValue = environmentVariableLookup.get(
            configValue.getProvided().getLookup()
          );
          return configValue.toBuilder().setString(newValue.orElse("")).build();
        default:
          LOG.error(
            "Config {} has unhandled Provided Source {}",
            config.getKey(),
            configValue.getProvided().getSource()
          );
          return ConfigValueUtils.from("");
      }
    }
    return configValue;
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
      final Optional<Match> match = configRuleEvaluator.getMatch(
        configElement,
        LookupContext.EMPTY
      );
      if (match.isPresent()) {
        sb.append(padded(key, 45));
        sb.append(
          padded(
            ConfigValueUtils
              .toDisplayString(match.get().getConfigValue())
              .orElse("[Unable to display]"),
            40
          )
        );
        sb.append(padded(configElement.getProvenance().toString(), 40));
        sb.append(padded(match.get().getReason(), 40));
        sb.append("\n");
      }
    }
    return sb.toString();
  }

  private String padded(String s, int size) {
    return String.format(
      "%-" + size + "s",
      s.substring(0, Math.min(s.length(), size - 1))
    );
  }
}
