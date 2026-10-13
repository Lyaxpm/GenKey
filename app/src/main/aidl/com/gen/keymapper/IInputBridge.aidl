package com.gen.keymapper;
interface IInputBridge {
    boolean ping();
    void tap(int x, int y, int durationMs);
    void swipe(int x1, int y1, int x2, int y2, int durationMs);
    void pointer(int pointerToken, int action, float x, float y);
    void releaseAll();
    void destroy() = 16777114;
}
