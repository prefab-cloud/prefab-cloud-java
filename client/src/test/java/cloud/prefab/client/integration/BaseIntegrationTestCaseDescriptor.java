package cloud.prefab.client.integration;

import cloud.prefab.client.Options;
import cloud.prefab.client.PrefabCloudClient;
import cloud.prefab.context.PrefabContextHelper;
import cloud.prefab.context.PrefabContextSetReadable;
import com.google.errorprone.annotations.MustBeClosed;
import org.junit.jupiter.api.function.Executable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BaseIntegrationTestCaseDescriptor {

  private static final Logger LOG = LoggerFactory.getLogger(
    BaseIntegrationTestCaseDescriptor.class
  );
  private final String name;
  private final IntegrationTestClientOverrides clientOverrides;

  BaseIntegrationTestCaseDescriptor(
    String name,
    IntegrationTestClientOverrides clientOverrides
  ) {
    this.name = name;
    this.clientOverrides = clientOverrides;
  }

  public String getName() {
    return name;
  }

  protected abstract void performVerification(PrefabCloudClient prefabCloudClient);

  public Executable asExecutable(PrefabContextSetReadable prefabContext) {
    return () -> {
      try (PrefabCloudClient client = buildClient(clientOverrides)) {
        PrefabContextHelper helper = new PrefabContextHelper(client.configClient());
        try (
          PrefabContextHelper.PrefabContextScope ignored = helper.performWorkWithAutoClosingContext(
            prefabContext
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

    clientOverrides.getNamespace().ifPresent(options::setNamespace);
    clientOverrides
      .getInitTimeoutSeconds()
      .ifPresent(options::setInitializationTimeoutSec);
    clientOverrides.getPrefabApiUrl().ifPresent(options::setPrefabApiUrl);
    clientOverrides.getOnInitFailure().ifPresent(options::setOnInitializationFailure);
    clientOverrides.getContextUploadMode().ifPresent(options::setContextUploadMode);

    if (clientOverrides.getAggregator().isPresent()) {
      LOG.error("clientOverrides-aggregator is not yet supported");
    }
    if (clientOverrides.getOnNoDefault().isPresent()) {
      LOG.error("clientOverrides-onNoDefault is not yet supported");
    }
    options.setTelemetryListener(new TelemetryAccumulator());
    return new PrefabCloudClient(options);
  }
}
