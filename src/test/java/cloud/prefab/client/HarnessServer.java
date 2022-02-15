package cloud.prefab.client;


import cloud.prefab.domain.Prefab;
import fi.iki.elonen.NanoHTTPD;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

public class HarnessServer extends NanoHTTPD {


  public HarnessServer() throws IOException {
    super(8080);
    start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
    System.out.println("\nRunning! Point your browsers to http://localhost:8080/ \n");
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

    System.out.println("params "+json);

    String key = (String) json.get("key");
    String namespace = (String) json.get("namespace");
    String environment = (String) json.get("environment");
    String user_key = (String) json.get("user_key");


    PrefabCloudClient prefabCloudClient = new PrefabCloudClient(new PrefabCloudClient.Builder()
        .setApikey("1-"+environment+"-local_development_api_key")
        .setTarget("localhost:50051")
        .setNamespace(namespace)
        .setSsl(false));

    final Optional<Prefab.ConfigValue> configValue = prefabCloudClient.configClient().get(key);
    return newFixedLengthResponse(configValue.orElse(Prefab.ConfigValue.newBuilder().setString("").build()).getString());
  }


}
