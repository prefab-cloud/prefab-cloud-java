package cloud.prefab.client;

import cloud.prefab.client.util.Cache;
import cloud.prefab.client.util.NoopCache;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PrefabCloudClient {

  private static final Logger LOG = LoggerFactory.getLogger(PrefabCloudClient.class);

  private final Options options;
  private ManagedChannel channel;
  private RateLimitClient rateLimitClient;
  private ConfigClient configClient;
  private FeatureFlagClient featureFlagClient;
  private Cache noopCache;

  public PrefabCloudClient(Options options) {
    this.options = options;
    if (options.getApikey() == null || options.getApikey().isEmpty()) {
      throw new RuntimeException("PREFAB_API_KEY not set");
    }

    long apiKeyId = Long.parseLong(options.getApikey().split("\\-")[0]);
    LOG.info("Initializing Prefab for apiKeyId {}", apiKeyId);
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
    if (channel == null) {
      channel = createChannel();
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

  public static class Options {

    private String prefabGrpcUrl;

    private String prefabApiUrl;
    private boolean ssl = true;
    private String apikey;
    private Optional<Cache> distributedCache = Optional.empty();

    private String configClasspathDir;
    private String configOverrideDir;

    private String namespace = "";

    public Options() {
      this.apikey = System.getenv("PREFAB_API_KEY");
      this.prefabGrpcUrl =
        Optional
          .ofNullable(System.getenv("PREFAB_GRPC_URL"))
          .orElse("grpc.prefab.cloud:443");
      this.prefabApiUrl =
        Optional
          .ofNullable(System.getenv("PREFAB_API_URL"))
          .orElse("https://api.prefab.cloud");
      configClasspathDir = "";
      configOverrideDir = "";
    }

    public String getApikey() {
      return apikey;
    }

    public Options setApikey(String apikey) {
      this.apikey = apikey;
      return this;
    }

    public Optional<Cache> getDistributedCache() {
      return distributedCache;
    }

    public Options setDistributedCache(Cache distributedCache) {
      this.distributedCache = Optional.of(distributedCache);
      return this;
    }

    public String getConfigClasspathDir() {
      return configClasspathDir;
    }

    public Options setConfigClasspathDir(String configClasspathDir) {
      this.configClasspathDir = configClasspathDir;
      return this;
    }

    public String getConfigOverrideDir() {
      return configOverrideDir;
    }

    public Options setConfigOverrideDir(String configOverrideDir) {
      this.configOverrideDir = configOverrideDir;
      return this;
    }

    public String getNamespace() {
      return namespace;
    }

    public Options setNamespace(String namespace) {
      this.namespace = namespace;
      return this;
    }

    public String getPrefabGrpcUrl() {
      return prefabGrpcUrl;
    }

    public Options setPrefabGrpcUrl(String prefabGrpcUrl) {
      this.prefabGrpcUrl = prefabGrpcUrl;
      return this;
    }

    public String getPrefabApiUrl() {
      return prefabApiUrl;
    }

    public Options setPrefabApiUrl(String prefabApiUrl) {
      this.prefabApiUrl = prefabApiUrl;
      return this;
    }

    public boolean isSsl() {
      return ssl;
    }

    public Options setSsl(boolean ssl) {
      this.ssl = ssl;
      return this;
    }

    public String getCDNUrl() {
      String envVar = System.getenv("PREFAB_CDN_URL");
      if (envVar != null) {
        return envVar;
      } else {
        return String.format(
          "%s.global.ssl.fastly.net",
          prefabApiUrl.replaceAll("\\.", "-")
        );
      }
    }
  }
}
