package cloud.prefab.client;

import cloud.prefab.client.util.Cache;
import com.codahale.metrics.MetricRegistry;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class PrefabCloudClient {

  private static final Logger LOG = LoggerFactory.getLogger(PrefabCloudClient.class);

  private final Builder builder;
  private final long accountId;

  public PrefabCloudClient(Builder builder) {
    this.builder = builder;
    if (builder.getApikey() == null || builder.getApikey().isEmpty()) {
      throw new RuntimeException("PREFAB_API_KEY not set");
    }

    this.accountId = Long.parseLong(builder.getApikey().split("|")[0]);
  }

  public RateLimitClient newRateLimitClient() {
    return new RateLimitClient(this);
  }

  public ConfigClient newConfigClient(String namespace) {
    return new ConfigClient(namespace, this);
  }

  public ManagedChannel getChannel() {
    ManagedChannelBuilder<?> managedChannelBuilder = ManagedChannelBuilder
        .forAddress(builder.getHost(), builder.getPort());
    if (builder.isLocal()) {
      managedChannelBuilder = ManagedChannelBuilder.forAddress("localhost", 8443);
      managedChannelBuilder.usePlaintext(true);
    }

    return managedChannelBuilder
        .intercept(new ClientAuthenticationInterceptor(builder.getApikey()))
        .build();
  }

  public long getAccountId() {
    return accountId;
  }

  public void logInternal(String message) {
    LOG.info(message);
  }

  public static class Builder {
    private boolean local = false;
    private String host = "api.prefab.cloud";
    private int port = 8443;
    private String apikey;
    private Optional<Cache> distributedCache = Optional.empty();
    private Optional<MetricRegistry> metricRegistry = Optional.empty();

    private String configClasspathDir;
    private String configOverrideDir;

    public Builder() {
      this.apikey = System.getenv("PREFAB_API_KEY");
      configClasspathDir = "";
      configOverrideDir = "";
    }

    public String getHost() {
      return host;
    }

    public Builder setHost(String host) {
      this.host = host;
      return this;
    }

    public int getPort() {
      return port;
    }

    public Builder setPort(int port) {
      this.port = port;
      return this;
    }

    public String getApikey() {
      return apikey;
    }

    public Builder setApikey(String apikey) {
      this.apikey = apikey;
      return this;
    }

    public Optional<Cache> getDistributedCache() {
      return distributedCache;
    }

    public Builder setDistributedCache(Optional<Cache> distributedCache) {
      this.distributedCache = distributedCache;
      return this;
    }

    public Optional<MetricRegistry> getMetricRegistry() {
      return metricRegistry;
    }

    public Builder setMetricRegistry(Optional<MetricRegistry> metricRegistry) {
      this.metricRegistry = metricRegistry;
      return this;
    }

    public String getConfigClasspathDir() {
      return configClasspathDir;
    }

    public Builder setConfigClasspathDir(String configClasspathDir) {
      this.configClasspathDir = configClasspathDir;
      return this;
    }

    public String getConfigOverrideDir() {
      return configOverrideDir;
    }

    public Builder setConfigOverrideDir(String configOverrideDir) {
      this.configOverrideDir = configOverrideDir;
      return this;
    }

    public boolean isLocal() {
      return local;
    }

    public Builder setLocal(boolean local) {
      this.local = local;
      return this;
    }
  }
}
