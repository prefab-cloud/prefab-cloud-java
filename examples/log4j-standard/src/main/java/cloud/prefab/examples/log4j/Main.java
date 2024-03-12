package cloud.prefab.examples.log4j;

import cloud.prefab.client.Options;
import cloud.prefab.client.PrefabCloudClient;
import cloud.prefab.client.config.logging.PrefabContextFilter;
import cloud.prefab.context.PrefabContext;
import cloud.prefab.context.PrefabContextHelper;
import cloud.prefab.context.PrefabContextSet;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

  private static final Logger LOG = LoggerFactory.getLogger(Main.class);

  public void runDemo() {
    // these contexts don't change so they
    PrefabContext applicationContext = PrefabContext
      .newBuilder("application")
      .put("key", "prefab-log4j-example")
      .build();
    PrefabContext deploymentContext = PrefabContext
      .newBuilder("deploy")
      .put("key", "prefab-log4j-example-daemon")
      .put("az", "1a")
      .put("type", "daemon")
      .build();

    Options options = new Options()
      .setGlobalContext(PrefabContextSet.from(applicationContext, deploymentContext));
    PrefabCloudClient prefabClient = new PrefabCloudClient(options);
    PrefabContextFilter.install(prefabClient.configClient());
    PrefabContextHelper prefabContextHelper = new PrefabContextHelper(
      prefabClient.configClient()
    );

    while (true) {
      LOG.error("\uD83D\uDEA9this is an error message");
      LOG.warn("⚠\uFE0Fthis is a warn message");
      LOG.info("ℹ\uFE0F this is an info message");
      LOG.debug("\uD83D\uDC1Bthis is a debug message");

      PrefabContext eventSpecificContext = PrefabContext
        .newBuilder("event")
        .put("type", "metric")
        .put("source", "cloud-monitor")
        .build();

      // this replaces any existing context data in a Thread-local until the try-block is over
      // this will get stacked on top of the global context, so all contexts can be targeted
      try (
        PrefabContextHelper.PrefabContextScope ignored = prefabContextHelper.performWorkWithAutoClosingContext(
          eventSpecificContext
        )
      ) {
        LOG.error(
          "\uD83C\uDFAF\uD83D\uDEA9this is a more specifically targeted error message"
        );
        LOG.warn("\uD83C\uDFAF⚠\uFE0Fthis is a more specifically targeted warn message");
        LOG.info("\uD83C\uDFAFℹ\uFE0F this is a more specifically targeted info message");
        LOG.debug(
          "\uD83C\uDFAF\uD83D\uDC1Bthis is a more specifically targeted debug message"
        );
      }

      try {
        Thread.sleep(TimeUnit.SECONDS.toMillis(1));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  public static void main(String... args) {
    System.out.println("Starting example");
    Main main = new Main();
    main.runDemo();
  }
}
