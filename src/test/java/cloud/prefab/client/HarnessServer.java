package cloud.prefab.client;

import cloud.prefab.domain.Prefab;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fi.iki.elonen.NanoHTTPD;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HarnessServer extends NanoHTTPD {

  private static final Logger LOG = LoggerFactory.getLogger(HarnessServer.class);

  public HarnessServer() throws IOException {
    super(8080);
    start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
    System.out.println("\nRunning! Point your browsers to http://localhost:8080/ \n");
    LOG.info("Running! Point your browsers to http://localhost:8080/");
  }

  public static void main(String[] args) {
    try {
      new HarnessServer();
    } catch (IOException ioe) {
      System.err.println("Couldn't start server:\n" + ioe);
    }
  }

  @Override
  public Response serve(IHTTPSession session) {
    Map<String, String> parms = session.getParms();
    String jsonString = new String(
      Base64.getDecoder().decode(parms.get("props")),
      StandardCharsets.UTF_8
    );

    JsonObject json = JsonParser.parseString(jsonString).getAsJsonObject();

    System.out.println("params " + json);

    String key = json.get("key").getAsString();
    String namespace = json.get("namespace").getAsString();
    String apiKey = json.get("api_key").getAsString();
    String user_key = json.get("user_key").getAsString();
    JsonElement attributesObj = json.get("attributes");
    Map<String, String> attributes = new HashMap<>();
    if (attributesObj != null) {
      JsonObject jsonAttributes = attributesObj.getAsJsonObject();
      jsonAttributes
        .entrySet()
        .forEach(entry -> attributes.put(entry.getKey(), entry.getValue().getAsString()));
    }
    boolean featureFlag = json.get("feature_flag") != null;

    try {
      PrefabCloudClient prefabCloudClient = new PrefabCloudClient(
        new PrefabCloudClient.Builder()
          .setApikey(apiKey)
          .setTarget("localhost:50051")
          .setNamespace(namespace)
          .setSsl(false)
      );
      if (featureFlag) {
        final Optional<Prefab.FeatureFlagVariant> featureFlagVariant = prefabCloudClient
          .featureFlagClient()
          .get(key, Optional.of(user_key), attributes);
        if (featureFlagVariant.isPresent()) {
          LOG.info("return {}", featureFlagVariant.get());
          return newFixedLengthResponse(featureFlagVariant.get().getString());
        } else {
          LOG.info("No ff found {}", key);
          return newFixedLengthResponse("No FF Found");
        }
      } else {
        final Optional<Prefab.ConfigValue> configValue = prefabCloudClient
          .configClient()
          .get(key);
        final String result = configValue
          .orElse(Prefab.ConfigValue.newBuilder().setString("").build())
          .getString();
        LOG.info("Return {} for {}", result, key);
        return newFixedLengthResponse(result);
      }
    } catch (Exception e) {
      e.printStackTrace();
      throw e;
    }
  }
}
