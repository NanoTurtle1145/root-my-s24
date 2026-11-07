#!/system/bin/sh
exec >/data/local/tmp/w/umh-kdp.log 2>&1
echo "start uid=$(id -u) ctx=$(cat /proc/self/attr/current 2>/dev/null) enforce=$(getenforce 2>/dev/null)"
echo "before: module=$(grep -c kernelsu /proc/modules 2>/dev/null)"
ls -ld /data/adb/modules 2>/dev/null
unshare -m sh -c '
  mount --bind /data/local/tmp/ksud-s25u-kdp /system/bin/logcat || { echo mount_failed; exit 11; }
  echo "bind mounted kdp"
  /system/bin/logcat --version
  # 3.3.0 的 stock ksud 不支持 --ephemeral：它会把管理器拉起来，还会跑 module stage
  # （zygisk 类模块重拉 zygote → 软重启）。这里把 manager 启动和模块脚本都拦掉，
  # 尽量让 3.3.0 也变成\"只加载模块\"。
  mkdir -p /data/local/tmp/w/nowam
  printf "#!/system/bin/sh\nexit 0\n" > /data/local/tmp/w/nowam/am
  chmod 755 /data/local/tmp/w/nowam/am
  if [ -d /data/adb/modules ] && [ ! -e /data/adb/modules.rms24bak ]; then
      mv /data/adb/modules /data/adb/modules.rms24bak && echo "modules moved away"
  fi
  export PATH=/data/local/tmp/w/nowam:/system/bin:/system/xbin
  /system/bin/logcat late-load --allow-shell --package-name me.weishu.kernelsu
  echo "late_rc=$?"
  if [ -d /data/adb/modules.rms24bak ] && [ ! -e /data/adb/modules ]; then
      mv /data/adb/modules.rms24bak /data/adb/modules && echo "modules restored"
  fi
'
echo "outer_rc=$?"
echo "after: module=$(grep -c kernelsu /proc/modules 2>/dev/null)"
grep -i kernelsu /proc/modules 2>/dev/null
ls -l /data/adb/ksud /data/adb/ksu/bin/ksud 2>&1
dmesg 2>/dev/null | grep -iE "kernelsu|ksud|module|vermagic|kdp|rkp|defex" | tail -40 > /data/local/tmp/w/dmesg-lateload.log 2>&1
echo "dmesg saved"
