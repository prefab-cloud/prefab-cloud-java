package cloud.prefab.client.internal;

import com.google.common.base.MoreObjects;
import com.google.common.base.Splitter;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SSEHandler
  extends SubmissionPublisher<SSEHandler.Event>
  implements Flow.Processor<String, SSEHandler.Event> {

  private static final Logger LOG = LoggerFactory.getLogger(SSEHandler.class);

  private static final Splitter COLON_SPLITTER = Splitter.on(':').limit(2);
  private static final String UTF8_BOM = "\uFEFF";

  private static final String DEFAULT_EVENT_NAME = "message";

  private Flow.Subscription subscription;

  private String currentEventName = DEFAULT_EVENT_NAME;
  private final StringBuilder dataBuffer = new StringBuilder();

  private String lastEventId = "";

  @Override
  public void onSubscribe(Flow.Subscription subscription) {
    this.subscription = subscription;
    subscription.request(1);
  }

  @Override
  public void onNext(String line) {
    LOG.debug("got line `{}`", line);
    String noBom = line;
    if (line.startsWith(UTF8_BOM)) {
      noBom = line.substring(UTF8_BOM.length());
    }

    if (noBom.startsWith(":")) {
      // ignore - these are comments/keepalives
    } else if (noBom.isBlank()) {
      LOG.debug(
        "broadcasting new event named {} lastEventId is {}",
        currentEventName,
        lastEventId
      );
      submit(new Event(currentEventName, dataBuffer.toString(), lastEventId));
      //reset things
      dataBuffer.setLength(0);
      currentEventName = DEFAULT_EVENT_NAME;
    } else {
      List<String> lineParts = COLON_SPLITTER.splitToList(noBom);
      if (lineParts.size() == 2) {
        String fieldName = lineParts.get(0);
        String value = stripLeadingSpaceIfPresent(lineParts.get(1));
        switch (fieldName) {
          case "event":
            currentEventName = value;
            break;
          case "data":
            dataBuffer.append(value).append("\n");
            break;
          case "id":
            lastEventId = value;
            break;
          case "retry":
            // ignored
            break;
        }
      }
    }
    subscription.request(1);
  }

  @Override
  public void onError(Throwable throwable) {
    LOG.warn("Error in SSE handler {}", throwable.getMessage());
    closeExceptionally(throwable);
  }

  @Override
  public void onComplete() {
    LOG.debug("SSE handler complete");
    close();
  }

  public static class Event {

    private final String eventName;
    private final String data;
    private final String lastEventId1;

    public Event(String eventName, String data, String lastEventId) {
      this.eventName = eventName;
      this.data = data;
      lastEventId1 = lastEventId;
    }

    public String getEventName() {
      return eventName;
    }

    public String getData() {
      return data;
    }

    public String getLastEventId() {
      return lastEventId1;
    }

    @Override
    public String toString() {
      return MoreObjects
        .toStringHelper(this)
        .add("eventName", eventName)
        .add("data", data)
        .add("lastEventId1", lastEventId1)
        .toString();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      Event event = (Event) o;
      return (
        Objects.equals(eventName, event.eventName) &&
        Objects.equals(data, event.data) &&
        Objects.equals(lastEventId1, event.lastEventId1)
      );
    }

    @Override
    public int hashCode() {
      return Objects.hash(eventName, data, lastEventId1);
    }
  }

  private String stripLeadingSpaceIfPresent(String field) {
    if (field.charAt(0) == ' ') {
      return field.substring(1);
    }
    return field;
  }
}
