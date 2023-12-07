package cloud.prefab.client.exceptions;

import cloud.prefab.domain.Prefab;

public class EnvironmentVariableTypeConversionException extends ConfigValueException {

  private final String configKey;
  private final String environmentVariableName;
  private final String environmentValue;
  private final Prefab.Config.ValueType targetValueType;

  public EnvironmentVariableTypeConversionException(
    String configKey,
    String environmentVariableName,
    String environmentValue,
    Prefab.Config.ValueType targetValueType,
    Throwable cause
  ) {
    super(
      String.format(
        "Key %s referencing environment variable %s with value %s cannot be coerced to type %s",
        configKey,
        environmentVariableName,
        environmentValue,
        targetValueType
      ),
      cause
    );
    this.configKey = configKey;
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

  public String getConfigKey() {
    return configKey;
  }
}
