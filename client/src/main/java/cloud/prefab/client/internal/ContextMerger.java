package cloud.prefab.client.internal;

import cloud.prefab.context.PrefabContext;
import cloud.prefab.context.PrefabContextSet;
import cloud.prefab.context.PrefabContextSetReadable;
import com.google.common.base.Predicates;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class ContextMerger {

  public static PrefabContextSetReadable merge(
    PrefabContextSetReadable globalContext,
    PrefabContextSetReadable apiDefaultContext,
    PrefabContextSetReadable contextStoreContext,
    PrefabContextSetReadable passedContext
  ) {
    // use naive strategy for now,
    PrefabContextSet prefabContextSet = new PrefabContextSet();

    Stream
      .of(globalContext, apiDefaultContext, contextStoreContext, passedContext)
      .filter(Predicates.notNull())
      .filter(Predicate.not(PrefabContextSetReadable::isEmpty))
      .forEach(prefabContextSetReadable -> {
        for (PrefabContext context : prefabContextSetReadable.getContexts()) {
          prefabContextSet.addContext(context);
        }
      });

    return prefabContextSet;
  }
}
