package cloud.prefab.client.config.logging;

import cloud.prefab.client.config.ConfigChangeListener;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class JavaUtilLoggingConfigListener extends AbstractLoggingListener<Level> {

  private static final ConfigChangeListener INSTANCE = new JavaUtilLoggingConfigListener();

  private static final String ROOT_LOGGER = "";

  private static final Map<String, Level> LEVEL_MAP = ImmutableMap
    .<String, Level>builder()
    .put("OFF", Level.OFF)
    .put("SEVERE", Level.SEVERE)
    .put("WARNING", Level.WARNING)
    .put("INFO", Level.INFO)
    .put("CONFIG", Level.CONFIG)
    .put("FINE", Level.FINE)
    .put("FINER", Level.FINER)
    .put("FINEST", Level.FINEST)
    .put("ALL", Level.ALL)
    .build();

  public static ConfigChangeListener getInstance() {
    return INSTANCE;
  }

  private JavaUtilLoggingConfigListener() {}

  @Override
  protected Map<String, Level> getValidLevels() {
    return LEVEL_MAP;
  }

  @Override
  protected void setDefaultLevel(Optional<Level> level) {
    setLevel(ROOT_LOGGER, level);
  }

  @Override
  protected void setLevel(String loggerName, Optional<Level> level) {
    Logger logger = LogManager.getLogManager().getLogger(loggerName);

    logger.setLevel(level.orElse(null));
  }
}
