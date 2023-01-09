package cloud.prefab.client.config;

import cloud.prefab.domain.Prefab;
import com.google.common.base.MoreObjects;

import java.util.Optional;

public class EvaluatedCriterion {

  private final Prefab.Criterion criterion;
  private final Optional<Prefab.ConfigValue> evaluatedProperty;

  private final boolean match;

  public EvaluatedCriterion(
      Prefab.Criterion criterion,
      String evaluatedProperty,
      boolean match
  ) {
    this(criterion, Prefab.ConfigValue.newBuilder().setString(evaluatedProperty).build(), match);
  }

  public EvaluatedCriterion(
      Prefab.Criterion criterion,
      Prefab.ConfigValue evaluatedProperty,
      boolean match
  ) {
    this.criterion = criterion;
    this.evaluatedProperty = Optional.of(evaluatedProperty);
    this.match = match;
  }
  public EvaluatedCriterion(
      Prefab.Criterion criterion,
      Optional<Prefab.ConfigValue> evaluatedProperty,
      boolean match
  ) {
    this.criterion = criterion;
    this.evaluatedProperty = evaluatedProperty;
    this.match = match;
  }

  public EvaluatedCriterion(
      Prefab.Criterion criterion,
      boolean match
  ) {
    this.criterion = criterion;
    this.evaluatedProperty = Optional.empty();
    this.match = match;
  }

  public Prefab.Criterion getCriterion() {
    return criterion;
  }

  public Optional<Prefab.ConfigValue> getEvaluatedProperty() {
    return evaluatedProperty;
  }

  public boolean isMatch() {
    return match;
  }

  public EvaluatedCriterion negated(){
    return new EvaluatedCriterion(criterion, evaluatedProperty, !match);
  }

  @Override
  public String toString() {
    return MoreObjects
        .toStringHelper(this)
        .add("criterion", criterion)
        .add("evaluatedProperty", evaluatedProperty)
        .add("match", match)
        .toString();
  }
}
