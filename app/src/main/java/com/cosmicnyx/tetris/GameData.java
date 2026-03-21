package com.cosmicnyx.tetris;

final class GameData {
    private GameData() {}

    static final int COLS = 10, ROWS = 20;
    static final int LOCK_DELAY_MS  = 1500;
    static final int SOFT_LOCK_MS   =  350;
    static final int MAX_LOCK_MOVES = 15;

    static final int[] COLORS = {
        0xFF0D0D1A, 0xFF00CCCC, 0xFFCCCC00, 0xFF9900CC,
        0xFF00AA00, 0xFFCC2200, 0xFF1144CC, 0xFFCC7700,
        0xFF2E2E44, // 8 = garbage
    };
    static final int[] COLORS_HC = {
        0xFF000000, 0xFF00FFFF, 0xFFFFFF00, 0xFFFF00FF,
        0xFF00FF00, 0xFFFF2200, 0xFF0077FF, 0xFFFF8800,
        0xFF555566, // 8 = garbage
    };
    static final int[] COLORS_GRAY = {
        0xFF0D0D1A, 0xFFAAAAAA, 0xFFAAAAAA, 0xFFAAAAAA,
        0xFFAAAAAA, 0xFFAAAAAA, 0xFFAAAAAA, 0xFFAAAAAA,
        0xFF444455, // 8 = garbage
    };
    static final int[] COLORS_GAMEBOY = {
        0xFF9BAA7A, 0xFF1E3018, 0xFF1E3018, 0xFF1E3018,
        0xFF1E3018, 0xFF1E3018, 0xFF1E3018, 0xFF1E3018,
        0xFF3D5230, // 8 = garbage
    };
    static final int[] COLORS_ASCII = {
        0xFF000000, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF,
        0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF,
        0xFF888888, // 8 = garbage
    };

    static final int[][][] BASE = {
        null,
        {{0,0},{0,1},{0,2},{0,3}},
        {{0,0},{0,1},{1,0},{1,1}},
        {{0,1},{1,0},{1,1},{1,2}},
        {{0,1},{0,2},{1,0},{1,1}},
        {{0,0},{0,1},{1,1},{1,2}},
        {{0,0},{1,0},{1,1},{1,2}},
        {{0,2},{1,0},{1,1},{1,2}},
    };

    static final int[] FIXED_DELAYS = {500, 300, 150, 80};
    static final String[] FIXED_LABELS = {"SLOW", "MED", "FAST", "ULTRA"};
}
