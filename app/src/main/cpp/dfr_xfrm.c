/*
 * netlink xfrm 实现：XFRM_MSG_NEWSA / DELSA + UDP 封装 socket。
 *
 * 全部使用 Linux 公开接口：NETLINK_XFRM、<linux/xfrm.h> 的结构与属性编号、
 * 以及 UDP_ENCAP 这个标准 setsockopt。这里没有任何来自第三方的代码。
 */
#include "dfr_xfrm.h"

#include <errno.h>
#include <string.h>
#include <unistd.h>

#include <arpa/inet.h>
#include <linux/netlink.h>
#include <linux/rtnetlink.h>
#include <linux/udp.h>
#include <linux/xfrm.h>
#include <netinet/in.h>
#include <sys/socket.h>

#define DFR_LOOPBACK "127.0.0.1"
#define DFR_REQID 0x44524600u /* "DFR\0"，仅用于标识本工具建立的 SA */

/** 往 nlmsghdr 尾部追加一个 rtattr（xfrm 用的是 rtattr 而非 nlattr）。 */
static int put_attr(struct nlmsghdr *nlh, size_t maxlen, int type,
                    const void *data, size_t len) {
    size_t alen = RTA_LENGTH(len);
    size_t newlen = NLMSG_ALIGN(nlh->nlmsg_len) + RTA_ALIGN(alen);
    if (newlen > maxlen) return -EMSGSIZE;

    struct rtattr *rta = (struct rtattr *)((char *)nlh + NLMSG_ALIGN(nlh->nlmsg_len));
    rta->rta_type = (unsigned short)type;
    rta->rta_len = (unsigned short)alen;
    if (len) memcpy(RTA_DATA(rta), data, len);
    nlh->nlmsg_len = (unsigned int)newlen;
    return 0;
}

/** 打开一个已 bind 的 NETLINK_XFRM socket。 */
static int xfrm_socket(void) {
    int sk = socket(AF_NETLINK, SOCK_RAW, NETLINK_XFRM);
    if (sk < 0) return -errno;

    struct sockaddr_nl nl;
    memset(&nl, 0, sizeof(nl));
    nl.nl_family = AF_NETLINK;
    if (bind(sk, (struct sockaddr *)&nl, sizeof(nl)) < 0) {
        int e = -errno;
        close(sk);
        return e;
    }
    return sk;
}

/** 发出请求并等待 ACK；返回内核给的 error（0 表示成功）。 */
static int xfrm_talk(int sk, struct nlmsghdr *nlh) {
    struct sockaddr_nl nl;
    memset(&nl, 0, sizeof(nl));
    nl.nl_family = AF_NETLINK;

    if (sendto(sk, nlh, nlh->nlmsg_len, 0, (struct sockaddr *)&nl, sizeof(nl)) < 0) {
        return -errno;
    }

    char rbuf[4096];
    ssize_t n = recv(sk, rbuf, sizeof(rbuf), 0);
    if (n < 0) return -errno;

    for (struct nlmsghdr *h = (struct nlmsghdr *)rbuf; NLMSG_OK(h, (unsigned int)n);
         h = NLMSG_NEXT(h, n)) {
        if (h->nlmsg_type == NLMSG_ERROR) {
            struct nlmsgerr *e = (struct nlmsgerr *)NLMSG_DATA(h);
            return e->error;
        }
    }
    return 0;
}

int dfr_sa_del(uint32_t spi) {
    int sk = xfrm_socket();
    if (sk < 0) return sk;

    char buf[512];
    memset(buf, 0, sizeof(buf));
    struct nlmsghdr *nlh = (struct nlmsghdr *)buf;
    nlh->nlmsg_len = NLMSG_LENGTH(sizeof(struct xfrm_usersa_id));
    nlh->nlmsg_type = XFRM_MSG_DELSA;
    nlh->nlmsg_flags = NLM_F_REQUEST | NLM_F_ACK;
    nlh->nlmsg_seq = 1;

    struct xfrm_usersa_id *id = (struct xfrm_usersa_id *)NLMSG_DATA(nlh);
    id->daddr.a4 = inet_addr(DFR_LOOPBACK);
    id->spi = htonl(spi);
    id->family = AF_INET;
    id->proto = IPPROTO_ESP;

    int r = xfrm_talk(sk, nlh);
    close(sk);
    /* 删不存在的 SA 会返回 -ESRCH/-ENOENT，对调用方不算错。 */
    if (r == -ESRCH || r == -ENOENT) return 0;
    return r;
}

