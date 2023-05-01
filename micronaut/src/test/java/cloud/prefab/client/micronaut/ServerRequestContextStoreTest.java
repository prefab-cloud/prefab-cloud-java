package cloud.prefab.client.micronaut;

import static org.assertj.core.api.Assertions.assertThat;

import cloud.prefab.context.PrefabContext;
import cloud.prefab.context.PrefabContextSet;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.http.simple.SimpleHttpRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ServerRequestContextStoreTest {

  ServerRequestContextStore prefabStateStore = new ServerRequestContextStore();
  PrefabContext userContext = PrefabContext
    .newBuilder("user")
    .put("country", "us")
    .build();
  PrefabContext serverContext = PrefabContext.newBuilder("server").put("az", "2").build();

  PrefabContextSet userAndServerContextSet = PrefabContextSet.from(
    userContext,
    serverContext
  );

  @Nested
  class MissingHttpRequest {

    @BeforeEach
    void beforeEach() {
      ServerRequestContext.set(null);
    }

    @Test
    void getContextReturnsEmpty() {
      assertThat(prefabStateStore.getContext()).isEmpty();
    }

    @Test
    void setContextQuietlyDoesNothing() {
      assertThat(prefabStateStore.setContext(userAndServerContextSet)).isEmpty();
      assertThat(prefabStateStore.getContext()).isEmpty();
    }

    @Test
    void addContextQuietlyDoesNothing() {
      prefabStateStore.addContext(userContext);
      assertThat(prefabStateStore.getContext()).isEmpty();
    }

    @Test
    void clearContextQuietlyDoesNothing() {
      assertThat(prefabStateStore.clearContext()).isEmpty();
      assertThat(prefabStateStore.getContext()).isEmpty();
    }
  }

  @Nested
  class WithHttpRequest {

    @BeforeEach
    void beforeEach() {
      ServerRequestContext.set(
        new SimpleHttpRequest<>(HttpMethod.POST, "http://localhost/", "The body")
      );
    }

    @Test
    void getContextReturnsEmptyWhenNoContextSet() {
      assertThat(prefabStateStore.getContext()).isEmpty();
    }

    @Test
    void setContextReturnsEmptyWhenNoContextSet() {
      assertThat(prefabStateStore.setContext(userAndServerContextSet)).isEmpty();
    }

    @Test
    void addContextWhenEmptyUpdatesTheContext() {
      prefabStateStore.addContext(userContext);
      assertThat(prefabStateStore.getContext())
        .isPresent()
        .get()
        .usingRecursiveComparison()
        .isEqualTo(PrefabContextSet.convert(userContext));
    }

    @Test
    void clearReturnsEmpty() {
      assertThat(prefabStateStore.clearContext()).isEmpty();
      assertThat(prefabStateStore.getContext()).isEmpty();
    }

    @Nested
    class WithPreExistingPrefabContext {

      PrefabContext newUserContext = PrefabContext
        .newBuilder("user")
        .put("country", "UK")
        .build();
      PrefabContextSet newUserAndServerContextSet = PrefabContextSet.from(
        newUserContext,
        serverContext
      );

      @BeforeEach
      void beforeEach() {
        prefabStateStore.setContext(userAndServerContextSet);
      }

      @Test
      void getReturnsExpectedSet() {
        assertThat(prefabStateStore.getContext())
          .isPresent()
          .get()
          .usingRecursiveComparison()
          .isEqualTo(userAndServerContextSet);
      }

      @Test
      void clearWorksAsExpected() {
        assertThat(prefabStateStore.clearContext())
          .isPresent()
          .get()
          .usingRecursiveComparison()
          .isEqualTo(userAndServerContextSet);
        assertThat(prefabStateStore.getContext()).isEmpty();
      }

      @Test
      void setWorksAsExpected() {
        assertThat(prefabStateStore.setContext(userContext))
          .isPresent()
          .get()
          .usingRecursiveComparison()
          .isEqualTo(userAndServerContextSet);
        assertThat(prefabStateStore.getContext())
          .isPresent()
          .get()
          .usingRecursiveComparison()
          .isEqualTo(userContext);
      }

      @Test
      void addWorksAsExpected() {
        prefabStateStore.addContext(newUserContext);
        assertThat(prefabStateStore.getContext())
          .isPresent()
          .get()
          .usingRecursiveComparison()
          .isEqualTo(newUserAndServerContextSet);
      }
    }
  }
}
