package cloud.prefab.context;

import java.util.Collections;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

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

    @Override
    public String toString() {
      return com.google.common.base.MoreObjects
        .toStringHelper(this)
        .add("contextByNameMap", "EMPTY")
        .toString();
    }
  };

  static PrefabContextSetReadable readOnlyContextSetView(PrefabContextSet contextSet) {
    return new PrefabContextSetReadable() {
      @Override
      public Optional<PrefabContext> getByName(String contextName) {
        return contextSet.getByName(contextName);
      }

      @Override
      public Iterable<PrefabContext> getContexts() {
        return contextSet.getContexts();
      }

      @Override
      public boolean isEmpty() {
        return contextSet.isEmpty();
      }

      @Override
      public String toString() {
        return contextSet.toString();
      }
    };
  }

  default String getFingerPrint() {
    return StreamSupport
      .stream(getContexts().spliterator(), false)
      .filter(c -> !c.getName().isBlank())
      .filter(c -> c.getProperties().containsKey("key"))
      .sorted(Comparator.comparing(PrefabContext::getName))
      .map(c -> c.getName() + c.getProperties().get("key"))
      .collect(Collectors.joining());
  }
}
