package cloud.prefab.client.exceptions;

public class EnvironmentVariableMissingException extends ConfigValueException {

  private final String configKey;
  private final String environmentVariableName;

  public EnvironmentVariableMissingException(
    String configKey,
    String environmentVariableName,
    Throwable cause
  ) {
    super(
      String.format(
        "config key `%s` provided by environment variable `%s` cannot be evaluated because the environment variable is missing",
        configKey,
        environmentVariableName
      ),
      cause
    );
    this.configKey = configKey;
    this.environmentVariableName = environmentVariableName;
  }
}
