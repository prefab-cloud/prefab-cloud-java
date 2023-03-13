package cloud.prefab.client.config.logging;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

class TargetedLoggingHelperTest {

  // Note this won't work unless a logging backend is installed

  Logger LOG = LoggerFactory.getLogger(TargetedLoggingHelperTest.class);

  @AfterEach
  void afterEach() {
    MDC.clear();
  }

  @Test
  void itRunsRunnableWithExclusiveMdcStartingBlank() {
    assertThat(MDC.getCopyOfContextMap()).isNull();

    Map<String, String> newContext = Map.of("portalId", "53", "userId", "abc123");

    TargetedLoggingHelper.logWithExclusiveContext(
      newContext,
      () -> {
        assertThat(MDC.getCopyOfContextMap()).isEqualTo(newContext);
      }
    );
    assertThat(MDC.getCopyOfContextMap()).isNull();
  }

  @Test
  void itRunsRunnableWithExclusiveMdcStartingWithContent() {
    assertThat(MDC.getCopyOfContextMap()).isNull();
    Map<String, String> starterContext = Map.of("foo", "bar", "something", "else");
    for (Map.Entry<String, String> entry : starterContext.entrySet()) {
      MDC.put(entry.getKey(), entry.getValue());
    }

    Map<String, String> newContext = Map.of("portalId", "53", "userId", "abc123");

    TargetedLoggingHelper.logWithExclusiveContext(
      newContext,
      () -> {
        assertThat(MDC.getCopyOfContextMap()).isEqualTo(newContext);
      }
    );
    assertThat(MDC.getCopyOfContextMap()).isEqualTo(starterContext);
  }

  @Test
  void itCallsCallableWithExclusiveMdcStartingBlank() throws Exception {
    assertThat(MDC.getCopyOfContextMap()).isNull();

    Map<String, String> newContext = Map.of("portalId", "53", "userId", "abc123");

    assertThat(
      TargetedLoggingHelper.logWithExclusiveContext(
        newContext,
        () -> {
          assertThat(MDC.getCopyOfContextMap()).isEqualTo(newContext);
          return 127;
        }
      )
    )
      .isEqualTo(127);
    assertThat(MDC.getCopyOfContextMap()).isNull();
  }

  @Test
  void itCallsCallableWithExclusiveMdcStartingWithContent() throws Exception {
    assertThat(MDC.getCopyOfContextMap()).isNull();
    Map<String, String> starterContext = Map.of("foo", "bar", "something", "else");
    for (Map.Entry<String, String> entry : starterContext.entrySet()) {
      MDC.put(entry.getKey(), entry.getValue());
    }

    Map<String, String> newContext = Map.of("portalId", "53", "userId", "abc123");

    assertThat(
      TargetedLoggingHelper.logWithExclusiveContext(
        newContext,
        () -> {
          assertThat(MDC.getCopyOfContextMap()).isEqualTo(newContext);
          return 127;
        }
      )
    )
      .isEqualTo(127);
    assertThat(MDC.getCopyOfContextMap()).isEqualTo(starterContext);
  }

  @Test
  void itRunsRunnableWithMergedMdcStartingBlank() {
    assertThat(MDC.getCopyOfContextMap()).isNull();

    Map<String, String> newContext = Map.of("portalId", "53", "userId", "abc123");

    TargetedLoggingHelper.logWithMergedContext(
      newContext,
      () -> {
        assertThat(MDC.getCopyOfContextMap()).isEqualTo(newContext);
      }
    );
    assertThat(MDC.getCopyOfContextMap()).isNull();
  }

  @Test
  void itRunsRunnableWithMergedMdcStartingWithContent() {
    assertThat(MDC.getCopyOfContextMap()).isNull();
    Map<String, String> starterContext = Map.of("foo", "bar", "something", "else");
    for (Map.Entry<String, String> entry : starterContext.entrySet()) {
      MDC.put(entry.getKey(), entry.getValue());
    }

    Map<String, String> newContext = Map.of("portalId", "53", "userId", "abc123");

    TargetedLoggingHelper.logWithMergedContext(
      newContext,
      () -> {
        assertThat(MDC.getCopyOfContextMap())
          .isEqualTo(mergeMaps(starterContext, newContext));
      }
    );
    assertThat(MDC.getCopyOfContextMap()).isEqualTo(starterContext);
  }

  @Test
  void itCallsCallableWithMergedMdcStartingBlank() throws Exception {
    assertThat(MDC.getCopyOfContextMap()).isNull();

    Map<String, String> newContext = Map.of("portalId", "53", "userId", "abc123");

    assertThat(
      TargetedLoggingHelper.logWithMergedContext(
        newContext,
        () -> {
          assertThat(MDC.getCopyOfContextMap()).isEqualTo(newContext);
          return 127;
        }
      )
    )
      .isEqualTo(127);
    assertThat(MDC.getCopyOfContextMap()).isNull();
  }

  @Test
  void itCallsCallableWithMergedMdcStartingWithContent() throws Exception {
    assertThat(MDC.getCopyOfContextMap()).isNull();
    Map<String, String> starterContext = Map.of("foo", "bar", "something", "else");
    for (Map.Entry<String, String> entry : starterContext.entrySet()) {
      MDC.put(entry.getKey(), entry.getValue());
    }

    Map<String, String> newContext = Map.of("portalId", "53", "userId", "abc123");

    assertThat(
      TargetedLoggingHelper.logWithMergedContext(
        newContext,
        () -> {
          assertThat(MDC.getCopyOfContextMap())
            .isEqualTo(mergeMaps(starterContext, newContext));
          return 127;
        }
      )
    )
      .isEqualTo(127);
    assertThat(MDC.getCopyOfContextMap()).isEqualTo(starterContext);
  }

  Map<String, String> mergeMaps(Map<String, String> first, Map<String, String> second) {
    return ImmutableMap
      .<String, String>builder()
      .putAll(first)
      .putAll(second)
      .buildKeepingLast();
  }
}
