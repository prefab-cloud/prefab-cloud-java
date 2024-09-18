package cloud.prefab.client.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cloud.prefab.client.Options;
import cloud.prefab.domain.Prefab;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PrefabHttpClientTest {

  @Captor
  private ArgumentCaptor<HttpRequest> requestCaptor;

  @Mock
  HttpClient mockHttpClient;

  Options options = new Options()
    .setApiHosts(List.of("http://a.example.com", "http://b.example.com"))
    .setStreamHosts(List.of("http://stream.example.com"))
    .setApikey("not-a-real-key");

  @Test
  void testFailoverForConfigFetch() throws ExecutionException, InterruptedException {
    PrefabHttpClient prefabHttpClient = new PrefabHttpClient(mockHttpClient, options);

    // Create a mock Prefab.Configs object
    Prefab.Configs mockConfigs = Prefab.Configs.newBuilder().build();

    // Mock responses: fail twice, then succeed
    HttpResponse<Supplier<Prefab.Configs>> failureResponse = mock(HttpResponse.class);
    when(failureResponse.statusCode()).thenReturn(500);

    HttpResponse<Supplier<Prefab.Configs>> successResponse = mock(HttpResponse.class);
    when(successResponse.statusCode()).thenReturn(200);
    when(successResponse.body()).thenReturn(() -> mockConfigs);

    // Set up the mock behavior
    CompletableFuture<HttpResponse<Supplier<Prefab.Configs>>> failureFuture = CompletableFuture.completedFuture(
      failureResponse
    );
    CompletableFuture<HttpResponse<Supplier<Prefab.Configs>>> successFuture = CompletableFuture.completedFuture(
      successResponse
    );

    when(
      mockHttpClient.sendAsync(
        requestCaptor.capture(),
        any(HttpResponse.BodyHandler.class)
      )
    )
      .thenReturn(failureFuture)
      .thenReturn(failureFuture)
      .thenReturn(successFuture);

    // Test method
    CompletableFuture<HttpResponse<Supplier<Prefab.Configs>>> result = prefabHttpClient.requestConfigs(
      0L
    );

    // Wait for the result
    HttpResponse<Supplier<Prefab.Configs>> response = result.get();

    // Verify
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body().get()).isEqualTo(mockConfigs);
    verify(mockHttpClient, times(3)).sendAsync(any(), any());

    assertThat(requestCaptor.getAllValues())
      .extracting(HttpRequest::uri)
      .extracting(URI::getHost)
      .containsOnly("a.example.com", "b.example.com");
  }

  @Test
  void testFailoverForSSEConnection() throws ExecutionException, InterruptedException {
    PrefabHttpClient prefabHttpClient = new PrefabHttpClient(mockHttpClient, options);

    // Mock responses: fail twice, then succeed
    HttpResponse<Supplier<Prefab.Configs>> failureResponse = mock(HttpResponse.class);
    when(failureResponse.statusCode()).thenReturn(500);

    HttpResponse<Supplier<Prefab.Configs>> successResponse = mock(HttpResponse.class);
    when(successResponse.statusCode()).thenReturn(200);

    Flow.Subscriber<String> lineSubscriber = mock(Flow.Subscriber.class);

    // Set up the mock behavior
    CompletableFuture<HttpResponse<Supplier<Prefab.Configs>>> failureFuture = CompletableFuture.completedFuture(
      failureResponse
    );
    CompletableFuture<HttpResponse<Supplier<Prefab.Configs>>> successFuture = CompletableFuture.completedFuture(
      successResponse
    );

    when(
      mockHttpClient.sendAsync(
        requestCaptor.capture(),
        any(HttpResponse.BodyHandler.class)
      )
    )
      .thenReturn(failureFuture)
      .thenReturn(failureFuture)
      .thenReturn(successFuture);

    // Test method
    CompletableFuture<HttpResponse<Void>> responseFuture = prefabHttpClient.createSSEConfigConnection(
      0L,
      lineSubscriber
    );

    // Wait for the result
    var response = responseFuture.get();

    // Verify
    assertThat(response.statusCode()).isEqualTo(200);
    verify(mockHttpClient, times(3)).sendAsync(any(), any());
    assertThat(requestCaptor.getAllValues())
      .extracting(HttpRequest::uri)
      .allSatisfy(uri -> assertThat(uri.getHost()).isEqualTo("stream.example.com"));
  }
}
