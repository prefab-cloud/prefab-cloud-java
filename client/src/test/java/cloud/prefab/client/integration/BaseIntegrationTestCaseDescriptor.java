package cloud.prefab.client.integration;

import static org.assertj.core.api.Assertions.fail;

import cloud.prefab.client.Options;
import cloud.prefab.client.PrefabCloudClient;
import cloud.prefab.context.PrefabContextHelper;
import cloud.prefab.context.PrefabContextSet;
import cloud.prefab.context.PrefabContextSetReadable;
import com.google.errorprone.annotations.MustBeClosed;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.function.Executable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BaseIntegrationTestCaseDescriptor {

  private static final Logger LOG = LoggerFactory.getLogger(
    BaseIntegrationTestCaseDescriptor.class
  );
  private final String name;
  private final IntegrationTestClientOverrides clientOverrides;
  private final Optional<PrefabContextSet> globalContextMaybe;
  protected Optional<String> dataType;

  public BaseIntegrationTestCaseDescriptor(
    String name,
    IntegrationTestClientOverrides clientOverrides,
    Optional<PrefabContextSet> globalContextMaybe
  ) {
    this.name = name;
    this.clientOverrides = clientOverrides;
    this.globalContextMaybe = globalContextMaybe;
  }

  public String getName() {
    return name;
  }

  protected abstract void performVerification(PrefabCloudClient prefabCloudClient);

  private static final List<String> REQUIRED_ENV_VARS = List.of(
    "PREFAB_INTEGRATION_TEST_API_KEY",
    "PREFAB_INTEGRATION_TEST_ENCRYPTION_KEY",
    "NOT_A_NUMBER",
    "IS_A_NUMBER"
  );

  protected PrefabContextSetReadable getBlockContext() {
    return PrefabContextSet.EMPTY;
  }

  public Executable asExecutable() {
    return () -> {
      for (String requiredEnvVar : REQUIRED_ENV_VARS) {
        if (System.getenv(requiredEnvVar) == null) {
          fail(
            "Environment variable %s must be set. Please see README for required setup",
            requiredEnvVar
          );
        }
      }
      try (PrefabCloudClient client = buildClient(clientOverrides)) {
        PrefabContextHelper helper = new PrefabContextHelper(client.configClient());
        try (
          PrefabContextHelper.PrefabContextScope ignored = helper.performWorkWithAutoClosingContext(
            getBlockContext()
          )
        ) {
          performVerification(client);
        }
      }
    };
  }

  @MustBeClosed
  private PrefabCloudClient buildClient(IntegrationTestClientOverrides clientOverrides) {
    String apiKey = System.getenv("PREFAB_INTEGRATION_TEST_API_KEY");
    if (apiKey == null) {
      throw new IllegalStateException(
        "Env var PREFAB_INTEGRATION_TEST_API_KEY is not set"
      );
    }

    Options options = new Options()
      .setApikey(apiKey)
      .setPrefabApiUrl("https://api.staging-prefab.cloud")
      .setInitializationTimeoutSec(1000);
    clientOverrides
      .getInitTimeoutSeconds()
      .ifPresent(options::setInitializationTimeoutSec);
    clientOverrides.getPrefabApiUrl().ifPresent(options::setPrefabApiUrl);
    clientOverrides.getOnInitFailure().ifPresent(options::setOnInitializationFailure);
    clientOverrides.getContextUploadMode().ifPresent(options::setContextUploadMode);
    globalContextMaybe.ifPresent(options::setGlobalContext);

    if (clientOverrides.getAggregator().isPresent()) {
      LOG.error("clientOverrides-aggregator is not yet supported");
    }
    if (clientOverrides.getOnNoDefault().isPresent()) {
      LOG.error("clientOverrides-onNoDefault is not yet supported");
    }

    customizeOptions(options);
    return new PrefabCloudClient(options);
  }

  protected void customizeOptions(Options options) {}
}
