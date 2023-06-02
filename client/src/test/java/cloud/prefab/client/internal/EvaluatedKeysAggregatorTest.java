package cloud.prefab.client.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import cloud.prefab.client.Options;
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
class EvaluatedKeysAggregatorTest {

  @Mock
  PrefabHttpClient prefabHttpClient;

  @Mock
  Clock clock;

  @Captor
  ArgumentCaptor<Prefab.EvaluatedKeys> evaluatedKeysArgumentCaptor;

  private EvaluatedKeysAggregator aggregator;

  @BeforeEach
  void beforeEach() {
    lenient().when(prefabHttpClient.reportEvaluatedKeys(any())).thenReturn(true);
    aggregator =
      new EvaluatedKeysAggregator(
        new Options().setNamespace("the-namespace"),
        prefabHttpClient,
        clock
      );
  }

  // make sure the data comes out as expected
  @Test
  void sendsCorrectData() {
    aggregator.reportKeyUsage("foo.bar");
    aggregator.reportKeyUsage("foo.bar");
    aggregator.reportKeyUsage("bar.foo");

    aggregator.doUpload();
    verify(prefabHttpClient).reportEvaluatedKeys(evaluatedKeysArgumentCaptor.capture());

    Prefab.EvaluatedKeys reportedKeys = evaluatedKeysArgumentCaptor.getValue();

    assertThat(reportedKeys.getNamespace()).isEqualTo("the-namespace");
    assertThat(reportedKeys.getKeysList())
      .containsExactlyInAnyOrder("foo.bar", "bar.foo");
  }

  @Test
  void sendsDataIfNeverSentBefore() {
    when(clock.millis()).thenReturn(1L);
    aggregator.reportKeyUsage("foo.bar");

    aggregator.doUpload();
    verify(prefabHttpClient).reportEvaluatedKeys(any());

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
      aggregator.reportKeyUsage("foo.bar");
      aggregator.doUpload();
      verify(prefabHttpClient).reportEvaluatedKeys(any());
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
      verify(prefabHttpClient, times(2)).reportEvaluatedKeys(any()); // the two counts the call in beforeEach
    }
  }

  @Nested
  class ChangedDataSend {

    @BeforeEach
    void beforeEach() {
      // this will set the last sent timestamp
      when(clock.millis()).thenReturn(1L);
      aggregator.reportKeyUsage("foo.bar");
      aggregator.doUpload();
      verify(prefabHttpClient).reportEvaluatedKeys(any());
      // set the dirty flag
      aggregator.reportKeyUsage("bar.foo");
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
      verify(prefabHttpClient, times(2)).reportEvaluatedKeys(any()); // the two counts the call in beforeEach
    }
  }
}
