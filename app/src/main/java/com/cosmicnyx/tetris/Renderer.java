package com.cosmicnyx.tetris;

import static com.cosmicnyx.tetris.GameData.*;

import android.graphics.*;

class Renderer {

    final TetrisView v;

    Renderer(TetrisView v) {
        this.v = v;
    }

    // ── Board Container ────────────────────────────────────────────
    void drawBoardContainer(Canvas canvas) {
        float r = v.cardR * 0.4f;
        float x0 = v.bLeft - 2, y0 = v.bTop - 2;
        float x1 = v.bLeft + COLS * v.cell + 2, y1 = v.bTop + ROWS * v.cell + 2;
        v.fill.setStyle(Paint.Style.FILL);
        v.fill.setColor(v.BG_BOARD);
        canvas.drawRoundRect(x0, y0, x1, y1, r, r, v.fill);
        v.fill.setStyle(Paint.Style.STROKE);
        v.fill.setStrokeWidth(1.5f);
        v.fill.setColor(v.BORDER);
        canvas.drawRoundRect(x0, y0, x1, y1, r, r, v.fill);
        v.fill.setStyle(Paint.Style.FILL);
    }

    // ── Garbage Indicator ──────────────────────────────────────────
    void drawGarbageIndicator(Canvas canvas) {
        if (v.selectedMode != 2 || v.state != TetrisView.State.PLAYING) return;
        long now = System.currentTimeMillis();
        float prog = v.nextGarbageAt > 0
            ? Math.max(0f, Math.min(1f, (float)(v.nextGarbageAt - now) / v.garbageInterval()))
            : 1f;
        float barX = v.bLeft - 7f;
        float barW = 4.5f;
        float barH = ROWS * v.cell;
        v.fill.setStyle(Paint.Style.FILL);
        v.fill.setColor(0x22FF2222);
        canvas.drawRect(barX, v.bTop, barX + barW, v.bTop + barH, v.fill);
        float fillH = barH * (1f - prog);
        int alpha = prog < 0.25f ? 0xFF : 0xAA;
        v.fill.setColor(Color.argb(alpha, 0xFF, 0x33, 0x22));
        canvas.drawRect(barX, v.bTop + barH - fillH, barX + barW, v.bTop + barH, v.fill);
        if (prog < 0.15f) {
            v.txt.setTextAlign(Paint.Align.LEFT);
            v.txt.setTypeface(Typeface.create(v.fontName(), Typeface.BOLD));
            v.txt.setTextSize(v.cell * 0.24f);
            v.txt.setColor(Color.argb((int)((0.15f - prog) / 0.15f * 200), 0xFF, 0x44, 0x33));
            canvas.drawText("!", barX - v.cell * 0.1f, v.bTop + barH * 0.5f, v.txt);
            v.txt.setTypeface(Typeface.create(v.fontName(), Typeface.NORMAL));
        }
    }

    // ── Info Bar ───────────────────────────────────────────────────
    void drawInfoBar(Canvas canvas) {
        float barH = v.bTop;

        v.fill.setStyle(Paint.Style.FILL);
        v.fill.setColor(v.BG_CARD);
        canvas.drawRect(0, 0, v.screenW, barH, v.fill);
        v.fill.setColor(v.BORDER);
        canvas.drawRect(0, barH - 1.5f, v.screenW, barH, v.fill);

        String[] labels = {"SCORE", "LEVEL", "LINES"};
        String[] values = {v.fmtScore(v.score), String.valueOf(v.level), String.valueOf(v.totalLines)};
        float sw = v.screenW / 3f;

        for (int i = 0; i < 3; i++) {
            float cx = sw * i + sw / 2f;
            if (i > 0) {
                v.fill.setColor(v.BORDER);
                canvas.drawRect(sw * i - 0.75f, barH * 0.15f, sw * i + 0.75f, barH * 0.85f, v.fill);
            }
            v.txt.setTextAlign(Paint.Align.CENTER);
            v.txt.setTypeface(Typeface.create(v.fontName(), Typeface.NORMAL));
            v.txt.setTextSize(barH * 0.28f);
            v.txt.setColor(v.TXT_MID);
            canvas.drawText(labels[i], cx, barH * 0.32f, v.txt);
            v.txt.setTextSize(barH * 0.50f);
            v.txt.setColor(i == 0 ? v.ACCENT : v.TXT_BRIGHT);
            v.txt.setTypeface(Typeface.create(v.fontName(), Typeface.BOLD));
            canvas.drawText(values[i], cx, barH * 0.84f, v.txt);
        }
        v.txt.setTypeface(Typeface.create(v.fontName(), Typeface.NORMAL));

        if (v.state == TetrisView.State.PLAYING || v.state == TetrisView.State.PAUSED) {
            float bcx = v.screenW - barH * 0.46f;
            float bcy = barH * 0.50f;
            float br  = barH * 0.22f;
            v.fill.setStyle(Paint.Style.FILL);
            v.fill.setColor(0x28FFFFFF);
            canvas.drawCircle(bcx, bcy, br * 1.5f, v.fill);
            v.fill.setColor(v.TXT_MID);
            if (v.state == TetrisView.State.PAUSED) {
                float ts = br * 0.75f;
                Path tri = new Path();
                tri.moveTo(bcx - ts * 0.55f, bcy - ts);
                tri.lineTo(bcx - ts * 0.55f, bcy + ts);
                tri.lineTo(bcx + ts * 0.9f, bcy);
                tri.close();
                canvas.drawPath(tri, v.fill);
            } else {
                float bw = br * 0.30f, bh = br * 0.85f, bg = br * 0.25f;
                canvas.drawRect(bcx - bg - bw, bcy - bh, bcx - bg, bcy + bh, v.fill);
                canvas.drawRect(bcx + bg,      bcy - bh, bcx + bg + bw, bcy + bh, v.fill);
            }
        }
    }

