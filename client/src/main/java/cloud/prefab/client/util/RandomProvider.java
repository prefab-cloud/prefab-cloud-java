package cloud.prefab.client.util;

import java.util.concurrent.ThreadLocalRandom;

public class RandomProvider implements RandomProviderIF {

  @Override
  public double random() {
    return ThreadLocalRandom.current().nextDouble();
  }
}
