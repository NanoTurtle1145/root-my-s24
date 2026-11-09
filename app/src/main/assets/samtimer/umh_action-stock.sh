#!/system/bin/sh
exec >/data/local/tmp/w/umh-kdp.log 2>&1
echo "start uid=$(id -u) ctx=$(cat /proc/self/attr/current 2>/dev/null) enforce=$(getenforce 2>/dev/null)"
echo "before: module=$(grep -c kernelsu /proc/modules 2>/dev/null)"
unshare -m sh -c '
  mount --bind /data/local/tmp/ksud-s25u-kdp /system/bin/logcat || { echo mount_failed; exit 11; }
  echo "bind mounted kdp"
  /system/bin/logcat --version
  # 3.3.0 的 stock ksud 不支持 --ephemeral：它会把管理器拉起来（am start）。
  # 这里只用假 am 挡掉"自动打开管理器"；**不再移走 /data/adb/modules** ——
  # 上一版移走 modules 会跳过 KSU 的 module stage，导致 Vector/Zygisk 本开机内
  # 永远不加载（用户实测：3.2.5 路径软重启可以加载 vector，3.3.0 不行）。
  # 代价：stage 会照常跑，Vector/Zygisk 自己重启 system_server（一次框架重启）。
  mkdir -p /data/local/tmp/w/nowam
  printf "#!/system/bin/sh\nexit 0\n" > /data/local/tmp/w/nowam/am
  chmod 755 /data/local/tmp/w/nowam/am
  export PATH=/data/local/tmp/w/nowam:/system/bin:/system/xbin
  if [ -f /data/local/tmp/w/disable-modules ]; then
      for d in /data/adb/modules /data/adb/modules_update; do
          if [ -d "$d" ] && [ ! -e "$d.rms24bak" ]; then
              mv "$d" "$d.rms24bak" && echo "modules disabled: $d"
          fi
      done
  fi
  /system/bin/logcat late-load --allow-shell --package-name me.weishu.kernelsu
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
dmesg 2>/dev/null | grep -iE "kernelsu|ksud|module|vermagic|kdp|rkp|defex" | tail -40 > /data/local/tmp/w/dmesg-lateload.log 2>&1
echo "dmesg saved"
