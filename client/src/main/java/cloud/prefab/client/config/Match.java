package cloud.prefab.client.config;

import cloud.prefab.domain.Prefab;
import com.google.common.base.MoreObjects;
import java.util.List;
import java.util.Optional;

public class Match {

  private final Prefab.ConfigValue configValue;
  private final ConfigElement configElement;
  private final List<EvaluatedCriterion> evaluatedCriterion;
  private final int rowIndex;
  private final int conditionalValueIndex;
  private final Optional<Integer> weightedValueIndex;
  private final Optional<Long> envId;

  public Match(
    Prefab.ConfigValue configValue,
    ConfigElement configElement,
    List<EvaluatedCriterion> evaluatedCriterion,
    int rowIndex,
    int conditionalValueIndex,
    Optional<Integer> weightedValueIndex,
    Optional<Long> envId
  ) {
    this.configValue = configValue;
    this.configElement = configElement;
    this.evaluatedCriterion = evaluatedCriterion;
    this.rowIndex = rowIndex;
    this.conditionalValueIndex = conditionalValueIndex;
    this.weightedValueIndex = weightedValueIndex;
    this.envId = envId;
  }

  public int getRowIndex() {
    return rowIndex;
  }

  public int getConditionalValueIndex() {
    return conditionalValueIndex;
  }

  public Optional<Integer> getWeightedValueIndex() {
    return weightedValueIndex;
  }

  public Optional<Long> getEnvId() {
    return envId;
  }

  public Prefab.ConfigValue getConfigValue() {
    return configValue;
  }

  public ConfigElement getConfigElement() {
    return configElement;
  }

  public String getReason() {
    StringBuilder sb = new StringBuilder();
    evaluatedCriterion.forEach(ec -> {
      sb.append(ec.getCriterion().getPropertyName());
      sb.append(":");
      sb.append(ec.getCriterion().getOperator());
    });
    return sb.toString();
  }

  public List<EvaluatedCriterion> getEvaluatedCriterion() {
    return evaluatedCriterion;
  }

  @Override
  public String toString() {
    return MoreObjects
      .toStringHelper(this)
      .add("configElement", configElement)
      .add("configValue", configValue)
      .add("evaluatedCriterion", evaluatedCriterion)
      .add("rowIndex", rowIndex)
      .add("conditionalValueIndex", conditionalValueIndex)
      .add("weightedValueIndex", weightedValueIndex)
      .add("envId", envId)
      .toString();
  }
}
