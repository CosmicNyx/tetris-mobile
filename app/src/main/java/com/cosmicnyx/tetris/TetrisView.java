package com.cosmicnyx.tetris;

import static com.cosmicnyx.tetris.GameData.*;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.*;
import android.os.*;
import android.view.*;

public class TetrisView extends View {

    // ── Design Tokens (mutable — set by applyTheme()) ─────────────
    int BG         = 0xFF06060F;
    int BG_BOARD   = 0xFF0B0B18;
    int BG_CARD    = 0xFF0F0F1E;
    int BORDER     = 0xFF1A1A2C;
    int ACCENT     = 0xFF7B6CF6;
    int ACCENT_DIM = 0xFF2E2A5A;
    int ON_ACCENT  = 0xFFFFFFFF;  // text color when placed on an ACCENT background
    int TXT_BRIGHT = 0xFFEEEEFF;
    int TXT_MID    = 0xFF8888AA;
    int TXT_DIM    = 0xFF3A3A55;

    // ── SRS I-piece data ───────────────────────────────────────────
    // Position corrections (Δrow, Δcol) after generic rotation, indexed by from-state.
    // These shift the piece so the rotation center is at the SRS gridline intersection.
    static final int[][] I_CORR_CW  = {{-1,2},{2,-2},{-2,1},{1,-1}};
    static final int[][] I_CORR_CCW = {{-1,1},{1,-2},{-2,2},{2,-1}};
    // SRS wall kicks (Δrow, Δcol) to try in order, indexed by from-state.
    static final int[][][] I_KICKS_CW = {
        {{0,0},{0,-2},{0,1},{-1,-2},{2,1}},   // state 0 → 1
        {{0,0},{0,-1},{0,2},{2,-1},{-1,2}},   // state 1 → 2
        {{0,0},{0,2},{0,-1},{1,2},{-2,-1}},   // state 2 → 3
        {{0,0},{0,1},{0,-2},{-2,1},{1,-2}},   // state 3 → 0
    };
    static final int[][][] I_KICKS_CCW = {
        {{0,0},{0,-1},{0,2},{2,-1},{-1,2}},   // state 0 → 3
        {{0,0},{0,2},{0,-1},{1,2},{-2,-1}},   // state 1 → 0
        {{0,0},{0,1},{0,-2},{-2,1},{1,-2}},   // state 2 → 1
        {{0,0},{0,-2},{0,1},{-1,-2},{2,1}},   // state 3 → 2
    };

    // ── Game State ─────────────────────────────────────────────────
    int[][] board = new int[ROWS][COLS];
    int[][] shape;
    int type, pr, pc;
    int iState = 0;  // I-piece rotation state: 0=horiz, 1=CW-vert, 2=180, 3=CCW-vert
    int score, totalLines, level;
    int[] nextQueue = new int[4];
    int holdType = 0;
    boolean canHold = true;
    int[] clearingRows = null;
    boolean onFloor = false;
    int lockResetCount = 0;
    long lockDelayStart = 0;
    long lockDelayDuration = LOCK_DELAY_MS;
    int[][] lockedCells = null;

    // ── Persistent Stats ───────────────────────────────────────────
    int highScore = 0;
    int statGames = 0;
    int statTotalLines = 0;
    int statBestLevel = 0;
    int statTotalPieces = 0;
    boolean newHighScore = false;
    boolean showStatsOverlay = false;
    int piecesPlaced = 0;

    // ── Garbage Mode ───────────────────────────────────────────────
    long nextGarbageAt = 0;

    enum State { MENU, COUNTDOWN, PLAYING, PAUSED, GAME_OVER }
    State state = State.MENU;

    int countdown = 0;
    long gameStartTime = 0;
    long timeSurvived  = 0;

    // Mode: 0=Classic, 1=Fixed Speed
    int selectedMode  = 0;
    int fixedSpeedIdx = 1;

    java.util.Random rng = new java.util.Random();

    // ── Settings ───────────────────────────────────────────────────
    boolean showGhost     = true;
    boolean showGrid      = true;
    boolean hapticsOn     = true;
    boolean reducedMotion = false;
    boolean highContrast  = false;
    int     colorTheme    = 0;     // 0=Default, 1=Grayscale
    boolean monoFont      = false;

    // ── Animation State ────────────────────────────────────────────
    float   settingsAnim    = 0f;   // 0=hidden, 1=open
    boolean settingsOpening = false;
    boolean settingsClosing = false;
    boolean settingsOpen    = false;

    float shakeX = 0, shakeY = 0, shakeIntensity = 0;

    int     levelUpAlpha  = 0;
    boolean levelUpActive = false;

    int     gameOverDisplay = 0;
    boolean scoreAnimDone   = false;

    // ── Layout ─────────────────────────────────────────────────────
    float cell, bLeft, bTop, footerTop, screenW, screenH, cardR;

    RectF pauseZone, gearZone, restartZone, menuZone;
    RectF playZone, statsZone, settingsBtnZone, resetBestZone, menuSettingsZone;
    RectF pauseSettingsZone;
    RectF[] modeZones;
    RectF[] speedZones;

