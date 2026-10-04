/*
 * 直接用 netlink 管理 xfrm SA（而不是 Android 的 IpSecManager）。
 *
 * 为什么不用 IpSecManager：DirtyFrag 需要自己指定 SA 的三个字段 ——
 *   * SPI（包里的 SPI 必须与 SA 一致，否则内核 xfrm_state_lookup 查不到）；
 *   * alg_trunc_len（决定随包 ICV 的字节数，即 authsize）；
 *   * UDP 封装端口（encap_sport/dport）。
 * IpSecManager 把这些全托管了，且不暴露回读入口（IpSecTransform 连 getSpi 都没有）。
 * 实测用它建出来的 SA 会导致 XfrmInNoStates / XfrmInStateProtoError，
 * 所以这里直接走公开的内核 xfrm 接口（<linux/xfrm.h>）。
 */
#ifndef DFR_XFRM_H
#define DFR_XFRM_H

#include <stddef.h>
#include <stdint.h>

/** 建 SA 的结果：0 成功，负值为 -errno。 */
int dfr_sa_add(uint32_t spi,
               const uint8_t *crypt_key, size_t crypt_key_len,
               const uint8_t *auth_key, size_t auth_key_len,
               int auth_trunc_bits,
               uint16_t encap_port);

/** 删 SA（重复删除不算错）。返回 0 或 -errno。 */
int dfr_sa_del(uint32_t spi);

/**
 * 开一个绑定在 127.0.0.1:port 的 UDP socket 并声明 UDP_ENCAP=ESPINUDP。
 * 内核收到该端口的 ESP-in-UDP 包时会走 esp4 入站路径。
 * @return fd，或负值 -errno。
 */
int dfr_open_encap_socket(uint16_t port);

#endif /* DFR_XFRM_H */
