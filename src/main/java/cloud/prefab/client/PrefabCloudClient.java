package cloud.prefab.client;

import cloud.prefab.client.util.Cache;
import cloud.prefab.client.util.NoopCache;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.ArrayList;
import java.util.List;
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

    if (options.isLocalOnly()) {
      LOG.info("Initializing Prefab LocalOnly");
    } else {
      if (options.getApikey() == null || options.getApikey().isEmpty()) {
        throw new RuntimeException("PREFAB_API_KEY not set");
      }

      long apiKeyId = Long.parseLong(options.getApikey().split("\\-")[0]);
      LOG.info("Initializing Prefab for apiKeyId {}", apiKeyId);
    }
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

    enum Datasources {
      ALL,
      LOCAL_ONLY,
    }

    enum OnInitializationFailure {
      RAISE,
      UNLOCK,
    }

    private static final String DEFAULT_ENV = "default";
    private String prefabGrpcUrl;

    private String prefabApiUrl;
    private boolean ssl = true;
    private String apikey;
    private Optional<Cache> distributedCache = Optional.empty();

    private String configOverrideDir;
    private List<String> prefabEnvs = new ArrayList<>();

    private String namespace = "";
    private Datasources prefabDatasources = Datasources.ALL;
    private int initializationTimeoutSec = 10;
    private OnInitializationFailure onInitializationFailure =
      OnInitializationFailure.RAISE;

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
      configOverrideDir = System.getProperty("user.home");
      if ("LOCAL_ONLY".equals(System.getenv("PREFAB_DATASOURCES"))) {
        prefabDatasources = Datasources.LOCAL_ONLY;
      }
    }

    public boolean isLocalOnly() {
      return Datasources.LOCAL_ONLY == prefabDatasources;
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

    public List<String> getPrefabEnvs() {
      return prefabEnvs;
    }

    public Options setPrefabEnvs(List<String> prefabEnvs) {
      this.prefabEnvs = prefabEnvs;
      return this;
    }

    public boolean isSsl() {
      return ssl;
    }

    public Options setSsl(boolean ssl) {
      this.ssl = ssl;
      return this;
    }

    public Datasources getPrefabDatasource() {
      return prefabDatasources;
    }

    public Options setPrefabDatasource(Datasources prefabDatasources) {
      this.prefabDatasources = prefabDatasources;
      return this;
    }

    public int getInitializationTimeoutSec() {
      return initializationTimeoutSec;
    }

    public Options setInitializationTimeoutSec(int initializationTimeoutSec) {
      this.initializationTimeoutSec = initializationTimeoutSec;
      return this;
    }

    public OnInitializationFailure getOnInitializationFailure() {
      return onInitializationFailure;
    }

    public Options setOnInitializationFailure(
      OnInitializationFailure onInitializationFailure
    ) {
      this.onInitializationFailure = onInitializationFailure;
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

    public List<String> getAllPrefabEnvs() {
      final List<String> envs = new ArrayList<>();
      envs.add(DEFAULT_ENV);
      envs.addAll(prefabEnvs);
      return envs;
    }
  }
}
