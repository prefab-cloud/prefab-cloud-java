package cloud.prefab.client;

import cloud.prefab.client.util.Cache;
import cloud.prefab.client.util.NoopCache;
import com.google.common.base.Preconditions;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PrefabCloudClient implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(PrefabCloudClient.class);

  private final Options options;
  private volatile ManagedChannel channel;
  private RateLimitClient rateLimitClient;
  private ConfigClient configClient;
  private FeatureFlagClient featureFlagClient;
  private Cache noopCache;
  private final AtomicBoolean closed;

  public PrefabCloudClient(Options options) {
    this.options = options;

    if (options.isLocalOnly()) {
      LOG.info("Initializing Prefab LocalOnly");
    } else {
      if (options.getApikey() == null || options.getApikey().isEmpty()) {
        throw new RuntimeException("PREFAB_API_KEY not set");
      }

      long apiKeyId = Long.parseLong(options.getApikey().split("\\-")[0]);
      LOG.info("Initializing Prefab for apiKeyId {}", apiKeyId);
    }

    this.closed = new AtomicBoolean(false);
  }

  public RateLimitClient rateLimitClient() {
    if (rateLimitClient == null) {
      rateLimitClient = new RateLimitClient(this);
    }
    return rateLimitClient;
  }

  public ConfigClient configClient() {
    if (configClient == null) {
      configClient = new ConfigClient(this);
    }
    return configClient;
  }

  public FeatureFlagClient featureFlagClient() {
    if (featureFlagClient == null) {
      featureFlagClient = new FeatureFlagClient(configClient());
    }
    return featureFlagClient;
  }

  public ManagedChannel getChannel() {
    Preconditions.checkState(!closed.get(), "Client is closed");

    if (channel == null) {
      synchronized (this) {
        if (channel == null) {
          Preconditions.checkState(!closed.get(), "Client is closed");
          channel = createChannel();
        }
      }
    }

    return channel;
  }

  public Cache getDistributedCache() {
    if (options.getDistributedCache().isPresent()) {
      return options.getDistributedCache().get();
    } else {
      if (noopCache == null) {
        noopCache = new NoopCache();
      }
      return noopCache;
    }
  }

  public Options getOptions() {
    return options;
  }

  private ManagedChannel createChannel() {
    ManagedChannelBuilder<?> managedChannelBuilder = ManagedChannelBuilder.forTarget(
      options.getPrefabGrpcUrl()
    );

    if (!options.isSsl()) {
      managedChannelBuilder.usePlaintext();
    }

    return managedChannelBuilder
      .intercept(new ClientAuthenticationInterceptor(options.getApikey()))
      .build();
  }

  @Override
  public void close() {
    if (closed.get()) {
      return;
    }

    synchronized (this) {
      if (!closed.get()) {
        if (channel != null) {
          channel.shutdown();
        }

        closed.set(true);
      }
    }
  }
}
