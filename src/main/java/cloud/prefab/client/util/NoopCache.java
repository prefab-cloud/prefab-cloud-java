package cloud.prefab.client.util;

import java.util.concurrent.ExecutionException;

public class NoopCache implements Cache {
  @Override
  public byte[] get(String s) throws ExecutionException, InterruptedException {
    return null;
  }

  @Override
  public void set(String key, int expiryInSeconds, byte[] bytes) {
  }
}
