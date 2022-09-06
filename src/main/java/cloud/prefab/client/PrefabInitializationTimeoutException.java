package cloud.prefab.client;

public class PrefabInitializationTimeoutException extends RuntimeException {

  public PrefabInitializationTimeoutException(int initializationTimeoutSec) {
    super("Prefab Failed to Initialize after " + initializationTimeoutSec + " seconds");
  }
}
