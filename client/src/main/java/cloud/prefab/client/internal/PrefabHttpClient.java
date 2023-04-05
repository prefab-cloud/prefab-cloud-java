package cloud.prefab.client.internal;

import cloud.prefab.client.ClientAuthenticationInterceptor;
import cloud.prefab.client.Options;
import cloud.prefab.domain.Prefab;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PrefabHttpClient {

  private static final Logger LOG = LoggerFactory.getLogger(PrefabHttpClient.class);
  private static final String PROTO_MEDIA_TYPE = "application/x-protobuf";

  private final Options options;
  private final HttpClient httpClient;

  PrefabHttpClient(HttpClient httpClient, Options options) {
    this.httpClient = httpClient;
    this.options = options;
  }

  void reportLoggers(Prefab.Loggers loggers) {
    HttpRequest request = HttpRequest
      .newBuilder()
      .header(
        ClientAuthenticationInterceptor.CLIENT_HEADER_KEY,
        ClientAuthenticationInterceptor.CLIENT_HEADER_VALUE
      )
      .header(ClientAuthenticationInterceptor.CUSTOM_HEADER_KEY, options.getApikey())
      .header("Content-Type", PROTO_MEDIA_TYPE)
      .header("Accept", PROTO_MEDIA_TYPE)
      .header(
        "Authorization",
        getBasicAuthenticationHeader("ignored", options.getApikey())
      )
      .uri(URI.create(options.getPrefabApiUrl() + "/api/v1/known-loggers"))
      .POST(HttpRequest.BodyPublishers.ofByteArray(loggers.toByteArray()))
      .build();
    try {
      HttpResponse<Void> response = httpClient.send(
        request,
        HttpResponse.BodyHandlers.discarding()
      );

      if (!isSuccess(response.statusCode())) {
        LOG.info("Uploading logger stats returned code {}", response.statusCode());
      }
    } catch (IOException e) {
      LOG.warn("Error uploading logger stats via http {}", e.getMessage());
    } catch (InterruptedException e) {
      LOG.warn("Interrupted while uploading logger stats via http");
      Thread.currentThread().interrupt();
    }
  }

  static boolean isSuccess(int statusCode) {
    return statusCode >= 200 && statusCode < 300;
  }

  private String getBasicAuthenticationHeader(String username, String password) {
    String valueToEncode = username + ":" + password;
    return "Basic " + Base64.getEncoder().encodeToString(valueToEncode.getBytes());
  }
}
