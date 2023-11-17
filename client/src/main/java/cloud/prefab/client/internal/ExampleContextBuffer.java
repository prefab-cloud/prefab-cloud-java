package cloud.prefab.client.internal;

import cloud.prefab.context.PrefabContextSet;
import cloud.prefab.domain.Prefab;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExampleContextBuffer {

  private static final Logger LOG = LoggerFactory.getLogger(ExampleContextBuffer.class);
  private final ContextDeduplicator contextDeduplicator;
  private Set<Prefab.ExampleContext> recentlySeenContexts = new HashSet<>(); //make bounded!

  ExampleContextBuffer() {
    this.contextDeduplicator = new ContextDeduplicator(Duration.ofMinutes(15), 1000);
  }

  void recordContext(long timestamp, PrefabContextSet context) {
    if (context.isEmpty()) {
      return;
    }
    String fingerPrint = context.getFingerPrint();
    if (!fingerPrint.isBlank()) {
      if (!contextDeduplicator.recentlySeen(fingerPrint)) {
        LOG.debug(
          "have not seen context with fingerprint {} will add to recently seen contexts",
          fingerPrint
        );
        recentlySeenContexts.add(
          Prefab.ExampleContext
            .newBuilder()
            .setTimestamp(timestamp)
            .setContextSet(PrefabContextSet.convert(context).toProto())
            .build()
        );
      } else {
        LOG.debug("Already saw context with fingerprint {}", fingerPrint);
      }
    } else {
      LOG.trace("ignoring context with no fingerprint {}", context);
    }
  }

  Set<Prefab.ExampleContext> getAndResetContexts() {
    Set<Prefab.ExampleContext> copy = Set.copyOf(recentlySeenContexts);
    recentlySeenContexts.clear();
    return copy;
  }

  void setContexts(Set<Prefab.ExampleContext> exampleContexts) {
    recentlySeenContexts.addAll(exampleContexts);
  }

  static class ContextDeduplicator {

    private final Cache<String, String> cache;

    ContextDeduplicator(Duration expiry, int maxSize) {
      this.cache =
        CacheBuilder.newBuilder().expireAfterWrite(expiry).maximumSize(maxSize).build();
    }

    boolean recentlySeen(String fingerprint) {
      if (cache.getIfPresent(fingerprint) != null) {
        return true;
      }
      cache.put(fingerprint, fingerprint);
      return false;
    }
  }
}
