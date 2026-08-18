# Blood-pressure OCR benchmark

The source-of-truth dataset is `app/src/test/pressure/`; expected values are read from `test_pressures.csv` using the existing `name,sys,dia,pul` columns. Its identifier is `pressure-v1`.

Field accuracy is the number of exact SYS, DIA, and Pulse integer matches divided by all 15 expected fields. CR-003 requires at least 13/15 (86.7%), non-empty recognizer output for all five images, and usable SYS+DIA candidates for all five images.

Run on an attached Android device or emulator:

```bash
./gradlew connectedDebugAndroidTest
adb logcat -d -s PressureOcrBenchmark:I '*:S'
```

The instrumented verifier uses the same bundled Latin ML Kit input path and production parser. It also reports an experimental Google Play Services unbundled Chinese recognizer comparison; the verifier explicitly installs and confirms that optional module before measuring it, so model-download waits are not counted as OCR failures. This comparison dependency is androidTest-only and is not packaged in the production application.

`benchmark_history.csv` is append-only. Its columns are: evaluation date; authoritative implementation Git commit; version/tag when available; dataset identifier; exact correct and total field counts; percentage accuracy; images with three exact fields; images producing non-null SYS+DIA candidates; and a short change note.
