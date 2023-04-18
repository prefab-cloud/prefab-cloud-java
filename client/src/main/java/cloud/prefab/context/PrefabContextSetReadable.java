package cloud.prefab.context;

import java.util.Collections;
import java.util.Iterator;
import java.util.Optional;

public interface PrefabContextSetReadable {
  Optional<PrefabContext> getByType(String contextType);
  Iterable<PrefabContext> getContexts();

  PrefabContextSetReadable EMPTY = new PrefabContextSetReadable() {
    @Override
    public Optional<PrefabContext> getByType(String contextType) {
      return Optional.empty();
    }

    @Override
    public Iterable<PrefabContext> getContexts() {
      return Collections.emptyList();
    }
  };
}