    // ── Hold Panel ─────────────────────────────────────────────────
    void drawHoldPanel(Canvas canvas) {
        float fH  = v.screenH - v.footerTop;
        if (fH < 4) return;
        float pW  = v.screenW * 0.26f;
        float pX  = v.screenW * 0.01f;
        float pY  = v.footerTop + fH * 0.06f;
        float pH  = fH * 0.88f;
        drawCard(canvas, pX, pY, pW, pH, v.canHold ? v.ACCENT_DIM : v.BORDER);

        float lH = pH * 0.32f;
        v.txt.setTextSize(lH * 0.52f);
        v.txt.setColor(v.canHold ? v.TXT_MID : v.TXT_DIM);
        v.txt.setTextAlign(Paint.Align.CENTER);
        v.txt.setTypeface(Typeface.create(v.fontName(), Typeface.NORMAL));
        canvas.drawText("HOLD", pX + pW / 2f, pY + lH * 0.72f, v.txt);
        if (v.holdType != 0) {
            float cs = Math.min(pW / 5.5f, (pH - lH) / 2.3f);
            drawMini(canvas, v.holdType, pX + pW / 2f, pY + lH + (pH - lH) * 0.55f, cs, v.canHold);
        }
    }

    // ── Next Panel ─────────────────────────────────────────────────
    void drawNextPanel(Canvas canvas) {
        float fH  = v.screenH - v.footerTop;
        if (fH < 4) return;
        float pX  = v.screenW * 0.29f;
        float pW  = v.screenW * 0.70f;
        float pY  = v.footerTop + fH * 0.06f;
        float pH  = fH * 0.88f;
        drawCard(canvas, pX, pY, pW, pH, v.BORDER);

        float lH = pH * 0.32f;
        v.txt.setTextSize(lH * 0.52f);
        v.txt.setColor(v.TXT_MID);
        v.txt.setTextAlign(Paint.Align.LEFT);
        v.txt.setTypeface(Typeface.create(v.fontName(), Typeface.NORMAL));
        canvas.drawText("NEXT", pX + pW * 0.05f, pY + lH * 0.72f, v.txt);

        float slotW = pW / 4f;
        float cs    = Math.min(slotW / 5.5f, (pH - lH) / 2.3f);
        float cy    = pY + lH + (pH - lH) * 0.55f;
        for (int i = 0; i < 4; i++)
            drawMini(canvas, v.nextQueue[i], pX + slotW * i + slotW / 2f, cy, cs, true);
        v.txt.setTextAlign(Paint.Align.CENTER);
    }

    void drawCard(Canvas canvas, float x, float y, float w, float h, int borderColor) {
        float r = v.cardR * 0.6f;
        v.fill.setStyle(Paint.Style.FILL);
        v.fill.setColor(v.BG_CARD);
        canvas.drawRoundRect(x, y, x + w, y + h, r, r, v.fill);
        v.fill.setStyle(Paint.Style.STROKE);
        v.fill.setStrokeWidth(1f);
        v.fill.setColor(borderColor);
        canvas.drawRoundRect(x, y, x + w, y + h, r, r, v.fill);
        v.fill.setStyle(Paint.Style.FILL);
    }

    void drawMini(Canvas canvas, int t, float cx, float cy, float cs, boolean bright) {
        if (t == 0) return;
        int[][] s = BASE[t];
        int maxC = 0, maxR = 0;
        for (int[] b : s) { maxC = Math.max(maxC, b[1]); maxR = Math.max(maxR, b[0]); }
        float ox = cx - cs * (maxC + 1) / 2f;
        float oy = cy - cs * (maxR + 1) / 2f;
        if (v.colorTheme == 1) {  // Grayscale mini: hollow + dot
            float g = Math.max(1f, cs * 0.07f);
            float sw = Math.max(1.2f, cs * 0.06f);
            float cr = cs * 0.18f;
            int col = bright ? v.TXT_BRIGHT : v.TXT_DIM;
            for (int[] b : s) {
                float px = ox + b[1] * cs, py = oy + b[0] * cs;
                v.fill.setStyle(Paint.Style.STROKE);
                v.fill.setStrokeWidth(sw);
                v.fill.setColor(col);
                canvas.drawRoundRect(px+g, py+g, px+cs-g, py+cs-g, cr, cr, v.fill);
                v.fill.setStyle(Paint.Style.FILL);
                canvas.drawCircle(px + cs/2f, py + cs/2f, cs * 0.09f, v.fill);
            }
            return;
        }
        if (v.colorTheme == 3) {  // ASCII mini
            v.txt.setTypeface(Typeface.create("monospace", Typeface.NORMAL));
            v.txt.setTextSize(cs * 0.68f);
            v.txt.setColor(bright ? v.ACCENT : v.withAlpha(v.TXT_DIM, 0x88));
            v.txt.setTextAlign(Paint.Align.CENTER);
            for (int[] b : s) {
                float px = ox + b[1] * cs + cs / 2f;
                float py = oy + b[0] * cs + cs * 0.70f;
                canvas.drawText("[]", px, py, v.txt);
            }
            return;
        }
        float g = Math.max(1f, cs * 0.06f);
        int[] pal = v.palette();
        int col = bright ? pal[t] : ((pal[t] & 0x00FFFFFF) | 0x44000000);
        for (int[] b : s) {
            float px = ox + b[1] * cs, py = oy + b[0] * cs;
            v.fill.setStyle(Paint.Style.FILL);
            v.fill.setColor(col);
            canvas.drawRect(px+g, py+g, px+cs-g, py+cs-g, v.fill);
            if (bright) {
                v.fill.setColor(0x30FFFFFF);
                canvas.drawRect(px+g, py+g, px+cs-g, py+g+2f, v.fill);
                canvas.drawRect(px+g, py+g, px+g+2f, py+cs-g, v.fill);
            }
        }
    }

    // ── Board ──────────────────────────────────────────────────────
    void drawBoard(Canvas canvas) {
        int[] pal = v.palette();
        for (int r = 0; r < ROWS; r++) {
            if (isFlashRow(r)) {
                v.fill.setStyle(Paint.Style.FILL);
                v.fill.setColor(0xFFFFFFFF);
                float g = Math.max(1.5f, v.cell * 0.04f);
                for (int c = 0; c < COLS; c++) {
                    float x = v.bLeft + c*v.cell, y = v.bTop + r*v.cell;
                    canvas.drawRect(x+g, y+g, x+v.cell-g, y+v.cell-g, v.fill);
                }
            } else {
                for (int c = 0; c < COLS; c++)
                    drawCell(canvas, v.bLeft+c*v.cell, v.bTop+r*v.cell, v.cell, v.board[r][c], pal, false);
            }
        }
        if (v.lockedCells != null && v.clearingRows == null) {
            v.fill.setStyle(Paint.Style.FILL);
            v.fill.setColor(0xBBFFFFFF);
            float g = Math.max(1.5f, v.cell * 0.04f);
            for (int[] rc : v.lockedCells) {
                if (rc[0] >= 0 && rc[0] < ROWS) {
                    float x = v.bLeft+rc[1]*v.cell, y = v.bTop+rc[0]*v.cell;
                    canvas.drawRect(x+g, y+g, x+v.cell-g, y+v.cell-g, v.fill);
                }
            }
        }
    }

