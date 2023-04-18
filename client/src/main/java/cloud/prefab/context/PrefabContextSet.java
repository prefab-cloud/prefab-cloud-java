package cloud.prefab.context;

import com.google.common.collect.ImmutableList;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;

public class PrefabContextSet implements PrefabContextSetReadable {

  private ConcurrentSkipListMap<String, PrefabContext> contextByNameMap = new ConcurrentSkipListMap();

  public void addContext(PrefabContext prefabContext) {
    contextByNameMap.put(prefabContext.getContextType().toLowerCase(), prefabContext);
  }

  public boolean isEmpty() {
    return contextByNameMap.isEmpty();
  }

  @Override
  public Optional<PrefabContext> getByType(String contextType) {
    return Optional.ofNullable(contextByNameMap.get(contextType.toLowerCase()));
  }

  @Override
  public Iterable<PrefabContext> getContexts() {
    return ImmutableList.copyOf(contextByNameMap.values());
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
