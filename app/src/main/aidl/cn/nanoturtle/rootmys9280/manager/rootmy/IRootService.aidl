package cn.nanoturtle.rootmys9280.manager.rootmy;

import cn.nanoturtle.rootmys9280.manager.rootmy.IRootCallback;

interface IRootService {
    void setKsudAsset(String name);
    /** 本次运行是否在 late-load 前禁用 KSU 模块（98260 路径，root 上下文里执行）。 */
    void setDisableModules(boolean disable);
    void runExploit(IRootCallback callback);
    boolean isReady();
    boolean isRooted();
    void destroy();
}
