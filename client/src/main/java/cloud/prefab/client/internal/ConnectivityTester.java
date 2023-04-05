package cloud.prefab.client.internal;

import cloud.prefab.client.Options;
import cloud.prefab.domain.GreetingServiceGrpc;
import cloud.prefab.domain.Prefab;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ConnectivityTester {

  private static final Logger LOG = LoggerFactory.getLogger(ConnectivityTester.class);
  private final GreetingServiceGrpc.GreetingServiceFutureStub greetingServiceFutureStub;
  private final HttpClient httpClient;
  private final Options options;

  ConnectivityTester(
    GreetingServiceGrpc.GreetingServiceFutureStub greetingServiceFutureStub,
    HttpClient httpClient,
    Options options
  ) {
    this.greetingServiceFutureStub = greetingServiceFutureStub;
    this.httpClient = httpClient;
    this.options = options;
  }

  public boolean testGrpc() {
    int attempts = 4;
    try {
      int attempt = 1;
      while (attempt < attempts) {
        String uniqueGreeting = UUID.randomUUID().toString();
        try {
          ListenableFuture<Prefab.GreetingResponse> responseFuture = greetingServiceFutureStub.greet(
            Prefab.GreetingMessage.newBuilder().setGreeting(uniqueGreeting).build()
          );
          responseFuture.get(5, TimeUnit.SECONDS);
          LOG.info("GRPC connection check succeeded");
          return true;
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
        attempt++;
      }
      LOG.info("Attempts to reach GRPC connectivity test exhausted");
    } catch (TimeoutException e) {
      LOG.info("Timeout while attempting to reach GRPC connectivity test service");
      return false;
    } catch (ExecutionException e) {
      LOG.info(
        "Error while performing GRPC connectivity test {} (DEBUG for stacktrace)",
        e.getCause().getMessage()
      );
      LOG.debug("Error while performing GRPC connectivity test", e.getCause());
    }
    return false;
  }

  public boolean testHttps() {
    HttpRequest request = HttpRequest
      .newBuilder()
      .uri(URI.create(options.getPrefabApiUrl() + "/hello"))
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
