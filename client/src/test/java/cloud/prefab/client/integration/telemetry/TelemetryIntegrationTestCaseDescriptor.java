package cloud.prefab.client.integration.telemetry;

import cloud.prefab.client.Options;
import cloud.prefab.client.PrefabCloudClient;
import cloud.prefab.client.integration.BaseIntegrationTestCaseDescriptor;
import cloud.prefab.client.integration.IntegrationTestCaseDescriptorIF;
import cloud.prefab.client.integration.IntegrationTestClientOverrides;
import cloud.prefab.client.integration.TelemetryAccumulator;
import com.google.common.base.MoreObjects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class TelemetryIntegrationTestCaseDescriptor
  extends BaseIntegrationTestCaseDescriptor
  implements IntegrationTestCaseDescriptorIF {

  private static final Logger LOG = LoggerFactory.getLogger(
    TelemetryIntegrationTestCaseDescriptor.class
  );

  public TelemetryIntegrationTestCaseDescriptor(
    String name,
    IntegrationTestClientOverrides clientOverrides
  ) {
    super(
      name,
      MoreObjects.firstNonNull(clientOverrides, IntegrationTestClientOverrides.empty())
    );
  }

  TelemetryAccumulator getTelemetryAccumulator(PrefabCloudClient prefabCloudClient) {
    return (TelemetryAccumulator) prefabCloudClient
      .getOptions()
      .getTelemetryListener()
      .orElseThrow();
  }

  @Override
  protected void customizeOptions(Options options) {
    super.customizeOptions(options);
    options.setTelemetryListener(new TelemetryAccumulator());
    options.setTelemetryUploadIntervalSeconds(1);
  }
}
