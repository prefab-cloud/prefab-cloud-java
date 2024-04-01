package cloud.prefab.client.util;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * this was almost completely generated by Claude Opus
 * the built-in Duration doesn't allow fractional parts other than the seconds field while the ISO spec allows the smallest present unit to have decimals
 */
public class BetterDurationParser {

  public static Duration parse(String durationString) {
    long seconds = 0;
    int nanos = 0;

    Pattern pattern = Pattern.compile(
      "P((?<year>\\d+)(?<yearFraction>\\.\\d+)?Y)?((?<month>\\d+)(?<monthFraction>\\.\\d+)?M)?((?<day>\\d+)(?<dayFraction>\\.\\d+)?D)?(T((?<hour>\\d+)(?<hourFraction>\\.\\d+)?H)?((?<minute>\\d+)(?<minuteFraction>\\.\\d+)?M)?((?<second>\\d+)(?<secondFraction>\\.\\d+)?S)?)?"
    );
    Matcher matcher = pattern.matcher(durationString);

    if (matcher.matches()) {
      // Extract whole number and decimal parts for each unit
      String yearStr = matcher.group("year");
      String yearFractionStr = matcher.group("yearFraction");
      String monthStr = matcher.group("month");
      String monthFractionStr = matcher.group("monthFraction");
      String dayStr = matcher.group("day");
      String dayFractionStr = matcher.group("dayFraction");
      String hourStr = matcher.group("hour");
      String hourFractionStr = matcher.group("hourFraction");
      String minuteStr = matcher.group("minute");
      String minuteFractionStr = matcher.group("minuteFraction");
      String secondStr = matcher.group("second");
      String secondFractionStr = matcher.group("secondFraction");

      // Convert whole number and decimal parts to seconds and nanoseconds
      if (yearStr != null) {
        seconds += Long.parseLong(yearStr) * 31536000;
        if (yearFractionStr != null) {
          double yearFraction = Double.parseDouble(yearFractionStr);
          seconds += (long) (yearFraction * 31536000);
          nanos +=
            (int) (
              (yearFraction * 31536000 - (long) (yearFraction * 31536000)) * 1_000_000_000
            );
        }
      }
      if (monthStr != null) {
        seconds += Long.parseLong(monthStr) * 2592000;
        if (monthFractionStr != null) {
          double monthFraction = Double.parseDouble(monthFractionStr);
          seconds += (long) (monthFraction * 2592000);
          nanos +=
            (int) (
              (monthFraction * 2592000 - (long) (monthFraction * 2592000)) * 1_000_000_000
            );
        }
      }
      if (dayStr != null) {
        seconds += Long.parseLong(dayStr) * 86400;
        if (dayFractionStr != null) {
          double dayFraction = Double.parseDouble(dayFractionStr);
          seconds += (long) (dayFraction * 86400);
          nanos +=
            (int) ((dayFraction * 86400 - (long) (dayFraction * 86400)) * 1_000_000_000);
        }
      }
      if (hourStr != null) {
        seconds += Long.parseLong(hourStr) * 3600;
        if (hourFractionStr != null) {
          double hourFraction = Double.parseDouble(hourFractionStr);
          seconds += (long) (hourFraction * 3600);
          nanos +=
            (int) ((hourFraction * 3600 - (long) (hourFraction * 3600)) * 1_000_000_000);
        }
      }
      if (minuteStr != null) {
        seconds += Long.parseLong(minuteStr) * 60;
        if (minuteFractionStr != null) {
          double minuteFraction = Double.parseDouble(minuteFractionStr);
          seconds += (long) (minuteFraction * 60);
          nanos +=
            (int) ((minuteFraction * 60 - (long) (minuteFraction * 60)) * 1_000_000_000);
        }
      }
      if (secondStr != null) {
        seconds += Long.parseLong(secondStr);
        if (secondFractionStr != null) {
          double secondFraction = Double.parseDouble(secondFractionStr);
          nanos += (int) (secondFraction * 1_000_000_000);
        }
      }
    }

    // Normalize nanoseconds to be within the valid range
    seconds += nanos / 1_000_000_000;
    nanos %= 1_000_000_000;

    return Duration.ofSeconds(seconds, nanos);
  }
}
