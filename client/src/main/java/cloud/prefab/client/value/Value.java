package cloud.prefab.client.value;

import java.util.function.Supplier;

public interface Value<T> extends Supplier<T> {
  T get();

  T or(T defaultValue);

  T orNull();
}
