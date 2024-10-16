# prefab-cloud-java
Java Client for Prefab LogLevels, FeatureFlags, Config as a Service: https://www.prefab.cloud

See full documentation https://docs.prefab.cloud/docs/java-sdk/java

# Micronaut Support

## Context Storage

Out of the box, the Prefab client includes the ThreadLocalContextStore which relies on the same Thread handling a HTTP Request. 

Micronaut has an event based model, so state must be managed without ThreadLocals - to that end we provide the `ServerRequestContextStore` that uses the ServerRequestContext. 
_Note: Behind the scenes ServerRequestContext is based on a threadlocal, but micronaut's instrumentation code knows to copy this threadlocal between threads as the request moves through processing._

### Usage

Maven

Maven
```xml
<dependency>
    <groupId>cloud.prefab</groupId>
    <artifactId>micronaut</artifactId>
    <version>0.3.24</version>
</dependency>
```

The context store implementation is added to the Prefab `Options` class.

You'll likely have a factory class like this one - see the `options.setContextStore(new PrefabMicronautStateStore());` in the prefabCloudClient method

```java
@Factory
public class PrefabFactory {
    private static final Logger LOG = LoggerFactory.getLogger(PrefabFactory.class);

    @Singleton
    public PrefabCloudClient prefabCloudClient(Environment environment) {
        final Options options = new Options();
        options.setPrefabEnvs(environment.getActiveNames().stream().toList());
        options.setContextStore(new PrefabMicronautStateStore());
        return new PrefabCloudClient(options);
    }

    @Singleton
    public FeatureFlagClient featureFlagClient(PrefabCloudClient prefabCloudClient) {
        return prefabCloudClient.featureFlagClient();
    }

    @Context
    public ConfigClient configClient(
            PrefabCloudClient prefabCloudClient
    ) {
        ConfigClient configClient = prefabCloudClient.configClient();
        // install the logging filter at the same time
        PrefabMDCTurboFilter.install(configClient);
        return configClient;
    }
}
```

