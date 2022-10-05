package cloud.prefab.client.config.logging;

import cloud.prefab.client.config.ConfigChangeListener;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.Optional;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;

public class Log4j1ConfigListener extends AbstractLoggingListener<Level> {

  private static final ConfigChangeListener INSTANCE = new Log4j1ConfigListener();

  private static final Map<String, Level> LEVEL_MAP = ImmutableMap
    .<String, Level>builder()
    .put("OFF", Level.OFF)
    .put("FATAL", Level.FATAL)
    .put("ERROR", Level.ERROR)
    .put("WARN", Level.WARN)
    .put("INFO", Level.INFO)
    .put("DEBUG", Level.DEBUG)
    .put("TRACE", Level.TRACE)
    .put("ALL", Level.ALL)
    .build();

  public static ConfigChangeListener getInstance() {
    return INSTANCE;
  }

  private Log4j1ConfigListener() {}

  @Override
  protected Map<String, Level> getValidLevels() {
    return LEVEL_MAP;
  }

  @Override
  protected void setDefaultLevel(Optional<Level> level) {
    LogManager.getRootLogger().setLevel(level.orElse(null));
  }

  @Override
  protected void setLevel(String loggerName, Optional<Level> level) {
    LogManager.getLogger(loggerName).setLevel(level.orElse(null));
  }
}
