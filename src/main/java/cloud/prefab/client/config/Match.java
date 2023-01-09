package cloud.prefab.client.config;

import cloud.prefab.domain.Prefab;
import com.google.common.base.MoreObjects;
import java.util.List;

public class Match {

  private final Prefab.ConfigValue configValue;
  private final ConfigElement configElement;
  private final List<EvaluatedCriterion> evaluatedCriterion;

  public Match(
    Prefab.ConfigValue configValue,
    ConfigElement configElement,
    List<EvaluatedCriterion> evaluatedCriterion
  ) {
    this.configValue = configValue;
    this.configElement = configElement;
    this.evaluatedCriterion = evaluatedCriterion;
  }

  public Prefab.ConfigValue getConfigValue() {
    return configValue;
  }

  public String getReason(){
    StringBuilder sb = new StringBuilder();
    evaluatedCriterion.forEach(ec -> {
      sb.append(ec.getCriterion().getPropertyName());
      sb.append(":");
      sb.append(ec.getCriterion().getOperator());
    });
    return sb.toString();
  }

  @Override
  public String toString() {
    return MoreObjects
      .toStringHelper(this)
      .add("configElement", configElement)
      .add("configValue", configValue)
      .add("evaluatedCriterion", evaluatedCriterion)
      .toString();
  }
}
