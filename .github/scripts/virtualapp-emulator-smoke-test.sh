#!/usr/bin/env bash
set -eu

echo "Downloaded APK files:"
find candidate-apks -type f -name '*.apk' -print

# Prefer the emulator's native x86_64 APK to avoid the larger multi-ABI universal APK.
HOST_APK="$(find candidate-apks -type f -iname '*VirtualApp*x86_64.apk' -print -quit || true)"
if [ -z "$HOST_APK" ]; then
  HOST_APK="$(find candidate-apks -type f -iname '*VirtualApp*universal.apk' -print -quit || true)"
fi
if [ -z "$HOST_APK" ]; then
  HOST_APK="$(find candidate-apks/candidate -type f -name '*.apk' -print -quit 2>/dev/null || true)"
fi
GUEST_APK="$(find candidate-apks -type f -path '*guest-test*' -name '*.apk' -print -quit || true)"
if [ -z "$GUEST_APK" ]; then
  GUEST_APK="$(find candidate-apks -type f -name 'app-debug.apk' -print -quit || true)"
fi
if [ -z "$HOST_APK" ] || [ -z "$GUEST_APK" ]; then
  echo "Could not locate both APKs in the downloaded artifact."
  exit 1
fi

# Disable the emulator's package verifier, then push the APK and invoke pm install directly.
# The adb install wrapper timed out during Android 14 integrity verification in prior runs.
adb shell settings put global package_verifier_enable 0 || true
adb shell settings put global verifier_verify_adb_installs 0 || true
echo "package_verifier_enable=$(adb shell settings get global package_verifier_enable | tr -d '\\r')"
echo "verifier_verify_adb_installs=$(adb shell settings get global verifier_verify_adb_installs | tr -d '\\r')"
install_apk() {
  local apk_path="$1"
  local remote_path="/data/local/tmp/$(basename "$apk_path")"
  adb push "$apk_path" "$remote_path"
  adb shell pm install -r "$remote_path"
}
install_apk "$HOST_APK"
adb shell monkey -p io.va.exposed64 -c android.intent.category.LAUNCHER 1
sleep 15
adb logcat -d -t 5000 > host-logcat.txt
if grep -E 'FATAL EXCEPTION.*io\\.va\\.exposed64|Process: io\\.va\\.exposed64' host-logcat.txt; then
  echo "Candidate host app reported a fatal Java crash during startup."
  exit 1
fi
echo "Host APK installed and launch command completed without a detected fatal crash."

if [ "${API_LEVEL}" = "34" ]; then
  echo "Installing harmless guest normally first to establish its system-instance marker."
  install_apk "$GUEST_APK"
  adb shell monkey -p com.example.guestisolationtest -c android.intent.category.LAUNCHER 1
  sleep 3
  adb shell uiautomator dump /data/local/tmp/system-window.xml
  adb exec-out cat /data/local/tmp/system-window.xml > system-window.xml
  test -s system-window.xml
  python3 - <<'PY'
import re
s = open("system-window.xml", encoding="utf-8").read()
m = re.search(r'INSTANCE-[0-9a-fA-F-]{36}', s)
if not m:
    raise SystemExit("Could not read the normal guest app instance marker from emulator UI.")
open("system-marker.txt", "w").write(m.group(0))
print("Normal Android instance marker captured.")
PY
  adb shell mkdir -p /sdcard/Download
  adb push "$GUEST_APK" /sdcard/Download/GuestIsolationTest.apk
  echo "Opening candidate APK-import screen."
  adb shell am start -W -a android.intent.action.VIEW -d file:///sdcard/Download/GuestIsolationTest.apk -t application/vnd.android.package-archive -n io.va.exposed64/vxp.installer
  python3 - <<'PY'
import re, subprocess, time, xml.etree.ElementTree as ET
def dump():
    result = subprocess.run(["adb","shell","uiautomator","dump","/data/local/tmp/guest-window.xml"], capture_output=True, text=True, timeout=30)
    if result.returncode != 0:
        raise RuntimeError("uiautomator dump failed: " + result.stderr[-500:])
    with open("guest-window.xml", "w", encoding="utf-8") as out:
        subprocess.run(["adb","exec-out","cat","/data/local/tmp/guest-window.xml"], check=True, stdout=out, timeout=30)
    if not open("guest-window.xml", encoding="utf-8").read().strip():
        raise RuntimeError("uiautomator produced an empty UI hierarchy")
    raw = open("guest-window.xml", encoding="utf-8").read()
    return ET.fromstring(raw)
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
                        x = (nums[0] + nums[2]) // 2
                        y = (nums[1] + nums[3]) // 2
                        subprocess.run(["adb","shell","input","tap",str(x),str(y)], check=True, timeout=10)
                        print("Tapped UI control:", label)
                        return
        except Exception:
            pass
        time.sleep(2)
    raise SystemExit("Timed out waiting for expected candidate UI control.")
tap_text(lambda s: s.strip() == "install")
time.sleep(15)
tap_text(lambda s: "install complete and open" in s or ("complete" in s and "open" in s))
time.sleep(8)
root = dump()
xml = ET.tostring(root, encoding="unicode")
if "GUEST_ISOLATION_TEST" not in xml:
    raise SystemExit("Candidate did not display guest test screen after import/open.")
m = re.search(r'INSTANCE-[0-9a-fA-F-]{36}', xml)
if not m:
    raise SystemExit("Guest screen opened but virtual instance marker could not be read.")
virtual_marker = m.group(0)
system_marker = open("system-marker.txt").read().strip()
print("System marker:", system_marker)
print("Virtual marker:", virtual_marker)
if virtual_marker == system_marker:
    raise SystemExit("FAIL: guest appears to share normal Android marker; isolation not demonstrated.")
open("virtual-marker.txt", "w").write(virtual_marker)
print("PASS: guest opened in candidate and local marker differs from normal Android instance.")
PY
else
  echo "Guest import/data-isolation test is limited to API 34 in this run."
fi
