package cn.nanoturtle.rootmys9280.manager.rootmy;

import cn.nanoturtle.rootmys9280.manager.rootmy.IRootCallback;

interface IRootService {
    void setKsudAsset(String name);
    /** 设备构建号（如 S9280ZCS6DZJ3）：用于优先加载 samtimer/targets/<TAG>/ 下的专属载荷。 */
    void setTargetTag(String tag);
    /** 本次运行是否在 late-load 前禁用 KSU 模块（98260 路径，root 上下文里执行）。 */
    void setDisableModules(boolean disable);
    void runExploit(IRootCallback callback);
    boolean isReady();
    boolean isRooted();
    void destroy();
}
