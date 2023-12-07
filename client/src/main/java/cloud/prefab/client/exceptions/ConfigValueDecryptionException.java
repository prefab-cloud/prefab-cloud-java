package cloud.prefab.client.exceptions;

public class ConfigValueDecryptionException extends ConfigValueException {

  public ConfigValueDecryptionException(
    String secretValueConfigKey,
    String encryptionKeyConfigKey,
    Throwable t
  ) {
    super(
      String.format(
        "Unable to decrypt value found in `%s` with encryption key found in `%s`",
        secretValueConfigKey,
        encryptionKeyConfigKey
      ),
      t
    );
  }
}
