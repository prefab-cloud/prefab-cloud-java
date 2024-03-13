package cloud.prefab.client.internal;

import cloud.prefab.context.PrefabContext;
import cloud.prefab.context.PrefabContextSet;
import cloud.prefab.context.PrefabContextSetReadable;
import com.google.common.base.Predicates;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.annotation.Nullable;

public class ContextMerger {

  public static PrefabContextSetReadable merge(
    @Nullable PrefabContextSetReadable globalContext,
    @Nullable PrefabContextSetReadable apiDefaultContext,
    @Nullable PrefabContextSetReadable contextStoreContext,
    @Nullable PrefabContextSetReadable passedContext
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
