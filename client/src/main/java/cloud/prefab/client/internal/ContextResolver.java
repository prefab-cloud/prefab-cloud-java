package cloud.prefab.client.internal;

import cloud.prefab.context.ContextStore;
import cloud.prefab.context.PrefabContext;
import cloud.prefab.context.PrefabContextSet;
import cloud.prefab.context.PrefabContextSetReadable;
import cloud.prefab.domain.Prefab;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.annotation.Nullable;

class ContextResolver {

  private final ContextStore contextStore;
  private final Supplier<PrefabContextSetReadable> defaultContextSupplier;

  ContextResolver(
    Supplier<PrefabContextSetReadable> defaultContextSupplier,
    ContextStore contextStore
  ) {
    this.defaultContextSupplier = defaultContextSupplier;
    this.contextStore = contextStore; // source of ambient context
  }

  /**
   * This builds a context from three sources (in order):
   * 1) the API-derived default context (sent with the Configs)
   * 2) Ambient context set into the context store (thread/request scoped things)
   * 3) Context passed directly to a config client lookup method
   *
   * Steps
   * 1) Merge directly passed context onto ambient context - clobbering colliding contexts at the context "type" level so all of "foo" context in ambient context store is replaced by a "foo" context directly passed
   * 2) Merge results from 1 into default context, only clobbering individual sub keys. if the default context contains  foo -> { "bar": 123 } and the merged context from step one contains "foo" -> {"baz": 567} the resulting context is foo -> {"bar": 123, "baz": 567}
   *
   * @param directlyPassedContext
   * @return
   */

  public LookupContext resolve(@Nullable PrefabContextSetReadable directlyPassedContext) {
    Optional<PrefabContextSetReadable> mergedContextWithoutDefault = mergeAmbientAndDirectlyPassedContext(
      canonicalizeEmptyContextSetToEmptyOptional(contextStore.getContext()),
      canonicalizeEmptyContextSetToEmptyOptional(directlyPassedContext)
    );
    return new LookupContext(
      Optional.empty(),
      mergeDefaultContextOntoContext(
        mergedContextWithoutDefault,
        canonicalizeEmptyContextSetToEmptyOptional(defaultContextSupplier.get())
      )
        .orElse(PrefabContextSetReadable.EMPTY)
    );
  }

  private Optional<PrefabContextSetReadable> canonicalizeEmptyContextSetToEmptyOptional(
    Optional<PrefabContextSetReadable> contextMaybe
  ) {
    return contextMaybe.filter(Predicate.not(PrefabContextSetReadable::isEmpty));
  }

  private Optional<PrefabContextSetReadable> canonicalizeEmptyContextSetToEmptyOptional(
    PrefabContextSetReadable context
  ) {
    return canonicalizeEmptyContextSetToEmptyOptional(Optional.of(context));
  }

  private Optional<PrefabContextSetReadable> mergeDefaultContextOntoContext(
    Optional<PrefabContextSetReadable> contextMaybe,
    Optional<PrefabContextSetReadable> defaultContextMaybe
  ) {
    if (defaultContextMaybe.isPresent() && contextMaybe.isPresent()) {
      Set<String> contextNames = new HashSet<>();
      PrefabContextSet prefabContextSet = new PrefabContextSet();
      for (PrefabContext context : defaultContextMaybe.get().getContexts()) {
        contextNames.add(context.getName());
        prefabContextSet.addContext(context);
      }
      for (PrefabContext defaultContext : defaultContextMaybe.get().getContexts()) {
        if (!contextNames.contains(defaultContext.getName())) {
          prefabContextSet.addContext(defaultContext);
        } else {
          prefabContextSet.addContext(
            defaultMerge(
              defaultContext,
              prefabContextSet.getByName(defaultContext.getName()).orElseThrow()
            )
          );
        }
      }
      return Optional.of(prefabContextSet);
    }
    return firstNonEmpty(defaultContextMaybe, contextMaybe);
  }

  private PrefabContext defaultMerge(
    PrefabContext defaultContext,
    PrefabContext context
  ) {
    HashMap<String, Prefab.ConfigValue> propertyMap = new HashMap<>(
      context.getProperties()
    );
    propertyMap.putAll(defaultContext.getProperties());
    return PrefabContext.fromMap(defaultContext.getName(), propertyMap);
  }

  private Optional<PrefabContextSetReadable> mergeAmbientAndDirectlyPassedContext(
    Optional<PrefabContextSetReadable> ambientContext,
    Optional<PrefabContextSetReadable> directlyPassed
  ) {
    if (ambientContext.isPresent() && directlyPassed.isPresent()) {
      PrefabContextSet prefabContextSet = new PrefabContextSet();
      for (PrefabContext context : ambientContext.get().getContexts()) {
        prefabContextSet.addContext(context);
      }
      for (PrefabContext context : directlyPassed.get().getContexts()) {
        prefabContextSet.addContext(context);
      }
      return Optional.of(prefabContextSet);
    }

    return firstNonEmpty(ambientContext, directlyPassed);
  }

  private Optional<PrefabContextSetReadable> firstNonEmpty(
    Optional<PrefabContextSetReadable> a,
    Optional<PrefabContextSetReadable> b
  ) {
    return Stream.concat(a.stream(), b.stream()).findFirst();
  }
}
