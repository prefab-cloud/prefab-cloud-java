package cloud.prefab.client.internal;

import cloud.prefab.client.Options;
import cloud.prefab.client.util.MavenInfo;
import cloud.prefab.domain.Prefab;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PrefabHttpClient {

  private static final Logger LOG = LoggerFactory.getLogger(PrefabHttpClient.class);

  public static final String CLIENT_HEADER_KEY = "client";

  public static final String CLIENT_HEADER_VALUE = String.format(
    "%s.%s",
    MavenInfo.getInstance().getArtifactId(),
    MavenInfo.getInstance().getVersion()
  );
  private static final String PROTO_MEDIA_TYPE = "application/x-protobuf";
  private static final String EVENT_STREAM_MEDIA_TYPE = "text/event-stream";
  private static final String START_AT_HEADER = "x-prefab-start-at-id";

  private final Options options;
  private final HttpClient httpClient;

  PrefabHttpClient(HttpClient httpClient, Options options) {
    this.httpClient = httpClient;
    this.options = options;
  }

  void reportLoggers(Prefab.Loggers loggers) {
    HttpRequest request = getClientBuilderWithStandardHeaders()
      .header("Content-Type", PROTO_MEDIA_TYPE)
      .header("Accept", PROTO_MEDIA_TYPE)
      .uri(URI.create(options.getPrefabApiUrl() + "/api/v1/known-loggers"))
      .POST(HttpRequest.BodyPublishers.ofByteArray(loggers.toByteArray()))
      .build();
    try {
      HttpResponse<String> response = httpClient.send(
        request,
        HttpResponse.BodyHandlers.ofString()
      );

      if (!isSuccess(response.statusCode())) {
        LOG.info(
          "Uploading logger stats returned unsuccessful code {} with body {}",
          response.statusCode(),
          response.body()
        );
      }
    } catch (IOException e) {
      LOG.warn("Error uploading logger stats via http {}", e.getMessage());
    } catch (InterruptedException e) {
      LOG.warn("Interrupted while uploading logger stats via http");
      Thread.currentThread().interrupt();
    }
  }

  CompletableFuture<HttpResponse<Void>> requestConfigSSE(
    long offset,
    Flow.Subscriber<String> lineSubscriber
  ) {
    HttpRequest request = getClientBuilderWithStandardHeaders()
      .header("Accept", EVENT_STREAM_MEDIA_TYPE)
      .header(START_AT_HEADER, String.valueOf(offset))
      .timeout(Duration.ofSeconds(5))
      .uri(URI.create(options.getPrefabApiUrl() + "/api/v1/sse/config"))
      .build();

    return httpClient.sendAsync(
      request,
      HttpResponse.BodyHandlers.fromLineSubscriber(lineSubscriber)
    );
  }

  CompletableFuture<HttpResponse<Supplier<Prefab.Configs>>> requestConfigsFromApi(
    long offset
  ) {
    return requestConfigsFromURI(
      URI.create(options.getPrefabApiUrl() + "/api/v1/configs/" + offset)
    );
  }

  CompletableFuture<HttpResponse<Supplier<Prefab.Configs>>> requestConfigsFromCDN(
    long offset
  ) {
    return requestConfigsFromURI(
      URI.create(options.getCDNUrl() + "/api/v1/configs/" + offset)
    );
  }

  private CompletableFuture<HttpResponse<Supplier<Prefab.Configs>>> requestConfigsFromURI(
    URI uri
  ) {
    HttpRequest request = getClientBuilderWithStandardHeaders()
      .header("Accept", PROTO_MEDIA_TYPE)
      .timeout(Duration.ofSeconds(5))
      .uri(uri)
      .build();

    HttpResponse.BodySubscriber<InputStream> upstream = HttpResponse.BodySubscribers.ofInputStream();
    HttpResponse.BodySubscriber<Supplier<Prefab.Configs>> mapper = HttpResponse.BodySubscribers.mapping(
      upstream,
      this::configsSupplier
    );

    return httpClient.sendAsync(request, resp -> mapper);
  }

  private Supplier<Prefab.Configs> configsSupplier(InputStream inputStream) {
    return () -> {
      try {
        return Prefab.Configs.parseFrom(inputStream);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    };
  }

  private HttpRequest.Builder getClientBuilderWithStandardHeaders() {
    return HttpRequest
      .newBuilder()
      .header(CLIENT_HEADER_KEY, CLIENT_HEADER_VALUE)
      .header(
        "Authorization",
        getBasicAuthenticationHeader(options.getApiKeyId(), options.getApikey())
      );
  }

  static boolean isSuccess(int statusCode) {
    return statusCode >= 200 && statusCode < 300;
  }

  private String getBasicAuthenticationHeader(String username, String password) {
    String valueToEncode = username + ":" + password;
    return "Basic " + Base64.getEncoder().encodeToString(valueToEncode.getBytes());
  }
}
