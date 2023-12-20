package cloud.prefab.client.config.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import cloud.prefab.client.ConfigClient;
import cloud.prefab.client.config.ConfigValueUtils;
import cloud.prefab.context.PrefabContext;
import cloud.prefab.domain.Prefab;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import org.slf4j.MDC;

public class PrefabMDCTurboFilter extends BaseTurboFilter {

  PrefabMDCTurboFilter(ConfigClient configClient) {
    super(configClient);
  }

  public static void install(ConfigClient configClient) {
    LogbackUtils.installTurboFilter(new PrefabMDCTurboFilter(configClient));
  }

  @Override
  Optional<Prefab.LogLevel> getLogLevel(Logger logger, Level level) {
    Map<String, String> mdcData = MDC.getCopyOfContextMap();
    return configClient.getLogLevel(
      logger.getName(),
      PrefabContext.unnamedFromMap(
        ConfigValueUtils.fromStringMap(mdcData != null ? mdcData : Collections.emptyMap())
      )
    );
  }
}