    boolean isFlashRow(int r) {
        if (v.clearingRows == null) return false;
        for (int cr : v.clearingRows) if (cr == r) return true;
        return false;
    }

    void drawCell(Canvas canvas, float x, float y, float cs, int t, int[] pal, boolean active) {
        v.fill.setStyle(Paint.Style.FILL);
        if (v.colorTheme == 1) {  // Grayscale: hollow block + center dot
            if (t == 0) { v.fill.setColor(v.BG_BOARD); canvas.drawRect(x, y, x+cs, y+cs, v.fill); return; }
            float g = Math.max(2f, cs * 0.07f);
            float sw = Math.max(1.5f, cs * 0.06f);
            float cr = cs * 0.18f;
            v.fill.setStyle(Paint.Style.STROKE);
            v.fill.setStrokeWidth(sw);
            v.fill.setColor(active ? v.TXT_BRIGHT : v.TXT_MID);
            canvas.drawRoundRect(x+g, y+g, x+cs-g, y+cs-g, cr, cr, v.fill);
            v.fill.setStyle(Paint.Style.FILL);
            v.fill.setColor(active ? v.TXT_BRIGHT : v.TXT_MID);
            canvas.drawCircle(x + cs/2f, y + cs/2f, cs * 0.09f, v.fill);
            return;
        }
        if (v.colorTheme == 2) {  // Game Boy: flat pixel blocks, no rounding
            if (t == 0) { v.fill.setColor(v.BG_BOARD); canvas.drawRect(x, y, x+cs, y+cs, v.fill); return; }
            float g = Math.max(2f, cs * 0.06f);
            v.fill.setColor(pal[t]);
            canvas.drawRect(x+g, y+g, x+cs-g, y+cs-g, v.fill);
            // Subtle raised-pixel inner frame: lighter top+left edges
            float s = Math.max(1.5f, cs * 0.05f);
            v.fill.setColor(active ? v.BORDER : v.withAlpha(v.BORDER, 0xAA));
            canvas.drawRect(x+g, y+g, x+cs-g, y+g+s, v.fill);
            canvas.drawRect(x+g, y+g, x+g+s, y+cs-g, v.fill);
            return;
        }
        if (v.colorTheme == 3) {  // ASCII: render chars
            v.txt.setTypeface(Typeface.create("monospace", Typeface.NORMAL));
            v.txt.setTextAlign(Paint.Align.CENTER);
            if (t == 0) {
                v.txt.setTextSize(cs * 0.48f);
                v.txt.setColor(v.TXT_DIM);
                canvas.drawText(".", x + cs / 2f, y + cs * 0.70f, v.txt);
            } else {
                v.txt.setTextSize(cs * 0.68f);
                v.txt.setColor(active ? v.ACCENT : v.TXT_MID);
                canvas.drawText("[]", x + cs / 2f, y + cs * 0.70f, v.txt);
            }
            return;
        }
        if (t == 0) { v.fill.setColor(v.BG_BOARD); canvas.drawRect(x, y, x+cs, y+cs, v.fill); return; }
        float g = Math.max(1.5f, cs * 0.045f);
        float r = cs * 0.18f;
        int col = pal[t];
        v.fill.setColor(col);
        canvas.drawRoundRect(x+g, y+g, x+cs-g, y+cs-g, r, r, v.fill);
        v.fill.setColor(active ? 0x50FFFFFF : 0x28FFFFFF);
        canvas.drawRect(x+g, y+g, x+cs-g, y+g+2f, v.fill);
        canvas.drawRect(x+g, y+g, x+g+2f, y+cs-g, v.fill);
        v.fill.setColor(0x40000000);
        canvas.drawRect(x+g, y+cs-g-2f, x+cs-g, y+cs-g, v.fill);
        canvas.drawRect(x+cs-g-2f, y+g, x+cs-g, y+cs-g, v.fill);
    }

    void drawGhost(Canvas canvas) {
        if (v.shape == null) return;
        int gr = v.ghostRow();
        if (gr == v.pr) return;
        if (v.colorTheme == 3) {  // ASCII ghost
            v.txt.setTypeface(Typeface.create("monospace", Typeface.NORMAL));
            v.txt.setTextSize(v.cell * 0.68f);
            v.txt.setColor(v.withAlpha(v.ACCENT, 0x44));
            v.txt.setTextAlign(Paint.Align.CENTER);
            for (int[] b : v.shape) {
                int rr = gr+b[0], c = v.pc+b[1];
                if (rr < 0) continue;
                canvas.drawText("[]",
                    v.bLeft + c*v.cell + v.cell/2f,
                    v.bTop  + rr*v.cell + v.cell*0.70f, v.txt);
            }
            return;
        }
        v.fill.setStyle(Paint.Style.STROKE);
        v.fill.setStrokeWidth(1.5f);
        int ghostCol = v.colorTheme == 2 ? v.withAlpha(v.ACCENT, 0x66)
                     : v.colorTheme == 1 ? v.withAlpha(v.TXT_DIM,  0xAA)
                     : 0x55FFFFFF;
        v.fill.setColor(ghostCol);
        float g = Math.max(2f, v.cell * 0.05f);
        for (int[] b : v.shape) {
            int rr = gr+b[0], c = v.pc+b[1];
            if (rr < 0) continue;
            float x = v.bLeft+c*v.cell, y = v.bTop+rr*v.cell;
            canvas.drawRect(x+g, y+g, x+v.cell-g, y+v.cell-g, v.fill);
        }
        v.fill.setStyle(Paint.Style.FILL);
    }

    void drawPiece(Canvas canvas) {
        if (v.shape == null) return;
        int[] pal = v.palette();
        for (int[] b : v.shape) {
            int r = v.pr+b[0], c = v.pc+b[1];
            if (r >= 0) drawCell(canvas, v.bLeft+c*v.cell, v.bTop+r*v.cell, v.cell, v.type, pal, true);
        }
    }

