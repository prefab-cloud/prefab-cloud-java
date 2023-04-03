# prefab-cloud-java
Java Client for Prefab LogLevels, FeatureFlags, Config as a Service: https://www.prefab.cloud

See full documentation https://docs.prefab.cloud/docs/java-sdk/java

## Logback Logging Filter

The filter will 
* Capture information about your logger volume by logger name and level
* Filter logs based on the dynamic configuration


Maven
```xml
<dependency>
    <groupId>cloud.prefab</groupId>
    <artifactId>logback-listener</artifactId>
    <version>0.3.5</version>
</dependency>
```

Install
```java
PrefabMDCTurboFilter.install(client);
```

## Copyright

Copyright (c) 2023 PrefabCloud LLC. See LICENSE.txt for further details.
