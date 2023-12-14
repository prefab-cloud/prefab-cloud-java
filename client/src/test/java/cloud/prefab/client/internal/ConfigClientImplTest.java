package cloud.prefab.client.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cloud.prefab.client.ConfigClient;
import cloud.prefab.client.Options;
import cloud.prefab.client.PrefabCloudClient;
import cloud.prefab.client.PrefabInitializationTimeoutException;
import cloud.prefab.client.config.ConfigChangeEvent;
import cloud.prefab.client.config.ConfigChangeListener;
import cloud.prefab.client.config.ConfigValueUtils;
import cloud.prefab.client.config.TestData;
import cloud.prefab.context.PrefabContext;
import cloud.prefab.context.PrefabContextHelper;
import cloud.prefab.context.PrefabContextSet;
import cloud.prefab.context.PrefabContextSetReadable;
import cloud.prefab.domain.Prefab;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConfigClientImplTest {

  @Test
  void localModeUnlocks() {
    final PrefabCloudClient baseClient = new PrefabCloudClient(
      new Options().setPrefabDatasource(Options.Datasources.LOCAL_ONLY)
    );
    ConfigClient configClient = new ConfigClientImpl(baseClient);

    final Optional<Prefab.ConfigValue> key = configClient.get("key");
    assertThat(key).isNotPresent();
  }

  @Test
  void initializationTimeout() {
    final PrefabCloudClient baseClient = new PrefabCloudClient(
      new Options()
        .setApikey("0-P1-E1-SDK-1234-123-23")
        .setInitializationTimeoutSec(1)
        .setOnInitializationFailure(Options.OnInitializationFailure.RAISE)
    );

    ConfigClient configClient = new ConfigClientImpl(baseClient);
    assertThrows(
      PrefabInitializationTimeoutException.class,
      () -> configClient.get("key")
    );
  }

  @Test
  void initializationUnlock() {
    final PrefabCloudClient baseClient = new PrefabCloudClient(
      new Options()
        .setApikey("0-P1-E1-SDK-1234-123-23")
        .setInitializationTimeoutSec(1)
        .setOnInitializationFailure(Options.OnInitializationFailure.UNLOCK)
    );

    ConfigClient configClient = new ConfigClientImpl(baseClient);
    assertThat(configClient.get("key")).isNotPresent();
  }

  @Test
  void broadcast() {
    final PrefabCloudClient baseClient = new PrefabCloudClient(
      new Options()
        .setApikey("0-P1-E1-SDK-1234-123-23")
        .setConfigOverrideDir("none")
        .setInitializationTimeoutSec(1)
        .setOnInitializationFailure(Options.OnInitializationFailure.UNLOCK)
    );

    ConfigClient configClient = new ConfigClientImpl(baseClient);

    List<ConfigChangeEvent> receivedEvents = new ArrayList<>();
    ConfigChangeListener listener = receivedEvents::add;

    configClient.addConfigChangeListener(listener);

    assertThat(configClient.get("key")).isNotPresent();

    assertThat(receivedEvents)
      .containsExactlyInAnyOrder(
        new ConfigChangeEvent(
          "sample_bool",
          Optional.empty(),
          Optional.of(
            Prefab.Config
              .newBuilder()
              .addRows(
                Prefab.ConfigRow
                  .newBuilder()
                  .addValues(
                    Prefab.ConditionalValue
                      .newBuilder()
                      .setValue(ConfigValueUtils.from(true))
                      .build()
                  )
                  .build()
              )
              .build()
          )
        ),
        new ConfigChangeEvent(
          "sample",
          Optional.empty(),
          Optional.of(
            Prefab.Config
              .newBuilder()
              .addRows(
                Prefab.ConfigRow
                  .newBuilder()
                  .addValues(
                    Prefab.ConditionalValue
                      .newBuilder()
                      .setValue(ConfigValueUtils.from("default sample value"))
                      .build()
                  )
                  .build()
              )
              .build()
          )
        )
      );
  }

  @Test
  void localDataFileMode() {
    final PrefabCloudClient baseClient = new PrefabCloudClient(
      new Options()
        .setLocalDatafile("src/test/resources/prefab.Development.5.config.json")
    );
    ConfigClient configClient = new ConfigClientImpl(baseClient);
    final Optional<Prefab.ConfigValue> key = configClient.get("cool.bool.enabled");
    assertThat(key).isPresent();
  }

  @Test
  void itLooksUpLogLevelsWithProvidedEmptyContext() {
    try (
      PrefabCloudClient prefabCloudClient = new PrefabCloudClient(
        TestData.getDefaultOptionsWithEnvName("logging_multilevel")
      )
    ) {
      ConfigClient configClient = prefabCloudClient.configClient();
      assertThat(
        configClient.getLogLevel(
          "com.example.p1.ClassOne",
          PrefabContextSetReadable.EMPTY
        )
      )
        .contains(Prefab.LogLevel.TRACE);

      assertThat(
        configClient.getLogLevel(
          "com.example.p1.ClassTwo",
          PrefabContextSetReadable.EMPTY
        )
      )
        .contains(Prefab.LogLevel.DEBUG);

      assertThat(
        configClient.getLogLevel(
          "com.example.AnotherClass",
          PrefabContextSetReadable.EMPTY
        )
      )
        .contains(Prefab.LogLevel.ERROR);

      assertThat(
        configClient.getLogLevel("com.foo.ClipBoard", PrefabContextSetReadable.EMPTY)
      )
        .contains(Prefab.LogLevel.WARN);
    }
  }

  @Nested
  class ContextTests {

    @Captor
    ArgumentCaptor<LookupContext> lookupContextArgumentCaptor;

    @Mock
    UpdatingConfigResolver updatingConfigResolver;

    @Mock
    PrefabCloudClient prefabCloudClient;

    ConfigClientImpl configClient;
    private PrefabContextHelper contextHelper;

    @BeforeEach
    void beforeEach() {
      Options options = new Options().setPrefabDatasource(Options.Datasources.LOCAL_ONLY);
      when(prefabCloudClient.getOptions()).thenReturn(options);
      when(updatingConfigResolver.update())
        .thenReturn(
          new UpdatingConfigResolver.ChangeLists(
            Collections.emptyList(),
            Collections.emptyList()
          )
        );

      this.configClient = new ConfigClientImpl(prefabCloudClient, updatingConfigResolver);
      this.contextHelper = new PrefabContextHelper(configClient);
    }

    @Test
    void requestWithNoPassedContextHasAnEmptyLookupContext() {
      configClient.get("foobar");
      verify(updatingConfigResolver)
        .getMatch("foobar", new LookupContext(PrefabContextSetReadable.EMPTY));
    }

    @Test
    void requestWithPassedContextHasSameInLookupContext() {
      PrefabContext prefabContext = PrefabContext
        .newBuilder("user")
        .put("name", "james")
        .put("isHuman", true)
        .build();

      configClient.get("foobar", prefabContext);
      verify(updatingConfigResolver).getMatch("foobar", new LookupContext(prefabContext));
    }

    @Test
    void requestWithGlobalContextAndNoPassedContextHasExpectedLookup() {
      PrefabContext prefabContext = PrefabContext
        .newBuilder("user")
        .put("name", "james")
        .put("isHuman", true)
        .build();

      try (
        PrefabContextHelper.PrefabContextScope ignored = contextHelper.performWorkWithAutoClosingContext(
          prefabContext
        )
      ) {
        configClient.get("foobar");
        verify(updatingConfigResolver)
          .getMatch(eq("foobar"), lookupContextArgumentCaptor.capture());
      }

      LookupContext lookupContext = lookupContextArgumentCaptor.getValue();
      LookupContext expected = new LookupContext(PrefabContextSet.from(prefabContext));

      assertThat(lookupContext).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void requestWithGlobalContextAndConflictingPassedContextHasExpectedLookup() {
      PrefabContextSet globalUserContext = PrefabContextSet.from(
        PrefabContext
          .newBuilder("user")
          .put("name", "james")
          .put("isHuman", true)
          .put("somethingCount", 11)
          .build(),
        PrefabContext.newBuilder("computer").put("greeting", "hello computer").build()
      );

      PrefabContextSet localUserContext = PrefabContextSet.from(
        PrefabContext
          .newBuilder("user")
          .put("name", "roboto")
          .put("isHuman", false)
          .build(),
        PrefabContext.newBuilder("transaction").put("type", "credit").build()
      );

      try (
        PrefabContextHelper.PrefabContextScope ignored = contextHelper.performWorkWithAutoClosingContext(
          globalUserContext
        )
      ) {
        configClient.get("foobar", localUserContext);
        verify(updatingConfigResolver)
          .getMatch(eq("foobar"), lookupContextArgumentCaptor.capture());
      }
      LookupContext lookupContext = lookupContextArgumentCaptor.getValue();

      LookupContext expected = new LookupContext(
        PrefabContextSet.from(
          PrefabContext
            .newBuilder("user")
            .put("name", "roboto")
            .put("isHuman", false)
            .build(),
          PrefabContext.newBuilder("computer").put("greeting", "hello computer").build(),
          PrefabContext.newBuilder("transaction").put("type", "credit").build()
        )
      );

      assertThat(lookupContext).usingRecursiveComparison().isEqualTo(expected);
    }
  }
}