    // ── Paints ─────────────────────────────────────────────────────
    Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    Paint txt  = new Paint(Paint.ANTI_ALIAS_FLAG);

    // ── Renderer ───────────────────────────────────────────────────
    Renderer renderer;

    // ── Handler ────────────────────────────────────────────────────
    Handler handler = new Handler(Looper.getMainLooper());

    // ── Runnables ──────────────────────────────────────────────────
    Runnable dropTick = new Runnable() {
        public void run() {
            if (state == State.PLAYING && clearingRows == null) {
                moveDown();
                postInvalidate();
                handler.postDelayed(this, dropDelay());
            }
        }
    };

    Runnable lockDelay = new Runnable() {
        public void run() {
            if (state == State.PLAYING && clearingRows == null) {
                lock();
                postInvalidate();
            }
        }
    };

    Runnable finishClear = new Runnable() {
        public void run() {
            if (clearingRows == null) return;
            int count = clearingRows.length;
            int prevLevel = level;
            totalLines += count;
            score += new int[]{0, 10, 30, 50, 120}[Math.min(count, 4)] * level;  // Tetris = 120
            level = totalLines / 10 + 1;
            if (level > prevLevel) triggerLevelUp();
            boolean[] skip = new boolean[ROWS];
            for (int r : clearingRows) skip[r] = true;
            int wr = ROWS - 1;
            for (int r = ROWS - 1; r >= 0; r--)
                if (!skip[r]) board[wr--] = board[r].clone();
            while (wr >= 0) board[wr--] = new int[COLS];
            clearingRows = null;
            spawnPiece();
            postInvalidate();
            if (state == State.PLAYING) handler.postDelayed(dropTick, dropDelay());
        }
    };

    Runnable clearLockedFlash = new Runnable() {
        public void run() { lockedCells = null; postInvalidate(); }
    };

    Runnable garbageTick = new Runnable() {
        public void run() {
            if (state == State.PLAYING && selectedMode == 2) {
                sendGarbage();
                scheduleGarbage();
            }
        }
    };

    Runnable countdownTick = new Runnable() {
        public void run() {
            countdown--;
            postInvalidate();
            if (countdown > 0) {
                handler.postDelayed(this, 1000);
            } else {
                state = State.PLAYING;
                gameStartTime = System.currentTimeMillis();
                handler.postDelayed(dropTick, dropDelay());
                if (selectedMode == 2) scheduleGarbage();
            }
        }
    };

    Runnable animTick = new Runnable() {
        public void run() {
            boolean more = false;

            if (settingsOpening) {
                settingsAnim = Math.min(1f, settingsAnim + 0.13f);
                if (settingsAnim >= 1f) settingsOpening = false;
                else more = true;
            } else if (settingsClosing) {
                settingsAnim = Math.max(0f, settingsAnim - 0.13f);
                if (settingsAnim <= 0f) { settingsClosing = false; settingsOpen = false; }
                else more = true;
            }

            if (levelUpActive) {
                levelUpAlpha = Math.max(0, levelUpAlpha - 14);
                if (levelUpAlpha > 0) more = true;
                else levelUpActive = false;
            }

            if (shakeIntensity > 0.4f) {
                shakeIntensity *= 0.72f;
                double a = Math.random() * Math.PI * 2;
                shakeX = (float)(Math.cos(a) * shakeIntensity);
                shakeY = (float)(Math.sin(a) * shakeIntensity);
                more = true;
            } else {
                shakeIntensity = 0; shakeX = 0; shakeY = 0;
            }

            if (state == State.GAME_OVER && !scoreAnimDone) {
                int diff = score - gameOverDisplay;
                if (diff <= 0) { gameOverDisplay = score; scoreAnimDone = true; }
                else { gameOverDisplay += Math.max(1, diff / 8); more = true; }
            }

            // Keep running while on floor or in garbage mode so bars are smooth
            if (state == State.PLAYING && (onFloor || selectedMode == 2)) more = true;

            postInvalidate();
            if (more) handler.postDelayed(this, 33);  // 30fps — saves battery vs 60fps
        }
    };

    // ── Gesture State ──────────────────────────────────────────────
    float touchStartX, touchStartY, lastX, lastY;
    long  touchStartMs;
    float dragAccX, dragAccY;
    boolean waitForLift = false, lockedDuringGesture = false;

    static final int VEL_BUF = 5;
    float[] velBufX = new float[VEL_BUF];
    float[] velBufY = new float[VEL_BUF];
    long[]  velBufT = new long[VEL_BUF];
    int     velIdx  = 0;

    float TAP_MAX_DIST, STEP_DIST, FAST_DROP_VEL;

    // ── Constructor ────────────────────────────────────────────────
    public TetrisView(Context ctx) {
        super(ctx);
        setBackgroundColor(BG);
        txt.setTextAlign(Paint.Align.CENTER);
        txt.setTypeface(Typeface.create(fontName(), Typeface.NORMAL));
        loadStats();
        applyTheme();
        renderer = new Renderer(this);
    }

