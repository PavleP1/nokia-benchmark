import javax.microedition.lcdui.*;
import javax.microedition.rms.RecordStore;
import java.io.*;
import java.util.Vector;

final class BenchCanvas extends Canvas implements Runnable {
    static final int MENU = 0, RUN = 1, STRESS = 2, SDONE = 3;
    static final int DUR = 6000, ROWS = Tests.COUNT + 2;
    static final int TL = Graphics.TOP | Graphics.LEFT;
    static final int TC = Graphics.TOP | Graphics.HCENTER;
    static final int TR = Graphics.TOP | Graphics.RIGHT;
    static final int BG = 0x121214, CARD = 0x1E1E22, TXT = 0xFFFFFF, SUB = 0x8E8E93, ACC = 0x0A84FF;

    final PhoneBench midlet;
    final Font fs = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
    final Font fb = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_SMALL);
    final Font fh = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_MEDIUM);
    volatile boolean running = true, stopReq;
    volatile int state;
    int w, h, headH, barH, sel, scroll, cur, frames;
    boolean runAll;
    long startT, lastPaint, msgT;
    final int[] best = new int[Tests.COUNT];
    int lastLevel, lastKB, lastMin;
    String msg;

    // stress test
    Vector blocks;
    int[] junk;
    int slevel, sFps, sFrames, sMin, sKB, lowCnt, hn, sSecs;
    long sLast, sStart;
    String sEnd;
    final int[] hist = new int[56];

    BenchCanvas(PhoneBench m) {
        midlet = m;
        setFullScreenMode(true);
        w = getWidth();
        h = getHeight();
        headH = fh.getHeight() + 12;
        barH = fs.getHeight() + 10;
        load();
    }

    void start() { new Thread(this).start(); }

    void stop(boolean exiting) {
        if (exiting) running = false;
        if (state == RUN) abort();
        else if (state == STRESS) stopReq = true;
        save();
    }

    // ---------- helpers
    static int kind(int id) { return id == 9 ? 1 : id == 10 ? 2 : id == 11 ? 3 : id == 12 ? 4 : 0; }

    static String fmt(int id, int v) {
        int k = kind(id);
        if (k == 0) return (v / 10) + "." + (v % 10) + " fps";
        if (k == 2) return (v / 10) + "." + (v % 10) + " MB/s";
        return v + (k == 1 ? " Kops/s" : k == 3 ? " /s" : " ch/s");
    }

    static int pts(int id, int v) {
        int k = kind(id);
        if (k == 1) return v / 20;
        if (k == 3) return v * 2;
        if (k == 4) return v / 10;
        return v;
    }

    int total() {
        int t = 0;
        for (int i = 0; i < Tests.COUNT; i++) t += pts(i, best[i]);
        return t;
    }

    void note(String s) { msg = s; msgT = System.currentTimeMillis(); }

    void load() {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore("mbench", true);
            if (rs.getNumRecords() > 0) {
                DataInputStream in = new DataInputStream(new ByteArrayInputStream(rs.getRecord(1)));
                for (int i = 0; i < best.length; i++) best[i] = in.readInt();
                lastLevel = in.readInt(); lastKB = in.readInt(); lastMin = in.readInt();
            }
        } catch (Exception e) {
        } finally {
            if (rs != null) try { rs.closeRecordStore(); } catch (Exception e) {}
        }
    }

    synchronized void save() {
        RecordStore rs = null;
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            DataOutputStream o = new DataOutputStream(bo);
            for (int i = 0; i < best.length; i++) o.writeInt(best[i]);
            o.writeInt(lastLevel); o.writeInt(lastKB); o.writeInt(lastMin);
            o.flush();
            byte[] d = bo.toByteArray();
            rs = RecordStore.openRecordStore("mbench", true);
            if (rs.getNumRecords() == 0) rs.addRecord(d, 0, d.length);
            else rs.setRecord(1, d, 0, d.length);
        } catch (Exception e) {
        } finally {
            if (rs != null) try { rs.closeRecordStore(); } catch (Exception e) {}
        }
    }

    // ---------- benchmark control
    void startTest(int id) {
        try {
            cur = id;
            Tests.init(id, w, h);
            frames = 0;
            startT = System.currentTimeMillis();
            state = RUN;
        } catch (Throwable e) {
            Tests.release();
            runAll = false;
            state = MENU;
            note("Test failed (memory?)");
        }
    }

    void abort() {
        state = MENU;
        runAll = false;
        Tests.release();
        note("Stopped");
    }

    void finish() {
        long ms = System.currentTimeMillis() - startT;
        if (ms < 1) ms = 1;
        int k = kind(cur), v;
        if (k == 0) v = (int) (frames * 10000L / ms);
        else if (k == 1) v = (int) (Tests.ops / ms);
        else if (k == 2) v = (int) (Tests.ops * 10000L / (ms * 1048576L));
        else v = (int) (Tests.ops * 1000L / ms);
        if (v > best[cur]) best[cur] = v;
        Tests.release();
        save();
        if (runAll && cur + 1 < Tests.COUNT) {
            startTest(cur + 1);
        } else {
            state = MENU;
            if (runAll) note("All done: " + total() + " pts");
            else note(Tests.NAMES[cur] + ": " + fmt(cur, v));
            runAll = false;
        }
    }

    // ---------- stress test
    void startStress() {
        try {
            Tests.init(7, w, h);
            Tests.pn = 30;
            blocks = new Vector();
            junk = null;
            slevel = 0; sKB = 0; sMin = 999; lowCnt = 0; sFrames = 0; hn = 0; sFps = 0;
            stopReq = false;
            sEnd = null;
            sLast = System.currentTimeMillis();
            sStart = sLast;
            state = STRESS;
        } catch (Throwable e) {
            note("Stress start failed");
        }
    }

    void endStress(String why) {
        blocks = null;
        junk = null;
        System.gc();
        sEnd = why;
        sSecs = (int) ((System.currentTimeMillis() - sStart) / 1000);
        if (slevel >= lastLevel) { lastLevel = slevel; lastKB = sKB; lastMin = sMin; }
        save();
        stopReq = false;
        state = SDONE;
    }

    void stressStep(long now) {
        if (stopReq) { endStress("Stopped by you"); return; }
        Tests.burn(slevel * 2500);
        junk = new int[slevel * 1500 + 64];
        repaint();
        serviceRepaints();
        sFrames++;
        if (now - sLast >= 1000) {
            long dt = now - sLast;
            sFps = (int) (sFrames * 1000L / dt);
            sFrames = 0;
            sLast = now;
            slevel++;
            if (sFps < sMin) sMin = sFps;
            if (hn < hist.length) hist[hn++] = sFps;
            else { System.arraycopy(hist, 1, hist, 0, hist.length - 1); hist[hist.length - 1] = sFps; }
            blocks.addElement(new byte[8192 * slevel]);
            sKB += 8 * slevel;
            Tests.addParticles(12);
            lastLevel = slevel; lastKB = sKB; lastMin = sMin;
            save();
            if (sFps <= 2) { lowCnt++; if (lowCnt >= 3) endStress("Frozen: FPS collapsed"); }
            else lowCnt = 0;
        }
    }

    // ---------- main loop
    public void run() {
        while (running) {
            int st = state;
            long now = System.currentTimeMillis();
            try {
                if (st == RUN) {
                    if (now - startT >= DUR) finish();
                    else if (Tests.GFX[cur]) { repaint(); serviceRepaints(); }
                    else {
                        Tests.cpu(cur);
                        frames++;
                        if (now - lastPaint > 150) { lastPaint = now; repaint(); }
                    }
                } else if (st == STRESS) {
                    stressStep(now);
                } else {
                    try { Thread.sleep(80); } catch (InterruptedException e) {}
                }
            } catch (OutOfMemoryError e) {
                if (state == STRESS) endStress("Out of memory"); else abort();
            } catch (Throwable e) {
                if (state == STRESS) endStress("Error"); else abort();
            }
        }
    }

    // ---------- drawing
    protected void paint(Graphics g) {
        w = getWidth();
        h = getHeight();
        if (state == RUN) paintRun(g);
        else if (state == STRESS) paintStress(g);
        else if (state == SDONE) paintDone(g);
        else paintMenu(g);
    }

    void head(Graphics g, String title, String pill) {
        g.setColor(0x1C1C1E);
        g.fillRect(0, 0, w, headH);
        g.setColor(0x3A3A3C);
        g.drawLine(0, headH - 1, w, headH - 1);
        g.setFont(fh);
        g.setColor(TXT);
        g.drawString(title, 12, (headH - fh.getHeight()) / 2, TL);
        if (pill != null) {
            int pw = fb.stringWidth(pill) + 16, ph = fb.getHeight() + 4;
            g.setColor(ACC);
            g.fillRoundRect(w - pw - 8, (headH - ph) / 2, pw, ph, ph, ph);
            g.setFont(fb);
            g.setColor(TXT);
            g.drawString(pill, w - 8 - pw / 2, (headH - ph) / 2 + 2, TC);
        }
    }

    void bar(Graphics g, String l, String c, String r) {
        g.setColor(0x1C1C1E);
        g.fillRect(0, h - barH, w, barH);
        g.setColor(0x3A3A3C);
        g.drawLine(0, h - barH, w, h - barH);
        int y = h - barH + 5;
        g.setFont(fb);
        g.setColor(ACC);
        g.drawString(l, 10, y, TL);
        g.drawString(r, w - 10, y, TR);
        g.setFont(fs);
        g.setColor(SUB);
        g.drawString(c, w / 2, y, TC);
    }

    String rowName(int r) {
        if (r == 0) return "Run all tests";
        if (r <= Tests.COUNT) return Tests.NAMES[r - 1];
        return "Stress test";
    }

    String rowVal(int r) {
        if (r == 0) return total() > 0 ? total() + " pts" : "";
        if (r <= Tests.COUNT) return best[r - 1] > 0 ? fmt(r - 1, best[r - 1]) : "-";
        return lastLevel > 0 ? "Lvl " + lastLevel : "-";
    }

    void paintMenu(Graphics g) {
        g.setColor(BG);
        g.fillRect(0, 0, w, h);
        int rh = fs.getHeight() + 12, top = headH + 6, view = h - barH - top - 4;
        if (sel * rh < scroll) scroll = sel * rh;
        if (sel * rh + rh > scroll + view) scroll = sel * rh + rh - view;
        if (scroll < 0) scroll = 0;
        for (int r = 0; r < ROWS; r++) {
            int y = top + r * rh - scroll;
            if (y + rh < headH || y > h - barH) continue;
            g.setColor(CARD);
            g.fillRoundRect(8, y, w - 16, rh - 4, 10, 10);
            if (r == sel) {
                g.setColor(ACC);
                g.drawRoundRect(8, y, w - 17, rh - 5, 10, 10);
            }
            g.setFont(r == 0 || r == ROWS - 1 ? fb : fs);
            g.setColor(r == ROWS - 1 ? 0xFF9F0A : TXT);
            g.drawString(rowName(r), 18, y + 4, TL);
            g.setFont(fs);
            g.setColor(SUB);
            g.drawString(rowVal(r), w - 18, y + 4, TR);
        }
        head(g, "Phone Bench", total() > 0 ? total() + " pts" : null);
        String c = "";
        if (msg != null && System.currentTimeMillis() - msgT < 5000) c = msg;
        bar(g, "Start", c, "Exit");
    }

    void paintRun(Graphics g) {
        if (Tests.GFX[cur]) {
            Tests.draw(cur, g, w, h, frames);
            frames++;
        } else {
            g.setColor(BG);
            g.fillRect(0, 0, w, h);
        }
        long el = System.currentTimeMillis() - startT;
        if (el < 1) el = 1;
        int left = (int) ((DUR - el) / 1000) + 1;
        g.setColor(0x000000);
        g.fillRect(0, 0, w, fb.getHeight() + fs.getHeight() + 6);
        g.setFont(fb);
        g.setColor(TXT);
        g.drawString((runAll ? (cur + 1) + "/" + Tests.COUNT + " " : "") + Tests.NAMES[cur] + "  " + left + "s", 4, 2, TL);
        g.setFont(fs);
        g.setColor(0x30D158);
        if (kind(cur) == 0) {
            int f10 = (int) (frames * 10000L / el);
            g.drawString((f10 / 10) + "." + (f10 % 10) + " fps", 4, 2 + fb.getHeight(), TL);
        } else {
            g.drawString("working...", 4, 2 + fb.getHeight(), TL);
        }
    }

    void paintStress(Graphics g) {
        g.setColor(0x000000);
        g.fillRect(0, 0, w, h);
        Tests.stepParticles(w, h);
        Tests.drawParticles(g);
        int lh = fs.getHeight();
        g.setColor(0x000000);
        g.fillRect(0, 0, w, lh * 2 + 6);
        g.setFont(fs);
        g.setColor(TXT);
        g.drawString("LEVEL " + slevel + "   FPS " + sFps, 4, 2, TL);
        long free = Runtime.getRuntime().freeMemory() / 1024;
        g.setColor(free < 200 ? 0xFF453A : 0xFF9F0A);
        g.drawString("FREE " + free + "K   PART " + Tests.pn, 4, 2 + lh, TL);
        int base = h - 6;
        for (int i = 0; i < hn; i++) {
            int v = hist[i], bh = v * 40 / 60;
            if (bh > 40) bh = 40;
            g.setColor(v < 10 ? 0xFF453A : v < 25 ? 0xFF9F0A : 0x30D158);
            g.fillRect(4 + i * 4, base - bh, 3, bh + 1);
        }
    }

    void paintDone(Graphics g) {
        g.setColor(BG);
        g.fillRect(0, 0, w, h);
        String[] L = {
            sEnd == null ? "" : sEnd,
            "Level reached: " + slevel,
            "Memory held: " + sKB + " KB",
            "Lowest FPS: " + (sMin == 999 ? 0 : sMin),
            "Particles: " + Tests.pn,
            "Time: " + sSecs + " s"
        };
        int y = headH + 14;
        for (int i = 0; i < L.length; i++) {
            g.setFont(i == 0 ? fb : fs);
            g.setColor(i == 0 ? 0xFF453A : TXT);
            g.drawString(L[i], 16, y, TL);
            y += fs.getHeight() + 8;
        }
        head(g, "Stress result", null);
        bar(g, "", "Any key", "Back");
    }

    // ---------- input
    protected void keyPressed(int k) {
        int a = 0;
        try { a = getGameAction(k); } catch (Exception e) {}
        boolean left = (k == -6 || k == -21), right = (k == -7 || k == -22);
        if (state == MENU) {
            if (a == UP && sel > 0) sel--;
            else if (a == DOWN && sel < ROWS - 1) sel++;
            else if (a == FIRE || left) {
                if (sel == 0) { runAll = true; startTest(0); }
                else if (sel <= Tests.COUNT) { runAll = false; startTest(sel - 1); }
                else startStress();
            } else if (right || k == KEY_POUND) midlet.exit();
        } else if (state == RUN) {
            if (right || k == KEY_STAR || a == FIRE) abort();
        } else if (state == STRESS) {
            if (right || a == FIRE) stopReq = true;
        } else if (state == SDONE) {
            state = MENU;
        }
        repaint();
    }

    protected void keyRepeated(int k) {
        if (state != MENU) return;
        int a = 0;
        try { a = getGameAction(k); } catch (Exception e) {}
        if (a == UP || a == DOWN) keyPressed(k);
    }
}
