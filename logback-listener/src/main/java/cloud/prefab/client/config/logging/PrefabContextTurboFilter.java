package cloud.prefab.client.config.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import cloud.prefab.client.ConfigClient;
import cloud.prefab.context.PrefabContextHelper;
import cloud.prefab.domain.Prefab;
import java.util.Optional;
import org.slf4j.LoggerFactory;

public class PrefabContextTurboFilter extends BaseTurboFilter {

  PrefabContextTurboFilter(ConfigClient configClient) {
    super(configClient);
  }

  public static void install(ConfigClient configClient) {
    PrefabContextTurboFilter filter = new PrefabContextTurboFilter(configClient);
    LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
    loggerContext.addTurboFilter(filter);
  }

  @Override
  Optional<Prefab.LogLevel> getLogLevel(Logger logger, Level level) {
    return configClient.getLogLevel(
      logger.getName(),
      PrefabContextHelper.getContextFromThreadLocal()
    );
  }
}
