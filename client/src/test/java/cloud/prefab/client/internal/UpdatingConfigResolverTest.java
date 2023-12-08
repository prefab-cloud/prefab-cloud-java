package cloud.prefab.client.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cloud.prefab.client.ConfigClient;
import cloud.prefab.client.Options;
import cloud.prefab.client.PrefabCloudClient;
import cloud.prefab.client.config.ConfigChangeEvent;
import cloud.prefab.client.config.ConfigElement;
import cloud.prefab.client.config.Provenance;
import cloud.prefab.domain.Prefab;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class UpdatingConfigResolverTest {

  private final int TEST_PROJ_ENV = 2;

  private UpdatingConfigResolver resolver;
  private PrefabCloudClient mockBaseClient;
  private Options mockOptions;
  private ConfigLoader mockLoader;

  @BeforeEach
  public void setup() {
    mockLoader = mock(ConfigLoader.class);
    mockOptions = mock(Options.class);

    when(mockLoader.calcConfig()).thenReturn(testData());
    mockBaseClient = mock(PrefabCloudClient.class);
    when(mockBaseClient.getOptions()).thenReturn(mockOptions);
    resolver =
      new UpdatingConfigResolver(
        mockLoader,
        new WeightedValueEvaluator(),
        new ConfigStoreConfigValueDeltaCalculator()
      );
  }

  @Test
  public void testUpdateChangeDetection() {
    UpdatingConfigResolver.ChangeLists changeLists = resolver.update();
    assertThat(changeLists.getConfigChangeEvents())
      .containsExactlyInAnyOrder(
        new ConfigChangeEvent(
          "key1",
          Optional.empty(),
          Optional.of(Prefab.ConfigValue.newBuilder().setString("value_none").build())
        ),
        new ConfigChangeEvent(
          "key2",
          Optional.empty(),
          Optional.of(Prefab.ConfigValue.newBuilder().setString("valueB2").build())
        )
      );

    when(mockLoader.calcConfig()).thenReturn(testDataAddingKey3andTombstoningKey1());
    assertThat(resolver.update().getConfigChangeEvents())
      .containsExactlyInAnyOrder(
        new ConfigChangeEvent(
          "key1",
          Optional.of(Prefab.ConfigValue.newBuilder().setString("value_none").build()),
          Optional.empty()
        ),
        new ConfigChangeEvent(
          "key3",
          Optional.empty(),
          Optional.of(Prefab.ConfigValue.newBuilder().setString("key3").build())
        )
      );
  }

  private MergedConfigData testDataAddingKey3andTombstoningKey1() {
    Map<String, ConfigElement> config = new HashMap<>();
    config.put("key1", ce(Prefab.Config.newBuilder().setKey("key1").build()));
    config.put("key2", ce(key2()));
    config.put(
      "key3",
      ce(
        Prefab.Config
          .newBuilder()
          .setKey("key1")
          .addRows(rowWithStringValue("key3"))
          .build()
      )
    );
    return new MergedConfigData(config, TEST_PROJ_ENV, DefaultContextWrapper.empty());
  }

  private Prefab.ConfigRow rowWithStringValue(String value) {
    return rowWithStringValue(value, Optional.empty(), Optional.empty());
  }

  private Prefab.ConfigRow rowWithStringValue(
    String value,
    Optional<Integer> env,
    Optional<Map<String, String>> namespaceValues
  ) {
    final Prefab.ConfigRow.Builder rowBuilder = Prefab.ConfigRow.newBuilder();
    env.ifPresent(rowBuilder::setProjectEnvId);
    namespaceValues.ifPresent(stringStringMap ->
      stringStringMap.forEach((namespace, stringValue) -> {
        final Prefab.ConditionalValue.Builder builder = Prefab.ConditionalValue
          .newBuilder()
          .setValue(Prefab.ConfigValue.newBuilder().setString(stringValue).build());

        if (namespace != null) {
          builder.addCriteria(
            Prefab.Criterion
              .newBuilder()
              .setPropertyName(ConfigResolver.NAMESPACE_KEY)
              .setOperator(Prefab.Criterion.CriterionOperator.HIERARCHICAL_MATCH)
              .setValueToMatch(Prefab.ConfigValue.newBuilder().setString(namespace))
          );
        }
        rowBuilder.addValues(builder.build());
      })
    );
    rowBuilder
      .addValues(
        Prefab.ConditionalValue
          .newBuilder()
          .setValue(Prefab.ConfigValue.newBuilder().setString(value))
          .build()
      )
      .build();
    return rowBuilder.build();
  }

  @Test
  public void testContentsString() {
    resolver.update();
    String expected =
      "key1                                         value_none                              LOCAL_ONLY:unit test                                                            \n" +
      "key2                                         valueB2                                 LOCAL_ONLY:unit test                                                            \n";
    assertThat(resolver.contentsString()).isEqualTo(expected);
  }

  private MergedConfigData testData() {
    Map<String, ConfigElement> config = new HashMap<>();
    config.put("key1", ce(key1()));
    config.put("key2", ce(key2()));
    return new MergedConfigData(config, TEST_PROJ_ENV, DefaultContextWrapper.empty());
  }

  private ConfigElement ce(Prefab.Config config) {
    return new ConfigElement(
      config,
      new Provenance(ConfigClient.Source.LOCAL_ONLY, "unit test")
    );
  }

  private Prefab.Config key1() {
    LinkedHashMap<String, String> map = new LinkedHashMap(); // insertion order important
    map.put("projectB.subprojectY", "projectB.subprojectY");
    map.put("projectB.subprojectX", "projectB.subprojectX");
    map.put("projectA", "valueA");
    map.put("projectB", "valueB");
    map.put(null, "value_none");

    return Prefab.Config
      .newBuilder()
      .setKey("key1")
      .addRows(rowWithStringValue("value_no_env_default"))
      .addRows(
        rowWithStringValue("value_none", Optional.of(TEST_PROJ_ENV), Optional.of(map))
      ) // order important here
      .build();
  }

  private Prefab.Config key2() {
    return Prefab.Config
      .newBuilder()
      .setKey("key2")
      .addRows(rowWithStringValue("valueB2"))
      .build();
  }
}