    int garbageInterval() {
        return Math.max(3500, 10000 - level * 400);
    }

    void scheduleGarbage() {
        handler.removeCallbacks(garbageTick);
        int interval = garbageInterval();
        nextGarbageAt = System.currentTimeMillis() + interval;
        handler.postDelayed(garbageTick, interval);
        startAnim(); // keep indicator animating
    }

    void sendGarbage() {
        if (state != State.PLAYING || clearingRows != null) return;
        int rows = 3 + rng.nextInt(3);  // 3, 4, or 5 rows
        // Game over if board doesn't have enough empty rows at top
        for (int c = 0; c < COLS; c++) {
            if (board[rows - 1][c] != 0) {
                timeSurvived = System.currentTimeMillis() - gameStartTime;
                state = State.GAME_OVER;
                gameOverDisplay = 0; scoreAnimDone = false;
                handler.removeCallbacks(dropTick);
                handler.removeCallbacks(garbageTick);
                vibrate(250);
                saveStats();
                startAnim();
                return;
            }
        }
        // Shift entire board up by `rows`
        for (int r = 0; r < ROWS - rows; r++) board[r] = board[r + rows].clone();
        // Add garbage rows at bottom — same gap column per wave for fairness
        int gap = rng.nextInt(COLS);
        for (int r = ROWS - rows; r < ROWS; r++) {
            board[r] = new int[COLS];
            for (int c = 0; c < COLS; c++) if (c != gap) board[r][c] = 8;
        }
        // Shift active piece up to match
        pr -= rows;
        triggerShake(5f + rows);
        vibrate(30);
        postInvalidate();
    }

    void loadStats() {
        SharedPreferences p = getContext().getSharedPreferences("tetris_v2", Context.MODE_PRIVATE);
        highScore       = p.getInt("highScore", 0);
        statGames       = p.getInt("statGames", 0);
        statTotalLines  = p.getInt("statTotalLines", 0);
        statBestLevel   = p.getInt("statBestLevel", 0);
        statTotalPieces = p.getInt("statTotalPieces", 0);
        showGhost     = p.getBoolean("showGhost",     true);
        showGrid      = p.getBoolean("showGrid",      true);
        reducedMotion = p.getBoolean("reducedMotion", false);
        highContrast  = p.getBoolean("highContrast",  false);
        hapticsOn     = p.getBoolean("hapticsOn",     true);
        monoFont      = p.getBoolean("monoFont",      false);
        colorTheme    = p.getInt("colorTheme",        0);
    }

    void saveStats() {
        boolean best = score > highScore;
        if (best) highScore = score;
        newHighScore = best;
        statGames++;
        statTotalLines  += totalLines;
        if (level > statBestLevel) statBestLevel = level;
        statTotalPieces += piecesPlaced;
        getContext().getSharedPreferences("tetris_v2", Context.MODE_PRIVATE)
            .edit()
            .putInt("highScore",       highScore)
            .putInt("statGames",       statGames)
            .putInt("statTotalLines",  statTotalLines)
            .putInt("statBestLevel",   statBestLevel)
            .putInt("statTotalPieces", statTotalPieces)
            .apply();
    }

