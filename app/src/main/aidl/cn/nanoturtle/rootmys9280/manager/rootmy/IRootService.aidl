package cn.nanoturtle.rootmys9280.manager.rootmy;

import cn.nanoturtle.rootmys9280.manager.rootmy.IRootCallback;

interface IRootService {
    void runExploit(IRootCallback callback);
    boolean isReady();
    boolean isRooted();
    void destroy();
}
