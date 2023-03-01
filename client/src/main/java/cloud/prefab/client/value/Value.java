package cloud.prefab.client.value;

import java.util.Optional;
import java.util.function.Supplier;

public interface Value<T> extends Supplier<T> {
  T get();

  Optional<T> getMaybe();

  T orElse(T defaultValue);

  T orElseGet(Supplier<T> defaultValueSupplier);

  T orNull();
}
