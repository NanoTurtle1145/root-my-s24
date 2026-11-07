package cn.nanoturtle.rootmys9280.manager.rootmy;

interface IRootCallback {
    void onLog(String line);
    void onComplete(int exitCode, boolean ksuLoaded);
}
