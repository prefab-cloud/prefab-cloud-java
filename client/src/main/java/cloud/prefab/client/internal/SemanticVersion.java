package cloud.prefab.client.internal;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    // If one has prerelease and the other doesn't, the one without prerelease is greater
    if (this.prerelease == null && other.prerelease != null) {
      return 1;
    }
    if (this.prerelease != null && other.prerelease == null) {
      return -1;
    }
    if (this.prerelease != null && other.prerelease != null) {
      return this.prerelease.compareTo(other.prerelease);
    }

    return 0; // Build metadata doesn't affect precedence
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
