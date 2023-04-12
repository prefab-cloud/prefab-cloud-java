package cloud.prefab.client.internal;

class RetryDelayCalculator {

  static long exponentialMillisToNextTry(
    int errorCount,
    long delayIntervalMillis,
    long maxDelayMillis
  ) {
    if (errorCount == 0) {
      return 0;
    }
    double exp = Math.pow(2, errorCount - 1);
    long result = Math.round(delayIntervalMillis * exp);
    if (result > maxDelayMillis) {
      return maxDelayMillis;
    }
    return result;
  }
}