    void drawGrid(Canvas canvas) {
        if (v.colorTheme == 3) return;  // ASCII: chars define the grid
        v.fill.setStyle(Paint.Style.STROKE);
        v.fill.setStrokeWidth(0.6f);
        v.fill.setColor(v.BORDER);
        float right = v.bLeft+COLS*v.cell, bottom = v.bTop+ROWS*v.cell;
        for (int r = 0; r <= ROWS; r++) canvas.drawLine(v.bLeft, v.bTop+r*v.cell, right, v.bTop+r*v.cell, v.fill);
        for (int c = 0; c <= COLS; c++) canvas.drawLine(v.bLeft+c*v.cell, v.bTop, v.bLeft+c*v.cell, bottom, v.fill);
        v.fill.setColor(v.ACCENT_DIM); v.fill.setStrokeWidth(1.5f);
        canvas.drawRect(v.bLeft, v.bTop, right, bottom, v.fill);
        v.fill.setStyle(Paint.Style.FILL);
    }

    void drawLockBar(Canvas canvas) {
        if (v.shape == null) return;
        int bottomRow = v.pr;
        for (int[] b : v.shape) bottomRow = Math.max(bottomRow, v.pr + b[0]);
        float y  = v.bTop + (bottomRow + 1) * v.cell - 4f;
        float x0 = v.bLeft, x1 = v.bLeft + COLS * v.cell;
        long elapsed = System.currentTimeMillis() - v.lockDelayStart;
        float prog = 1f - Math.min(1f, (float) elapsed / v.lockDelayDuration);
        v.fill.setStyle(Paint.Style.FILL);
        v.fill.setColor(0x22FFFFFF);
        canvas.drawRect(x0, y, x1, y + 3.5f, v.fill);
        v.fill.setColor(v.ACCENT);
        canvas.drawRect(x0, y, x0 + (x1 - x0) * prog, y + 3.5f, v.fill);
    }

    // ── Menu Decoration ────────────────────────────────────────────
    void drawMenuDecoration(Canvas canvas) {
        int[] pal = v.palette();
        float cs = v.cell * 1.35f;
        float g = cs * 0.1f, cr = cs * 0.2f;
        // Hardcoded piece shapes placed at screen corners
        int[][] pA = {{0,0},{1,0},{2,0},{3,0}};  // I vertical
        int[][] pB = {{0,0},{0,1},{1,0},{1,1}};  // O
        int[][] pC = {{0,0},{1,0},{1,1},{1,2}};  // J-ish
        int[][] pD = {{0,1},{0,2},{1,0},{1,1}};  // S
        int[][] pE = {{0,0},{0,1},{1,1},{2,1}};  // L-ish
        int[][][] pieces = {pA, pB, pC, pD, pE};
        int[] types  = {1, 2, 3, 5, 6};
        float[][] ox = {
            {v.screenW * 0.86f, v.screenH * 0.02f},
            {-cs * 0.3f,        v.screenH * 0.02f},
            {-cs * 0.4f,        v.screenH * 0.74f},
            {v.screenW * 0.80f, v.screenH * 0.80f},
            {v.screenW * 0.83f, v.screenH * 0.44f},
        };
        v.fill.setStyle(Paint.Style.FILL);
        for (int i = 0; i < pieces.length; i++) {
            int col = v.withAlpha(pal[types[i]], 0x22);
            if (v.colorTheme == 3) {
                v.txt.setTypeface(Typeface.create("monospace", Typeface.NORMAL));
                v.txt.setTextSize(cs * 0.65f);
                v.txt.setColor(col);
                v.txt.setTextAlign(Paint.Align.CENTER);
                for (int[] b : pieces[i]) {
                    canvas.drawText("[]",
                        ox[i][0] + b[1] * cs + cs / 2f,
                        ox[i][1] + b[0] * cs + cs * 0.70f, v.txt);
                }
            } else {
                v.fill.setColor(col);
                for (int[] b : pieces[i]) {
                    float px = ox[i][0] + b[1] * cs, py = ox[i][1] + b[0] * cs;
                    canvas.drawRoundRect(px+g, py+g, px+cs-g, py+cs-g, cr, cr, v.fill);
                }
            }
        }
    }

