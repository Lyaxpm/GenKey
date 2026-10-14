package com.gen.keymapper;

interface IInputBridge {
    // Shizuku mewajibkan destroy() memakai transaction code 16777114.
    // Jika satu method memakai ID eksplisit, SEMUA method harus memakai ID eksplisit.
    void destroy() = 16777114;
    boolean ping() = 1;
    // Satu jari virtual. token = ID bebas milik pemanggil, action = MotionEvent.ACTION_DOWN/MOVE/UP/CANCEL.
    void pointer(int token, int action, float x, float y) = 2;
    void releaseAll() = 3;
    // Kirim satu penekanan tombol sistem (mis. KEYCODE_BACK) ke jendela yang sedang fokus.
    void key(int keyCode) = 4;
    // Informasi diagnosa (metode injeksi yang dipakai, error terakhir).
    String diag() = 5;
}
