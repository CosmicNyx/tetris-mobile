package com.cosmicnyx.tetris;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.*;
import android.os.*;
import android.view.*;

public class TetrisView extends View {

    // ── Design Tokens ─────────────────────────────────────────────
    static final int BG         = 0xFF06060F;
    static final int BG_BOARD   = 0xFF0B0B18;
    static final int BG_CARD    = 0xFF0F0F1E;
    static final int BORDER     = 0xFF1A1A2C;
    static final int ACCENT     = 0xFF7B6CF6;
    static final int ACCENT_DIM = 0xFF2E2A5A;
    static final int TXT_BRIGHT = 0xFFEEEEFF;
    static final int TXT_MID    = 0xFF8888AA;
    static final int TXT_DIM    = 0xFF3A3A55;

    // ── Game Constants ─────────────────────────────────────────────
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

    // ── Game State ─────────────────────────────────────────────────
    int[][] board = new int[ROWS][COLS];
    int[][] shape;
    int type, pr, pc;
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
    RectF playZone, statsZone, settingsBtnZone;
    RectF[] modeZones;
    RectF[] speedZones;

    // ── Paints ─────────────────────────────────────────────────────
    Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    Paint txt  = new Paint(Paint.ANTI_ALIAS_FLAG);

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
            score += new int[]{0, 10, 30, 50, 80}[Math.min(count, 4)] * level;
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
            if (more) handler.postDelayed(this, 16);
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
        txt.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        loadStats();
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
        // Game over if board is already stacked into the top row
        for (int c = 0; c < COLS; c++) {
            if (board[0][c] != 0) {
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
        // Shift entire board up by 1
        for (int r = 0; r < ROWS - 1; r++) board[r] = board[r + 1].clone();
        // Add garbage row at bottom with one random gap
        int gap = rng.nextInt(COLS);
        board[ROWS - 1] = new int[COLS];
        for (int c = 0; c < COLS; c++) if (c != gap) board[ROWS - 1][c] = 8;
        // Shift active piece up to match
        pr--;
        triggerShake(5f);
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
        pr = 0; pc = COLS / 2 - 2;
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

    void rotate() {
        if (state != State.PLAYING || clearingRows != null) return;
        int[][] r = rotateCW(shape);
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
            pr = 0; pc = COLS / 2 - 2;
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

    int[][] copy(int[][] src) {
        int[][] o = new int[src.length][2];
        for (int i = 0; i < src.length; i++) o[i] = src[i].clone();
        return o;
    }

    int[] palette() { return highContrast ? COLORS_HC : COLORS; }

    // ── onDraw ─────────────────────────────────────────────────────
    @Override
    protected void onDraw(Canvas canvas) {
        if (cell <= 0) return;

        boolean shaking = shakeIntensity > 0 && !reducedMotion;
        if (shaking) { canvas.save(); canvas.translate(shakeX, shakeY); }

        if (state == State.MENU) {
            drawMenuScreen(canvas);
            if (showStatsOverlay) drawStatsOverlay(canvas);
        } else {
            drawBoardContainer(canvas);
            drawBoard(canvas);
            if (clearingRows == null && state != State.GAME_OVER) {
                if (showGhost) drawGhost(canvas);
                drawPiece(canvas);
            }
            if (showGrid) drawGrid(canvas);
            drawInfoBar(canvas);
            drawHoldPanel(canvas);
            drawNextPanel(canvas);
            drawGarbageIndicator(canvas);

            // Level up flash
            if (levelUpActive && levelUpAlpha > 0 && !reducedMotion) {
                fill.setColor(Color.argb(levelUpAlpha / 4, 123, 108, 246));
                canvas.drawRect(0, 0, screenW, screenH, fill);
                txt.setTextSize(cell * 1.1f);
                txt.setColor(Color.argb(levelUpAlpha, 255, 255, 255));
                txt.setTextAlign(Paint.Align.CENTER);
                txt.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                canvas.drawText("LEVEL  " + level, screenW / 2f, screenH * 0.44f, txt);
                txt.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            }

            if      (state == State.GAME_OVER) drawGameOverScreen(canvas);
            else if (state == State.PAUSED)    drawPauseOverlay(canvas);
            else if (state == State.COUNTDOWN) drawCountdownOverlay(canvas);
        }

        if (settingsAnim > 0) drawSettingsPanel(canvas);

        if (shaking) canvas.restore();
    }

    // ── Board Container ────────────────────────────────────────────
    void drawBoardContainer(Canvas canvas) {
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(BG_BOARD);
        canvas.drawRoundRect(bLeft - 2, bTop - 2,
            bLeft + COLS * cell + 2, bTop + ROWS * cell + 2,
            cardR * 0.4f, cardR * 0.4f, fill);
    }

    // ── Garbage Indicator ──────────────────────────────────────────
    void drawGarbageIndicator(Canvas canvas) {
        if (selectedMode != 2 || state != State.PLAYING) return;
        long now = System.currentTimeMillis();
        float prog = nextGarbageAt > 0
            ? Math.max(0f, Math.min(1f, (float)(nextGarbageAt - now) / garbageInterval()))
            : 1f;
        // prog = 1.0 right after garbage sent, 0.0 when about to arrive
        float barX = bLeft - 7f;
        float barW = 4.5f;
        float barH = ROWS * cell;
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(0x22FF2222);
        canvas.drawRect(barX, bTop, barX + barW, bTop + barH, fill);
        float fillH = barH * (1f - prog);
        int alpha = prog < 0.25f ? 0xFF : 0xAA;
        fill.setColor(Color.argb(alpha, 0xFF, 0x33, 0x22));
        canvas.drawRect(barX, bTop + barH - fillH, barX + barW, bTop + barH, fill);
        // pulse label when imminent
        if (prog < 0.15f) {
            txt.setTextAlign(Paint.Align.LEFT);
            txt.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            txt.setTextSize(cell * 0.24f);
            txt.setColor(Color.argb((int)((0.15f - prog) / 0.15f * 200), 0xFF, 0x44, 0x33));
            canvas.drawText("!", barX - cell * 0.1f, bTop + barH * 0.5f, txt);
            txt.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        }
    }

    // ── Info Bar ───────────────────────────────────────────────────
    void drawInfoBar(Canvas canvas) {
        float barH = bTop;
        String[] labels = {"SCORE", "LEVEL", "LINES"};
        String[] values = {fmtScore(score), String.valueOf(level), String.valueOf(totalLines)};
        float sw = screenW / 3f;

        for (int i = 0; i < 3; i++) {
            float cx = sw * i + sw / 2f;
            txt.setTextAlign(Paint.Align.CENTER);
            txt.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            txt.setTextSize(barH * 0.26f);
            txt.setColor(TXT_DIM);
            canvas.drawText(labels[i], cx, barH * 0.30f, txt);
            txt.setTextSize(barH * 0.45f);
            txt.setColor(i == 0 ? ACCENT : TXT_BRIGHT);
            txt.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            canvas.drawText(values[i], cx, barH * 0.80f, txt);
        }
        txt.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));

        // Pause button — manually drawn, no emoji (avoids colored-emoji rendering)
        if (state == State.PLAYING || state == State.PAUSED) {
            float bcx = screenW - barH * 0.46f;
            float bcy = barH * 0.50f;
            float br  = barH * 0.22f;
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(0x28FFFFFF);
            canvas.drawCircle(bcx, bcy, br * 1.5f, fill);
            fill.setColor(TXT_MID);
            if (state == State.PAUSED) {
                // Triangle (play)
                float ts = br * 0.75f;
                Path tri = new Path();
                tri.moveTo(bcx - ts * 0.55f, bcy - ts);
                tri.lineTo(bcx - ts * 0.55f, bcy + ts);
                tri.lineTo(bcx + ts * 0.9f, bcy);
                tri.close();
                canvas.drawPath(tri, fill);
            } else {
                // Two bars (pause)
                float bw = br * 0.30f, bh = br * 0.85f, bg = br * 0.25f;
                canvas.drawRect(bcx - bg - bw, bcy - bh, bcx - bg, bcy + bh, fill);
                canvas.drawRect(bcx + bg,      bcy - bh, bcx + bg + bw, bcy + bh, fill);
            }
        }
    }

    // ── Hold Panel ─────────────────────────────────────────────────
    void drawHoldPanel(Canvas canvas) {
        float fH  = screenH - footerTop;
        if (fH < 4) return;
        float pW  = screenW * 0.26f;
        float pX  = screenW * 0.01f;
        float pY  = footerTop + fH * 0.06f;
        float pH  = fH * 0.88f;
        drawCard(canvas, pX, pY, pW, pH, canHold ? ACCENT_DIM : BORDER);

        float lH = pH * 0.32f;
        txt.setTextSize(lH * 0.52f);
        txt.setColor(canHold ? TXT_MID : TXT_DIM);
        txt.setTextAlign(Paint.Align.CENTER);
        txt.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        canvas.drawText("HOLD", pX + pW / 2f, pY + lH * 0.72f, txt);
        if (holdType != 0) {
            float cs = Math.min(pW / 5.5f, (pH - lH) / 2.3f);
            drawMini(canvas, holdType, pX + pW / 2f, pY + lH + (pH - lH) * 0.55f, cs, canHold);
        }
    }

    // ── Next Panel ─────────────────────────────────────────────────
    void drawNextPanel(Canvas canvas) {
        float fH  = screenH - footerTop;
        if (fH < 4) return;
        float pX  = screenW * 0.29f;
        float pW  = screenW * 0.70f;
        float pY  = footerTop + fH * 0.06f;
        float pH  = fH * 0.88f;
        drawCard(canvas, pX, pY, pW, pH, BORDER);

        float lH = pH * 0.32f;
        txt.setTextSize(lH * 0.52f);
        txt.setColor(TXT_MID);
        txt.setTextAlign(Paint.Align.LEFT);
        txt.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        canvas.drawText("NEXT", pX + pW * 0.05f, pY + lH * 0.72f, txt);

        float slotW = pW / 4f;
        float cs    = Math.min(slotW / 5.5f, (pH - lH) / 2.3f);
        float cy    = pY + lH + (pH - lH) * 0.55f;
        for (int i = 0; i < 4; i++)
            drawMini(canvas, nextQueue[i], pX + slotW * i + slotW / 2f, cy, cs, true);
        txt.setTextAlign(Paint.Align.CENTER);
    }

    void drawCard(Canvas canvas, float x, float y, float w, float h, int borderColor) {
        float r = cardR * 0.6f;
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(BG_CARD);
        canvas.drawRoundRect(x, y, x + w, y + h, r, r, fill);
        fill.setStyle(Paint.Style.STROKE);
        fill.setStrokeWidth(1f);
        fill.setColor(borderColor);
        canvas.drawRoundRect(x, y, x + w, y + h, r, r, fill);
        fill.setStyle(Paint.Style.FILL);
    }

    void drawMini(Canvas canvas, int t, float cx, float cy, float cs, boolean bright) {
        if (t == 0) return;
        int[][] s = BASE[t];
        int maxC = 0, maxR = 0;
        for (int[] b : s) { maxC = Math.max(maxC, b[1]); maxR = Math.max(maxR, b[0]); }
        float ox = cx - cs * (maxC + 1) / 2f;
        float oy = cy - cs * (maxR + 1) / 2f;
        float g = Math.max(1f, cs * 0.06f);
        int[] pal = palette();
        int col = bright ? pal[t] : ((pal[t] & 0x00FFFFFF) | 0x44000000);
        for (int[] b : s) {
            float px = ox + b[1] * cs, py = oy + b[0] * cs;
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(col);
            canvas.drawRect(px+g, py+g, px+cs-g, py+cs-g, fill);
            if (bright) {
                fill.setColor(0x30FFFFFF);
                canvas.drawRect(px+g, py+g, px+cs-g, py+g+2f, fill);
                canvas.drawRect(px+g, py+g, px+g+2f, py+cs-g, fill);
            }
        }
    }

    // ── Board ──────────────────────────────────────────────────────
    void drawBoard(Canvas canvas) {
        int[] pal = palette();
        for (int r = 0; r < ROWS; r++) {
            if (isFlashRow(r)) {
                fill.setStyle(Paint.Style.FILL);
                fill.setColor(0xFFFFFFFF);
                float g = Math.max(1.5f, cell * 0.04f);
                for (int c = 0; c < COLS; c++) {
                    float x = bLeft + c*cell, y = bTop + r*cell;
                    canvas.drawRect(x+g, y+g, x+cell-g, y+cell-g, fill);
                }
            } else {
                for (int c = 0; c < COLS; c++)
                    drawCell(canvas, bLeft+c*cell, bTop+r*cell, cell, board[r][c], pal, false);
            }
        }
        if (lockedCells != null && clearingRows == null) {
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(0xBBFFFFFF);
            float g = Math.max(1.5f, cell * 0.04f);
            for (int[] rc : lockedCells) {
                if (rc[0] >= 0 && rc[0] < ROWS) {
                    float x = bLeft+rc[1]*cell, y = bTop+rc[0]*cell;
                    canvas.drawRect(x+g, y+g, x+cell-g, y+cell-g, fill);
                }
            }
        }
    }

    boolean isFlashRow(int r) {
        if (clearingRows == null) return false;
        for (int cr : clearingRows) if (cr == r) return true;
        return false;
    }

    void drawCell(Canvas canvas, float x, float y, float cs, int t, int[] pal, boolean active) {
        fill.setStyle(Paint.Style.FILL);
        if (t == 0) { fill.setColor(BG_BOARD); canvas.drawRect(x, y, x+cs, y+cs, fill); return; }
        float g = Math.max(1.5f, cs * 0.045f);
        int col = pal[t];
        // Base fill — sharp corners
        fill.setColor(col);
        canvas.drawRect(x+g, y+g, x+cs-g, y+cs-g, fill);
        // Thin top-left bright edge
        fill.setColor(active ? 0x50FFFFFF : 0x28FFFFFF);
        canvas.drawRect(x+g, y+g, x+cs-g, y+g+2f, fill);
        canvas.drawRect(x+g, y+g, x+g+2f, y+cs-g, fill);
        // Thin bottom-right dark edge
        fill.setColor(0x40000000);
        canvas.drawRect(x+g, y+cs-g-2f, x+cs-g, y+cs-g, fill);
        canvas.drawRect(x+cs-g-2f, y+g, x+cs-g, y+cs-g, fill);
    }

    void drawGhost(Canvas canvas) {
        if (shape == null) return;
        int gr = ghostRow();
        if (gr == pr) return;
        fill.setStyle(Paint.Style.STROKE);
        fill.setStrokeWidth(1.5f);
        fill.setColor(0x55FFFFFF);
        float g = Math.max(2f, cell * 0.05f);
        for (int[] b : shape) {
            int rr = gr+b[0], c = pc+b[1];
            if (rr < 0) continue;
            float x = bLeft+c*cell, y = bTop+rr*cell;
            canvas.drawRect(x+g, y+g, x+cell-g, y+cell-g, fill);
        }
        fill.setStyle(Paint.Style.FILL);
    }

    void drawPiece(Canvas canvas) {
        if (shape == null) return;
        int[] pal = palette();
        for (int[] b : shape) {
            int r = pr+b[0], c = pc+b[1];
            if (r >= 0) drawCell(canvas, bLeft+c*cell, bTop+r*cell, cell, type, pal, true);
        }
    }

    void drawGrid(Canvas canvas) {
        fill.setStyle(Paint.Style.STROKE);
        fill.setStrokeWidth(0.6f);
        fill.setColor(0xFF111122);
        float right = bLeft+COLS*cell, bottom = bTop+ROWS*cell;
        for (int r = 0; r <= ROWS; r++) canvas.drawLine(bLeft, bTop+r*cell, right, bTop+r*cell, fill);
        for (int c = 0; c <= COLS; c++) canvas.drawLine(bLeft+c*cell, bTop, bLeft+c*cell, bottom, fill);
        fill.setColor(ACCENT_DIM); fill.setStrokeWidth(1.5f);
        canvas.drawRect(bLeft, bTop, right, bottom, fill);
        fill.setStyle(Paint.Style.FILL);
    }

    void drawLockBar(Canvas canvas) {
        if (shape == null) return;
        int bottomRow = pr;
        for (int[] b : shape) bottomRow = Math.max(bottomRow, pr + b[0]);
        float y  = bTop + (bottomRow + 1) * cell - 4f;
        float x0 = bLeft, x1 = bLeft + COLS * cell;
        long elapsed = System.currentTimeMillis() - lockDelayStart;
        float prog = 1f - Math.min(1f, (float) elapsed / lockDelayDuration);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(0x22FFFFFF);
        canvas.drawRect(x0, y, x1, y + 3.5f, fill);
        fill.setColor(ACCENT);
        canvas.drawRect(x0, y, x0 + (x1 - x0) * prog, y + 3.5f, fill);
    }

    // ── Menu Screen ────────────────────────────────────────────────
    void drawMenuScreen(Canvas canvas) {
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(BG);
        canvas.drawRect(0, 0, screenW, screenH, fill);

        float cx = screenW / 2f;

        // Title
        txt.setTextAlign(Paint.Align.CENTER);
        txt.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        txt.setTextSize(cell * 2.3f);
        txt.setColor(TXT_BRIGHT);
        canvas.drawText("TETRIS", cx, screenH * 0.19f, txt);
        txt.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        txt.setTextSize(cell * 0.36f);
        txt.setColor(TXT_DIM);
        canvas.drawText("v1.0", cx, screenH * 0.245f, txt);

        // High score
        if (highScore > 0) {
            txt.setTextSize(cell * 0.26f);
            txt.setColor(TXT_DIM);
            canvas.drawText("BEST", cx, screenH * 0.295f, txt);
            txt.setTextSize(cell * 0.52f);
            txt.setColor(ACCENT);
            txt.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            canvas.drawText(fmtScore(highScore), cx, screenH * 0.340f, txt);
            txt.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        }

        // Mode selector
        float modeTop = screenH * 0.40f;
        txt.setTextSize(cell * 0.30f);
        txt.setColor(TXT_DIM);
        canvas.drawText("SELECT MODE", cx, modeTop - cell * 0.3f, txt);

        String[] modeLabels = {"CLASSIC", "FIXED SPEED", "GARBAGE"};
        boolean[] modeAvail = {true, true, true};
        modeZones = new RectF[3];
        float mW = screenW * 0.27f, mH = cell * 0.85f;
        float spacing = (screenW - mW * 3) / 4f;
        for (int i = 0; i < 3; i++) {
            float mx = spacing + (mW + spacing) * i;
            float my = modeTop;
            modeZones[i] = new RectF(mx, my, mx + mW, my + mH);
            boolean sel = selectedMode == i;
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(sel ? ACCENT : (modeAvail[i] ? ACCENT_DIM : 0xFF0D0D18));
            canvas.drawRoundRect(modeZones[i], cardR * 0.5f, cardR * 0.5f, fill);
            if (!sel) {
                fill.setStyle(Paint.Style.STROKE);
                fill.setStrokeWidth(1f);
                fill.setColor(modeAvail[i] ? ACCENT_DIM : BORDER);
                canvas.drawRoundRect(modeZones[i], cardR * 0.5f, cardR * 0.5f, fill);
                fill.setStyle(Paint.Style.FILL);
            }
            txt.setTextSize(cell * 0.34f);
            txt.setColor(sel ? 0xFFFFFFFF : (modeAvail[i] ? TXT_MID : TXT_DIM));
            txt.setTypeface(sel ? Typeface.create("sans-serif", Typeface.BOLD)
                                : Typeface.create("sans-serif", Typeface.NORMAL));
            canvas.drawText(modeLabels[i], mx + mW / 2f, my + mH * 0.67f, txt);
            txt.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            if (!modeAvail[i]) {
                txt.setTextSize(cell * 0.22f);
                txt.setColor(TXT_DIM);
                canvas.drawText("SOON", mx + mW / 2f, my + mH * 0.90f, txt);
            }
        }

        // Speed selector (fixed mode only)
        float speedTop = modeTop + mH + cell * 0.5f;
        if (selectedMode == 1) {
            speedZones = new RectF[4];
            txt.setTextSize(cell * 0.28f);
            txt.setColor(TXT_DIM);
            canvas.drawText("SPEED", cx, speedTop - cell * 0.15f, txt);
            float sW = screenW * 0.17f, sH = cell * 0.70f;
            float sSpacing = (screenW - sW * 4) / 5f;
            for (int i = 0; i < 4; i++) {
                float sx = sSpacing + (sW + sSpacing) * i;
                float sy = speedTop + cell * 0.05f;
                speedZones[i] = new RectF(sx, sy, sx + sW, sy + sH);
                boolean sel = fixedSpeedIdx == i;
                fill.setStyle(Paint.Style.FILL);
                fill.setColor(sel ? ACCENT : ACCENT_DIM);
                canvas.drawRoundRect(speedZones[i], cardR * 0.4f, cardR * 0.4f, fill);
                txt.setTextSize(cell * 0.30f);
                txt.setColor(sel ? 0xFFFFFFFF : TXT_MID);
                txt.setTypeface(sel ? Typeface.create("sans-serif", Typeface.BOLD)
                                    : Typeface.create("sans-serif", Typeface.NORMAL));
                canvas.drawText(FIXED_LABELS[i], sx + sW / 2f, sy + sH * 0.68f, txt);
                txt.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            }
        } else {
            speedZones = null;
        }

        // PLAY button
        float playY = screenH * 0.64f;
        float playW = screenW * 0.52f, playH = cell * 1.15f;
        playZone = new RectF(cx - playW/2f, playY, cx + playW/2f, playY + playH);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(ACCENT);
        canvas.drawRoundRect(playZone, cardR * 0.7f, cardR * 0.7f, fill);
        fill.setColor(0x22FFFFFF);
        canvas.drawRoundRect(playZone.left, playZone.top, playZone.right,
            playZone.top + playH * 0.45f, cardR * 0.7f, cardR * 0.7f, fill);
        txt.setTextSize(cell * 0.58f);
        txt.setColor(0xFFFFFFFF);
        txt.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        canvas.drawText("PLAY", cx, playY + playH * 0.68f, txt);
        txt.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));