    // ── Menu Screen ────────────────────────────────────────────────
    void drawMenuScreen(Canvas canvas) {
        v.fill.setStyle(Paint.Style.FILL);
        v.fill.setColor(v.BG);
        canvas.drawRect(0, 0, v.screenW, v.screenH, v.fill);
        drawMenuDecoration(canvas);

        boolean ascii = v.colorTheme == 3;
        float cx = v.screenW / 2f;

        // Title
        v.txt.setTextAlign(Paint.Align.CENTER);
        v.txt.setTypeface(Typeface.create(v.fontName(), Typeface.BOLD));
        v.txt.setTextSize(v.cell * 2.3f);
        v.txt.setColor(v.TXT_BRIGHT);
        float titleY = v.screenH * 0.19f;
        canvas.drawText(ascii ? "[TETRIS]" : "TETRIS", cx, titleY, v.txt);

        // Accent bar under title
        float barW = ascii ? v.cell * 5.5f : v.cell * 3.0f;
        float barY = titleY + v.cell * 0.35f;
        v.fill.setStyle(Paint.Style.FILL);
        v.fill.setColor(v.ACCENT);
        canvas.drawRect(cx - barW/2f, barY, cx + barW/2f, barY + v.cell * 0.07f, v.fill);

        v.txt.setTypeface(Typeface.create(v.fontName(), Typeface.NORMAL));
        v.txt.setTextSize(v.cell * 0.36f);
        v.txt.setColor(v.TXT_DIM);
        canvas.drawText(ascii ? ">> v1.4 <<" : "v1.4", cx, v.screenH * 0.26f, v.txt);

        if (v.highScore > 0) {
            v.txt.setTextSize(v.cell * 0.26f);
            v.txt.setColor(v.TXT_DIM);
            canvas.drawText("BEST", cx, v.screenH * 0.295f, v.txt);
            v.txt.setTextSize(v.cell * 0.52f);
            v.txt.setColor(v.ACCENT);
            v.txt.setTypeface(Typeface.create(v.fontName(), Typeface.BOLD));
            canvas.drawText(v.fmtScore(v.highScore), cx, v.screenH * 0.340f, v.txt);
            v.txt.setTypeface(Typeface.create(v.fontName(), Typeface.NORMAL));
        }

        float modeTop = v.screenH * 0.40f;
        v.txt.setTextSize(v.cell * 0.30f);
        v.txt.setColor(v.TXT_DIM);
        canvas.drawText("SELECT MODE", cx, modeTop - v.cell * 0.3f, v.txt);

        String[] modeLabels = ascii
            ? new String[]{"[CLASSIC]", "[F-SPEED]", "[GARBAGE]"}
            : new String[]{"CLASSIC",   "FIXED SPEED", "GARBAGE"};
        boolean[] modeAvail = {true, true, true};
        v.modeZones = new RectF[3];
        float mW = v.screenW * 0.27f, mH = v.cell * 0.85f;
        float spacing = (v.screenW - mW * 3) / 4f;
        for (int i = 0; i < 3; i++) {
            float mx = spacing + (mW + spacing) * i;
            float my = modeTop;
            v.modeZones[i] = new RectF(mx, my, mx + mW, my + mH);
            boolean sel = v.selectedMode == i;
            v.fill.setStyle(Paint.Style.FILL);
            v.fill.setColor(sel ? v.ACCENT : (modeAvail[i] ? v.ACCENT_DIM : v.BG_CARD));
            canvas.drawRoundRect(v.modeZones[i], v.cardR * 0.5f, v.cardR * 0.5f, v.fill);
            if (!sel) {
                v.fill.setStyle(Paint.Style.STROKE);
                v.fill.setStrokeWidth(1f);
                v.fill.setColor(modeAvail[i] ? v.ACCENT_DIM : v.BORDER);
                canvas.drawRoundRect(v.modeZones[i], v.cardR * 0.5f, v.cardR * 0.5f, v.fill);
                v.fill.setStyle(Paint.Style.FILL);
            }
            v.txt.setTextSize(v.cell * 0.34f);
            v.txt.setColor(sel ? v.ON_ACCENT : (modeAvail[i] ? v.TXT_MID : v.TXT_DIM));
            v.txt.setTypeface(sel ? Typeface.create(v.fontName(), Typeface.BOLD)
                                  : Typeface.create(v.fontName(), Typeface.NORMAL));
            canvas.drawText(modeLabels[i], mx + mW / 2f, my + mH * 0.67f, v.txt);
            v.txt.setTypeface(Typeface.create(v.fontName(), Typeface.NORMAL));
            if (!modeAvail[i]) {
                v.txt.setTextSize(v.cell * 0.22f);
                v.txt.setColor(v.TXT_DIM);
                canvas.drawText("SOON", mx + mW / 2f, my + mH * 0.90f, v.txt);
            }
        }

        float speedTop = modeTop + mH + v.cell * 0.5f;
        if (v.selectedMode == 1) {
            v.speedZones = new RectF[4];
            v.txt.setTextSize(v.cell * 0.28f);
            v.txt.setColor(v.TXT_DIM);
            canvas.drawText("SPEED", cx, speedTop - v.cell * 0.15f, v.txt);
            float sW = v.screenW * 0.17f, sH = v.cell * 0.70f;
            float sSpacing = (v.screenW - sW * 4) / 5f;
            for (int i = 0; i < 4; i++) {
                float sx = sSpacing + (sW + sSpacing) * i;
                float sy = speedTop + v.cell * 0.05f;
                v.speedZones[i] = new RectF(sx, sy, sx + sW, sy + sH);
                boolean sel = v.fixedSpeedIdx == i;
                v.fill.setStyle(Paint.Style.FILL);
                v.fill.setColor(sel ? v.ACCENT : v.ACCENT_DIM);
                canvas.drawRoundRect(v.speedZones[i], v.cardR * 0.4f, v.cardR * 0.4f, v.fill);
                v.txt.setTextSize(v.cell * 0.30f);
                v.txt.setColor(sel ? v.ON_ACCENT : v.TXT_MID);
                v.txt.setTypeface(sel ? Typeface.create(v.fontName(), Typeface.BOLD)
                                      : Typeface.create(v.fontName(), Typeface.NORMAL));
                canvas.drawText(FIXED_LABELS[i], sx + sW / 2f, sy + sH * 0.68f, v.txt);
                v.txt.setTypeface(Typeface.create(v.fontName(), Typeface.NORMAL));
            }
        } else {
            v.speedZones = null;
        }

        float playY = v.screenH * 0.64f;
        float playW = v.screenW * 0.52f, playH = v.cell * 1.15f;
        v.playZone = new RectF(cx - playW/2f, playY, cx + playW/2f, playY + playH);
        v.fill.setStyle(Paint.Style.FILL);
        v.fill.setColor(v.ACCENT);
        canvas.drawRoundRect(v.playZone, v.cardR * 0.7f, v.cardR * 0.7f, v.fill);
        v.fill.setColor(0x22FFFFFF);
        canvas.drawRoundRect(v.playZone.left, v.playZone.top, v.playZone.right,
            v.playZone.top + playH * 0.45f, v.cardR * 0.7f, v.cardR * 0.7f, v.fill);
        v.txt.setTextSize(v.cell * 0.58f);
        v.txt.setColor(v.ON_ACCENT);
        v.txt.setTypeface(Typeface.create(v.fontName(), Typeface.BOLD));
        canvas.drawText(ascii ? "[ PLAY ]" : "PLAY", cx, playY + playH * 0.68f, v.txt);
        v.txt.setTypeface(Typeface.create(v.fontName(), Typeface.NORMAL));

        float btnY  = v.screenH * 0.78f;
        float btnH  = v.cell * 0.88f;
        float btnW  = v.screenW * 0.40f;
        float gap   = v.screenW * 0.04f;
        float totalW = btnW * 2 + gap;
        float startX = cx - totalW / 2f;

        v.statsZone = new RectF(startX, btnY, startX + btnW, btnY + btnH);
        drawCard(canvas, v.statsZone.left, v.statsZone.top, v.statsZone.width(), v.statsZone.height(), v.BORDER);
        v.txt.setTextSize(v.cell * 0.38f);
        v.txt.setColor(v.TXT_MID);
        canvas.drawText(ascii ? "[STATS]" : "STATS", v.statsZone.centerX(), btnY + btnH * 0.67f, v.txt);

        float sX = startX + btnW + gap;
        v.menuSettingsZone = new RectF(sX, btnY, sX + btnW, btnY + btnH);
        drawCard(canvas, v.menuSettingsZone.left, v.menuSettingsZone.top, v.menuSettingsZone.width(), v.menuSettingsZone.height(), v.BORDER);
        v.txt.setTextSize(v.cell * 0.38f);
        v.txt.setColor(v.TXT_MID);
        canvas.drawText(ascii ? "[SETTINGS]" : "\u2699  SETTINGS", v.menuSettingsZone.centerX(), btnY + btnH * 0.67f, v.txt);
    }

