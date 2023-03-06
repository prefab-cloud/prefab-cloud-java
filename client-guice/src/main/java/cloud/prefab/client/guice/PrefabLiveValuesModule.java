package cloud.prefab.client.guice;

import cloud.prefab.client.ConfigClient;
import cloud.prefab.client.value.LiveBoolean;
import cloud.prefab.client.value.LiveDouble;
import cloud.prefab.client.value.LiveString;
import cloud.prefab.domain.Prefab;
import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PrefabLiveValuesModule extends AbstractModule {

  private static final Logger LOG = LoggerFactory.getLogger(PrefabLiveValuesModule.class);

  private static final TypeLiteral<Supplier<Long>> LONG_SUPPLIER_CLASS = new TypeLiteral<>() {};
  private static final TypeLiteral<Supplier<Double>> DOUBLE_SUPPLIER_CLASS = new TypeLiteral<>() {};
  private static final TypeLiteral<Supplier<String>> STRING_SUPPLIER_CLASS = new TypeLiteral<>() {};
  private static final TypeLiteral<Supplier<Boolean>> BOOLEAN_SUPPLIER_CLASS = new TypeLiteral<>() {};

  private final ConfigClient configClient;

  public PrefabLiveValuesModule(ConfigClient configClient) {
    this.configClient = configClient;
  }

  @Override
  protected void configure() {
    Binder namesBinder = binder().skipSources(Names.class);
    configClient
      .getAllValues()
      .forEach((String key, Prefab.ConfigValue value) -> {
        switch (value.getTypeCase()) {
          case INT:
            namesBinder
              .bind(Key.get(LONG_SUPPLIER_CLASS, Names.named(key)))
              .toInstance(configClient.liveLong(key));
            break;
          case STRING:
            namesBinder
              .bind(Key.get(STRING_SUPPLIER_CLASS, Names.named(key)))
              .toInstance(configClient.liveString(key));
            break;
          case DOUBLE:
            namesBinder
              .bind(Key.get(DOUBLE_SUPPLIER_CLASS, Names.named(key)))
              .toInstance(configClient.liveDouble(key));
            break;
          case BOOL:
            namesBinder
              .bind(Key.get(BOOLEAN_SUPPLIER_CLASS, Names.named(key)))
              .toInstance(configClient.liveBoolean(key));
            break;
          default:
            LOG.debug(
              "Binding values encountered unhandled type {} for key {}",
              value.getTypeCase(),
              key
            );
        }
      });
  }
}
