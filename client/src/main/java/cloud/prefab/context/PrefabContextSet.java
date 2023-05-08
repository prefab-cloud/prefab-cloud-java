package cloud.prefab.context;

import com.google.common.collect.ImmutableList;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;

public class PrefabContextSet implements PrefabContextSetReadable {

  private ConcurrentSkipListMap<String, PrefabContext> contextByNameMap = new ConcurrentSkipListMap();

  public void addContext(PrefabContext prefabContext) {
    if (prefabContext != null) {
      contextByNameMap.put(prefabContext.getName().toLowerCase(), prefabContext);
    }
  }

  public boolean isEmpty() {
    return contextByNameMap.isEmpty();
  }

  @Override
  public Optional<PrefabContext> getByName(String contextType) {
    return Optional.ofNullable(contextByNameMap.get(contextType.toLowerCase()));
  }

  @Override
  public Iterable<PrefabContext> getContexts() {
    return ImmutableList.copyOf(contextByNameMap.values());
  }

  public static PrefabContextSet from(PrefabContext... contexts) {
    PrefabContextSet set = new PrefabContextSet();
    for (PrefabContext context : contexts) {
      set.addContext(context);
    }
    return set;
  }

  /**
   * Converts the given `PrefabContextSetReadable` instance into a PrefabContextSet
   * If the argument is already a PrefabContextSet return it, othewise create a new PrefabContextSet and add the contents
   * of the PrefabContextSetReadable to it, then return the new set
   * @param prefabContextSetReadable instance to convert
   * @return a PrefabContextSet built as discussed above
   */
  public static PrefabContextSet convert(
    PrefabContextSetReadable prefabContextSetReadable
  ) {
    if (prefabContextSetReadable instanceof PrefabContextSet) {
      return (PrefabContextSet) prefabContextSetReadable;
    }
    PrefabContextSet prefabContextSet = new PrefabContextSet();
    for (PrefabContext context : prefabContextSetReadable.getContexts()) {
      prefabContextSet.addContext(context);
    }
    return prefabContextSet;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    PrefabContextSet that = (PrefabContextSet) o;
    return Objects.equals(contextByNameMap, that.contextByNameMap);
  }

  @Override
  public int hashCode() {
    return Objects.hash(contextByNameMap);
  }
}