    // ── Pause Overlay ──────────────────────────────────────────────
    void drawPauseOverlay(Canvas canvas) {
        float right = v.bLeft + COLS * v.cell, bottom = v.bTop + ROWS * v.cell;
        v.fill.setStyle(Paint.Style.FILL);
        v.fill.setColor(v.withAlpha(v.BG, 0xEE));
        canvas.drawRect(v.bLeft, v.bTop, right, bottom, v.fill);
        float cx = (v.bLeft + right) / 2f, cy = (v.bTop + bottom) / 2f;

        v.txt.setTextAlign(Paint.Align.CENTER);
        v.txt.setTypeface(Typeface.create(v.fontName(), Typeface.BOLD));
        v.txt.setTextSize(v.cell * 1.1f);
        v.txt.setColor(v.TXT_BRIGHT);
        canvas.drawText("PAUSED", cx, cy - v.cell * 1.5f, v.txt);
        v.txt.setTypeface(Typeface.create(v.fontName(), Typeface.NORMAL));

        v.txt.setTextSize(v.cell * 0.38f);
        v.txt.setColor(v.TXT_MID);
        canvas.drawText("SCORE  " + v.fmtScore(v.score) + "   LVL  " + v.level, cx, cy - v.cell * 0.8f, v.txt);

        v.txt.setTextSize(v.cell * 0.32f);
        v.txt.setColor(v.TXT_DIM);
        canvas.drawText("tap anywhere to resume", cx, cy - v.cell * 0.38f, v.txt);

        float bW = v.cell * 4.0f, bH = v.cell * 0.78f;
        float gap = v.cell * 0.32f;
        float bT = cy + v.cell * 0.25f;
        float bL = cx - bW / 2f;

        v.restartZone = new RectF(bL, bT, bL + bW, bT + bH);
        v.fill.setStyle(Paint.Style.FILL);
        v.fill.setColor(v.ACCENT);
        canvas.drawRect(v.restartZone, v.fill);
        v.txt.setTextSize(v.cell * 0.38f);
        v.txt.setColor(v.ON_ACCENT);
        v.txt.setTypeface(Typeface.create(v.fontName(), Typeface.BOLD));
        canvas.drawText("RESTART", cx, bT + bH * 0.67f, v.txt);
        v.txt.setTypeface(Typeface.create(v.fontName(), Typeface.NORMAL));

        float sT = bT + bH + gap;
        v.pauseSettingsZone = new RectF(bL, sT, bL + bW, sT + bH);
        drawCard(canvas, bL, sT, bW, bH, v.BORDER);
        v.txt.setTextSize(v.cell * 0.38f);
        v.txt.setColor(v.TXT_MID);
        canvas.drawText("\u2699  SETTINGS", cx, sT + bH * 0.67f, v.txt);

        float mT = sT + bH + gap;
        v.menuZone = new RectF(bL, mT, bL + bW, mT + bH);
        drawCard(canvas, bL, mT, bW, bH, v.BORDER);
        v.txt.setTextSize(v.cell * 0.38f);
        v.txt.setColor(v.TXT_MID);
        canvas.drawText("MAIN MENU", cx, mT + bH * 0.67f, v.txt);
    }

    // ── Countdown Overlay ──────────────────────────────────────────
    void drawCountdownOverlay(Canvas canvas) {
        float right = v.bLeft + COLS*v.cell, bottom = v.bTop + ROWS*v.cell;
        v.fill.setStyle(Paint.Style.FILL);
        v.fill.setColor(v.withAlpha(v.BG, 0xCC));
        canvas.drawRect(v.bLeft, v.bTop, right, bottom, v.fill);
        float cx = (v.bLeft + right) / 2f, cy = (v.bTop + bottom) / 2f;
        v.txt.setTextAlign(Paint.Align.CENTER);
        v.txt.setTypeface(Typeface.create(v.fontName(), Typeface.NORMAL));
        v.txt.setTextSize(v.cell * 0.44f);
        v.txt.setColor(v.TXT_DIM);
        canvas.drawText("get ready", cx, cy - v.cell * 1.6f, v.txt);
        v.txt.setTypeface(Typeface.create(v.fontName(), Typeface.BOLD));
        v.txt.setTextSize(v.cell * 4.2f);
        v.txt.setColor(v.ACCENT);
        canvas.drawText(String.valueOf(v.countdown), cx, cy + v.cell * 1.6f, v.txt);
        v.txt.setTypeface(Typeface.create(v.fontName(), Typeface.NORMAL));
    }

