# prefab-cloud-java
Java Client for Prefab RateLimits, FeatureFlags, Config as a Service: https://www.prefab.cloud


### Rate Limit
```java
final PrefabCloudClient prefabCloudClient = new PrefabCloudClient(new Options());

FeatureFlagClient featureFlagClient = prefabCloudClient.featureFlagClient();

featureFlagClient.featureIsOnFor(
    "features.example-flag",
    "123",
    Map.of("customer-group", "beta")
);

final Optional<Prefab.ConfigValue> configValue = prefabCloudClient.configClient().get("the.key");
if(configValue.isPresent()){
    System.out.println(configValue.get().getString());
}        
```


See full documentation https://www.prefab.cloud/documentation/getting_started

Maven
```xml
<dependency>
    <groupId>cloud.prefab</groupId>
    <artifactId>prefab-cloud-java</artifactId>
    <version>0.1.6</version>
</dependency>
```

## Supports
* [FeatureFlags](https://docs.prefab.cloud/docs/java) as a Service


## Contributing to prefab-cloud-java
 
* Check out the latest `main` to make sure the feature hasn't been implemented or the bug hasn't been fixed yet.
* Check out the issue tracker to make sure someone already hasn't requested it and/or contributed it.
* Fork the project.
* Start a feature/bugfix branch.
* Commit and push until you are happy with your contribution.
* Make sure to add tests for it. This is important so I don't break it in a future version unintentionally.
* Please try not to mess with the Rakefile, version, or history. If you want to have your own version, or is otherwise necessary, that is fine, but please isolate to its own commit so I can cherry-pick around it.

If you get errors about the pom not being sorted, run `mvn sortpom:sort -Dsort.createBackupFile=false`
If you get errors about the code not being formatted, run `mvn prettier:write`

## Copyright

Copyright (c) 2012 PrefabCloud LLC. See LICENSE.txt for
further details.
