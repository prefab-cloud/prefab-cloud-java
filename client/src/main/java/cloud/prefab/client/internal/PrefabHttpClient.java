package cloud.prefab.client.internal;

import cloud.prefab.client.Options;
import cloud.prefab.client.util.MavenInfo;
import cloud.prefab.domain.Prefab;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.SSLSession;
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
  private final List<String> streamHosts;

  // Use Guava's cache with maximum size of 2 entries.
  // (The cache respects HTTP cache-control expiry values provided by the server.)
  private final Cache<URI, CacheEntry> configCache = CacheBuilder
    .newBuilder()
    .maximumSize(2)
    .build();

  // Cache entry definition.
  static class CacheEntry {

    final byte[] data;
    final String etag;
    final long expiresAt; // timestamp in millis

    CacheEntry(byte[] data, String etag, long expiresAt) {
      this.data = data;
      this.etag = etag;
      this.expiresAt = expiresAt;
    }
  }

  // A basic HttpResponse implementation for synthetic (cached) responses.
  private static class CachedHttpResponse<T> implements HttpResponse<T> {

    private final URI uri;
    private final int statusCode;
    private final T body;
    private final HttpHeaders headers;

    CachedHttpResponse(
      URI uri,
      int statusCode,
      T body,
      Map<String, List<String>> headerMap
    ) {
      this.uri = uri;
      this.statusCode = statusCode;
      this.body = body;
      this.headers = HttpHeaders.of(headerMap, (k, v) -> true);
    }

    @Override
    public int statusCode() {
      return statusCode;
    }

    @Override
    public HttpRequest request() {
      return null;
    }

    @Override
    public Optional<HttpResponse<T>> previousResponse() {
      return Optional.empty();
    }

    @Override
    public HttpHeaders headers() {
      return headers;
    }

    @Override
    public T body() {
      return body;
    }

    @Override
    public Optional<SSLSession> sslSession() {
      return Optional.empty();
    }

    @Override
    public URI uri() {
      return uri;
    }

    @Override
    public HttpClient.Version version() {
      return HttpClient.Version.HTTP_1_1;
    }
  }

  public PrefabHttpClient(HttpClient httpClient, Options options) {
    this.httpClient = httpClient;
    this.options = options;
    this.telemetryUrl =
      URI.create(options.getPrefabTelemetryHost() + "/api/v1/telemetry");
    this.apiHosts = options.getApiHosts();
    this.streamHosts = options.getStreamHosts();

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

  public CompletableFuture<HttpResponse<Supplier<Prefab.TelemetryEventsResponse>>> reportTelemetryEvents(
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

  public CompletableFuture<HttpResponse<Void>> createSSEConfigConnection(
    long offset,
    Flow.Subscriber<String> lineSubscriber
  ) {
    return executeWithFailover(
      host -> {
        URI uri = URI.create(host + "/api/v1/sse/config");
        LOG.info("Requesting SSE from {}", uri);
        HttpRequest request = getClientBuilderWithStandardHeaders()
          .header("Accept", EVENT_STREAM_MEDIA_TYPE)
          .header(START_AT_HEADER, String.valueOf(offset))
          .timeout(Duration.ofSeconds(5))
          .uri(uri)
          .build();
        return httpClient
          .sendAsync(
            request,
            HttpResponse.BodyHandlers.fromLineSubscriber(lineSubscriber)
          )
          .whenCompleteAsync(this::checkForAuthFailure);
      },
      streamHosts
    );
  }

  /**
   * Fetches configurations with caching.
   */
  public CompletableFuture<HttpResponse<Supplier<Prefab.Configs>>> requestConfigs(
    long offset
  ) {
    return executeWithFailover(
      host -> {
        URI uri = URI.create(host + "/api/v1/configs/" + offset);
        return requestConfigsFromURI(uri);
      },
      apiHosts
    );
  }

  private CompletableFuture<HttpResponse<Supplier<Prefab.Configs>>> requestConfigsFromURI(
    URI uri
  ) {
    long now = System.currentTimeMillis();
    CacheEntry cachedEntry = configCache.getIfPresent(uri);

    // If a valid cache entry exists, return it.
    if (cachedEntry != null && cachedEntry.expiresAt > now) {
      return CompletableFuture.completedFuture(createCachedHitResponse(uri, cachedEntry));
    }

    HttpRequest.Builder requestBuilder = getClientBuilderWithStandardHeaders()
      .header("Accept", PROTO_MEDIA_TYPE)
      .timeout(Duration.ofSeconds(5))
      .uri(uri);

    if (cachedEntry != null && cachedEntry.etag != null) {
      requestBuilder.header("If-None-Match", cachedEntry.etag);
    }
    HttpRequest request = requestBuilder.build();

    return httpClient
      .sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
      .thenApply(response -> {
        if (response.statusCode() == 304 && cachedEntry != null) {
          // Server indicates data is unchanged.
          return createCachedHitResponse(uri, cachedEntry);
        } else if (response.statusCode() == 200) {
          byte[] bodyBytes = response.body();
          String cacheControl = response.headers().firstValue("Cache-Control").orElse("");
          String etag = response.headers().firstValue("ETag").orElse(null);
          long expiresAt = 0;
          if (!cacheControl.contains("no-store")) {
            // Parse max-age (assumed in seconds)
            Pattern pattern = Pattern.compile("max-age=(\\d+)");
            Matcher matcher = pattern.matcher(cacheControl);
            if (matcher.find()) {
              int maxAge = Integer.parseInt(matcher.group(1));
              expiresAt = now + maxAge * 1000L;
            }
          }
          // Cache the response if appropriate.
          if (expiresAt > now) {
            CacheEntry newEntry = new CacheEntry(bodyBytes, etag, expiresAt);
            configCache.put(uri, newEntry);
          }
          Supplier<Prefab.Configs> supplier = () -> {
            try {
              return Prefab.Configs.parseFrom(bodyBytes);
            } catch (IOException e) {
              throw new UncheckedIOException(e);
            }
          };
          // Mark this as a MISS since it's a fresh network call.
          Map<String, List<String>> headerMap = new HashMap<>(response.headers().map());
          headerMap.put("X-Cache", List.of("MISS"));
          return createResponse(uri, response.statusCode(), supplier, headerMap);
        } else {
          // For other status codes, simply wrap the response.
          Supplier<Prefab.Configs> supplier = () -> {
            try (ByteArrayInputStream bais = new ByteArrayInputStream(response.body())) {
              return Prefab.Configs.parseFrom(bais);
            } catch (IOException e) {
              throw new UncheckedIOException(e);
            }
          };
          return createResponse(
            uri,
            response.statusCode(),
            supplier,
            response.headers().map()
          );
        }
      })
      .whenCompleteAsync(this::checkForAuthFailure);
  }

  /**
   * Helper method to create a cached response.
   */
  private HttpResponse<Supplier<Prefab.Configs>> createCachedHitResponse(
    URI uri,
    CacheEntry entry
  ) {
    Supplier<Prefab.Configs> supplier = () -> {
      try {
        return Prefab.Configs.parseFrom(entry.data);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    };
    Map<String, List<String>> headerMap = Map.of(
      "ETag",
      List.of(entry.etag),
      "X-Cache",
      List.of("HIT")
    );
    return new CachedHttpResponse<>(uri, 200, supplier, headerMap);
  }

  /**
   * Helper method to create a general response.
   */
  private HttpResponse<Supplier<Prefab.Configs>> createResponse(
    URI uri,
    int statusCode,
    Supplier<Prefab.Configs> supplier,
    Map<String, List<String>> headerMap
  ) {
    return new CachedHttpResponse<>(uri, statusCode, supplier, headerMap);
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
    java.util.function.Function<String, CompletableFuture<T>> operation,
    List<String> hostList
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
        String currentHost = hostList.get(hostIndex.get() % hostList.size());
        return operation.apply(currentHost);
      });
  }

  // ----- Cache management methods -----

  /** Clears the internal configuration cache. */
  public void clearCache() {
    configCache.invalidateAll();
  }

  /** For testing: returns the current number of cached entries. */
  public long getCacheSize() {
    return configCache.size();
  }
}
