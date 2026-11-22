#!/usr/bin/env bash
# Runs instrumented tests and independently validates the produced encrypted PDF with qpdf.
adb logcat -c || true
adb shell run-as com.iman.pdf rm -f files/protection-regression-output.pdf files/protection-regression-unprotected.pdf 2>/dev/null || true
gradle connectedDebugAndroidTest --stacktrace
status=$?
if [ "$status" -eq 0 ]; then
  pdf_file="$(mktemp --suffix=.pdf)"
  if ! adb exec-out run-as com.iman.pdf cat files/protection-regression-output.pdf > "$pdf_file"; then
    echo "Could not retrieve the PDF protection regression fixture."
    status=1
  else
    pdf_test_password='Test,="pass\word!42'
    if ! qpdf --check --password="$pdf_test_password" "$pdf_file"; then
      echo "qpdf rejected the PDF produced by the protection feature."
      status=1
    elif qpdf --check --password='wrong-password' "$pdf_file" >/dev/null 2>&1; then
      echo "The protected PDF incorrectly accepted a wrong password."
      status=1
    else
      echo "Independent qpdf encryption and password checks passed."
    fi
  fi
  rm -f "$pdf_file"

  plain_pdf="$(mktemp --suffix=.pdf)"
  if ! adb exec-out run-as com.iman.pdf cat files/protection-regression-unprotected.pdf > "$plain_pdf"; then
    echo "Could not retrieve the password-removal regression fixture."
    status=1
  elif ! qpdf --check "$plain_pdf"; then
    echo "qpdf rejected the unprotected PDF produced by password removal."
    status=1
  else
    echo "Independent qpdf validation of the unprotected PDF passed."
  fi
  rm -f "$plain_pdf"
  adb shell run-as com.iman.pdf rm -f files/protection-regression-output.pdf files/protection-regression-unprotected.pdf 2>/dev/null || true
fi
if [ "$status" -ne 0 ]; then
  echo "===== CRASH BUFFER ====="
  adb logcat -d -b crash | tail -100
  echo "===== APP / RUNTIME LOG ====="
  adb logcat -d | grep -E "AndroidRuntime|FATAL EXCEPTION|Process crashed|com\.iman\.pdf|TestRunner|Instrumentation" | tail -150
fi
exit "$status"
