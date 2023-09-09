package cloud.prefab.client.config.logging;

import cloud.prefab.domain.Prefab;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import org.apache.logging.log4j.Level;

class Log4jLevelMapper {

  static final Map<Prefab.LogLevel, Level> LEVEL_MAP = ImmutableMap
    .<Prefab.LogLevel, Level>builder()
    .put(Prefab.LogLevel.FATAL, Level.ERROR)
    .put(Prefab.LogLevel.ERROR, Level.ERROR)
    .put(Prefab.LogLevel.WARN, Level.WARN)
    .put(Prefab.LogLevel.INFO, Level.INFO)
    .put(Prefab.LogLevel.DEBUG, Level.DEBUG)
    .put(Prefab.LogLevel.TRACE, Level.TRACE)
    .build();

  static final Map<Level, Prefab.LogLevel> REVERSE_LEVEL_MAP = ImmutableMap
    .<Level, Prefab.LogLevel>builder()
    .put(Level.ERROR, Prefab.LogLevel.ERROR)
    .put(Level.WARN, Prefab.LogLevel.WARN)
    .put(Level.INFO, Prefab.LogLevel.INFO)
    .put(Level.DEBUG, Prefab.LogLevel.DEBUG)
    .put(Level.TRACE, Prefab.LogLevel.TRACE)
    .build();
}
