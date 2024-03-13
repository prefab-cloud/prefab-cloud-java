package cloud.prefab.client.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import cloud.prefab.context.PrefabContext;
import cloud.prefab.context.PrefabContextSet;
import cloud.prefab.context.PrefabContextSetReadable;
import org.junit.jupiter.api.Test;

class ContextMergerTest {

  private static final PrefabContextSet GLOBAL = PrefabContextSet.from(
    PrefabContext.newBuilder("a").put("ga-foo", "bar").put("ga-abc", 123).build(),
    PrefabContext.newBuilder("b").put("gb-foo", "bar").put("gb-abc", 123).build(),
    PrefabContext.newBuilder("global").put("sunny", "day").put("solar", 123).build()
  );

  private static final PrefabContextSet API = PrefabContextSet.from(
    PrefabContext.newBuilder("a").put("api-a-foo", "bar").put("api-a-abc", 123).build(),
    PrefabContext.newBuilder("b").put("api-a-foo", "bar").put("api-a-abc", 123).build(),
    PrefabContext.newBuilder("api").put("cloudy", "day").put("solar", 234).build()
  );

  private static final PrefabContextSet CURRENT = PrefabContextSet.from(
    PrefabContext
      .newBuilder("a")
      .put("current-a-foo", "bar")
      .put("current-a-abc", 123)
      .build(),
    PrefabContext
      .newBuilder("b")
      .put("current-a-foo", "bar")
      .put("current-a-abc", 123)
      .build(),
    PrefabContext.newBuilder("current").put("rainy", "day").put("solar", 456).build()
  );

  private static final PrefabContextSet PASSED = PrefabContextSet.from(
    PrefabContext
      .newBuilder("a")
      .put("passed-a-foo", "bar")
      .put("passed-a-abc", 123)
      .build(),
    PrefabContext
      .newBuilder("b")
      .put("passed-a-foo", "bar")
      .put("passed-a-abc", 123)
      .build(),
    PrefabContext.newBuilder("passed").put("foggy", "day").put("solar", 345).build()
  );

  @Test
  void itReturnsEmptyContextForAllNullArguments() {
    assertThat(ContextMerger.merge(null, null, null, null).isEmpty()).isTrue();
  }

  @Test
  void itReturnsEmptyContextForAllEmptyArguments() {
    assertThat(
      ContextMerger
        .merge(
          PrefabContextSetReadable.EMPTY,
          PrefabContextSetReadable.EMPTY,
          PrefabContextSetReadable.EMPTY,
          PrefabContextSetReadable.EMPTY
        )
        .isEmpty()
    )
      .isTrue();
  }

  @Test
  void itMergesGlobalWithApiInCorrectOrderWithoutPassedOrCurrentContext() {
    PrefabContextSetReadable merged = ContextMerger.merge(GLOBAL, API, CURRENT, PASSED);
    assertThat(merged)
      .isEqualTo(
        PASSED
          .addContext(GLOBAL.getByName("global").get())
          .addContext(API.getByName("api").get())
          .addContext(CURRENT.getByName("current").get())
      );
  }

  @Test
  void itMergesAllContextsCorrectly() {
    PrefabContextSetReadable merged = ContextMerger.merge(
      GLOBAL,
      API,
      PrefabContextSetReadable.EMPTY,
      PrefabContextSetReadable.EMPTY
    );

    assertThat(merged).isEqualTo(API.addContext(GLOBAL.getByName("global").get()));
  }
}