    // ── Game Over Screen ───────────────────────────────────────────
    void drawGameOverScreen(Canvas canvas) {
        v.fill.setStyle(Paint.Style.FILL);
        v.fill.setColor(v.withAlpha(v.BG, 0xF2));
        canvas.drawRect(0, 0, v.screenW, v.screenH, v.fill);
        float cx = v.screenW / 2f;

        v.txt.setTextAlign(Paint.Align.CENTER);
        v.txt.setTypeface(Typeface.create(v.fontName(), Typeface.BOLD));
        v.txt.setTextSize(v.cell * 1.5f);
        v.txt.setColor(v.TXT_BRIGHT);
        canvas.drawText("GAME OVER", cx, v.screenH * 0.18f, v.txt);
        v.txt.setTypeface(Typeface.create(v.fontName(), Typeface.NORMAL));

        float cX = v.screenW * 0.09f, cW = v.screenW * 0.82f;
        float cY = v.screenH * 0.24f, cH = v.screenH * 0.40f;
        drawCard(canvas, cX, cY, cW, cH, v.BORDER);

        String[] labels = {"SCORE", "LEVEL", "LINES", "TIME"};
        String[] values = {String.format("%,d", v.gameOverDisplay),
            String.valueOf(v.level), String.valueOf(v.totalLines), v.fmtTime(v.timeSurvived)};
        float rowH = cH / 4f;
        for (int i = 0; i < 4; i++) {
            float ry = cY + rowH * i + rowH * 0.62f;
            v.txt.setTextSize(v.cell * 0.33f);
            v.txt.setColor(v.TXT_DIM);
            v.txt.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(labels[i], cX + cW * 0.08f, ry, v.txt);
            v.txt.setTextSize(v.cell * 0.46f);
            v.txt.setColor(i == 0 ? v.ACCENT : v.TXT_BRIGHT);
            v.txt.setTypeface(i == 0 ? Typeface.create(v.fontName(), Typeface.BOLD)
                                     : Typeface.create(v.fontName(), Typeface.NORMAL));
            v.txt.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(values[i], cX + cW * 0.92f, ry, v.txt);
            v.txt.setTypeface(Typeface.create(v.fontName(), Typeface.NORMAL));
            if (i == 0 && v.newHighScore) {
                float bW = v.cell * 2.0f, bH = v.cell * 0.38f;
                float bX = cX + cW * 0.08f, bY = ry - rowH * 0.52f;
                v.fill.setColor(v.ACCENT);
                canvas.drawRoundRect(bX, bY, bX + bW, bY + bH, bH/2f, bH/2f, v.fill);
                v.txt.setTextSize(v.cell * 0.22f);
                v.txt.setColor(v.ON_ACCENT);
                v.txt.setTypeface(Typeface.create(v.fontName(), Typeface.BOLD));
                v.txt.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("NEW BEST", bX + bW/2f, bY + bH * 0.72f, v.txt);
                v.txt.setTypeface(Typeface.create(v.fontName(), Typeface.NORMAL));
            }
            if (i < 3) {
                v.fill.setStyle(Paint.Style.FILL);
                v.fill.setColor(v.BORDER);
                canvas.drawRect(cX + cW*0.06f, cY + rowH*(i+1),
                    cX + cW*0.94f, cY + rowH*(i+1) + 1f, v.fill);
            }
        }

        float btnY = v.screenH * 0.67f;
        float btnW = v.screenW * 0.37f, btnH = v.cell * 0.95f;
        float gap  = v.screenW * 0.05f;
        v.restartZone = new RectF(cx - gap/2f - btnW, btnY, cx - gap/2f, btnY + btnH);
        v.menuZone    = new RectF(cx + gap/2f, btnY, cx + gap/2f + btnW, btnY + btnH);

        v.fill.setStyle(Paint.Style.FILL);
        v.fill.setColor(v.ACCENT);
        canvas.drawRoundRect(v.restartZone, v.cardR * 0.5f, v.cardR * 0.5f, v.fill);
        v.txt.setTextSize(v.cell * 0.40f);
        v.txt.setColor(v.ON_ACCENT);
        v.txt.setTypeface(Typeface.create(v.fontName(), Typeface.BOLD));
        v.txt.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("RESTART", v.restartZone.centerX(), btnY + btnH * 0.67f, v.txt);
        v.txt.setTypeface(Typeface.create(v.fontName(), Typeface.NORMAL));

        drawCard(canvas, v.menuZone.left, v.menuZone.top, v.menuZone.width(), v.menuZone.height(), v.BORDER);
        v.txt.setTextSize(v.cell * 0.40f);
        v.txt.setColor(v.TXT_MID);
        canvas.drawText("MENU", v.menuZone.centerX(), btnY + btnH * 0.67f, v.txt);
    }

    // ── Settings Panel ─────────────────────────────────────────────
    void drawSettingsPanel(Canvas canvas) {     
        float pW = v.screenW * 0.78f;
        float px = v.screenW - pW * v.settingsAnim;

        float panelTop = (v.state == TetrisView.State.MENU) ? 0 : v.bTop;

        int dimA = (int)(v.settingsAnim * 160);
        v.fill.setStyle(Paint.Style.FILL);
        v.fill.setColor(v.withAlpha(v.BG, dimA));
        canvas.drawRect(0, panelTop, px, v.screenH, v.fill);

        v.fill.setColor(v.BG_CARD);
        canvas.drawRect(px, panelTop, v.screenW, v.screenH, v.fill);
        v.fill.setStyle(Paint.Style.STROKE);
        v.fill.setStrokeWidth(1f);
        v.fill.setColor(v.BORDER);
        canvas.drawLine(px, panelTop, px, v.screenH, v.fill);
        v.fill.setStyle(Paint.Style.FILL);

        float mg = v.screenW * 0.055f;
        float y  = v.screenH * 0.07f;

        v.txt.setTextAlign(Paint.Align.LEFT);
        v.txt.setTypeface(Typeface.create(v.fontName(), Typeface.BOLD));
        v.txt.setTextSize(v.cell * 0.62f);
        v.txt.setColor(v.TXT_BRIGHT);
        canvas.drawText("SETTINGS", px + mg, y, v.txt);
        v.txt.setTextAlign(Paint.Align.RIGHT);
        v.txt.setTextSize(v.cell * 0.52f);
        v.txt.setColor(v.TXT_MID);
        v.txt.setTypeface(Typeface.create(v.fontName(), Typeface.NORMAL));
        canvas.drawText("\u2715", v.screenW - mg, y, v.txt);

        y += v.cell * 0.8f;

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

        for (Object[] item : items) {
            String label = (String) item[0];
            String key   = (String) item[1];
            if (key == null) {
                y += v.cell * 0.18f;
                v.txt.setTextAlign(Paint.Align.LEFT);
                v.txt.setTextSize(v.cell * 0.28f);
                v.txt.setColor(v.ACCENT);
                v.txt.setTypeface(Typeface.create(v.fontName(), Typeface.BOLD));
                canvas.drawText(label, px + mg, y, v.txt);
                v.fill.setStyle(Paint.Style.FILL);
                v.fill.setColor(v.ACCENT_DIM);
                canvas.drawRect(px + mg, y + v.cell * 0.08f, v.screenW - mg, y + v.cell * 0.09f, v.fill);
                v.txt.setTypeface(Typeface.create(v.fontName(), Typeface.NORMAL));
                y += v.cell * 0.55f;
            } else {
                boolean isRadio = key.startsWith("theme:");
                float rH = v.cell * 0.85f;
                v.txt.setTextAlign(Paint.Align.LEFT);
                v.txt.setTextSize(v.cell * 0.37f);
                v.txt.setTypeface(Typeface.create(v.fontName(), Typeface.NORMAL));

                if (isRadio) {
                    int themeVal = Integer.parseInt(key.substring(6));
                    boolean sel = (v.colorTheme == themeVal);
                    v.txt.setColor(sel ? v.TXT_BRIGHT : v.TXT_MID);
                    canvas.drawText(label, px + mg, y + rH * 0.63f, v.txt);
                    float rR = v.cell * 0.22f;
                    float rX = v.screenW - mg - rR, rY = y + rH / 2f;
                    v.fill.setStyle(Paint.Style.STROKE);
                    v.fill.setStrokeWidth(1.8f);
                    v.fill.setColor(sel ? v.ACCENT : v.BORDER);
                    canvas.drawCircle(rX, rY, rR, v.fill);
                    v.fill.setStyle(Paint.Style.FILL);
                    if (sel) {
                        v.fill.setColor(v.ACCENT);
                        canvas.drawCircle(rX, rY, rR * 0.52f, v.fill);
                    }
                } else {
                    v.txt.setColor(v.TXT_BRIGHT);
                    canvas.drawText(label, px + mg, y + rH * 0.63f, v.txt);
                    boolean on = v.getSetting(key);
                    float swW = v.cell * 1.1f, swH = v.cell * 0.52f;
                    float swX = v.screenW - mg - swW, swY = y + (rH - swH) / 2f;
                    v.fill.setStyle(Paint.Style.FILL);
                    v.fill.setColor(on ? v.ACCENT : v.BORDER);
                    canvas.drawRoundRect(swX, swY, swX + swW, swY + swH, swH/2f, swH/2f, v.fill);
                    float kR = swH * 0.40f;
                    float kX = on ? swX + swW - kR - swH*0.1f : swX + kR + swH*0.1f;
                    v.fill.setColor(0xFFFFFFFF);
                    canvas.drawCircle(kX, swY + swH/2f, kR, v.fill);
                }
                y += rH;
            }
        }
    }

