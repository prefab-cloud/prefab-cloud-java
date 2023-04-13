package cloud.prefab.client;

import cloud.prefab.client.util.Cache;
import cloud.prefab.context.PrefabContext;
import cloud.prefab.context.PrefabContextHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public class Options {

  public enum Datasources {
    ALL,
    LOCAL_ONLY,
  }

  public enum OnInitializationFailure {
    RAISE,
    UNLOCK,
  }

  private static final String DEFAULT_ENV = "default";

  private String prefabApiUrl;
  private boolean ssl = true;
  private String apikey;
  private Optional<Cache> distributedCache = Optional.empty();

  private String configOverrideDir;
  private List<String> prefabEnvs = new ArrayList<>();

  private String namespace = "";
  private Datasources prefabDatasources = Datasources.ALL;
  private int initializationTimeoutSec = 10;
  private OnInitializationFailure onInitializationFailure = OnInitializationFailure.RAISE;
  private boolean reportLogStats = true;

  private Supplier<Optional<PrefabContext>> contextSupplier =
    PrefabContextHelper::getContextFromThreadLocal;

  public Options() {
    this.apikey = System.getenv("PREFAB_API_KEY");
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

  public Optional<String> getNamespace() {
    if (namespace.isEmpty()) {
      return Optional.empty();
    }
    return Optional.ofNullable(namespace);
  }

  public Options setNamespace(String namespace) {
    this.namespace = namespace;
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

  public boolean isReportLogStats() {
    return reportLogStats;
  }

  public Options setReportLogStats(boolean reportLogStats) {
    this.reportLogStats = reportLogStats;
    return this;
  }

  public String getCDNUrl() {
    String envVar = System.getenv("PREFAB_CDN_URL");
    if (envVar != null) {
      return envVar;
    } else {
      return String.format(
        "%s.global.ssl.fastly.net",
        prefabApiUrl.replaceAll("/$", "").replaceAll("\\.", "-")
      );
    }
  }

  public List<String> getAllPrefabEnvs() {
    final List<String> envs = new ArrayList<>();
    envs.add(DEFAULT_ENV);
    envs.addAll(prefabEnvs);
    return envs;
  }

  public String getApiKeyId() {
    return getApikey().split("\\-")[0];
  }

  public Options setContextSupplier(Supplier<Optional<PrefabContext>> supplier) {
    this.contextSupplier = supplier;
    return this;
  }

  public Supplier<Optional<PrefabContext>> getContextSupplier() {
    return contextSupplier;
  }
}
