package cloud.prefab.client.config.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import cloud.prefab.client.ConfigClient;
import cloud.prefab.domain.Prefab;
import java.util.Optional;

public class PrefabContextTurboFilter extends BaseTurboFilter {

  PrefabContextTurboFilter(ConfigClient configClient) {
    super(configClient);
  }

  public static void install(ConfigClient configClient) {
    LogbackUtils.installTurboFilter(new PrefabContextTurboFilter(configClient));
  }

  @Override
  Optional<Prefab.LogLevel> getLogLevel(Logger logger, Level level) {
    return configClient.getLogLevel(logger.getName());
  }
}
