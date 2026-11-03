#!/usr/bin/env bash
# Runs the instrumented tests; on failure prints the crash log so the cause is readable in the Actions log.
adb logcat -c || true
gradle connectedDebugAndroidTest --stacktrace
status=$?
if [ "$status" -ne 0 ]; then
  echo "===== CRASH BUFFER ====="
  adb logcat -d -b crash | tail -100
  echo "===== APP / RUNTIME LOG ====="
  adb logcat -d | grep -E "AndroidRuntime|FATAL EXCEPTION|Process crashed|com\.iman\.pdf|TestRunner|Instrumentation" | tail -150
fi
exit "$status"
