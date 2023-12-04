package cloud.prefab.client.exceptions;

import cloud.prefab.domain.Prefab;

public class EnvironmentVariableTypeConversionException extends RuntimeException {

  private final String environmentVariableName;
  private final String environmentValue;
  private final Prefab.Config.ValueType targetValueType;

  public EnvironmentVariableTypeConversionException(
    String environmentVariableName,
    String environmentValue,
    Prefab.Config.ValueType targetValueType,
    Throwable cause
  ) {
    super(
      String.format(
        "Unable to %s from environment variable %s to type %s",
        environmentValue,
        environmentVariableName,
        targetValueType
      ),
      cause
    );
    this.environmentVariableName = environmentVariableName;
    this.environmentValue = environmentValue;
    this.targetValueType = targetValueType;
  }

  public String getEnvironmentVariableName() {
    return environmentVariableName;
  }

  public String getEnvironmentValue() {
    return environmentValue;
  }

  public Prefab.Config.ValueType getTargetValueType() {
    return targetValueType;
  }
}