        // Stats button
        float btnY = screenH * 0.78f;
        float btnW = screenW * 0.55f, btnH = cell * 0.88f;
        statsZone = new RectF(cx - btnW/2f, btnY, cx + btnW/2f, btnY + btnH);
        drawCard(canvas, statsZone.left, statsZone.top, statsZone.width(), statsZone.height(), BORDER);
        txt.setTextSize(cell * 0.38f);
        txt.setColor(TXT_MID);
        canvas.drawText("STATS", cx, btnY + btnH * 0.67f, txt);

    }

    RectF pauseSettingsZone;

    // ── Pause Overlay ──────────────────────────────────────────────
    void drawPauseOverlay(Canvas canvas) {
        float right = bLeft + COLS * cell, bottom = bTop + ROWS * cell;
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(0xEE06060F);
        canvas.drawRect(bLeft, bTop, right, bottom, fill);
        float cx = (bLeft + right) / 2f, cy = (bTop + bottom) / 2f;

        txt.setTextAlign(Paint.Align.CENTER);
        txt.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        txt.setTextSize(cell * 1.1f);
        txt.setColor(TXT_BRIGHT);
        canvas.drawText("PAUSED", cx, cy - cell * 1.5f, txt);
        txt.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));

        // Score summary
        txt.setTextSize(cell * 0.38f);
        txt.setColor(TXT_MID);
        canvas.drawText("SCORE  " + fmtScore(score) + "   LVL  " + level, cx, cy - cell * 0.8f, txt);

        // Tap to resume hint
        txt.setTextSize(cell * 0.32f);
        txt.setColor(TXT_DIM);
        canvas.drawText("tap anywhere to resume", cx, cy - cell * 0.38f, txt);

        float bW = cell * 4.0f, bH = cell * 0.78f;
        float gap = cell * 0.32f;
        float totalH = bH * 3 + gap * 2;
        float bT = cy + cell * 0.25f;
        float bL = cx - bW / 2f;

        // RESTART
        restartZone = new RectF(bL, bT, bL + bW, bT + bH);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(ACCENT);
        canvas.drawRect(restartZone, fill);
        txt.setTextSize(cell * 0.38f);
        txt.setColor(0xFFFFFFFF);
        txt.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        canvas.drawText("RESTART", cx, bT + bH * 0.67f, txt);
        txt.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));

        // SETTINGS
        float sT = bT + bH + gap;
        pauseSettingsZone = new RectF(bL, sT, bL + bW, sT + bH);
        drawCard(canvas, bL, sT, bW, bH, BORDER);
        txt.setTextSize(cell * 0.38f);
        txt.setColor(TXT_MID);
        canvas.drawText("\u2699  SETTINGS", cx, sT + bH * 0.67f, txt);

        // MAIN MENU
        float mT = sT + bH + gap;
        menuZone = new RectF(bL, mT, bL + bW, mT + bH);
        drawCard(canvas, bL, mT, bW, bH, BORDER);
        txt.setTextSize(cell * 0.38f);
        txt.setColor(TXT_MID);
        canvas.drawText("MAIN MENU", cx, mT + bH * 0.67f, txt);
    }

    // ── Game Over Screen ───────────────────────────────────────────
    void drawGameOverScreen(Canvas canvas) {
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(0xF206060F);
        canvas.drawRect(0, 0, screenW, screenH, fill);
        float cx = screenW / 2f;

        txt.setTextAlign(Paint.Align.CENTER);
        txt.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        txt.setTextSize(cell * 1.5f);
        txt.setColor(TXT_BRIGHT);
        canvas.drawText("GAME OVER", cx, screenH * 0.18f, txt);
        txt.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));

        // Stats card
        float cX = screenW * 0.09f, cW = screenW * 0.82f;
        float cY = screenH * 0.24f, cH = screenH * 0.40f;
        drawCard(canvas, cX, cY, cW, cH, BORDER);

        String[] labels = {"SCORE", "LEVEL", "LINES", "TIME"};
        String[] values = {String.format("%,d", gameOverDisplay),
            String.valueOf(level), String.valueOf(totalLines), fmtTime(timeSurvived)};
        float rowH = cH / 4f;
        for (int i = 0; i < 4; i++) {
            float ry = cY + rowH * i + rowH * 0.62f;
            txt.setTextSize(cell * 0.33f);
            txt.setColor(TXT_DIM);
            txt.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(labels[i], cX + cW * 0.08f, ry, txt);
            txt.setTextSize(cell * 0.46f);
            txt.setColor(i == 0 ? ACCENT : TXT_BRIGHT);
            txt.setTypeface(i == 0 ? Typeface.create("sans-serif", Typeface.BOLD)
                                   : Typeface.create("sans-serif", Typeface.NORMAL));
            txt.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(values[i], cX + cW * 0.92f, ry, txt);
            txt.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            // "NEW BEST" badge on score row
            if (i == 0 && newHighScore) {
                float bW = cell * 2.0f, bH = cell * 0.38f;
                float bX = cX + cW * 0.08f, bY = ry - rowH * 0.52f;
                fill.setColor(ACCENT);
                canvas.drawRoundRect(bX, bY, bX + bW, bY + bH, bH/2f, bH/2f, fill);
                txt.setTextSize(cell * 0.22f);
                txt.setColor(0xFFFFFFFF);
                txt.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                txt.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("NEW BEST", bX + bW/2f, bY + bH * 0.72f, txt);
                txt.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            }
            if (i < 3) {
                fill.setStyle(Paint.Style.FILL);
                fill.setColor(BORDER);
                canvas.drawRect(cX + cW*0.06f, cY + rowH*(i+1),
                    cX + cW*0.94f, cY + rowH*(i+1) + 1f, fill);
            }
        }

        float btnY = screenH * 0.67f;
        float btnW = screenW * 0.37f, btnH = cell * 0.95f;
        float gap  = screenW * 0.05f;
        restartZone = new RectF(cx - gap/2f - btnW, btnY, cx - gap/2f, btnY + btnH);
        menuZone    = new RectF(cx + gap/2f, btnY, cx + gap/2f + btnW, btnY + btnH);

        fill.setStyle(Paint.Style.FILL);
        fill.setColor(ACCENT);
        canvas.drawRoundRect(restartZone, cardR * 0.5f, cardR * 0.5f, fill);
        txt.setTextSize(cell * 0.40f);
        txt.setColor(0xFFFFFFFF);
        txt.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        txt.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("RESTART", restartZone.centerX(), btnY + btnH * 0.67f, txt);
        txt.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));

        drawCard(canvas, menuZone.left, menuZone.top, menuZone.width(), menuZone.height(), BORDER);
        txt.setTextSize(cell * 0.40f);
        txt.setColor(TXT_MID);
        canvas.drawText("MENU", menuZone.centerX(), btnY + btnH * 0.67f, txt);
    }

    // ── Countdown Overlay ──────────────────────────────────────────
    void drawCountdownOverlay(Canvas canvas) {
        float right = bLeft + COLS*cell, bottom = bTop + ROWS*cell;
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(0xCC06060F);
        canvas.drawRect(bLeft, bTop, right, bottom, fill);
        float cx = (bLeft + right) / 2f, cy = (bTop + bottom) / 2f;
        txt.setTextAlign(Paint.Align.CENTER);
        txt.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        txt.setTextSize(cell * 0.44f);
        txt.setColor(TXT_DIM);
        canvas.drawText("get ready", cx, cy - cell * 1.6f, txt);
        txt.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        txt.setTextSize(cell * 4.2f);
        txt.setColor(ACCENT);
        canvas.drawText(String.valueOf(countdown), cx, cy + cell * 1.6f, txt);
        txt.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
    }

    // ── Settings Panel ─────────────────────────────────────────────
    void drawSettingsPanel(Canvas canvas) {
        float pW = screenW * 0.78f;
        float px = screenW - pW * settingsAnim;

        float panelTop = (state == State.MENU) ? 0 : bTop;

        // dim left area
        int dimA = (int)(settingsAnim * 160);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(Color.argb(dimA, 0, 0, 8));
        canvas.drawRect(0, panelTop, px, screenH, fill);

        // panel bg
        fill.setColor(0xFF0B0B1A);
        canvas.drawRect(px, panelTop, screenW, screenH, fill);
        fill.setStyle(Paint.Style.STROKE);
        fill.setStrokeWidth(1f);
        fill.setColor(BORDER);
        canvas.drawLine(px, panelTop, px, screenH, fill);
        fill.setStyle(Paint.Style.FILL);

        float mg = screenW * 0.055f;
        float cx = pW / 2f;
        float y  = screenH * 0.07f;

        txt.setTextAlign(Paint.Align.LEFT);
        txt.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        txt.setTextSize(cell * 0.62f);
        txt.setColor(TXT_BRIGHT);
        canvas.drawText("SETTINGS", px + mg, y, txt);
        txt.setTextAlign(Paint.Align.RIGHT);
        txt.setTextSize(cell * 0.52f);
        txt.setColor(TXT_MID);
        txt.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        canvas.drawText("\u2715", screenW - mg, y, txt);

        y += cell * 0.8f;

        Object[][] items = {
            {"GAME",         null},
            {"Ghost Piece",  "showGhost"},
            {"Grid Lines",   "showGrid"},
            {"VISUAL",       null},
            {"Reduce Motion","reducedMotion"},
            {"ACCESSIBILITY",null},
            {"High Contrast","highContrast"},
            {"HAPTICS",      null},
            {"Vibration",    "hapticsOn"},
        };

        for (Object[] item : items) {
            String label = (String) item[0];
            String key   = (String) item[1];
            if (key == null) {
                y += cell * 0.18f;
                txt.setTextAlign(Paint.Align.LEFT);
                txt.setTextSize(cell * 0.28f);
                txt.setColor(ACCENT);
                txt.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                canvas.drawText(label, px + mg, y, txt);
                fill.setColor(ACCENT_DIM);
                canvas.drawRect(px + mg, y + cell * 0.08f, screenW - mg, y + cell * 0.09f, fill);
                txt.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                y += cell * 0.55f;
            } else {
                float rH = cell * 0.85f;
                txt.setTextAlign(Paint.Align.LEFT);
                txt.setTextSize(cell * 0.37f);
                txt.setColor(TXT_BRIGHT);
                txt.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                canvas.drawText(label, px + mg, y + rH * 0.63f, txt);

                boolean on = getSetting(key);
                float swW = cell * 1.1f, swH = cell * 0.52f;
                float swX = screenW - mg - swW, swY = y + (rH - swH) / 2f;
                fill.setColor(on ? ACCENT : BORDER);
                canvas.drawRoundRect(swX, swY, swX + swW, swY + swH, swH/2f, swH/2f, fill);
                float kR = swH * 0.40f;
                float kX = on ? swX + swW - kR - swH*0.1f : swX + kR + swH*0.1f;
                fill.setColor(0xFFFFFFFF);
                canvas.drawCircle(kX, swY + swH/2f, kR, fill);

                y += rH;
            }
        }
    }

    boolean getSetting(String key) {
        switch (key) {
            case "showGhost":    return showGhost;
            case "showGrid":     return showGrid;
            case "reducedMotion":return reducedMotion;
            case "highContrast": return highContrast;
            case "hapticsOn":    return hapticsOn;
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
        }
        postInvalidate();
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
                rotate();
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

    void drawStatsOverlay(Canvas canvas) {
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(0xCC000008);
        canvas.drawRect(0, 0, screenW, screenH, fill);

        float cx = screenW / 2f;
        float cW = screenW * 0.84f, cH = screenH * 0.56f;
        float cX = (screenW - cW) / 2f, cY = (screenH - cH) / 2f;
        drawCard(canvas, cX, cY, cW, cH, BORDER);

        txt.setTextAlign(Paint.Align.CENTER);
        txt.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        txt.setTextSize(cell * 0.58f);
        txt.setColor(TXT_BRIGHT);
        canvas.drawText("STATS", cx, cY + cH * 0.14f, txt);
        txt.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));

        String[] slabels = {"BEST SCORE", "GAMES", "TOTAL LINES", "BEST LEVEL", "PIECES"};
        String[] svalues = {
            fmtScore(highScore),
            String.valueOf(statGames),
            String.valueOf(statTotalLines),
            String.valueOf(statBestLevel),
            String.valueOf(statTotalPieces),
        };
        float rowH = cH * 0.13f;
        float startY = cY + cH * 0.22f;
        for (int i = 0; i < 5; i++) {
            float ry = startY + rowH * i + rowH * 0.55f;
            txt.setTextSize(cell * 0.29f);
            txt.setColor(TXT_DIM);
            txt.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(slabels[i], cX + cW * 0.08f, ry, txt);
            txt.setTextSize(cell * 0.42f);
            txt.setColor(i == 0 ? ACCENT : TXT_BRIGHT);
            txt.setTypeface(i == 0 ? Typeface.create("sans-serif", Typeface.BOLD)
                                   : Typeface.create("sans-serif", Typeface.NORMAL));
            txt.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(svalues[i], cX + cW * 0.92f, ry, txt);
            txt.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            if (i < 4) {
                fill.setColor(BORDER);
                canvas.drawRect(cX + cW*0.06f, startY + rowH*(i+1),
                    cX + cW*0.94f, startY + rowH*(i+1) + 1f, fill);
            }
        }
        txt.setTextSize(cell * 0.26f);
        txt.setColor(TXT_DIM);
        txt.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("tap to close", cx, cY + cH * 0.92f, txt);
    }

    void handleMenuTap(float x, float y) {
        if (showStatsOverlay) { showStatsOverlay = false; postInvalidate(); return; }
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
        if (statsZone != null && statsZone.contains(x, y)) { showStatsOverlay = true; postInvalidate(); }
    }

    void handleSettingsTap(float x, float y, float panelX) {
        float mg = screenW * 0.055f;
        // Close X button
        if (y < screenH * 0.12f && x > screenW - mg - cell * 0.6f) { closeSettings(); return; }

        // Reconstruct toggle y positions and check taps
        Object[][] items = {
            {"GAME",         null},
            {"Ghost Piece",  "showGhost"},
            {"Grid Lines",   "showGrid"},
            {"VISUAL",       null},
            {"Reduce Motion","reducedMotion"},
            {"ACCESSIBILITY",null},
            {"High Contrast","highContrast"},
            {"HAPTICS",      null},
            {"Vibration",    "hapticsOn"},
        };
        float fy = screenH * 0.07f + cell * 0.8f;
        for (Object[] item : items) {
            String key = (String) item[1];
            if (key == null) { fy += cell * 0.18f + cell * 0.55f; }
            else {
                float rH = cell * 0.85f;
                if (y >= fy && y <= fy + rH) { toggleSetting(key); return; }
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
