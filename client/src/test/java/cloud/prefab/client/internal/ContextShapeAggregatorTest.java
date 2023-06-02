package cloud.prefab.client.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import cloud.prefab.client.Options;
import cloud.prefab.context.PrefabContext;
import cloud.prefab.domain.Prefab;
import java.time.Clock;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContextShapeAggregatorTest {

  @Mock
  PrefabHttpClient prefabHttpClient;

  @Mock
  Clock clock;

  @Captor
  ArgumentCaptor<Prefab.ContextShapes> shapesArgumentCaptor;

  private ContextShapeAggregator aggregator;

  @BeforeEach
  void beforeEach() {
    lenient().when(prefabHttpClient.reportContextShape(any())).thenReturn(true);
    aggregator =
      new ContextShapeAggregator(
        new Options().setNamespace("the-namespace"),
        prefabHttpClient,
        clock
      );
  }

  // make sure the data comes out as expected
  @Test
  void sendsCorrectData() {
    aggregator.reportContextUsage(
      PrefabContext
        .newBuilder("user")
        .put("tier", "gold")
        .put("age", 44)
        .put("alive", true)
        .build()
    );

    aggregator.reportContextUsage(
      PrefabContext
        .newBuilder("user")
        .put("tier", "silver")
        .put("age", 100)
        .put("alive", true)
        .put("foo", "bar")
        .build()
    );

    aggregator.reportContextUsage(
      PrefabContext.newBuilder("").put("something", "else").build()
    );

    aggregator.doUpload();
    verify(prefabHttpClient).reportContextShape(shapesArgumentCaptor.capture());

    Prefab.ContextShapes reportedShape = shapesArgumentCaptor.getValue();

    assertThat(reportedShape.getNamespace()).isEqualTo("the-namespace");
    assertThat(reportedShape.getShapesList())
      .containsExactlyInAnyOrder(
        Prefab.ContextShape
          .newBuilder()
          .setName("user")
          .putFieldTypes("age", Prefab.ConfigValue.TypeCase.INT.getNumber())
          .putFieldTypes("tier", Prefab.ConfigValue.TypeCase.STRING.getNumber())
          .putFieldTypes("alive", Prefab.ConfigValue.TypeCase.BOOL.getNumber())
          .putFieldTypes("foo", Prefab.ConfigValue.TypeCase.STRING.getNumber())
          .build(),
        Prefab.ContextShape
          .newBuilder()
          .setName("")
          .putFieldTypes("something", Prefab.ConfigValue.TypeCase.STRING.getNumber())
          .build()
      );
  }

  @Test
  void sendsDataIfNeverSentBefore() {
    when(clock.millis()).thenReturn(1L);
    aggregator.reportContextUsage(
      PrefabContext
        .newBuilder("user")
        .put("tier", "gold")
        .put("age", 44)
        .put("alive", true)
        .build()
    );

    aggregator.doUpload();
    verify(prefabHttpClient).reportContextShape(any());

    // won't send again now that last time sent is non zero
    aggregator.doUpload();
    verifyNoMoreInteractions(prefabHttpClient);
  }

  @Nested
  class UnchangedDataSend {

    @BeforeEach
    void beforeEach() {
      // this will set the last sent timestamp
      when(clock.millis()).thenReturn(1L);
      aggregator.reportContextUsage(
        PrefabContext
          .newBuilder("user")
          .put("tier", "gold")
          .put("age", 44)
          .put("alive", true)
          .build()
      );
      aggregator.doUpload();
      verify(prefabHttpClient).reportContextShape(any());
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 5, 10, 15, 19 })
    void itDoesNotSendBefore20Min(int minutesLater) {
      when(clock.millis()).thenReturn(1 + TimeUnit.MINUTES.toMillis(minutesLater));
      aggregator.doUpload();
      verifyNoMoreInteractions(prefabHttpClient);
    }

    @ParameterizedTest
    @ValueSource(ints = { 20, 21, 100 })
    void itSendsAtAndAfter20Min(int minutesLater) {
      when(clock.millis()).thenReturn(1 + TimeUnit.MINUTES.toMillis(minutesLater));
      aggregator.doUpload();
      verify(prefabHttpClient, times(2)).reportContextShape(any()); // the two counts the call in beforeEach
    }
  }

  @Nested
  class ChangedDataSend {

    @BeforeEach
    void beforeEach() {
      // this will set the last sent timestamp
      when(clock.millis()).thenReturn(1L);
      aggregator.reportContextUsage(
        PrefabContext
          .newBuilder("user")
          .put("tier", "gold")
          .put("age", 44)
          .put("alive", true)
          .build()
      );
      aggregator.doUpload();
      verify(prefabHttpClient).reportContextShape(any());
      // set the dirty flag
      aggregator.reportContextUsage(
        PrefabContext.newBuilder("user").put("foo", "bar").build()
      );
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 2, 3, 4 })
    void itDoesNotSendBefore20Min(int minutesLater) {
      when(clock.millis()).thenReturn(1 + TimeUnit.MINUTES.toMillis(minutesLater));
      aggregator.doUpload();
      verifyNoMoreInteractions(prefabHttpClient);
    }

    @ParameterizedTest
    @ValueSource(ints = { 5, 6, 10 })
    void itSendsAtAndAfter20Min(int minutesLater) {
      when(clock.millis()).thenReturn(1 + TimeUnit.MINUTES.toMillis(minutesLater));
      aggregator.doUpload();
      verify(prefabHttpClient, times(2)).reportContextShape(any()); // the two counts the call in beforeEach
    }
  }
}
