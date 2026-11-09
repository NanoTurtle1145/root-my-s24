#!/system/bin/sh
exec >/data/local/tmp/w/umh-kdp.log 2>&1
echo "start uid=$(id -u) ctx=$(cat /proc/self/attr/current 2>/dev/null) enforce=$(getenforce 2>/dev/null)"
echo "before: module=$(grep -c kernelsu /proc/modules 2>/dev/null)"
unshare -m sh -c '
  mount --bind /data/local/tmp/ksud-s25u-kdp /system/bin/logcat || { echo mount_failed; exit 11; }
  echo "bind mounted kdp"
  /system/bin/logcat --version
  if [ -f /data/local/tmp/w/disable-modules ]; then
      for d in /data/adb/modules /data/adb/modules_update; do
          if [ -d "$d" ] && [ ! -e "$d.rms24bak" ]; then
              mv "$d" "$d.rms24bak" && echo "modules disabled: $d"
          fi
      done
  fi
  /system/bin/logcat late-load --ephemeral --allow-shell --package-name me.weishu.kernelsu
  echo "late_rc=$?"
  for d in /data/adb/modules /data/adb/modules_update; do
      if [ -d "$d.rms24bak" ]; then
          if [ -d "$d" ]; then
              rmdir "$d" 2>/dev/null || true
          fi
          if [ ! -e "$d" ]; then
              mv "$d.rms24bak" "$d" && echo "modules restored: $d"
          else
              echo "modules restore skipped: $d exists, backup kept at $d.rms24bak"
          fi
      fi
  done
'
echo "outer_rc=$?"
echo "after: module=$(grep -c kernelsu /proc/modules 2>/dev/null)"
grep -i kernelsu /proc/modules 2>/dev/null
ls -l /data/adb/ksud /data/adb/ksu/bin/ksud 2>&1
