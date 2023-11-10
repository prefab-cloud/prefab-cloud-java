package cloud.prefab.client.internal;

import cloud.prefab.domain.Prefab;

/**
 * this is for Prefab integration test use; consider unsupported
 */
@PrefabInternal
public interface TelemetryListener {
  void telemetryUpload(Prefab.TelemetryEvents telemetryEvents);
}
