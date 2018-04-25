package cloud.prefab.client.util;

public class RandomProvider implements RandomProviderIF {
  @Override
  public double random() {
    return Math.random();
  }
}
