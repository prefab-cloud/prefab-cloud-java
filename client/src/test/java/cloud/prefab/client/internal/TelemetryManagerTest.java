package cloud.prefab.client.internal;

import static java.util.stream.Collectors.groupingBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;

import cloud.prefab.client.ConfigClient;
import cloud.prefab.client.Options;
import cloud.prefab.client.config.ConfigElement;
import cloud.prefab.client.config.ConfigValueUtils;
import cloud.prefab.client.config.Match;
import cloud.prefab.client.config.Provenance;
import cloud.prefab.client.config.TestUtils;
import cloud.prefab.context.PrefabContext;
import cloud.prefab.context.PrefabContextSet;
import cloud.prefab.domain.Prefab;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TelemetryManagerTest {

  @Mock
  PrefabHttpClient prefabHttpClient;

  @Mock
  HttpResponse<Supplier<Prefab.TelemetryEventsResponse>> mockHttpResponse;

  @Captor
  ArgumentCaptor<Prefab.TelemetryEvents> telemetryEventsArgumentCaptor;

  private TelemetryManager telemetryManager;

  @BeforeEach
  void beforeEach() {
    lenient()
      .when(
        prefabHttpClient.reportTelemetryEvents(telemetryEventsArgumentCaptor.capture())
      )
      .thenReturn(CompletableFuture.completedFuture(mockHttpResponse));
  }

  private void buildTelemetryManager(Options options) {
    Clock clock = Clock.systemUTC();
    telemetryManager =
      new TelemetryManager(
        new LoggerStatsAggregator(clock),
        new MatchStatsAggregator(),
        new ContextShapeAggregator(),
        new ExampleContextBuffer(),
        prefabHttpClient,
        options,
        clock
      );
    telemetryManager.start(0); // disables auto flush
  }

  @AfterEach
  void afterEach() throws Exception {
    telemetryManager.close();
  }

  static final Prefab.Config TEST_FEATURE_FLAG = Prefab.Config
    .newBuilder()
    .setConfigType(Prefab.ConfigType.FEATURE_FLAG)
    .setValueType(Prefab.Config.ValueType.BOOL)
    .setKey("the.key")
    .setId(1L)
    .addAllowableValues(ConfigValueUtils.from(true))
    .addAllowableValues(ConfigValueUtils.from(false))
    .addRows(
      Prefab.ConfigRow
        .newBuilder()
        .addValues(
          Prefab.ConditionalValue
            .newBuilder()
            .setValue(ConfigValueUtils.from(true))
            .build()
        )
    )
    .build();

  static final Prefab.Config TEST_CONFIG = Prefab.Config
    .newBuilder()
    .setConfigType(Prefab.ConfigType.CONFIG)
    .setValueType(Prefab.Config.ValueType.INT)
    .setKey("hatdog")
    .setId(2L)
    .addRows(
      Prefab.ConfigRow
        .newBuilder()
        .addValues(
          Prefab.ConditionalValue.newBuilder().setValue(ConfigValueUtils.from(1)).build()
        )
    )
    .build();

  @Nested
  class LoggingTests {

    @Test
    void itRecordsLoggingCountsWhenEnabled() {
      buildTelemetryManager(
        new Options()
          .setContextUploadMode(Options.CollectContextMode.PERIODIC_EXAMPLE)
          .setCollectEvaluationSummaries(true)
          .setCollectLoggerCounts(true)
      );
      telemetryManager.reportLoggerUsage("a.b", Prefab.LogLevel.DEBUG, 4);
      telemetryManager.reportLoggerUsage("a.b", Prefab.LogLevel.WARN, 21);
      telemetryManager.reportLoggerUsage("a.b", Prefab.LogLevel.WARN, 4);
      telemetryManager.reportLoggerUsage("a.b", Prefab.LogLevel.INFO, 3);
      telemetryManager.reportLoggerUsage("a.b", Prefab.LogLevel.DEBUG, 7);
      telemetryManager.reportLoggerUsage("a.b.c", Prefab.LogLevel.DEBUG, 33);
      assertThat(telemetryManager.requestFlush().join()).isTrue();
      Prefab.TelemetryEvents telemetryEvents = telemetryEventsArgumentCaptor.getValue();

      List<Prefab.Logger> loggers = telemetryEvents
        .getEventsList()
        .stream()
        .filter(Prefab.TelemetryEvent::hasLoggers)
        .map(Prefab.TelemetryEvent::getLoggers)
        .map(Prefab.LoggersTelemetryEvent::getLoggersList)
        .flatMap(List::stream)
        .collect(Collectors.toList());

      assertThat(loggers)
        .containsExactlyInAnyOrder(
          Prefab.Logger
            .newBuilder()
            .setLoggerName("a.b")
            .setWarns(25)
            .setInfos(3)
            .setDebugs(11)
            .build(),
          Prefab.Logger.newBuilder().setLoggerName("a.b.c").setDebugs(33).build()
        );
    }
  }

  @Nested
  class MatchTests {

    @Test
    void itRecordsAllMatchRelatedData() {
      buildTelemetryManager(
        new Options()
          .setContextUploadMode(Options.CollectContextMode.PERIODIC_EXAMPLE)
          .setCollectEvaluationSummaries(true)
          .setCollectLoggerCounts(true)
      );

      reportSomeMatches(telemetryManager);
      assertThat(telemetryManager.requestFlush().join()).isTrue();

      Prefab.TelemetryEvents telemetryEvents = telemetryEventsArgumentCaptor.getValue();
      assertThat(telemetryEvents).isNotNull();
      // should see shapes, contexts and summaries
      assertOnlyExpectedKindsOfTelemetryPresent(
        telemetryEvents,
        Prefab.TelemetryEvent.PayloadCase.SUMMARIES,
        Prefab.TelemetryEvent.PayloadCase.CONTEXT_SHAPES,
        Prefab.TelemetryEvent.PayloadCase.EXAMPLE_CONTEXTS
      );
      assertContextShapesData(telemetryEvents);
      assertExampleContexts(telemetryEvents);
      assertEvaluationSummaries(telemetryEvents);
    }

    @Test
    void itExcludesExampleContexts() {
      buildTelemetryManager(
        new Options()
          .setContextUploadMode(Options.CollectContextMode.SHAPE_ONLY)
          .setCollectEvaluationSummaries(true)
          .setCollectLoggerCounts(true)
      );

      reportSomeMatches(telemetryManager);
      assertThat(telemetryManager.requestFlush().join()).isTrue();
      Prefab.TelemetryEvents telemetryEvents = telemetryEventsArgumentCaptor.getValue();
      assertThat(telemetryEvents).isNotNull();
      // should see shapes, contexts and summaries
      assertOnlyExpectedKindsOfTelemetryPresent(
        telemetryEvents,
        Prefab.TelemetryEvent.PayloadCase.SUMMARIES,
        Prefab.TelemetryEvent.PayloadCase.CONTEXT_SHAPES
      );
      assertContextShapesData(telemetryEvents);
      assertEvaluationSummaries(telemetryEvents);
    }

    @Test
    void itExcludesExampleContextsAndShapes() {
      buildTelemetryManager(
        new Options()
          .setContextUploadMode(Options.CollectContextMode.NONE)
          .setCollectEvaluationSummaries(true)
          .setCollectLoggerCounts(true)
      );

      reportSomeMatches(telemetryManager);
      assertThat(telemetryManager.requestFlush().join()).isTrue();
      Prefab.TelemetryEvents telemetryEvents = telemetryEventsArgumentCaptor.getValue();
      assertThat(telemetryEvents).isNotNull();
      // should see shapes, contexts and summaries
      assertOnlyExpectedKindsOfTelemetryPresent(
        telemetryEvents,
        Prefab.TelemetryEvent.PayloadCase.SUMMARIES
      );
      assertEvaluationSummaries(telemetryEvents);
    }

    @Test
    void itExcludesAllTelemetry() {
      buildTelemetryManager(
        new Options()
          .setContextUploadMode(Options.CollectContextMode.NONE)
          .setCollectEvaluationSummaries(false)
          .setCollectLoggerCounts(true)
      );

      reportSomeMatches(telemetryManager);
      assertThat(telemetryManager.requestFlush().join()).isTrue();
      verifyNoInteractions(prefabHttpClient);
    }

    private void assertOnlyExpectedKindsOfTelemetryPresent(
      Prefab.TelemetryEvents telemetryEvent,
      Prefab.TelemetryEvent.PayloadCase... cases
    ) {
      Map<Prefab.TelemetryEvent.PayloadCase, List<Prefab.TelemetryEvent>> eventsByCase = telemetryEvent
        .getEventsList()
        .stream()
        .collect(groupingBy(Prefab.TelemetryEvent::getPayloadCase));

      assertThat(eventsByCase.keySet()).contains(cases);
    }

    final Prefab.Context EXPECTED_TEAM_CONTEXT_PROTO = Prefab.Context
      .newBuilder()
      .setType("team")
      .putValues("key", ConfigValueUtils.from("t123"))
      .putValues("number", ConfigValueUtils.from(123))
      .putValues("cool", ConfigValueUtils.from(true))
      .putValues("pi", ConfigValueUtils.from(3.14))
      .build();

    private void assertEvaluationSummaries(Prefab.TelemetryEvents telemetryEvents) {
      List<Prefab.ConfigEvaluationSummary> summaries = telemetryEvents
        .getEventsList()
        .stream()
        .filter(Prefab.TelemetryEvent::hasSummaries)
        .map(Prefab.TelemetryEvent::getSummaries)
        .map(Prefab.ConfigEvaluationSummaries::getSummariesList)
        .flatMap(List::stream)
        .map(TestUtils::normalizeCounterOrder)
        .collect(Collectors.toList());

      assertThat(summaries)
        .containsExactlyInAnyOrder(
          TestUtils.normalizeCounterOrder(
            Prefab.ConfigEvaluationSummary
              .newBuilder()
              .setKey("the.key")
              .setType(Prefab.ConfigType.FEATURE_FLAG)
              .addCounters(
                Prefab.ConfigEvaluationCounter
                  .newBuilder()
                  .setCount(1)
                  .setConfigId(1)
                  .setSelectedIndex(1)
                  .setSelectedValue(ConfigValueUtils.from(false))
                  .setConfigRowIndex(1)
                  .setConditionalValueIndex(2)
              )
              .addCounters(
                Prefab.ConfigEvaluationCounter
                  .newBuilder()
                  .setCount(2)
                  .setConfigId(1)
                  .setSelectedIndex(0)
                  .setSelectedValue(ConfigValueUtils.from(true))
                  .setConfigRowIndex(1)
                  .setConditionalValueIndex(2)
              )
              .build()
          ),
          TestUtils.normalizeCounterOrder(
            Prefab.ConfigEvaluationSummary
              .newBuilder()
              .setKey("hatdog")
              .setType(Prefab.ConfigType.CONFIG)
              .addCounters(
                Prefab.ConfigEvaluationCounter
                  .newBuilder()
                  .setCount(1)
                  .setConfigId(2)
                  .setSelectedValue(ConfigValueUtils.from(1))
                  .setConfigRowIndex(0)
                  .setConditionalValueIndex(0)
              )
              .build()
          )
        );

      assertThat(summaries.stream().map(Prefab.ConfigEvaluationSummary::getKey))
        .doesNotContain("a.secret.key");
    }

    private void assertExampleContexts(Prefab.TelemetryEvents telemetryEvents) {
      List<Prefab.ContextSet> exampleContexts = telemetryEvents
        .getEventsList()
        .stream()
        .filter(Prefab.TelemetryEvent::hasExampleContexts)
        .map(Prefab.TelemetryEvent::getExampleContexts)
        .map(Prefab.ExampleContexts::getExamplesList)
        .flatMap(List::stream)
        .map(Prefab.ExampleContext::getContextSet)
        .collect(Collectors.toList());

      assertThat(exampleContexts)
        .containsExactlyInAnyOrder(
          Prefab.ContextSet
            .newBuilder()
            .addContexts(EXPECTED_TEAM_CONTEXT_PROTO)
            .addContexts(
              Prefab.Context
                .newBuilder()
                .setType("user")
                .putValues("key", ConfigValueUtils.from("u123"))
            )
            .build(),
          Prefab.ContextSet
            .newBuilder()
            .addContexts(EXPECTED_TEAM_CONTEXT_PROTO)
            .addContexts(
              Prefab.Context
                .newBuilder()
                .setType("user")
                .putValues("key", ConfigValueUtils.from("u124"))
            )
            .build()
        );
    }

    private void assertContextShapesData(Prefab.TelemetryEvents telemetryEvents) {
      List<Prefab.ContextShape> contextShapes = telemetryEvents
        .getEventsList()
        .stream()
        .filter(Prefab.TelemetryEvent::hasContextShapes)
        .map(Prefab.TelemetryEvent::getContextShapes)
        .map(Prefab.ContextShapes::getShapesList)
        .flatMap(List::stream)
        .collect(Collectors.toList());

      assertThat(contextShapes)
        .containsExactlyInAnyOrder(
          Prefab.ContextShape
            .newBuilder()
            .setName("team")
            .putFieldTypes("key", 2)
            .putFieldTypes("cool", 5)
            .putFieldTypes("number", 1)
            .putFieldTypes("pi", 4)
            .build(),
          Prefab.ContextShape.newBuilder().setName("user").putFieldTypes("key", 2).build()
        );
    }
  }

  private static void reportSomeMatches(TelemetryManager telemetryManager) {
    PrefabContext teamContext = PrefabContext
      .newBuilder("team")
      .put("key", "t123")
      .put("number", 123)
      .put("cool", true)
      .put("pi", 3.14)
      .build();

    telemetryManager.reportMatch(
      "a.key",
      new Match(
        ConfigValueUtils.from(false),
        new ConfigElement(
          TEST_FEATURE_FLAG,
          new Provenance(ConfigClient.Source.STREAMING)
        ),
        Collections.emptyList(),
        1,
        2,
        Optional.empty()
      ),
      new LookupContext(
        Optional.empty(),
        PrefabContextSet.from(
          PrefabContext.newBuilder("user").put("key", "u123").build(),
          teamContext
        )
      )
    );

    telemetryManager.reportMatch(
      "a.key",
      new Match(
        ConfigValueUtils.from(true),
        new ConfigElement(
          TEST_FEATURE_FLAG,
          new Provenance(ConfigClient.Source.STREAMING)
        ),
        Collections.emptyList(),
        1,
        2,
        Optional.empty()
      ),
      new LookupContext(
        Optional.empty(),
        PrefabContextSet.from(
          PrefabContext.newBuilder("user").put("key", "u123").build(),
          teamContext
        )
      )
    );

    telemetryManager.reportMatch(
      "a.key",
      new Match(
        ConfigValueUtils.from(true),
        new ConfigElement(
          TEST_FEATURE_FLAG,
          new Provenance(ConfigClient.Source.STREAMING)
        ),
        Collections.emptyList(),
        1,
        2,
        Optional.empty()
      ),
      new LookupContext(
        Optional.empty(),
        PrefabContextSet.from(
          PrefabContext.newBuilder("user").put("key", "u124").build(),
          teamContext
        )
      )
    );

    telemetryManager.reportMatch(
      "a.key",
      new Match(
        ConfigValueUtils.from(1),
        new ConfigElement(TEST_CONFIG, new Provenance(ConfigClient.Source.STREAMING)),
        Collections.emptyList(),
        0,
        0,
        Optional.empty()
      ),
      new LookupContext(
        Optional.empty(),
        PrefabContextSet.from(
          PrefabContext.newBuilder("user").put("key", "u123").build(),
          teamContext
        )
      )
    );

    telemetryManager.reportMatch(
      "a.secret.key",
      new Match(
        ConfigValueUtils.from(1).toBuilder().setConfidential(true).build(),
        new ConfigElement(TEST_CONFIG, new Provenance(ConfigClient.Source.STREAMING)),
        Collections.emptyList(),
        0,
        0,
        Optional.empty()
      ),
      new LookupContext(
        Optional.empty(),
        PrefabContextSet.from(
          PrefabContext.newBuilder("user").put("key", "u123").build(),
          teamContext
        )
      )
    );
  }
}
