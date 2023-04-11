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
  public void onNext(String input) {
    LOG.debug("got line `{}`", input);
    String line = removeTrailingNewline(removeLeadingBom(input));

    if (line.startsWith(":")) {
      submit(new CommentEvent(line.substring(1).trim()));
    } else if (line.isBlank()) {
      LOG.debug(
        "broadcasting new event named {} lastEventId is {}",
        currentEventName,
        lastEventId
      );
      submit(new DataEvent(currentEventName, dataBuffer.toString(), lastEventId));
      //reset things
      dataBuffer.setLength(0);
      currentEventName = DEFAULT_EVENT_NAME;
    } else if (line.contains(":")) {
      List<String> lineParts = COLON_SPLITTER.splitToList(line);
      if (lineParts.size() == 2) {
        handleFieldValue(lineParts.get(0), stripLeadingSpaceIfPresent(lineParts.get(1)));
      }
    } else {
      handleFieldValue(line, "");
    }
    subscription.request(1);
  }

  private void handleFieldValue(String fieldName, String value) {
    switch (fieldName) {
      case "event":
        currentEventName = value;
        break;
      case "data":
        dataBuffer.append(value).append("\n");
        break;
      case "id":
        if (!value.contains("\0")) {
          lastEventId = value;
        }
        break;
      case "retry":
        // ignored
        break;
    }
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

  public abstract static class Event {

    enum Type {
      COMMENT,
      DATA,
    }

    abstract Type getType();
  }

  public static class CommentEvent extends Event {

    private final String comment;

    @Override
    Type getType() {
      return Type.COMMENT;
    }

    public CommentEvent(String comment) {
      this.comment = comment;
    }

    public String getComment() {
      return comment;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      CommentEvent that = (CommentEvent) o;
      return Objects.equals(comment, that.comment);
    }

    @Override
    public int hashCode() {
      return Objects.hash(comment);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this).add("comment", comment).toString();
    }
  }

  public static class DataEvent extends Event {

    private final String eventName;
    private final String data;
    private final String lastEventId;

    public DataEvent(String eventName, String data, String lastEventId) {
      this.eventName = eventName;
      this.data = data;
      this.lastEventId = lastEventId;
    }

    @Override
    Type getType() {
      return Type.DATA;
    }

    public String getEventName() {
      return eventName;
    }

    public String getData() {
      return data;
    }

    public String getLastEventId() {
      return lastEventId;
    }

    @Override
    public String toString() {
      return MoreObjects
        .toStringHelper(this)
        .add("eventName", eventName)
        .add("data", data)
        .add("lastEventId1", lastEventId)
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
      DataEvent event = (DataEvent) o;
      return (
        Objects.equals(getType(), event.getType()) &&
        Objects.equals(eventName, event.eventName) &&
        Objects.equals(data, event.data) &&
        Objects.equals(lastEventId, event.lastEventId)
      );
    }

    @Override
    public int hashCode() {
      return Objects.hash(getType(), eventName, data, lastEventId);
    }
  }

  private String stripLeadingSpaceIfPresent(String field) {
    if (field.charAt(0) == ' ') {
      return field.substring(1);
    }
    return field;
  }

  private String removeLeadingBom(String input) {
    if (input.startsWith(UTF8_BOM)) {
      return input.substring(UTF8_BOM.length());
    }
    return input;
  }

  private String removeTrailingNewline(String input) {
    if (input.endsWith("\n")) {
      return input.substring(0, input.length() - 1);
    }
    return input;
  }
}