    // ── Stats Overlay ──────────────────────────────────────────────
    void drawStatsOverlay(Canvas canvas) {
        v.fill.setStyle(Paint.Style.FILL);
        v.fill.setColor(v.withAlpha(v.BG, 0xCC));
        canvas.drawRect(0, 0, v.screenW, v.screenH, v.fill);

        float cx = v.screenW / 2f;
        float cW = v.screenW * 0.84f, cH = v.screenH * 0.56f;
        float cX = (v.screenW - cW) / 2f, cY = (v.screenH - cH) / 2f;
        drawCard(canvas, cX, cY, cW, cH, v.BORDER);

        v.txt.setTextAlign(Paint.Align.CENTER);
        v.txt.setTypeface(Typeface.create(v.fontName(), Typeface.BOLD));
        v.txt.setTextSize(v.cell * 0.58f);
        v.txt.setColor(v.TXT_BRIGHT);
        canvas.drawText("STATS", cx, cY + cH * 0.14f, v.txt);
        v.txt.setTypeface(Typeface.create(v.fontName(), Typeface.NORMAL));

        String[] slabels = {"BEST SCORE", "GAMES", "TOTAL LINES", "BEST LEVEL", "PIECES"};
        String[] svalues = {
            v.fmtScore(v.highScore),
            String.valueOf(v.statGames),
            String.valueOf(v.statTotalLines),
            String.valueOf(v.statBestLevel),
            String.valueOf(v.statTotalPieces),
        };
        float rowH = cH * 0.13f;
        float startY = cY + cH * 0.22f;
        for (int i = 0; i < 5; i++) {
            float ry = startY + rowH * i + rowH * 0.55f;
            v.txt.setTextSize(v.cell * 0.29f);
            v.txt.setColor(v.TXT_DIM);
            v.txt.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(slabels[i], cX + cW * 0.08f, ry, v.txt);
            v.txt.setTextSize(v.cell * 0.42f);
            v.txt.setColor(i == 0 ? v.ACCENT : v.TXT_BRIGHT);
            v.txt.setTypeface(i == 0 ? Typeface.create(v.fontName(), Typeface.BOLD)
                                     : Typeface.create(v.fontName(), Typeface.NORMAL));
            v.txt.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(svalues[i], cX + cW * 0.92f, ry, v.txt);
            v.txt.setTypeface(Typeface.create(v.fontName(), Typeface.NORMAL));
            if (i < 4) {
                v.fill.setColor(v.BORDER);
                canvas.drawRect(cX + cW*0.06f, startY + rowH*(i+1),
                    cX + cW*0.94f, startY + rowH*(i+1) + 1f, v.fill);
            }
        }
        float rBtnW = cW * 0.52f, rBtnH = v.cell * 0.72f;
        float rBtnX = cx - rBtnW / 2f, rBtnY = cY + cH * 0.77f;
        v.resetBestZone = new RectF(rBtnX, rBtnY, rBtnX + rBtnW, rBtnY + rBtnH);
        v.fill.setStyle(Paint.Style.STROKE);
        v.fill.setStrokeWidth(1.5f);
        v.fill.setColor(0xFF663333);
        canvas.drawRoundRect(v.resetBestZone, v.cardR * 0.5f, v.cardR * 0.5f, v.fill);
        v.fill.setStyle(Paint.Style.FILL);
        v.txt.setTextSize(v.cell * 0.30f);
        v.txt.setColor(0xFFCC4444);
        v.txt.setTypeface(Typeface.create(v.fontName(), Typeface.BOLD));
        v.txt.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("RESET BEST SCORE", cx, rBtnY + rBtnH * 0.66f, v.txt);
        v.txt.setTypeface(Typeface.create(v.fontName(), Typeface.NORMAL));

        v.txt.setTextSize(v.cell * 0.26f);
        v.txt.setColor(v.TXT_DIM);
        v.txt.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("tap anywhere else to close", cx, cY + cH * 0.95f, v.txt);
    }
}
