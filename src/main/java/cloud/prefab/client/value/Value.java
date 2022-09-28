package cloud.prefab.client.value;

public interface Value<T> {
  T get();

  T or(T defaultValue);

  T orNull();
}
