package cloud.prefab.client.config.logging;

import cloud.prefab.client.config.ConfigChangeListener;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.Optional;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;

public class Log4j2ConfigListener extends AbstractLoggingListener<Level> {

  private static final ConfigChangeListener INSTANCE = new Log4j2ConfigListener();

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

  private Log4j2ConfigListener() {}

  @Override
  protected Map<String, Level> getValidLevels() {
    return LEVEL_MAP;
  }

  @Override
  protected void setDefaultLevel(Optional<Level> level) {
    Configurator.setRootLevel(level.orElse(null));
  }

  @Override
  protected void setLevel(String loggerName, Optional<Level> level) {
    Configurator.setLevel(loggerName, level.orElse(null));
  }
}
