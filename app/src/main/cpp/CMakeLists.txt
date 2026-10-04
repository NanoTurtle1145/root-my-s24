# DirtyFrag 引擎的 native 部分。
#
# 为什么这一段必须是 native：DirtyFrag 的支点是「自己指定 xfrm SA 的全部字段」——
# SPI、算法、alg_trunc_len（ICV 长度）、以及把一页**文件页缓存**挂进 UDP skb。
# Android 的 IpSecManager 是给 VPN 设计的，SA 与这些参数全由框架托管，
# 无法指定；而 splice/vmsplice 也没有公开的 Java API。
# 这些能力只有通过 netlink（NETLINK_XFRM）与 splice(2) 才能拿到。
cmake_minimum_required(VERSION 3.22.1)
project(dfr C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -Wall -Wextra -Wno-unused-parameter -fvisibility=hidden")

add_library(dfr SHARED
    dfr_jni.c
    dfr_xfrm.c
    dfr_sha256.c
    aes256.c
    dfr_splice.c
)

find_library(log-lib log)
target_link_libraries(dfr ${log-lib})
