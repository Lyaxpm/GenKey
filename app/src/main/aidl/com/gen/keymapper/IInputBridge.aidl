package com.gen.keymapper;

interface IInputBridge {
    // Shizuku mewajibkan destroy() memakai transaction code 16777114.
    // Jika satu method memakai ID eksplisit, SEMUA method harus memakai ID eksplisit.
    void destroy() = 16777114;
    boolean ping() = 1;
    void tap(int x, int y, int durationMs) = 2;
    void swipe(int x1, int y1, int x2, int y2, int durationMs) = 3;
    void pointer(int pointerToken, int action, float x, float y) = 4;
    void releaseAll() = 5;
}
