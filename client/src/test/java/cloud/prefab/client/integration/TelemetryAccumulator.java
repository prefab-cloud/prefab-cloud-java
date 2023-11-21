package cloud.prefab.client.integration;

import cloud.prefab.client.internal.TelemetryListener;
import cloud.prefab.domain.Prefab;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TelemetryAccumulator implements TelemetryListener {

  private static final Logger LOG = LoggerFactory.getLogger(TelemetryAccumulator.class);

  final List<Prefab.TelemetryEvents> telemetryEventsList = new ArrayList<>();

  @Override
  public void telemetryUpload(Prefab.TelemetryEvents telemetryEvents) {
    telemetryEventsList.add(telemetryEvents);
    LOG.info("telemetry event stored for verification");
  }

  public List<Prefab.TelemetryEvents> getTelemetryEventsList() {
    return telemetryEventsList;
  }
}
