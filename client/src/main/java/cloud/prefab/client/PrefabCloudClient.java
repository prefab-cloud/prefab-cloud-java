package cloud.prefab.client;

import cloud.prefab.client.internal.ConfigClientImpl;
import cloud.prefab.client.internal.FeatureFlagClientImpl;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PrefabCloudClient implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(PrefabCloudClient.class);

  private final Options options;
  private ConfigClient configClient;
  private FeatureFlagClient featureFlagClient;
  private final AtomicBoolean closed;

  public PrefabCloudClient(Options options) {
    this.options = options;

    if (options.isLocalOnly()) {
      LOG.info("Initializing Prefab LocalOnly");
    } else {
      if (options.getApikey() == null || options.getApikey().isEmpty()) {
        throw new RuntimeException("PREFAB_API_KEY not set");
      }
      LOG.info("Initializing Prefab for apiKeyId {}", options.getApiKeyId());
    }

    this.closed = new AtomicBoolean(false);
  }

  public ConfigClient configClient() {
    if (configClient == null) {
      synchronized (this) {
        if (configClient == null) {
          configClient = new ConfigClientImpl(this);
        }
      }
    }
    return configClient;
  }

  public FeatureFlagClient featureFlagClient() {
    if (featureFlagClient == null) {
      synchronized (this) {
        if (featureFlagClient == null) {
          featureFlagClient = new FeatureFlagClientImpl(configClient());
        }
      }
    }
    return featureFlagClient;
  }

  public Options getOptions() {
    return options;
  }

  @Override
  public void close() {
    if (closed.get()) {
      return;
    }

    synchronized (this) {
      if (!closed.get()) {
        closed.set(true);
      }
    }
  }
}
