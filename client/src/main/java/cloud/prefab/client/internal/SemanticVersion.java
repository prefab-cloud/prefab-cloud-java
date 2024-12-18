package cloud.prefab.client.internal;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

public class SemanticVersion implements Comparable<SemanticVersion> {

  private static final Pattern SEMVER_PATTERN = Pattern.compile(
    "^(?<major>0|[1-9]\\d*)\\.(?<minor>0|[1-9]\\d*)\\.(?<patch>0|[1-9]\\d*)" +
    "(?:-(?<prerelease>(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)" +
    "(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?" +
    "(?:\\+(?<buildmetadata>[0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?$"
  );

  private final int major;
  private final int minor;
  private final int patch;
  private final String prerelease;
  private final String buildMetadata;

  private SemanticVersion(
    int major,
    int minor,
    int patch,
    String prerelease,
    String buildMetadata
  ) {
    this.major = major;
    this.minor = minor;
    this.patch = patch;
    this.prerelease = prerelease;
    this.buildMetadata = buildMetadata;
  }

  public static @Nullable SemanticVersion parseQuietly(String version) {
    try {
      return parse(version);
    } catch (Exception e) {
      return null;
    }
  }

  public static SemanticVersion parse(String version) {
    Objects.requireNonNull(version, "Version string cannot be null");

    Matcher matcher = SEMVER_PATTERN.matcher(version);
    if (!matcher.matches()) {
      throw new IllegalArgumentException("Invalid semantic version format: " + version);
    }

    int major = Integer.parseInt(matcher.group("major"));
    int minor = Integer.parseInt(matcher.group("minor"));
    int patch = Integer.parseInt(matcher.group("patch"));
    String prerelease = matcher.group("prerelease");
    String buildMetadata = matcher.group("buildmetadata");

    return new SemanticVersion(major, minor, patch, prerelease, buildMetadata);
  }

  public int getMajor() {
    return major;
  }

  public int getMinor() {
    return minor;
  }

  public int getPatch() {
    return patch;
  }

  public Optional<String> getPrerelease() {
    return Optional.ofNullable(prerelease);
  }

  public Optional<String> getBuildMetadata() {
    return Optional.ofNullable(buildMetadata);
  }

  private static boolean isNumeric(String str) {
    try {
      Integer.parseInt(str);
      return true;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  private static int comparePreReleaseIdentifiers(String id1, String id2) {
    // If both are numeric, compare numerically
    if (isNumeric(id1) && isNumeric(id2)) {
      int num1 = Integer.parseInt(id1);
      int num2 = Integer.parseInt(id2);
      return Integer.compare(num1, num2);
    }

    // If only one is numeric, numeric ones have lower precedence
    if (isNumeric(id1)) {
      return -1;
    }
    if (isNumeric(id2)) {
      return 1;
    }

    // Neither is numeric, compare as strings
    return id1.compareTo(id2);
  }

  private static int comparePreRelease(@Nullable String pre1, @Nullable String pre2) {
    // If both are null (or empty), they're equal
    if (pre1 == null && pre2 == null) {
      return 0;
    }

    // A version without prerelease has higher precedence
    if (pre1 == null) {
      return 1;
    }
    if (pre2 == null) {
      return -1;
    }

    // Split into identifiers
    String[] ids1 = pre1.split("\\.");
    String[] ids2 = pre2.split("\\.");

    // Compare each identifier until we find a difference
    int minLength = Math.min(ids1.length, ids2.length);
    for (int i = 0; i < minLength; i++) {
      int result = comparePreReleaseIdentifiers(ids1[i], ids2[i]);
      if (result != 0) {
        return result;
      }
    }

    // If all identifiers match up to the length of the shorter one,
    // the longer one has higher precedence
    return Integer.compare(ids1.length, ids2.length);
  }

  @Override
  public int compareTo(SemanticVersion other) {
    int result = Integer.compare(this.major, other.major);
    if (result != 0) {
      return result;
    }

    result = Integer.compare(this.minor, other.minor);
    if (result != 0) {
      return result;
    }

    result = Integer.compare(this.patch, other.patch);
    if (result != 0) {
      return result;
    }

    return comparePreRelease(this.prerelease, other.prerelease);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(major).append('.').append(minor).append('.').append(patch);
    if (prerelease != null) {
      sb.append('-').append(prerelease);
    }
    if (buildMetadata != null) {
      sb.append('+').append(buildMetadata);
    }
    return sb.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof SemanticVersion)) {
      return false;
    }
    SemanticVersion that = (SemanticVersion) o;
    return (
      major == that.major &&
      minor == that.minor &&
      patch == that.patch &&
      Objects.equals(prerelease, that.prerelease)
    );
    // Build metadata is ignored in equality checks
  }

  @Override
  public int hashCode() {
    return Objects.hash(major, minor, patch, prerelease);
    // Build metadata is ignored in hash calculation
  }
}
