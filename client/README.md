# prefab-cloud-java

[![javadoc](https://javadoc.io/badge2/cloud.prefab/client/javadoc.svg)](https://javadoc.io/doc/cloud.prefab/client)
[![Better Uptime Badge](https://betteruptime.com/status-badges/v1/monitor/pdi9.svg)](https://betteruptime.com/?utm_source=status_badge)

Java (11+) Client for Prefab LogLevels, FeatureFlags, Config as a Service: https://www.prefab.cloud

See full documentation https://docs.prefab.cloud/docs/java-sdk/java


### Maven
```xml
<dependency>
    <groupId>cloud.prefab</groupId>
    <artifactId>client</artifactId>
    <version>0.3.17</version>
</dependency>
```

### Maven Shaded Pom

For an uber-jar including relocated guava and failsafe dependencies add the "uberjar" classifier as below

```xml
<dependency>
    <groupId>cloud.prefab</groupId>
    <artifactId>client</artifactId>
    <version>0.3.17</version>
    <classifier>uberjar</classifier>
</dependency>
```

## Logging Setup

Live log levels with log4j or logback require additional maven dependencies.

* [Log4J (one)](../log4j-one-listener/README.md)
* [Log4j (two)](../log4j-two-listener/README.md)
* [LogBack](../logback-listener/README.md)


## Container Support

ThreadLocal context state management handles many containers. For event-based containers where ThreadLocals are harder to manage, we have container-specific modules to support tying ContextState to the request scope

* [Micronaut](../micronaut/README.md)


## Contributing to prefab-cloud-java

* Check out the latest `main` to make sure the feature hasn't been implemented or the bug hasn't been fixed yet.
* Check out the issue tracker to make sure someone already hasn't requested it and/or contributed it.
* Fork the project.
* Start a feature/bugfix branch.
* Fetch the submodules with `git submodule init` and `git submodule update`
* Run tests with `mvn test` to ensure everything is in a good state.
* Commit and push until you are happy with your contribution.
* Make sure to add tests for it. This is important so we don't break it in a future version unintentionally.

If you get errors about the pom not being sorted, run `mvn sortpom:sort -Dsort.createBackupFile=false`
If you get errors about the code not being formatted, run `mvn prettier:write`

## Copyright

Copyright (c) 2023 PrefabCloud LLC. See LICENSE.txt for further details.
