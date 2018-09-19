# prefab-cloud-java
Java Client for Prefab RateLimits, FeatureFlags, Config as a Service: https://www.prefab.cloud


### Rate Limit
```java
PrefabCloudClient prefabCloudClient = new PrefabCloudClient(new PrefabCloudClient.Builder()
                                          .setDistributedCache(new MemcachedCache(memcachedClient)));
RateLimitClient rateLimitClient = prefabCloudClient.newRateLimitClient();

boolean result = rateLimitClient.isPass(Prefab.LimitRequest.newBuilder()
                                .setAcquireAmount(1)
                                .addGroups("hubtest.secondly")
                                .build());
System.out.println("acquire? " + result);
```


See full documentation https://www.prefab.cloud/documentation/installation

Maven
```xml
<dependency>
    <groupId>cloud.prefab</groupId>
    <artifactId>prefab-cloud-java</artifactId>
    <version>0.0.7</version>
</dependency>
```

## Supports

* [RateLimits](https://www.prefab.cloud/documentation/basic_rate_limits)
* Millions of individual limits sharing the same policies
* WebUI for tweaking limits & feature flags
* Infinite retention for [deduplication workflows](https://www.prefab.cloud/documentation/once_and_only_once)
* [FeatureFlags](https://www.prefab.cloud/documentation/feature_flags) as a Service


## Contributing to prefab-cloud-java
 
* Check out the latest master to make sure the feature hasn't been implemented or the bug hasn't been fixed yet.
* Check out the issue tracker to make sure someone already hasn't requested it and/or contributed it.
* Fork the project.
* Start a feature/bugfix branch.
* Commit and push until you are happy with your contribution.
* Make sure to add tests for it. This is important so I don't break it in a future version unintentionally.
* Please try not to mess with the Rakefile, version, or history. If you want to have your own version, or is otherwise necessary, that is fine, but please isolate to its own commit so I can cherry-pick around it.

## Copyright

Copyright (c) 2018 Jeff Dwyer. See LICENSE.txt for
further details.
