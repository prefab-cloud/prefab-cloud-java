package cloud.prefab.client;

import cloud.prefab.client.config.ConfigChangeListener;
import cloud.prefab.client.config.logging.LogLevelChangeListener;
import cloud.prefab.client.internal.PrefabInternal;
import cloud.prefab.client.internal.TelemetryListener;
import cloud.prefab.client.internal.ThreadLocalContextStore;
import cloud.prefab.context.ContextStore;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;

public class Options {

  public enum Datasources {
    ALL,
    LOCAL_ONLY,
  }

  public enum OnInitializationFailure {
    RAISE,
    UNLOCK,
  }

  public enum CollectContextMode {
    NONE,
    SHAPE_ONLY,
    PERIODIC_EXAMPLE,
  }

  private static final String DEFAULT_ENV = "default";

  private String prefabApiUrl;
  private String apikey;
  private String configOverrideDir;
  private List<String> prefabEnvs = new ArrayList<>();
  private Datasources prefabDatasources = Datasources.ALL;
  private int initializationTimeoutSec = 10;
  private OnInitializationFailure onInitializationFailure = OnInitializationFailure.RAISE;
  private boolean collectLoggerCounts = true;

  private ContextStore contextStore = ThreadLocalContextStore.INSTANCE;

  private final Set<ConfigChangeListener> changeListenerSet = new HashSet<>();

  private final Set<LogLevelChangeListener> logLevelChangeListeners = new HashSet<>();

  private boolean evaluatedConfigKeyUploadEnabled = true;

  private boolean collectEvaluationSummaries = true;

  private CollectContextMode collectContextMode = CollectContextMode.PERIODIC_EXAMPLE;

  private int telemetryUploadIntervalSeconds = 15;

  @Nullable
  private String localDatafile;

  @Nullable
  private TelemetryListener telemetryListener;

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
    localDatafile = System.getProperty("PREFAB_DATAFILE");
  }

  public boolean isLocalOnly() {
    return Datasources.LOCAL_ONLY == prefabDatasources;
  }

  public String getApikey() {
    return apikey;
  }

  /**
   * Sets the API key to be used to communicate with the Prefab APIs
   * Can also be specified with env var `PREFAB_API_KEY`
   * @param apikey the key
   * @return Options
   */
  public Options setApikey(String apikey) {
    this.apikey = apikey;
    return this;
  }

  public String getConfigOverrideDir() {
    return configOverrideDir;
  }

  /**
   * Sets a directory to load additional config files from in addition to on the classpath
   * Defaults to the current user's home directory.
   * see the docs for {@link Options#setPrefabEnvs(List)} setPrefabEnvs} for more dicussion on file loading
   * @param configOverrideDir
   * @return
   */
  public Options setConfigOverrideDir(String configOverrideDir) {
    this.configOverrideDir = configOverrideDir;
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

  /**
   * Set the prefab environment names in order of increasing precedence
   * Files named with the pattern `.prefab.%s.config.yaml` are loaded first with `default` then the supplied envs, in order
   * This means a key in an env named later in the list will override the same key earlier in the list
   * Files are loaded from the classpath first, then from the configured override directory
   * @param prefabEnvs
   * @return this
   */
  public Options setPrefabEnvs(List<String> prefabEnvs) {
    this.prefabEnvs = prefabEnvs;
    return this;
  }

  public Datasources getPrefabDatasource() {
    return prefabDatasources;
  }

  /**
   * Configure the Prefab clients to use the API or rely solely on local files
   * @param prefabDatasources one of DataSource.ALL or DataSource.LOCAL_ONLY
   * @return
   */

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

  public boolean isCollectLoggerCounts() {
    return collectLoggerCounts;
  }

  /**
   * Configure client to report logging statistics to prefab.
   * The captured data consists of fully qualified logger name with counts of log messages by level.
   * The data allows prefab to preconfigure the log levels UI.
   * Defaults to true
   * @param collectLoggerCounts
   * @return
   */

  public Options setCollectLoggerCounts(boolean collectLoggerCounts) {
    this.collectLoggerCounts = collectLoggerCounts;
    return this;
  }

  public CollectContextMode getContextUploadMode() {
    return collectContextMode;
  }

  public Options setContextUploadMode(CollectContextMode collectContextMode) {
    this.collectContextMode = collectContextMode;
    return this;
  }

  public boolean isCollectContextShapeEnabled() {
    return collectContextMode != CollectContextMode.NONE;
  }

  public boolean isCollectExampleContextEnabled() {
    return collectContextMode == CollectContextMode.PERIODIC_EXAMPLE;
  }

  /**
   * Configure client to report the keys of evaluated configurations
   * The data allows prefab to show which configs are used vs unused
   * Defaults to true
   * @param enabled
   * @return
   */

  public Options setEvaluatedConfigKeyUploadEnabled(boolean enabled) {
    this.evaluatedConfigKeyUploadEnabled = enabled;
    return this;
  }

  public boolean isEvaluatedConfigKeyUploadEnabled() {
    return evaluatedConfigKeyUploadEnabled;
  }

  public boolean isCollectEvaluationSummaries() {
    return collectEvaluationSummaries;
  }

  public Options setCollectEvaluationSummaries(boolean collectEvaluationSummaries) {
    this.collectEvaluationSummaries = collectEvaluationSummaries;
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

  public Options setContextStore(ContextStore contextStore) {
    this.contextStore = contextStore;
    return this;
  }

  public ContextStore getContextStore() {
    return contextStore;
  }

  public Options addConfigChangeListener(ConfigChangeListener configChangeListener) {
    changeListenerSet.add(configChangeListener);
    return this;
  }

  public Set<ConfigChangeListener> getChangeListeners() {
    return ImmutableSet.copyOf(changeListenerSet);
  }

  public Options addLogLevelChangeListener(LogLevelChangeListener configChangeListener) {
    logLevelChangeListeners.add(configChangeListener);
    return this;
  }

  public Set<LogLevelChangeListener> getLogLevelChangeListeners() {
    return ImmutableSet.copyOf(logLevelChangeListeners);
  }

  @PrefabInternal
  public Options setTelemetryListener(@Nullable TelemetryListener telemetryListener) {
    this.telemetryListener = telemetryListener;
    return this;
  }

  @PrefabInternal
  public Optional<TelemetryListener> getTelemetryListener() {
    return Optional.ofNullable(telemetryListener);
  }

  public int getTelemetryUploadIntervalSeconds() {
    return telemetryUploadIntervalSeconds;
  }

  public Options setTelemetryUploadIntervalSeconds(int telemetryUploadIntervalSeconds) {
    this.telemetryUploadIntervalSeconds = telemetryUploadIntervalSeconds;
    return this;
  }

  @Nullable
  public String getLocalDatafile() {
    return localDatafile;
  }

  public Options setLocalDatafile(@Nullable String localDatafile) {
    this.localDatafile = localDatafile;
    return this;
  }

  public boolean isLocalDatafileMode() {
    return localDatafile != null;
  }
}