    // ── Layout ─────────────────────────────────────────────────────
    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        screenW = w; screenH = h;
        cardR = w * 0.022f;
        float infoH   = h * 0.085f;
        float footerH = h * 0.13f;
        cell      = Math.min(w / (float) COLS, (h - infoH - footerH) / ROWS);
        bLeft     = (w - cell * COLS) / 2f;
        bTop      = infoH;
        footerTop = bTop + cell * ROWS;
        float iconSize = infoH * 1.1f;
        pauseZone = new RectF(w - iconSize, 0, w, infoH);
        gearZone  = null;
        TAP_MAX_DIST  = cell * 0.75f;
        STEP_DIST     = cell * 0.65f;
        FAST_DROP_VEL = cell * 0.018f;
    }

    // ── Game Lifecycle ─────────────────────────────────────────────
    void startGame() {
        board = new int[ROWS][COLS];
        score = totalLines = 0; level = 1;
        holdType = 0; canHold = true;
        clearingRows = null; lockedCells = null;
        onFloor = false; lockResetCount = 0;
        lockedDuringGesture = false; waitForLift = false;
        piecesPlaced = 0; newHighScore = false; showStatsOverlay = false;
        shakeIntensity = 0; shakeX = 0; shakeY = 0;
        levelUpAlpha = 0; levelUpActive = false;
        gameOverDisplay = 0; scoreAnimDone = false;
        settingsOpen = false; settingsAnim = 0;
        settingsOpening = false; settingsClosing = false;
        handler.removeCallbacks(finishClear);
        handler.removeCallbacks(clearLockedFlash);
        handler.removeCallbacks(countdownTick);
        handler.removeCallbacks(dropTick);
        handler.removeCallbacks(lockDelay);
        handler.removeCallbacks(garbageTick);
        handler.removeCallbacks(animTick);
        nextGarbageAt = 0;
        for (int i = 0; i < 4; i++) nextQueue[i] = rng.nextInt(7) + 1;
        spawnPiece();
        state = State.COUNTDOWN;
        countdown = 3;
        invalidate();
        handler.postDelayed(countdownTick, 1000);
        handler.post(animTick);
    }

    int dropDelay() {
        if (selectedMode == 1) return FIXED_DELAYS[fixedSpeedIdx];
        return Math.max(60, (int)(550 * Math.pow(0.84, level - 1)));
    }

    void spawnPiece() {
        type  = nextQueue[0];
        System.arraycopy(nextQueue, 1, nextQueue, 0, 3);
        nextQueue[3] = rng.nextInt(7) + 1;
        shape = copy(BASE[type]);
        pr = 0; pc = COLS / 2 - 2; iState = 0;
        canHold = true; onFloor = false; lockResetCount = 0;
        handler.removeCallbacks(lockDelay);
        if (!valid(shape, pr, pc)) {
            timeSurvived = System.currentTimeMillis() - gameStartTime;
            state = State.GAME_OVER;
            gameOverDisplay = 0; scoreAnimDone = false;
            handler.removeCallbacks(dropTick);
            handler.removeCallbacks(garbageTick);
            vibrate(250);
            saveStats();
            startAnim();
        }
    }

    void triggerLevelUp() {
        levelUpAlpha = 220;
        levelUpActive = true;
        vibrate(70);
        startAnim();
    }

    void triggerShake(float intensity) {
        if (reducedMotion) return;
        shakeIntensity = intensity;
        startAnim();
    }

    void startAnim() {
        handler.removeCallbacks(animTick);
        handler.post(animTick);
    }

    void vibrate(int ms) {
        if (!hapticsOn) return;
        Vibrator v = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
        if (v == null || !v.hasVibrator()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            v.vibrate(ms);
        }
    }

    // ── Moves ──────────────────────────────────────────────────────
    void moveDown() {
        if (valid(shape, pr + 1, pc)) {
            pr++;
            if (onFloor) { onFloor = false; handler.removeCallbacks(lockDelay); }
        } else if (!onFloor) {
            onFloor = true;
            lockDelayStart = System.currentTimeMillis();
            lockDelayDuration = LOCK_DELAY_MS;
            handler.postDelayed(lockDelay, LOCK_DELAY_MS);
            startAnim();
        }
    }

    void moveLeft() {
        if (state != State.PLAYING || clearingRows != null || !valid(shape, pr, pc - 1)) return;
        pc--; postInvalidate(); resetLockDelay(); vibrate(10);
    }

    void moveRight() {
        if (state != State.PLAYING || clearingRows != null || !valid(shape, pr, pc + 1)) return;
        pc++; postInvalidate(); resetLockDelay(); vibrate(10);
    }

    void rotate(boolean cw) {
        if (state != State.PLAYING || clearingRows != null) return;
        int[][] r = cw ? rotateCW(shape) : rotateCCW(shape);

        if (type == 1) {
            // I-piece: SRS rotation center is at the gridline intersection
            // between the middle two rows/cols of the 4×4 bounding box.
            int[] corr  = cw ? I_CORR_CW[iState]   : I_CORR_CCW[iState];
            int[][] kix = cw ? I_KICKS_CW[iState]   : I_KICKS_CCW[iState];
            int bpr = pr + corr[0], bpc = pc + corr[1];
            for (int[] k : kix) {
                if (valid(r, bpr + k[0], bpc + k[1])) {
                    shape = r; pr = bpr + k[0]; pc = bpc + k[1];
                    iState = cw ? (iState + 1) % 4 : (iState + 3) % 4;
                    postInvalidate(); resetLockDelay(); vibrate(12);
                    return;
                }
            }
            return;
        }

        // All other pieces: standard kicks
        int[][] kicks = {{0,0},{0,1},{0,-1},{-1,0},{-1,1},{-1,-1},{0,2},{0,-2},{-2,0},{-1,2},{-1,-2}};
        for (int[] k : kicks) {
            if (valid(r, pr + k[0], pc + k[1])) {
                shape = r; pr += k[0]; pc += k[1];
                postInvalidate(); resetLockDelay(); vibrate(12);
                return;
            }
        }
    }

    void resetLockDelay() {
        if (!onFloor || lockResetCount >= MAX_LOCK_MOVES) return;
        lockResetCount++;
        lockDelayStart = System.currentTimeMillis();
        lockDelayDuration = LOCK_DELAY_MS;
        handler.removeCallbacks(lockDelay);
        handler.postDelayed(lockDelay, LOCK_DELAY_MS);
    }

    void hardDrop() {
        if (state != State.PLAYING || clearingRows != null) return;
        int dropped = 0;
        while (valid(shape, pr + 1, pc)) { pr++; dropped++; }
        score += dropped * 2;
        lock(); postInvalidate(); vibrate(28);
    }

    void softDrop() {
        if (state != State.PLAYING || clearingRows != null) return;
        boolean wasFloor = onFloor;
        int prevRow = pr;
        moveDown();
        if (pr > prevRow) score += 1;
        if (!wasFloor && onFloor) {
            handler.removeCallbacks(lockDelay);
            lockDelayStart = System.currentTimeMillis();
            lockDelayDuration = SOFT_LOCK_MS;
            handler.postDelayed(lockDelay, SOFT_LOCK_MS);
        }
        postInvalidate();
    }

    void hold() {
        if (!canHold || state != State.PLAYING || clearingRows != null) return;
        canHold = false;
        int prev = holdType; holdType = type;
        if (prev == 0) { spawnPiece(); canHold = false; }
        else {
            type = prev; shape = copy(BASE[type]);
            pr = 0; pc = COLS / 2 - 2; iState = 0;
            onFloor = false; lockResetCount = 0;
            handler.removeCallbacks(lockDelay);
            if (!valid(shape, pr, pc)) {
                timeSurvived = System.currentTimeMillis() - gameStartTime;
                state = State.GAME_OVER;
                gameOverDisplay = 0; scoreAnimDone = false;
                handler.removeCallbacks(dropTick);
                handler.removeCallbacks(garbageTick);
                vibrate(250); startAnim();
            }
        }
        postInvalidate();
    }

    void lock() {
        // Speed bonus: reward fast placement after piece lands naturally
        if (onFloor && lockDelayStart > 0) {
            long floorMs = System.currentTimeMillis() - lockDelayStart;
            score += (int) Math.max(0, 4 - floorMs / 300);
        }
        piecesPlaced++;
        onFloor = false;
        handler.removeCallbacks(lockDelay);
        lockedCells = new int[shape.length][2];
        for (int i = 0; i < shape.length; i++) {
            lockedCells[i][0] = pr + shape[i][0];
            lockedCells[i][1] = pc + shape[i][1];
        }
        handler.removeCallbacks(clearLockedFlash);
        handler.postDelayed(clearLockedFlash, 130);
        for (int[] b : shape) { int r = pr+b[0], c = pc+b[1]; if (r >= 0) board[r][c] = type; }
        lockedDuringGesture = true; waitForLift = true;
        vibrate(18);
        tryLineClear();
    }

    void tryLineClear() {
        int count = 0;
        int[] rows = new int[ROWS];
        for (int r = 0; r < ROWS; r++) if (lineFull(r)) rows[count++] = r;
        if (count == 0) { spawnPiece(); return; }
        clearingRows = new int[count];
        System.arraycopy(rows, 0, clearingRows, 0, count);
        handler.removeCallbacks(dropTick);
        handler.postDelayed(finishClear, 250);
        if (count >= 3) triggerShake(count == 4 ? 16f : 9f);
        vibrate(count == 4 ? 90 : 40);
        postInvalidate();
    }

    boolean lineFull(int r) {
        for (int c = 0; c < COLS; c++) if (board[r][c] == 0) return false;
        return true;
    }

    int ghostRow() {
        int g = pr;
        while (valid(shape, g + 1, pc)) g++;
        return g;
    }

    boolean valid(int[][] s, int row, int col) {
        for (int[] b : s) {
            int r = row+b[0], c = col+b[1];
            if (c < 0 || c >= COLS || r >= ROWS) return false;
            if (r >= 0 && board[r][c] != 0) return false;
        }
        return true;
    }

    int[][] rotateCW(int[][] cells) {
        int[][] r = new int[cells.length][2];
        for (int i = 0; i < cells.length; i++) { r[i][0] = cells[i][1]; r[i][1] = -cells[i][0]; }
        int minR = Integer.MAX_VALUE, minC = Integer.MAX_VALUE;
        for (int[] c : r) { minR = Math.min(minR, c[0]); minC = Math.min(minC, c[1]); }
        for (int[] c : r) { c[0] -= minR; c[1] -= minC; }
        return r;
    }

    int[][] rotateCCW(int[][] cells) {
        int[][] r = new int[cells.length][2];
        for (int i = 0; i < cells.length; i++) { r[i][0] = -cells[i][1]; r[i][1] = cells[i][0]; }
        int minR = Integer.MAX_VALUE, minC = Integer.MAX_VALUE;
        for (int[] c : r) { minR = Math.min(minR, c[0]); minC = Math.min(minC, c[1]); }
        for (int[] c : r) { c[0] -= minR; c[1] -= minC; }
        return r;
    }

    int[][] copy(int[][] src) {
        int[][] o = new int[src.length][2];
        for (int i = 0; i < src.length; i++) o[i] = src[i].clone();
        return o;
    }

    int[] palette() {
        if (colorTheme == 1) return COLORS_GRAY;
        if (colorTheme == 2) return COLORS_GAMEBOY;
        if (colorTheme == 3) return COLORS_ASCII;
        return highContrast ? COLORS_HC : COLORS;
    }

    String fontName() { return (monoFont || colorTheme == 3) ? "monospace" : "sans-serif"; }

    int withAlpha(int color, int alpha) { return (alpha << 24) | (color & 0x00FFFFFF); }

    void applyTheme() {
        switch (colorTheme) {
            case 1:  // Grayscale — true black BG saves OLED power
                BG         = 0xFF000000;
                BG_BOARD   = 0xFF1A1A1A;
                BG_CARD    = 0xFF181818;
                BORDER     = 0xFF303030;
                ACCENT     = 0xFFBBBBBB;
                ACCENT_DIM = 0xFF282828;
                ON_ACCENT  = 0xFF000000;  // black text on grey button
                TXT_BRIGHT = 0xFFEEEEEE;
                TXT_MID    = 0xFF888888;
                TXT_DIM    = 0xFF444444;
                break;
            case 2:  // Game Boy — muted sage, board clearly distinct
                BG         = 0xFF7A8C62;
                BG_BOARD   = 0xFF9BAA7A;
                BG_CARD    = 0xFF7A8C62;
                BORDER     = 0xFF3D5230;
                ACCENT     = 0xFF1E3018;
                ACCENT_DIM = 0xFF4A6038;
                ON_ACCENT  = 0xFF9BAA7A;  // light sage text on dark button
                TXT_BRIGHT = 0xFF1E3018;
                TXT_MID    = 0xFF3D5230;
                TXT_DIM    = 0xFF4A6038;
                break;
            case 3:  // ASCII Terminal — white on black
                BG         = 0xFF000000;
                BG_BOARD   = 0xFF000000;
                BG_CARD    = 0xFF0A0A0A;
                BORDER     = 0xFF555555;
                ACCENT     = 0xFFFFFFFF;
                ACCENT_DIM = 0xFF222222;
                ON_ACCENT  = 0xFF000000;  // black text on white button
                TXT_BRIGHT = 0xFFFFFFFF;
                TXT_MID    = 0xFFAAAAAA;
                TXT_DIM    = 0xFF555555;
                break;
            default:  // 0 = Default
                BG         = 0xFF06060F;
                BG_BOARD   = 0xFF18182E;
                BG_CARD    = 0xFF0F0F1E;
                BORDER     = 0xFF1A1A2C;
                ACCENT     = 0xFF7B6CF6;
                ACCENT_DIM = 0xFF2E2A5A;
                ON_ACCENT  = 0xFFFFFFFF;  // white text on purple button
                TXT_BRIGHT = 0xFFEEEEFF;
                TXT_MID    = 0xFF8888AA;
                TXT_DIM    = 0xFF3A3A55;
                break;
        }
        setBackgroundColor(BG);
        postInvalidate();
    }

    // ── onDraw ─────────────────────────────────────────────────────
    @Override
    protected void onDraw(Canvas canvas) {
        if (cell <= 0) return;

        boolean shaking = shakeIntensity > 0 && !reducedMotion;
        if (shaking) { canvas.save(); canvas.translate(shakeX, shakeY); }

        if (state == State.MENU) {
            renderer.drawMenuScreen(canvas);
            if (showStatsOverlay) renderer.drawStatsOverlay(canvas);
        } else {
            renderer.drawBoardContainer(canvas);
            renderer.drawBoard(canvas);
            if (clearingRows == null && state != State.GAME_OVER) {
                if (showGhost) renderer.drawGhost(canvas);
                renderer.drawPiece(canvas);
            }
            if (showGrid) renderer.drawGrid(canvas);
            renderer.drawInfoBar(canvas);
            renderer.drawHoldPanel(canvas);
            renderer.drawNextPanel(canvas);
            renderer.drawGarbageIndicator(canvas);

            // Level up flash
            if (levelUpActive && levelUpAlpha > 0 && !reducedMotion) {
                fill.setColor(withAlpha(ACCENT, levelUpAlpha / 4));
                canvas.drawRect(0, 0, screenW, screenH, fill);
                txt.setTextSize(cell * 1.1f);
                txt.setColor(Color.argb(levelUpAlpha, 255, 255, 255));
                txt.setTextAlign(Paint.Align.CENTER);
                txt.setTypeface(Typeface.create(fontName(), Typeface.BOLD));
                canvas.drawText("LEVEL  " + level, screenW / 2f, screenH * 0.44f, txt);
                txt.setTypeface(Typeface.create(fontName(), Typeface.NORMAL));
            }

            if      (state == State.GAME_OVER) renderer.drawGameOverScreen(canvas);
            else if (state == State.PAUSED)    renderer.drawPauseOverlay(canvas);
            else if (state == State.COUNTDOWN) renderer.drawCountdownOverlay(canvas);
        }

        if (settingsAnim > 0) renderer.drawSettingsPanel(canvas);

        if (shaking) canvas.restore();
    }

    boolean getSetting(String key) {
        switch (key) {
            case "showGhost":    return showGhost;
            case "showGrid":     return showGrid;
            case "reducedMotion":return reducedMotion;
            case "highContrast": return highContrast;
            case "hapticsOn":    return hapticsOn;
            case "monoFont":     return monoFont;
            default:             return false;
        }
    }

    void toggleSetting(String key) {
        switch (key) {
            case "showGhost":    showGhost     = !showGhost;     break;
            case "showGrid":     showGrid      = !showGrid;      break;
            case "reducedMotion":reducedMotion = !reducedMotion; break;
            case "highContrast": highContrast  = !highContrast;  break;
            case "hapticsOn":    hapticsOn     = !hapticsOn;     break;
            case "monoFont":     monoFont      = !monoFont;      break;
        }
        saveSettings();
        postInvalidate();
    }

    void saveSettings() {
        getContext().getSharedPreferences("tetris_v2", Context.MODE_PRIVATE).edit()
            .putBoolean("showGhost",    showGhost)
            .putBoolean("showGrid",     showGrid)
            .putBoolean("reducedMotion",reducedMotion)
            .putBoolean("highContrast", highContrast)
            .putBoolean("hapticsOn",    hapticsOn)
            .putBoolean("monoFont",     monoFont)
            .putInt("colorTheme",       colorTheme)
            .apply();
    }

    // ── Touch ──────────────────────────────────────────────────────
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        float x = e.getX(0), y = e.getY(0);

        if (action == MotionEvent.ACTION_DOWN) {
            // Settings panel open — intercept touches
            if (settingsOpen || settingsAnim > 0.4f) {
                float pW = screenW * 0.78f;
                float px = screenW - pW * settingsAnim;
                if (x >= px) {
                    handleSettingsTap(x, y, px);
                } else {
                    closeSettings();
                }
                return true;
            }

            if (state == State.MENU) { handleMenuTap(x, y); return true; }
            if (state == State.COUNTDOWN) return true;
            if (state == State.GAME_OVER) {
                if (restartZone != null && restartZone.contains(x, y)) startGame();
                else if (menuZone != null && menuZone.contains(x, y)) goMenu();
                return true;
            }
            if (state == State.PAUSED) {
                if (restartZone      != null && restartZone.contains(x, y))      { startGame();    return true; }
                if (menuZone         != null && menuZone.contains(x, y))         { goMenu();       return true; }
                if (pauseSettingsZone!= null && pauseSettingsZone.contains(x, y)){ openSettings(); return true; }
                togglePause(); waitForLift = true; return true;
            }
            if (pauseZone != null && pauseZone.contains(x, y)) { togglePause();  return true; }

            waitForLift = false; lockedDuringGesture = false;
            touchStartX = x; touchStartY = y;
            lastX = x; lastY = y;
            touchStartMs = System.currentTimeMillis();
            dragAccX = 0; dragAccY = 0; velIdx = 0;
            recordVel(x, y);
        }

        else if (action == MotionEvent.ACTION_MOVE) {
            if (settingsOpen || settingsAnim > 0.4f) return true;
            float dx = x - lastX, dy = y - lastY;
            lastX = x; lastY = y;
            recordVel(x, y);
            if (state != State.PLAYING || clearingRows != null || waitForLift) return true;
            if (velocityY() < FAST_DROP_VEL * 0.6f) {
                dragAccX += dx;
                while (dragAccX >  STEP_DIST) { moveRight(); dragAccX -= STEP_DIST; }
                while (dragAccX < -STEP_DIST) { moveLeft();  dragAccX += STEP_DIST; }
            } else { dragAccX = 0; }
            if (dy > 0) {
                dragAccY += dy;
                while (dragAccY > STEP_DIST) { softDrop(); dragAccY -= STEP_DIST; }
            } else { dragAccY = 0; }
        }

        else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (settingsOpen || settingsAnim > 0.4f) return true;
            boolean skipGesture = waitForLift;
            waitForLift = false;
            boolean lockedThis = lockedDuringGesture;
            lockedDuringGesture = false;
            if (skipGesture || state != State.PLAYING || clearingRows != null) return true;
            float dist = dist(x, y, touchStartX, touchStartY);
            long  dur  = System.currentTimeMillis() - touchStartMs;
            if (dist < TAP_MAX_DIST && dur < 230) {
                rotate(touchStartX >= screenW / 2f);  // right half = CW, left half = CCW
            } else if (!lockedThis) {
                float vy = velocityY(), vx = velocityX();
                if (Math.abs(vy) > Math.abs(vx) * 0.8f) {
                    if      (vy >  FAST_DROP_VEL) hardDrop();
                    else if (vy < -FAST_DROP_VEL) hold();
                }
            }
        }

        return true;
    }

    void handleMenuTap(float x, float y) {
        if (showStatsOverlay) {
            if (resetBestZone != null && resetBestZone.contains(x, y)) {
                highScore = 0; statGames = 0; statTotalLines = 0;
                statBestLevel = 0; statTotalPieces = 0;
                getContext().getSharedPreferences("tetris_v2", Context.MODE_PRIVATE)
                    .edit().clear().apply();
                vibrate(40);
            }
            showStatsOverlay = false; postInvalidate(); return;
        }
        if (playZone != null && playZone.contains(x, y)) { startGame(); return; }
        if (modeZones != null) {
            for (int i = 0; i < modeZones.length; i++) {
                if (modeZones[i] != null && modeZones[i].contains(x, y)) {
                    selectedMode = i; postInvalidate(); return;
                }
            }
        }
        if (speedZones != null) {
            for (int i = 0; i < speedZones.length; i++) {
                if (speedZones[i] != null && speedZones[i].contains(x, y)) {
                    fixedSpeedIdx = i; postInvalidate(); return;
                }
            }
        }
        if (statsZone != null && statsZone.contains(x, y)) { showStatsOverlay = true; postInvalidate(); return; }
        if (menuSettingsZone != null && menuSettingsZone.contains(x, y)) { openSettings(); }
    }

    void handleSettingsTap(float x, float y, float panelX) {
        float mg = screenW * 0.055f;
        // Close X button
        if (y < screenH * 0.12f && x > screenW - mg - cell * 0.6f) { closeSettings(); return; }

        // Reconstruct toggle y positions and check taps
        Object[][] items = {
            {"GAME",          null},
            {"Ghost Piece",   "showGhost"},
            {"Grid Lines",    "showGrid"},
            {"VISUAL",        null},
            {"Reduce Motion", "reducedMotion"},
            {"ACCESSIBILITY", null},
            {"High Contrast", "highContrast"},
            {"HAPTICS",       null},
            {"Vibration",     "hapticsOn"},
            {"COLOR THEME",   null},
            {"Default",       "theme:0"},
            {"Grayscale",     "theme:1"},
            {"Game Boy",      "theme:2"},
            {"ASCII",         "theme:3"},
            {"FONT",          null},
            {"Monospace",     "monoFont"},
        };
        float fy = screenH * 0.07f + cell * 0.8f;
        for (Object[] item : items) {
            String key = (String) item[1];
            if (key == null) { fy += cell * 0.18f + cell * 0.55f; }
            else {
                float rH = cell * 0.85f;
                if (y >= fy && y <= fy + rH) {
                    if (key.startsWith("theme:")) {
                        colorTheme = Integer.parseInt(key.substring(6));
                        saveSettings();
                        applyTheme();
                    } else {
                        toggleSetting(key);
                    }
                    return;
                }
                fy += rH;
            }
        }
    }

    void openSettings() {
        settingsOpen = true;
        settingsOpening = true; settingsClosing = false;
        if (state == State.PLAYING) togglePause();
        startAnim();
    }

    void closeSettings() {
        settingsClosing = true; settingsOpening = false;
        startAnim();
    }

    void goMenu() {
        handler.removeCallbacks(dropTick);
        handler.removeCallbacks(lockDelay);
        handler.removeCallbacks(finishClear);
        handler.removeCallbacks(clearLockedFlash);
        handler.removeCallbacks(countdownTick);
        handler.removeCallbacks(garbageTick);
        handler.removeCallbacks(animTick);
        settingsAnim = 0; settingsOpen = false;
        state = State.MENU;
        postInvalidate();
    }

    void togglePause() {
        if (state == State.PLAYING) {
            state = State.PAUSED;
            handler.removeCallbacks(dropTick);
            handler.removeCallbacks(lockDelay);
            handler.removeCallbacks(garbageTick);
        } else if (state == State.PAUSED) {
            state = State.PLAYING;
            handler.postDelayed(dropTick, dropDelay());
            if (onFloor) handler.postDelayed(lockDelay, LOCK_DELAY_MS);
            if (selectedMode == 2) scheduleGarbage();
        }
        invalidate();
    }

    // ── Velocity ───────────────────────────────────────────────────
    void recordVel(float x, float y) {
        int i = velIdx % VEL_BUF;
        velBufX[i] = x; velBufY[i] = y; velBufT[i] = System.currentTimeMillis();
        velIdx++;
    }

    float velocityY() { return velocity(velBufY); }
    float velocityX() { return velocity(velBufX); }

    float velocity(float[] buf) {
        int count = Math.min(velIdx, VEL_BUF);
        if (count < 2) return 0;
        int newest = (velIdx - 1) % VEL_BUF;
        int oldest = (velIdx - count) % VEL_BUF;
        long dt = velBufT[newest] - velBufT[oldest];
        if (dt <= 0) return 0;
        return (buf[newest] - buf[oldest]) / (float) dt;
    }

    float dist(float x1, float y1, float x2, float y2) {
        float dx = x1-x2, dy = y1-y2;
        return (float) Math.sqrt(dx*dx + dy*dy);
    }

    // ── Formatters ─────────────────────────────────────────────────
    String fmtScore(int s) {
        if (s >= 1000000) return String.format("%.1fM", s / 1000000f);
        if (s >= 10000)   return String.format("%.1fK", s / 1000f);
        return String.valueOf(s);
    }

    String fmtTime(long ms) {
        if (ms <= 0) return "0:00";
        long s = ms / 1000;
        return String.format("%d:%02d", s / 60, s % 60);
    }

    // ── Cleanup ────────────────────────────────────────────────────
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        handler.removeCallbacks(dropTick);
        handler.removeCallbacks(lockDelay);
        handler.removeCallbacks(finishClear);
        handler.removeCallbacks(clearLockedFlash);
        handler.removeCallbacks(countdownTick);
        handler.removeCallbacks(garbageTick);
        handler.removeCallbacks(animTick);
    }
}
