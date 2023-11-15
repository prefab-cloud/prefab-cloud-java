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

  @Captor
  ArgumentCaptor<Prefab.ContextShapes> shapesArgumentCaptor;

  private ContextShapeAggregator aggregator;

  @BeforeEach
  void beforeEach() {
    aggregator = new ContextShapeAggregator();
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

    Prefab.ContextShapes reportedShape = aggregator.getShapes();

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
}
