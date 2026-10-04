/*
 * 单块 AES-256-ECB 解密（FIPS 197）。
 *
 * 只用于一件事：DirtyFrag 要构造一个 IV，使内核解密后的明文正好等于我们
 * 想写进文件的内容。ESP 的 CBC 解密是
 *     plaintext = AES_DEC(key, C_old) XOR IV
 * 其中 C_old 是目标页当前的 16 字节，所以
 *     IV = AES_DEC(key, C_old) XOR C_new
 * 因此只需要「单块解密」，不需要加密方向，也不需要 CBC 链。
 */
#ifndef DFR_AES256_H
#define DFR_AES256_H

#include <stdint.h>

void aes256_ecb_decrypt(const uint8_t key[32], const uint8_t in[16], uint8_t out[16]);

#endif /* DFR_AES256_H */
