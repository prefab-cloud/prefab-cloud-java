package cloud.prefab.client.internal;

import cloud.prefab.client.Options;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ConnectivityTester {

  private static final Logger LOG = LoggerFactory.getLogger(ConnectivityTester.class);
  private final HttpClient httpClient;
  private final Options options;

  ConnectivityTester(HttpClient httpClient, Options options) {
    this.httpClient = httpClient;
    this.options = options;
  }

  public boolean testHttps() {
    HttpRequest request = HttpRequest
      .newBuilder()
      .uri(URI.create(options.getApiHosts().get(0) + "/hello"))
      .build();
    try {
      HttpResponse<Void> response = httpClient.send(
        request,
        HttpResponse.BodyHandlers.discarding()
      );
      if (PrefabHttpClient.isSuccess(response.statusCode())) {
        LOG.info("HTTP connection check succeeded");
        return true;
      } else {
        LOG.info(
          "HTTP connection to {} failed with response code {}",
          request.uri(),
          response.statusCode()
        );
      }
    } catch (IOException e) {
      LOG.info(
        "HTTP connection to {} failed with IO exception {}",
        request.uri(),
        e.getMessage()
      );
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    return false;
  }
}
