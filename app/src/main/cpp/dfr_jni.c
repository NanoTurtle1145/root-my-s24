/*
 * JNI 门面：把 xfrm/splice 这些只有 native 才能做的事暴露给 Kotlin。
 *
 * 状态很轻：引擎的密钥与 ICV 长度是单例使用，设置一次即可；
 * 其余（SPI、seq、fd）由 Kotlin 侧持有并逐次传入，便于上层做自校准与回滚。
 */
#include <errno.h>
#include <fcntl.h>
#include <jni.h>
#include <string.h>
#include <unistd.h>

#include <arpa/inet.h>
#include <netinet/in.h>
#include <sys/socket.h>

#include "aes256.h"
#include "dfr_splice.h"
#include "dfr_xfrm.h"

#define DFR_CLASS "cn/nanoturtle/rootmys9280/manager/rootmy/dirtyfrag/DirtyFragNative"

static uint8_t g_aes_key[32];
static uint8_t g_auth_key[64];
static size_t g_auth_key_len;
static int g_trunc_bits;

static void copy_out(JNIEnv *env, jbyteArray src, uint8_t *dst, size_t max, size_t *out_len) {
    *out_len = 0;
    if (!src) return;
    jsize n = (*env)->GetArrayLength(env, src);
    if (n <= 0) return;
    if ((size_t)n > max) n = (jsize)max;
    (*env)->GetByteArrayRegion(env, src, 0, n, (jbyte *)dst);
    *out_len = (size_t)n;
}

/** 设置密钥与 ICV 截断长度。返回 0 或负 errno。 */
JNIEXPORT jint JNICALL
Java_cn_nanoturtle_rootmys9280_manager_rootmy_dirtyfrag_DirtyFragNative_nativeInit(
        JNIEnv *env, jclass cls, jbyteArray aesKey, jbyteArray authKey, jint truncBits) {
    (void)cls;
    size_t aes_len = 0;
    copy_out(env, aesKey, g_aes_key, sizeof(g_aes_key), &aes_len);
    if (aes_len != 16 && aes_len != 32) return -EINVAL;

    copy_out(env, authKey, g_auth_key, sizeof(g_auth_key), &g_auth_key_len);
    if (g_auth_key_len == 0) return -EINVAL;

    g_trunc_bits = truncBits;
    return 0;
}

/** 建 SA。返回 0 或负 errno。 */
JNIEXPORT jint JNICALL
Java_cn_nanoturtle_rootmys9280_manager_rootmy_dirtyfrag_DirtyFragNative_nativeSaAdd(
        JNIEnv *env, jclass cls, jint spi, jint encapPort) {
    (void)env; (void)cls;
    if (g_auth_key_len == 0) return -EINVAL;
    return dfr_sa_add((uint32_t)spi, g_aes_key, 32, g_auth_key, g_auth_key_len,
                      g_trunc_bits, (uint16_t)encapPort);
}

/** 删 SA。 */
JNIEXPORT jint JNICALL
Java_cn_nanoturtle_rootmys9280_manager_rootmy_dirtyfrag_DirtyFragNative_nativeSaDel(
        JNIEnv *env, jclass cls, jint spi) {
    (void)env; (void)cls;
    return dfr_sa_del((uint32_t)spi);
}

/** 开绑定在 127.0.0.1:port 且声明 UDP_ENCAP 的接收 socket，返回 fd 或负 errno。 */
JNIEXPORT jint JNICALL
Java_cn_nanoturtle_rootmys9280_manager_rootmy_dirtyfrag_DirtyFragNative_nativeOpenEncapSocket(
        JNIEnv *env, jclass cls, jint port) {
    (void)env; (void)cls;
    return dfr_open_encap_socket((uint16_t)port);
}

/** 开一个绑定在回环 srcPort、connect 到回环 dstPort 的普通 UDP 发送 socket。 */
JNIEXPORT jint JNICALL
Java_cn_nanoturtle_rootmys9280_manager_rootmy_dirtyfrag_DirtyFragNative_nativeOpenSenderSocket(
        JNIEnv *env, jclass cls, jint srcPort, jint dstPort) {
    (void)env; (void)cls;
    int fd = socket(AF_INET, SOCK_DGRAM, 0);
    if (fd < 0) return -errno;

    int one = 1;
    setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, &one, sizeof(one));

    struct sockaddr_in src;
    memset(&src, 0, sizeof(src));
    src.sin_family = AF_INET;
    src.sin_port = htons((uint16_t)srcPort);
    src.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
    if (bind(fd, (struct sockaddr *)&src, sizeof(src)) < 0) {
        int e = -errno; close(fd); return e;
    }

    struct sockaddr_in dst;
    memset(&dst, 0, sizeof(dst));
    dst.sin_family = AF_INET;
    dst.sin_port = htons((uint16_t)dstPort);
    dst.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
    if (connect(fd, (struct sockaddr *)&dst, sizeof(dst)) < 0) {
        int e = -errno; close(fd); return e;
    }
    return fd;
}

/**
 * 用 16 字节块改写文件页缓存（原语本体）。
 *
 * 按**路径**而不是 fd：原语只需要只读打开目标文件，让 native 自己 open
 * 可以免掉 Java FileDescriptor → int 的转换（那条路在 Android 上没有公开 API）。
 */
JNIEXPORT jint JNICALL
Java_cn_nanoturtle_rootmys9280_manager_rootmy_dirtyfrag_DirtyFragNative_nativeWriteBlock16(
        JNIEnv *env, jclass cls, jint udpFd, jstring path, jlong offset,
        jint spi, jint seq, jbyteArray desired, jint icvLen) {
    (void)cls;
    if (!desired || !path) return -EINVAL;
    if ((*env)->GetArrayLength(env, desired) != 16) return -EINVAL;
    uint8_t want[16];
    (*env)->GetByteArrayRegion(env, desired, 0, 16, (jbyte *)want);

    const char *cpath = (*env)->GetStringUTFChars(env, path, NULL);
    if (!cpath) return -ENOMEM;

    int fd = open(cpath, O_RDONLY);
    (*env)->ReleaseStringUTFChars(env, path, cpath);
    if (fd < 0) return -errno;

    int rc = dfr_write_block16(udpFd, fd, (off_t)offset,
                               (uint32_t)spi, (uint32_t)seq,
                               g_aes_key, g_auth_key, g_auth_key_len,
                               icvLen, want);
    close(fd);
    return rc;
}

/** 关闭 fd（供 Kotlin 侧统一释放）。 */
JNIEXPORT jint JNICALL
Java_cn_nanoturtle_rootmys9280_manager_rootmy_dirtyfrag_DirtyFragNative_nativeClose(
        JNIEnv *env, jclass cls, jint fd) {
    (void)env; (void)cls;
    if (fd < 0) return 0;
    return close(fd) == 0 ? 0 : -errno;
}
