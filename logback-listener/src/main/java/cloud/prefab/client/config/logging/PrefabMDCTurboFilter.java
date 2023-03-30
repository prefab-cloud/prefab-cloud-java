package cloud.prefab.client.config.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import cloud.prefab.client.ConfigClient;
import cloud.prefab.domain.Prefab;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.Marker;

public class PrefabMDCTurboFilter extends TurboFilter {

  private final ThreadLocal<Boolean> recursionCheck = ThreadLocal.withInitial(() -> false
  );

  private final ConfigClient configClient;

  PrefabMDCTurboFilter(ConfigClient configClient) {
    this.configClient = configClient;
  }

  public static void install(ConfigClient configClient) {
    PrefabMDCTurboFilter filter = new PrefabMDCTurboFilter(configClient);
    LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
    loggerContext.addTurboFilter(filter);
  }

  @Override
  public FilterReply decide(
    Marker marker,
    Logger logger,
    Level level,
    String s,
    Object[] objects,
    Throwable throwable
  ) {
    if (!configClient.isReady()) {
      return FilterReply.NEUTRAL;
    }
    if (recursionCheck.get()) {
      return FilterReply.NEUTRAL;
    } else {
      recursionCheck.set(true);
    }
    configClient.reportLoggerUsage(
      logger.getName(),
      LogbackLevelMapper.REVERSE_LEVEL_MAP.get(level),
      1
    );

    try {
      Map<String, String> mdcData = MDC.getCopyOfContextMap();
      Optional<Prefab.LogLevel> loglevelMaybe = configClient.getLogLevelFromStringMap(
        logger.getName(),
        mdcData != null ? mdcData : Collections.emptyMap()
      );
      if (loglevelMaybe.isPresent()) {
        Level calculatedMinLogLevelToAccept = LogbackLevelMapper.LEVEL_MAP.get(
          loglevelMaybe.get()
        );
        if (level.isGreaterOrEqual(calculatedMinLogLevelToAccept)) {
          return FilterReply.ACCEPT;
        }
        return FilterReply.DENY;
      }
      return FilterReply.NEUTRAL;
    } finally {
      recursionCheck.set(false);
    }
  }
}