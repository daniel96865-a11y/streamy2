# Playback diagnostics 3.33

Version 3.33 / code 153 adds an explicitly requested, local Android process-exit report to both existing playback diagnostics dialogs. It preserves the 3.32 playback implementation and leaves both update feeds on 3.30.

The report reads at most ten historical process exits for this package on Android 11+. It distinguishes Android's native-crash, Java-crash, ANR, low-memory, signal and update reasons. It includes timestamps, current app/device version and sampled PSS when present. Entries may predate the current version; missing history does not prove absence of a crash. It does not read raw traces or transmit reports. URLs and common credential fields in session messages are redacted. Reading runs off the main thread; a copy button becomes available when complete. Android 9/10 show the API limitation without accessing API 30 classes.

## Validation

- 45 automated tests passed, including native-crash/ANR history, Android 9 fallback, empty history, redaction and unknown/update/signal handling.
- Release built with minSdk 28 and targetSdk 34; DEX contains the diagnostic reader and excludes the withdrawn 3.31 playback command/watchdog classes.
- Offline upgrade preflight from 3.30, 3.31 and 3.32 passed: package, version, signer lineage and permission ownership.
- ZIP integrity and zipalign passed. Native libraries remain byte-identical to 3.30.
- APK SHA-256: `d281b61ca6f0a36742faf6db3c77d43837721379f546ea53fbef30de1609fcc6`.

These checks do not establish real-device playback stability or the cause of any specific process exit. The APK is a diagnostic build, not an automatic update release.

## Separate playback investigation

Sixteen isolated diagnostic test executions (eight cases at SDK 28 and 34) reproduced seven existing control-flow defects and passed one negative control. These are diagnostic assertions of current defective behavior, not production regression tests asserting desired behavior.

1. `playChannel` calls `hideVlc` and `LiveEngine.stop(true)` synchronously on the main thread. Actual APK DEX and matching libVLC sources confirm the native stop/release path. Native duration was not measured.
2. Vavoo's saved VLC preference is bypassed after a channel change clears `forceEngine`.
3. Vavoo recovery can replay the same failed hot URL repeatedly without incrementing the generic retry budget.
4. A channel change removes Vavoo prefetch without scheduling it again.
5. The resolver timeout leaves its generation valid, so a late result within that generation still starts playback. Existing generation checks reject previous-channel results.
6. Resolution ignores `activeHost` and always tries the fixed host order.
7. Controlled HTTP 403 responses reuse the same cached signature across all attempts; status-aware bounded reauthentication is absent.

Control: the HLS-to-TS fallback URL is valid. All six original APK native libraries match the published libVLC 3.6.0 artifact. No real provider requests, native decoder tests or device latency measurements were performed. These playback defects remain separate work from this diagnostic change.
