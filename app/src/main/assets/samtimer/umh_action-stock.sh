#!/system/bin/sh
exec >/data/local/tmp/w/umh-kdp.log 2>&1
echo "start uid=$(id -u) ctx=$(cat /proc/self/attr/current 2>/dev/null) enforce=$(getenforce 2>/dev/null)"
echo "before: module=$(grep -c kernelsu /proc/modules 2>/dev/null)"
unshare -m sh -c '
  mount --bind /data/local/tmp/ksud-s25u-kdp /system/bin/logcat || { echo mount_failed; exit 11; }
  echo "bind mounted kdp"
  /system/bin/logcat --version
  /system/bin/logcat late-load --allow-shell --package-name me.weishu.kernelsu
  echo "late_rc=$?"
'
echo "outer_rc=$?"
echo "after: module=$(grep -c kernelsu /proc/modules 2>/dev/null)"
grep -i kernelsu /proc/modules 2>/dev/null
ls -l /data/adb/ksud /data/adb/ksu/bin/ksud 2>&1
