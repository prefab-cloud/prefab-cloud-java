package cloud.prefab.client.util;

import com.google.common.collect.Maps;
import com.google.common.io.Resources;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Map;
import java.util.Properties;

public class MavenInfo {

  private static final Map<String, String> PROPERTIES = loadProperties();
  private static final MavenInfo INSTANCE = new MavenInfo(PROPERTIES);

  private final String groupId;
  private final String artifactId;
  private final String version;

  private MavenInfo(Map<String, String> properties) {
    this.groupId = properties.get("groupId");
    this.artifactId = properties.get("artifactId");
    this.version = properties.get("version");
  }

  public static MavenInfo getInstance() {
    return INSTANCE;
  }

  public String getGroupId() {
    return groupId;
  }

  public String getArtifactId() {
    return artifactId;
  }

  public String getVersion() {
    return version;
  }

  private static Map<String, String> loadProperties() {
    Properties properties = new Properties();

    URL propertiesUrl = Resources.getResource("prefab.properties");
    try (InputStream inputStream = propertiesUrl.openStream()) {
      properties.load(inputStream);
    } catch (IOException e) {
      throw new RuntimeException(
        "Error trying to load properties from: " + propertiesUrl,
        e
      );
    }

    return Maps.fromProperties(properties);
  }
}
