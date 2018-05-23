package cloud.prefab.client;

import cloud.prefab.client.util.RandomProvider;
import cloud.prefab.client.util.RandomProviderIF;
import cloud.prefab.domain.Prefab;
import com.google.common.collect.Lists;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

import java.util.List;
import java.util.Optional;

public class FeatureFlagClient {
  private static final HashFunction hash = Hashing.murmur3_32();
  private static final long UNSIGNED_INT_MAX = Integer.MAX_VALUE + (long) Integer.MAX_VALUE;

  private final PrefabCloudClient baseClient;

  private RandomProviderIF randomProvider = new RandomProvider();

  public FeatureFlagClient(PrefabCloudClient baseClient) {
    this.baseClient = baseClient;
  }

  public boolean featureIsOn(String feature) {
    return featureIsOnFor(feature, Optional.empty(), Lists.newArrayList());
  }

  public boolean featureIsOnFor(String feature, Optional<String> key, List<String> attributes) {

    final ConfigClient configClient = baseClient.configClient();
    final Optional<Prefab.ConfigValue> configValue = configClient.get(feature);

    if (configValue.isPresent() && configValue.get().getTypeCase() == Prefab.ConfigValue.TypeCase.FEATURE_FLAG) {
      return isOnFor(configValue.get().getFeatureFlag(), feature, key, attributes);
    } else {
      return true;
    }
  }

  public void upsert(String key, Prefab.FeatureFlag featureFlag) {
    baseClient.configClient().upsert(Prefab.UpsertRequest.newBuilder()
        .setConfigDelta(Prefab.ConfigDelta.newBuilder()
            .setKey(key)
            .setValue(Prefab.ConfigValue.newBuilder()
                .setFeatureFlag(featureFlag)))
        .build());
  }

  protected boolean isOnFor(Prefab.FeatureFlag featureFlag, String feature, Optional<String> key, List<String> attributes) {
    if (key.isPresent()) {
      attributes.add(key.get());
    }
    attributes.retainAll(featureFlag.getWhitelistedList());
    if (!attributes.isEmpty()) {
      return true;
    }

    if (key.isPresent()) {
      final String toHash = String.format("%d%s%s", baseClient.getAccountId(), feature, key.get());
      return getUserPct(toHash) < featureFlag.getPct();
    }

    return featureFlag.getPct() > randomProvider.random();
  }

  double getUserPct(String toHash) {
    final HashCode hashCode = hash.hashBytes(toHash.getBytes());
    return pct(hashCode.asInt());
  }

  private double pct(int signedInt) {
    long y = signedInt & 0x00000000ffffffffL;
    return y / (double) (UNSIGNED_INT_MAX);
  }

  public FeatureFlagClient setRandomProvider(RandomProviderIF randomProvider) {
    this.randomProvider = randomProvider;
    return this;
  }

}
