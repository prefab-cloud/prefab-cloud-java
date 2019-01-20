package cloud.prefab.client;

import cloud.prefab.client.util.Cache;
import cloud.prefab.client.util.NoopCache;
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
  private ManagedChannel channel;
  private RateLimitClient rateLimitClient;
  private ConfigClient configClient;
  private FeatureFlagClient featureFlagClient;
  private Cache noopCache;

  public PrefabCloudClient(Builder builder) {
    this.builder = builder;
    if (builder.getApikey() == null || builder.getApikey().isEmpty()) {
      throw new RuntimeException("PREFAB_API_KEY not set");
    }

    this.accountId = Long.parseLong(builder.getApikey().split("\\|")[0]);
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
      featureFlagClient = new FeatureFlagClient(this);
    }
    return featureFlagClient;
  }

  public ManagedChannel getChannel() {
    if (channel == null) {
      channel = createChannel();
    }
    return channel;
  }

  public Cache getDistributedCache(){

    if(builder.getDistributedCache().isPresent()){
      return builder.getDistributedCache().get();
    }else{
      if(noopCache == null){
        noopCache = new NoopCache();
      }
      return noopCache;
    }
  }

  private ManagedChannel createChannel() {
    ManagedChannelBuilder<?> managedChannelBuilder = ManagedChannelBuilder
        .forAddress(builder.getHost(), builder.getPort());

    if (builder.isLocal()) {
      managedChannelBuilder = ManagedChannelBuilder.forAddress("localhost", 8443);
      managedChannelBuilder.usePlaintext();
    }

    return managedChannelBuilder
        .intercept(new ClientAuthenticationInterceptor(builder.getApikey()))
        .build();
  }

  public String getNamespace() {
    return builder.getNamespace();
  }

  public String getApiKey() {
    return builder.getApikey();
  }

  public long getAccountId() {
    return accountId;
  }

  public static class Builder {
    private boolean local = false;
    private String host;
    private int port = 443;
    private String apikey;
    private Optional<Cache> distributedCache = Optional.empty();
    private Optional<MetricRegistry> metricRegistry = Optional.empty();

    private String configClasspathDir;
    private String configOverrideDir;

    private String namespace = "";


    public Builder() {
      this.apikey = System.getenv("PREFAB_API_KEY");
      this.host = Optional.ofNullable(System.getenv("PREFAB_API_URL")).orElse("api.prefab.cloud");
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

    public Builder setDistributedCache(Cache distributedCache) {
      this.distributedCache = Optional.of(distributedCache);
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

    public String getNamespace() {
      return namespace;
    }

    public Builder setNamespace(String namespace) {
      this.namespace = namespace;
      return this;
    }
  }
}
