package cloud.prefab.client.value;

import java.util.Optional;
import java.util.function.Supplier;

public class FixedValue<T> implements Value<T> {

  private final Optional<T> value;

  public FixedValue() {
    this.value = Optional.empty();
  }

  public FixedValue(T value) {
    this.value = Optional.of(value);
  }

  @Override
  public T get() {
    if (value.isPresent()) {
      return value.get();
    } else {
      throw new RuntimeException("Fixed Value Unset");
    }
  }

  @Override
  public java.util.Optional<T> getMaybe() {
    return value;
  }

  @Override
  public T orElse(T defaultValue) {
    return value.orElse(defaultValue);
  }


  @Override
  public T orElseGet(Supplier<T> defaultValueSupplier) {
    return value.orElseGet(defaultValueSupplier);
  }

  @Override
  public T orNull() {
    return value.orElse(null);
  }

  public static <T> FixedValue<T> of(T value) {
    return new FixedValue<>(value);
  }

  public static <T> FixedValue<T> empty() {
    return new FixedValue<>();
  }

  @Override
  public String toString() {
    return String.format("FixedValue{}=%s", orNull());
  }
}
