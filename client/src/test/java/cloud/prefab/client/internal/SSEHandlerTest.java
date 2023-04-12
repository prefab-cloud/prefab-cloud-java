package cloud.prefab.client.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.net.http.HttpRequest;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
class SSEHandlerTest {

  SubmissionPublisher<String> submissionPublisher = new SubmissionPublisher();
  SSEHandler sseHandler = new SSEHandler();
  EndSubscriber endSubscriber = new EndSubscriber();

  @BeforeEach
  void setup() {
    submissionPublisher.subscribe(sseHandler);
    sseHandler.subscribe(endSubscriber);
  }

  @Test
  void itHandlesComments() {
    submissionPublisher.submit(":foobar\n");
    await()
      .atMost(3, TimeUnit.SECONDS)
      .untilAsserted(() ->
        assertThat(endSubscriber.events)
          .hasSize(1)
          .containsOnly(new SSEHandler.CommentEvent("foobar"))
      );
  }

  @Test
  void itPublishesEventWithDefaultNameForData() throws InterruptedException {
    submissionPublisher.submit("data: hello\n");
    submissionPublisher.submit("\n");
    await()
      .atMost(3, TimeUnit.SECONDS)
      .untilAsserted(() ->
        assertThat(endSubscriber.events)
          .hasSize(1)
          .containsOnly(new SSEHandler.DataEvent("message", "hello\n", ""))
      );
  }

  @Test
  void itPublishesEventWithGivenNameForData() throws InterruptedException {
    submissionPublisher.submit("event: coolEvent");
    submissionPublisher.submit("data: hello\n");
    submissionPublisher.submit("\n");
    await()
      .atMost(3, TimeUnit.SECONDS)
      .untilAsserted(() ->
        assertThat(endSubscriber.events)
          .hasSize(1)
          .containsOnly(new SSEHandler.DataEvent("coolEvent", "hello\n", ""))
      );
  }

  @Test
  void itPublishesEventWithGivenNameForDataAndLastEventId() throws InterruptedException {
    submissionPublisher.submit("event: coolEvent");
    submissionPublisher.submit("id: 101A");
    submissionPublisher.submit("data: hello\n");
    submissionPublisher.submit("\n");
    await()
      .atMost(3, TimeUnit.SECONDS)
      .untilAsserted(() ->
        assertThat(endSubscriber.events)
          .hasSize(1)
          .containsOnly(new SSEHandler.DataEvent("coolEvent", "hello\n", "101A"))
      );
  }

  @Test
  void itPublishesEventWithGivenNameForMultiLineDataAndLastEventId()
    throws InterruptedException {
    submissionPublisher.submit("event: coolEvent");
    submissionPublisher.submit("id: 101A");
    submissionPublisher.submit("data: hello\n");
    submissionPublisher.submit("data: world\n");
    submissionPublisher.submit("data: !\n");
    submissionPublisher.submit("\n");
    await()
      .atMost(3, TimeUnit.SECONDS)
      .untilAsserted(() ->
        assertThat(endSubscriber.events)
          .hasSize(1)
          .containsOnly(
            new SSEHandler.DataEvent("coolEvent", "hello\nworld\n!\n", "101A")
          )
      );
  }

  @Test
  void itPublishesEventWithGivenNameForDataAndIgnoresNullEventId()
    throws InterruptedException {
    submissionPublisher.submit("event: coolEvent");
    submissionPublisher.submit("id: \0");
    submissionPublisher.submit("data: hello\n");
    submissionPublisher.submit("\n");
    await()
      .atMost(3, TimeUnit.SECONDS)
      .untilAsserted(() ->
        assertThat(endSubscriber.events)
          .hasSize(1)
          .containsOnly(new SSEHandler.DataEvent("coolEvent", "hello\n", ""))
      );
  }

  @Test
  void itPropagatesClose() throws InterruptedException {
    submissionPublisher.close();
    await()
      .atMost(3, TimeUnit.SECONDS)
      .untilAsserted(() -> assertThat(endSubscriber.isComplete.get()).isTrue());
  }

  @Test
  void itPropagatesError() throws InterruptedException {
    Exception e = new RuntimeException("closing exceptionally!");
    submissionPublisher.closeExceptionally(e);
    await()
      .atMost(3, TimeUnit.SECONDS)
      .untilAsserted(() ->
        assertThat(endSubscriber.throwableAtomicReference.get()).isEqualTo(e)
      );
  }

  private static class EndSubscriber implements Flow.Subscriber<SSEHandler.Event> {

    private static final Logger LOG = LoggerFactory.getLogger(EndSubscriber.class);

    private Flow.Subscription subscription;

    private CopyOnWriteArrayList<SSEHandler.Event> events = new CopyOnWriteArrayList<>();
    private AtomicBoolean isComplete = new AtomicBoolean(false);
    private AtomicReference<Throwable> throwableAtomicReference = new AtomicReference<>();

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
      this.subscription = subscription;
      this.subscription.request(1);
    }

    @Override
    public void onNext(SSEHandler.Event item) {
      events.add(item);
      LOG.info("Received event {}, Now have {} events", item, events.size());
      this.subscription.request(1);
    }

    @Override
    public void onError(Throwable throwable) {
      throwableAtomicReference.set(throwable);
    }

    @Override
    public void onComplete() {
      isComplete.set(true);
    }
  }
}
