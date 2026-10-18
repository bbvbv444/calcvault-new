#!/usr/bin/env bash
set -eu

echo "Downloaded APK files:"
find candidate-apks -type f -name '*.apk' -print

HOST_APK="$(find candidate-apks -type f -iname '*VirtualApp*x86_64.apk' -print -quit || true)"
[ -n "$HOST_APK" ] || HOST_APK="$(find candidate-apks -type f -iname '*VirtualApp*universal.apk' -print -quit || true)"
GUEST_APK="$(find candidate-apks -type f -path '*guest-test*' -name '*.apk' -print -quit || true)"
[ -n "$GUEST_APK" ] || GUEST_APK="$(find candidate-apks -type f -name 'app-debug.apk' -print -quit || true)"
if [ -z "$HOST_APK" ] || [ -z "$GUEST_APK" ]; then
  echo "Could not locate both APKs in the downloaded artifact."
  exit 1
fi

adb shell settings put global package_verifier_enable 0 || true
adb shell settings put global verifier_verify_adb_installs 0 || true
install_apk() {
  local apk_path="$1"
  local remote_path="/data/local/tmp/$(basename "$apk_path")"
  adb push "$apk_path" "$remote_path"
  if ! adb shell pm install -r "$remote_path"; then
    echo "APK installation failed; collecting diagnostics."
    adb logcat -b all -d -t 10000 2>&1 | grep -Ei 'FATAL EXCEPTION|system_server|PackageManager|PackageInstaller|Watchdog|Fatal signal|lmkd|low memory|avc: denied' | tail -n 250 || true
    return 1
  fi
}
read_marker_from_logcat() {
  local destination="$1"
  local attempt=0
  while [ "$attempt" -lt 12 ]; do
    adb logcat -d -s GuestIsolationTest:I '*:S' 2>/dev/null | grep -oE 'INSTANCE_MARKER=INSTANCE-[0-9a-fA-F-]{36}' | tail -n 1 | sed 's/.*INSTANCE_MARKER=//' > "$destination" || true
    if grep -qE '^INSTANCE-[0-9a-fA-F-]{36}$' "$destination"; then
      cat "$destination"
      return 0
    fi
    attempt=$((attempt + 1))
    sleep 2
  done
  echo "Could not capture GuestIsolationTest instance marker from logcat." >&2
  adb logcat -d -s GuestIsolationTest:I '*:S' >&2 || true
  return 1
}

install_apk "$HOST_APK"
adb shell monkey -p io.va.exposed64 -c android.intent.category.LAUNCHER 1
sleep 12
adb logcat -d -t 5000 > host-logcat.txt
if grep -E 'FATAL EXCEPTION.*io\.va\.exposed64|Process: io\.va\.exposed64' host-logcat.txt; then
  echo "Candidate host app reported a fatal Java crash during startup."
  exit 1
fi
echo "Host APK installed and launch command completed without a detected fatal crash."

if [ "${API_LEVEL}" = "34" ]; then
  echo "Installing harmless guest normally first to establish its system-instance marker."
  install_apk "$GUEST_APK"
  adb logcat -c
  adb shell monkey -p com.example.guestisolationtest -c android.intent.category.LAUNCHER 1
  sleep 4
  read_marker_from_logcat system-marker.txt

  adb shell mkdir -p /sdcard/Download
  adb push "$GUEST_APK" /sdcard/Download/GuestIsolationTest.apk
  echo "Opening candidate APK-import screen."
  adb shell am start -W -a android.intent.action.VIEW -d file:///sdcard/Download/GuestIsolationTest.apk -t application/vnd.android.package-archive -n io.va.exposed64/vxp.installer

  python3 - <<'PY'
import re, subprocess, time, xml.etree.ElementTree as ET

def dump():
    last = ""
    for _ in range(8):
        result = subprocess.run(["adb","shell","uiautomator","dump","/data/local/tmp/guest-window.xml"], capture_output=True, text=True, timeout=30)
        last = result.stdout + result.stderr
        if result.returncode == 0 and "null root node" not in last.lower():
            try:
                raw = subprocess.check_output(["adb","exec-out","cat","/data/local/tmp/guest-window.xml"], timeout=30).decode("utf-8")
                if raw.strip():
                    return ET.fromstring(raw)
            except Exception as exc:
                last += str(exc)
        time.sleep(2)
    raise RuntimeError("Android UI hierarchy unavailable after retries: " + last[-500:])

def tap_text(predicate, timeout=120):
    end = time.time() + timeout
    while time.time() < end:
        try:
            root = dump()
            for node in root.iter("node"):
                label = (node.attrib.get("text","") + " " + node.attrib.get("content-desc","")).strip().lower()
                if predicate(label):
                    nums = list(map(int, re.findall(r"\d+", node.attrib.get("bounds",""))))
                    if len(nums) == 4:
                        x, y = (nums[0] + nums[2]) // 2, (nums[1] + nums[3]) // 2
                        subprocess.run(["adb","shell","input","tap",str(x),str(y)], check=True, timeout=10)
                        print("Tapped UI control:", label)
                        return
        except Exception as exc:
            print("Waiting for candidate UI:", str(exc)[-180:])
        time.sleep(2)
    raise SystemExit("Timed out waiting for expected candidate UI control.")

tap_text(lambda s: s.strip() == "install")
time.sleep(12)
tap_text(lambda s: "install complete and open" in s or ("complete" in s and "open" in s))
time.sleep(8)
# Save UI evidence for diagnosis, but use the app's log marker as the source of truth.
try:
    root = dump()
    xml = ET.tostring(root, encoding="unicode")
    open("guest-screen-ui.xml","w",encoding="utf-8").write(xml)
    if "GUEST_ISOLATION_TEST" not in xml:
        print("Guest screen text not exposed in UI hierarchy; checking process/log marker too.")
except Exception as exc:
    print("Could not read guest UI hierarchy after launch:", exc)
PY

  read_marker_from_logcat virtual-marker.txt
  SYSTEM_MARKER="$(cat system-marker.txt)"
  VIRTUAL_MARKER="$(cat virtual-marker.txt)"
  echo "System marker: $SYSTEM_MARKER"
  echo "Virtual marker: $VIRTUAL_MARKER"
  if [ "$SYSTEM_MARKER" = "$VIRTUAL_MARKER" ]; then
    echo "FAIL: guest appears to share normal Android marker; isolation not demonstrated."
    exit 1
  fi
  echo "PASS: a guest marker was logged through the candidate, and differs from the normal Android instance."
else
  echo "Guest import/data-isolation test is limited to API 34 in this run."
fi
