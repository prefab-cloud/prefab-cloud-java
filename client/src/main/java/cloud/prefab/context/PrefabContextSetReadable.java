package cloud.prefab.context;

import java.util.Collections;
import java.util.Optional;

public interface PrefabContextSetReadable {
  Optional<PrefabContext> getByName(String contextName);
  Iterable<PrefabContext> getContexts();

  boolean isEmpty();

  PrefabContextSetReadable EMPTY = new PrefabContextSetReadable() {
    @Override
    public Optional<PrefabContext> getByName(String contextName) {
      return Optional.empty();
    }

    @Override
    public Iterable<PrefabContext> getContexts() {
      return Collections.emptyList();
    }

    @Override
    public boolean isEmpty() {
      return true;
    }
  };
}