int dfr_sa_add(uint32_t spi,
               const uint8_t *crypt_key, size_t crypt_key_len,
               const uint8_t *auth_key, size_t auth_key_len,
               int auth_trunc_bits,
               uint16_t encap_port) {
    if (crypt_key_len == 0 || crypt_key_len > 32) return -EINVAL;
    if (auth_key_len == 0 || auth_key_len > 64) return -EINVAL;

    int sk = xfrm_socket();
    if (sk < 0) return sk;

    int rc = 0;
    char buf[1024];
    memset(buf, 0, sizeof(buf));

    struct nlmsghdr *nlh = (struct nlmsghdr *)buf;
    nlh->nlmsg_len = NLMSG_LENGTH(sizeof(struct xfrm_usersa_info));
    nlh->nlmsg_type = XFRM_MSG_NEWSA;
    nlh->nlmsg_flags = NLM_F_REQUEST | NLM_F_ACK | NLM_F_CREATE | NLM_F_EXCL;
    nlh->nlmsg_seq = 1;

    struct xfrm_usersa_info *xs = (struct xfrm_usersa_info *)NLMSG_DATA(nlh);
    xs->family = AF_INET;
    xs->saddr.a4 = inet_addr(DFR_LOOPBACK);
    xs->id.daddr.a4 = inet_addr(DFR_LOOPBACK);
    xs->id.spi = htonl(spi);
    xs->id.proto = IPPROTO_ESP;
    xs->mode = XFRM_MODE_TRANSPORT;
    xs->reqid = DFR_REQID;
    xs->replay_window = 0;
    /* 生命周期设为无限：这是本机一次性 SA，不需要过期语义。 */
    xs->lft.soft_byte_limit = XFRM_INF;
    xs->lft.hard_byte_limit = XFRM_INF;
    xs->lft.soft_packet_limit = XFRM_INF;
    xs->lft.hard_packet_limit = XFRM_INF;
    xs->sel.family = AF_INET;
    xs->sel.prefixlen_d = 32;
    xs->sel.prefixlen_s = 32;
    xs->sel.daddr.a4 = inet_addr(DFR_LOOPBACK);
    xs->sel.saddr.a4 = inet_addr(DFR_LOOPBACK);

    /* 认证算法：hmac(sha256)，alg_trunc_len 决定 authsize。 */
    {
        size_t sz = sizeof(struct xfrm_algo_auth) + auth_key_len;
        char ab[sizeof(struct xfrm_algo_auth) + 64];
        if (sz > sizeof(ab)) { rc = -EINVAL; goto out; }
        memset(ab, 0, sizeof(ab));
        struct xfrm_algo_auth *aa = (struct xfrm_algo_auth *)ab;
        strncpy(aa->alg_name, "hmac(sha256)", sizeof(aa->alg_name) - 1);
        aa->alg_key_len = (unsigned int)(auth_key_len * 8);
        aa->alg_trunc_len = (unsigned int)auth_trunc_bits;
        memcpy(aa->alg_key, auth_key, auth_key_len);
        rc = put_attr(nlh, sizeof(buf), XFRMA_ALG_AUTH_TRUNC, ab, sz);
        if (rc) goto out;
    }

    /* 加密算法：cbc(aes)。 */
    {
        size_t sz = sizeof(struct xfrm_algo) + crypt_key_len;
        char eb[sizeof(struct xfrm_algo) + 32];
        if (sz > sizeof(eb)) { rc = -EINVAL; goto out; }
        memset(eb, 0, sizeof(eb));
        struct xfrm_algo *ea = (struct xfrm_algo *)eb;
        strncpy(ea->alg_name, "cbc(aes)", sizeof(ea->alg_name) - 1);
        ea->alg_key_len = (unsigned int)(crypt_key_len * 8);
        memcpy(ea->alg_key, crypt_key, crypt_key_len);
        rc = put_attr(nlh, sizeof(buf), XFRMA_ALG_CRYPT, eb, sz);
        if (rc) goto out;
    }

    /* UDP 封装：声明该 SA 走 ESP-in-UDP，端口与我们的 socket 一致。 */
    {
        struct xfrm_encap_tmpl enc;
        memset(&enc, 0, sizeof(enc));
        enc.encap_type = UDP_ENCAP_ESPINUDP;
        enc.encap_sport = htons(encap_port);
        enc.encap_dport = htons(encap_port);
        enc.encap_oa.a4 = 0;
        rc = put_attr(nlh, sizeof(buf), XFRMA_ENCAP, &enc, sizeof(enc));
        if (rc) goto out;
    }

    rc = xfrm_talk(sk, nlh);
out:
    close(sk);
    return rc;
}

int dfr_open_encap_socket(uint16_t port) {
    int fd = socket(AF_INET, SOCK_DGRAM, 0);
    if (fd < 0) return -errno;

    int one = 1;
    setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, &one, sizeof(one));

    struct sockaddr_in sa;
    memset(&sa, 0, sizeof(sa));
    sa.sin_family = AF_INET;
    sa.sin_port = htons(port);
    sa.sin_addr.s_addr = inet_addr(DFR_LOOPBACK);
    if (bind(fd, (struct sockaddr *)&sa, sizeof(sa)) < 0) {
        int e = -errno;
        close(fd);
        return e;
    }

    /* 关键：声明本 socket 承载 ESP-in-UDP。内核据此把 UDP 载荷交给 esp4。 */
    int encap = UDP_ENCAP_ESPINUDP;
    if (setsockopt(fd, IPPROTO_UDP, UDP_ENCAP, &encap, sizeof(encap)) < 0) {
        int e = -errno;
        close(fd);
        return e;
    }
    return fd;
}
