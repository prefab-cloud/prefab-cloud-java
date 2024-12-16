package cloud.prefab.client.internal;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class SemanticVersionTest {

  @Test
  @DisplayName("Should parse valid semantic versions")
  void shouldParseValidVersions() {
    SemanticVersion version = SemanticVersion.parse("1.2.3");

    assertThat(version.getMajor()).isEqualTo(1);
    assertThat(version.getMinor()).isEqualTo(2);
    assertThat(version.getPatch()).isEqualTo(3);
    assertThat(version.getPrerelease()).isEmpty();
    assertThat(version.getBuildMetadata()).isEmpty();
  }

  @Test
  @DisplayName("Should parse versions with prerelease and build metadata")
  void shouldParseVersionsWithPrereleaseAndBuildMetadata() {
    SemanticVersion version = SemanticVersion.parse("2.0.0-alpha.1+build.123");

    assertThat(version.getMajor()).isEqualTo(2);
    assertThat(version.getMinor()).isEqualTo(0);
    assertThat(version.getPatch()).isEqualTo(0);
    assertThat(version.getPrerelease()).hasValue("alpha.1");
    assertThat(version.getBuildMetadata()).hasValue("build.123");
  }

  @ParameterizedTest
  @ValueSource(
    strings = { "1.0", "1", "1.2.3.4", "1.2.3-", "1.2.3+", "01.2.3", "1.02.3", "1.2.03" }
  )
  @DisplayName("Should reject invalid version formats")
  void shouldRejectInvalidVersions(String invalidVersion) {
    assertThatThrownBy(() -> SemanticVersion.parse(invalidVersion))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Invalid semantic version format");
  }

  @Test
  @DisplayName("Should reject null version string")
  void shouldRejectNullVersion() {
    assertThatNullPointerException()
      .isThrownBy(() -> SemanticVersion.parse(null))
      .withMessage("Version string cannot be null");
  }

  @ParameterizedTest
  @CsvSource(
    {
      "1.2.3,     1.2.3,      0",
      "2.0.0,     1.9.9,      1",
      "1.2.3-alpha, 1.2.3,    -1",
      "1.2.3,     1.2.3-beta, 1",
      "2.0.0-alpha, 2.0.0-beta, -1",
      "1.0.0+build.1, 1.0.0+build.2, 0", // build metadata doesn't affect comparison
    }
  )
  @DisplayName("Should correctly compare versions")
  void shouldCompareVersionsCorrectly(
    String version1,
    String version2,
    int expectedComparison
  ) {
    SemanticVersion v1 = SemanticVersion.parse(version1);
    SemanticVersion v2 = SemanticVersion.parse(version2);

    assertThat(v1.compareTo(v2)).isEqualTo(expectedComparison);
  }

  @Test
  @DisplayName("Should implement equals and hashCode correctly")
  void shouldImplementEqualsAndHashCodeCorrectly() {
    SemanticVersion v1 = SemanticVersion.parse("1.2.3-alpha+build.123");
    SemanticVersion v2 = SemanticVersion.parse("1.2.3-alpha+different.build");
    SemanticVersion v3 = SemanticVersion.parse("1.2.3-beta+build.123");

    // Versions with same numbers/prerelease should be equal regardless of build metadata
    assertThat(v1)
      .isEqualTo(v2)
      .hasSameHashCodeAs(v2)
      .isNotEqualTo(v3)
      .isNotEqualTo(null)
      .isNotEqualTo(new Object());
  }

  @Test
  @DisplayName("Should create correct string representation")
  void shouldCreateCorrectStringRepresentation() {
    assertThat(SemanticVersion.parse("1.2.3")).hasToString("1.2.3");
    assertThat(SemanticVersion.parse("1.2.3-alpha")).hasToString("1.2.3-alpha");
    assertThat(SemanticVersion.parse("1.2.3+build.123")).hasToString("1.2.3+build.123");
    assertThat(SemanticVersion.parse("1.2.3-alpha+build.123"))
      .hasToString("1.2.3-alpha+build.123");
  }

  @Test
  @DisplayName("Should handle edge cases")
  void shouldHandleEdgeCases() {
    SemanticVersion v1 = SemanticVersion.parse("0.0.0");
    SemanticVersion v2 = SemanticVersion.parse("999999.999999.999999");
    SemanticVersion v3 = SemanticVersion.parse("1.2.3-alpha.0.beta.0+build-123.456");

    assertThat(v1.getMajor()).isZero();
    assertThat(v1.getMinor()).isZero();
    assertThat(v1.getPatch()).isZero();

    assertThat(v2.getMajor()).isEqualTo(999999);

    assertThat(v3.getPrerelease()).hasValue("alpha.0.beta.0");
    assertThat(v3.getBuildMetadata()).hasValue("build-123.456");
  }
}
