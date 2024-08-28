package cloud.prefab.client.internal;

import cloud.prefab.client.Options;
import cloud.prefab.client.util.MavenInfo;
import cloud.prefab.domain.Prefab;
import dev.failsafe.Failsafe;
import dev.failsafe.Fallback;
import dev.failsafe.RetryPolicy;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PrefabHttpClient {

  private static final Logger LOG = LoggerFactory.getLogger(PrefabHttpClient.class);

  public static final String CLIENT_HEADER_KEY = "client";
  private static final String VERSION_HEADER = "X-PrefabCloud-Client-Version";

  public static final String CLIENT_HEADER_VALUE = String.format(
    "%s.%s",
    MavenInfo.getInstance().getArtifactId(),
    MavenInfo.getInstance().getVersion()
  );

  public static final String NEW_CLIENT_HEADER_VALUE =
    "prefab-cloud-java-" + MavenInfo.getInstance().getVersion();

  private static final String PROTO_MEDIA_TYPE = "application/x-protobuf";
  private static final String EVENT_STREAM_MEDIA_TYPE = "text/event-stream";
  private static final String START_AT_HEADER = "x-prefab-start-at-id";
  private final Options options;
  private final HttpClient httpClient;
  private final URI telemetryUrl;
  private final List<String> apiHosts;

  PrefabHttpClient(HttpClient httpClient, Options options) {
    this.httpClient = httpClient;
    this.options = options;
    this.telemetryUrl =
      URI.create(options.getPrefabTelemetryHost() + "/api/v1/telemetry");
    this.apiHosts = options.getApiHosts();

    LOG.info("Will send telemetry to {}", telemetryUrl);
  }

  private static HttpResponse.BodySubscriber<Supplier<Prefab.TelemetryEventsResponse>> asProto() {
    HttpResponse.BodySubscriber<InputStream> upstream = HttpResponse.BodySubscribers.ofInputStream();

    return HttpResponse.BodySubscribers.mapping(
      upstream,
      inputStream ->
        () -> {
          try (InputStream stream = inputStream) {
            return Prefab.TelemetryEventsResponse.parseFrom(stream);
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
        }
    );
  }

  CompletableFuture<HttpResponse<Supplier<Prefab.TelemetryEventsResponse>>> reportTelemetryEvents(
    Prefab.TelemetryEvents telemetryEvents
  ) {
    HttpRequest request = getClientBuilderWithStandardHeaders()
      .header("Content-Type", PROTO_MEDIA_TYPE)
      .header("Accept", PROTO_MEDIA_TYPE)
      .uri(telemetryUrl)
      .POST(HttpRequest.BodyPublishers.ofByteArray(telemetryEvents.toByteArray()))
      .build();
    return httpClient.sendAsync(request, responseInfo -> asProto());
  }

  CompletableFuture<HttpResponse<Void>> createSSEConfigConnection(
    long offset,
    Flow.Subscriber<String> lineSubscriber
  ) {
    return executeWithFailover(host -> {
      URI uri = URI.create(host + "/api/v1/sse/config");
      LOG.info("Requesting SSE from {}", uri);
      HttpRequest request = getClientBuilderWithStandardHeaders()
        .header("Accept", EVENT_STREAM_MEDIA_TYPE)
        .header(START_AT_HEADER, String.valueOf(offset))
        .timeout(Duration.ofSeconds(5))
        .uri(uri)
        .build();
      return httpClient
        .sendAsync(request, HttpResponse.BodyHandlers.fromLineSubscriber(lineSubscriber))
        .whenCompleteAsync(this::checkForAuthFailure);
    });
  }

  CompletableFuture<HttpResponse<Supplier<Prefab.Configs>>> requestConfigs(long offset) {
    return executeWithFailover(host ->
      requestConfigsFromURI(URI.create(host + "/api/v1/configs/" + offset))
    );
  }

  private CompletableFuture<HttpResponse<Supplier<Prefab.Configs>>> requestConfigsFromURI(
    URI uri
  ) {
    LOG.info("Requesting configs from {}", uri);
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
    return httpClient
      .sendAsync(request, resp -> mapper)
      .whenCompleteAsync(this::checkForAuthFailure);
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
      .header(VERSION_HEADER, NEW_CLIENT_HEADER_VALUE)
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

  private static final Set<Integer> AUTH_PROBLEM_STATUS_CODES = Set.of(401, 403);

  private void checkForAuthFailure(HttpResponse<?> httpResponse, Throwable throwable) {
    if (throwable == null) {
      if (AUTH_PROBLEM_STATUS_CODES.contains(httpResponse.statusCode())) {
        LOG.error(
          "*** Prefab Auth failure, please check your credentials. Fetching configuration returned HTTP Status code {} (from {}) ",
          httpResponse.statusCode(),
          httpResponse.uri()
        );
      }
    }
  }

  private <T> CompletableFuture<T> executeWithFailover(
    Function<String, CompletableFuture<T>> operation
  ) {
    long maxRetriesPerHost = 2;
    AtomicInteger hostIndex = new AtomicInteger(0);

    RetryPolicy<T> retryPolicy = RetryPolicy
      .<T>builder()
      .handle(IOException.class, RuntimeException.class)
      .handleResultIf(result -> {
        if (result instanceof HttpResponse) {
          int statusCode = ((HttpResponse<?>) result).statusCode();
          return statusCode >= 500 && statusCode < 600;
        }
        return false;
      })
      .withBackoff(Duration.ofMillis(10), Duration.ofMillis(2000))
      .withDelay(Duration.ofMillis(500))
      .withMaxDuration(Duration.ofSeconds(5))
      .withMaxRetries(Integer.MAX_VALUE)
      .onFailedAttempt(executionAttemptedEvent -> {
        if (executionAttemptedEvent.getAttemptCount() % maxRetriesPerHost == 0) {
          hostIndex.incrementAndGet();
        }
      })
      .build();

    return Failsafe
      .with(retryPolicy)
      .getStageAsync(() -> {
        String currentHost = apiHosts.get(hostIndex.get() % apiHosts.size());
        return operation.apply(currentHost);
      });
  }
}
