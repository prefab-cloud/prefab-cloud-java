# prefab-cloud-java
Java Client for Prefab LogLevels, FeatureFlags, Config as a Service: https://www.prefab.cloud

See full documentation https://docs.prefab.cloud/docs/java-sdk/java

Maven
```xml
<dependency>
    <groupId>cloud.prefab</groupId>
    <artifactId>client</artifactId>
    <version>0.2.0.pre2</version>
</dependency>
```
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