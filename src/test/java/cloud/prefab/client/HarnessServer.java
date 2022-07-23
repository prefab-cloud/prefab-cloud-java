package cloud.prefab.client;

import cloud.prefab.domain.Prefab;
import com.google.common.collect.Maps;
import fi.iki.elonen.NanoHTTPD;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
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
    String jsonString = null;
    try {
      jsonString = new String(Base64.getDecoder().decode(parms.get("props")), "UTF-8");
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    }
    JSONParser parser = new JSONParser();
    JSONObject json = null;
    try {
      json = (JSONObject) parser.parse(jsonString);
    } catch (ParseException e) {
      e.printStackTrace();
    }

    System.out.println("params " + json);

    String key = (String) json.get("key");
    String namespace = (String) json.get("namespace");
    String apiKey = (String) json.get("api_key");
    String user_key = (String) json.get("user_key");
    Object attributesObj = json.get("attributes");
    Map<String, String> attributes = new HashMap<>();
    if (attributesObj != null) {
      JSONObject jsonAttributes = (JSONObject) attributesObj;
      attributes.putAll(jsonAttributes);
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
