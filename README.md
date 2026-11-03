# OBD Scan (Android, Bluetooth Classic ELM327)

Features: connect to a paired ELM327 adapter, read stored/pending/permanent DTCs, clear codes,
live RPM/speed/coolant/throttle, raw command console, and JSON-profile bidirectional tests.

## Get the APK without a computer
1. Create a GitHub repo and upload the contents of this folder (keep `.github/workflows/build.yml`).
2. Open the repo > Actions > "Build APK" > latest run > download the `obd-scanner-apk` artifact.
3. Unzip, open `app-debug.apk` on your Android phone, allow "install unknown apps" when prompted.

## Using it
1. Plug the adapter into the car, ignition on.
2. Pair it in Android Bluetooth settings (PIN usually 1234 or 0000).
3. Open the app > Connect > tap the adapter.

## Bidirectional tests
Tests live in JSON profiles (see `app/src/main/assets/profiles/template.json`).
Each test has `header` (ATSH), `rx` (ATCRA), `steps` and `stop` commands.
Use Tests > Import profile to load your own file. Only use commands verified for your exact vehicle.
Cars with a security gateway (mostly 2018+) will refuse write commands.

## Limits
Android only (iOS cannot use Bluetooth Classic ELM327). Standard CAN 11-bit IDs by default;
29-bit cars can add `ATCP`/`ATSH` steps in a profile. No DTC description database yet.
