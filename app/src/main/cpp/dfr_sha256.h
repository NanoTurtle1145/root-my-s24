/*
 * DirtyFrag 引擎用的 SHA-256 与 HMAC-SHA256。
 *
 * 为什么自己实现而不链 OpenSSL：NDK 默认不带 libcrypto，而系统里的
 * /system/lib64/libcrypto.so 版本随机型变化、符号也不保证稳定。
 * 我们只需要一个固定用途的 HMAC-SHA256（ESP 的 ICV），
 * 实现标准算法（FIPS 180-4 / RFC 2104）反而更可控、更小。
 */
#ifndef DFR_SHA256_H
#define DFR_SHA256_H

#include <stddef.h>
#include <stdint.h>

/** HMAC-SHA256：输出恒为 32 字节。 */
void dfr_hmac_sha256(const uint8_t *key, size_t key_len,
                     const uint8_t *msg, size_t msg_len,
                     uint8_t out[32]);

#endif /* DFR_SHA256_H */
