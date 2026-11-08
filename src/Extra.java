import javax.microedition.lcdui.*;
import java.util.Vector;

final class Burner extends Thread {
    volatile boolean on = true;
    public void run() {
        int a = 1;
        while (on) { a = a * 31 + (a >>> 3); }
    }
}

// ---------------------------------------------------------------- FIRE
final class Fire {
    static final String[] MODES = {"Campfire", "Wall of fire", "Torch"};
    static final int[] PAL = new int[256];
    static final int NE = 40;

    static {
        for (int i = 0; i < 256; i++) {
            int r = Math.min(255, i * 3);
            int g = i < 70 ? i / 6 : Math.min(255, 12 + (i - 70) * 22 / 10);
            int b = i < 190 ? 0 : Math.min(255, (i - 190) * 4);
            PAL[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
        }
    }

    int w, h, sc = 3, gw, gh, mode, fuel = 70, wind, fps, frames, seed = 99, flick = 128, xo, top, half;
    boolean embers = true, hud = true;
    int[] heat, line;
    int[] ex = new int[NE], ey = new int[NE], evx = new int[NE], evy = new int[NE], el = new int[NE];
    long fpsT;
    int lowCnt;

    int rnd(int n) {
        seed = seed * 1103515245 + 12345;
        return ((seed >>> 16) & 0x7fff) % n;
    }

    static int mix(int a, int b, int p) {
        if (p < 0) p = 0;
        if (p > 255) p = 255;
        int r = (((a >> 16) & 255) * (255 - p) + ((b >> 16) & 255) * p) / 255;
        int g = (((a >> 8) & 255) * (255 - p) + ((b >> 8) & 255) * p) / 255;
        int bl = ((a & 255) * (255 - p) + (b & 255) * p) / 255;
        return (r << 16) | (g << 8) | bl;
    }

    void init(int ww, int hh) {
        w = ww; h = hh;
        gw = w / sc; gh = h / sc;
        xo = (w - gw * sc) / 2;
        heat = new int[gw * gh];
        line = new int[gw * sc];
        for (int i = 0; i < NE; i++) el[i] = 0;
        fpsT = System.currentTimeMillis();
        frames = 0;
        setMode(mode);
    }

    void setMode(int m) {
        mode = m % 3;
        half = mode == 0 ? gw * 22 / 100 : (mode == 1 ? gw / 2 : gw * 7 / 100);
        if (half < 2) half = 2;
    }

    void step() {
        flick += rnd(25) - 12;
        if (flick < 90) flick = 90;
        if (flick > 165) flick = 165;
        int hm = (120 + fuel * 135 / 100) * flick / 128;
        if (hm > 255) hm = 255;
        int hr = gh * (30 + fuel * 40 / 100) / 100;
        if (hr < 6) hr = 6;
        int cool = hm / hr + 1;
        int mid = gw / 2;
        for (int k = 0; k < 2; k++) {
            int row = (gh - 1 - k) * gw;
            for (int x = 0; x < gw; x++) {
                int d = x > mid ? x - mid : mid - x;
                int v = 0;
                if (d < half) v = hm - d * hm / (2 * half) - rnd(70);
                heat[row + x] = v < 0 ? 0 : v;
            }
        }
        top = gh - hr * 2 - 8;
        if (top < 0) top = 0;
        int aw = wind < 0 ? -wind : wind;
        for (int y = top; y < gh - 2; y++) {
            int r1 = (y + 1) * gw, r2 = (y + 2) * gw, row = y * gw;
            int off = 0;
            if (wind != 0 && rnd(6) < aw) off = wind < 0 ? -1 : 1;
            for (int x = 0; x < gw; x++) {
                int xa = x + off - 1, xb = x + off, xc = x + off + 1;
                if (xa < 0) xa = 0; else if (xa >= gw) xa = gw - 1;
                if (xb < 0) xb = 0; else if (xb >= gw) xb = gw - 1;
                if (xc < 0) xc = 0; else if (xc >= gw) xc = gw - 1;
                int v = ((heat[r1 + xa] + 2 * heat[r1 + xb] + heat[r1 + xc] + heat[r2 + xb]) * 51) >> 8;
                v -= cool / 2 + rnd(cool + 1);
                heat[row + x] = v < 0 ? 0 : v;
            }
        }
        if (embers) {
            if (rnd(100) < fuel / 4 + 3) spawn();
            moveEmbers();
        }
        frames++;
        long now = System.currentTimeMillis();
        if (now - fpsT >= 1000) {
            fps = (int) (frames * 1000L / (now - fpsT));
            frames = 0;
            fpsT = now;
            if (fps < 9) lowCnt++; else lowCnt = 0;
            if (lowCnt >= 2 && sc < 6) { sc++; lowCnt = 0; init(w, h); }
        }
    }

    void spawn() {
        for (int i = 0; i < NE; i++) {
            if (el[i] <= 0) {
                int span = half * 2 * sc + 1;
                ex[i] = (w / 2 + rnd(span) - half * sc) * 16;
                ey[i] = (h - 10 - rnd(gh * sc / 3 + 1)) * 16;
                evx[i] = rnd(9) - 4 + wind * 3;
                evy[i] = -(10 + rnd(22));
                el[i] = 30 + rnd(60);
                return;
            }
        }
    }

    void moveEmbers() {
        for (int i = 0; i < NE; i++) {
            if (el[i] <= 0) continue;
            el[i]--;
            evx[i] += rnd(7) - 3 + wind;
            if (evx[i] > 40) evx[i] = 40;
            if (evx[i] < -40) evx[i] = -40;
            ex[i] += evx[i];
            ey[i] += evy[i];
            evy[i] = evy[i] * 63 / 64;
            if (ey[i] < 0) el[i] = 0;
        }
    }

    void paint(Graphics g) {
        g.setColor(0x000000);
        g.fillRect(0, 0, w, h);
        int n = gw * sc;
        for (int y = top; y < gh; y++) {
            int base = y * gw;
            for (int x = 0; x < gw; x++) {
                int c = PAL[heat[base + x]];
                int o = x * sc;
                for (int k = 0; k < sc; k++) line[o + k] = c;
            }
            for (int k = 0; k < sc; k++) g.drawRGB(line, 0, n, xo, y * sc + k, n, 1, false);
        }
        if (mode == 0) {
            int cx = w / 2, by = h - 4;
            g.setColor(0x2A160A);
            g.fillRoundRect(cx - 50, by - 12, 100, 12, 10, 10);
            g.fillRoundRect(cx - 38, by - 22, 76, 11, 10, 10);
            g.setColor(0x6B3A12);
            g.drawLine(cx - 44, by - 11, cx + 44, by - 11);
            g.setColor(0xFF7A18);
            g.drawLine(cx - 30, by - 22, cx + 30, by - 22);
        }
        if (embers) {
            for (int i = 0; i < NE; i++) {
                if (el[i] <= 0) continue;
                g.setColor(mix(0x802000, 0xFFD060, el[i] * 255 / 90));
                g.fillRect(ex[i] >> 4, ey[i] >> 4, 2, 2);
            }
        }
    }

    boolean key(int k, int a, boolean left, boolean right) {
        if (right) return true;
        if (left) { setMode(mode + 1); return false; }
        if (k == Canvas.KEY_STAR) { embers = !embers; return false; }
        if (k == Canvas.KEY_POUND) { sc = sc >= 5 ? 2 : sc + 1; init(w, h); return false; }
        if (k == Canvas.KEY_NUM0) { hud = !hud; return false; }
        if (a == Canvas.UP && fuel < 100) fuel += 10;
        else if (a == Canvas.DOWN && fuel > 0) fuel -= 10;
        else if (a == Canvas.LEFT && wind > -3) wind--;
        else if (a == Canvas.RIGHT && wind < 3) wind++;
        return false;
    }
}

// ---------------------------------------------------------------- CHAOS
final class Chaos {
    static final int M_MENU = 0, M_WARN = 1, M_LAG = 2, M_FREEZE = 3, M_MEM = 4, M_CRASH = 5;
    static final String[] NAMES = {"Random lag", "Random freezes", "Memory pressure", "Total crash"};
    static final String[] DESC = {"Short CPU stalls at random", "Hangs of 2 to 7 seconds", "Fills and frees memory", "Escalates until it gives up"};

    int mode, sel, seed = 4242, nb, heldKB, froze, maxFrame, crashAt, lastFrame;
    long start, nextEv, lastPaint;
    volatile int burn;
    volatile boolean crashNow;
    boolean finalStage;
    Vector log = new Vector();
    Vector hog = new Vector();
    Burner[] bs = new Burner[4];

    int rnd(int n) {
        seed = seed * 1103515245 + 12345;
        return ((seed >>> 16) & 0x7fff) % n;
    }

    void open() { stopAll(); sel = 0; }

    void startMode(int m) {
        stopAll();
        mode = m;
        start = System.currentTimeMillis();
        nextEv = start + 1500;
        hog = new Vector();
        log.removeAllElements();
        heldKB = 0; froze = 0; maxFrame = 0; lastPaint = 0; burn = 0;
        crashNow = false; finalStage = false;
        crashAt = 30 + rnd(25);
        note("started");
    }

    void stopAll() {
        for (int i = 0; i < nb; i++) if (bs[i] != null) bs[i].on = false;
        nb = 0;
        burn = 0;
        crashNow = false;
        hog = new Vector();
        heldKB = 0;
        mode = M_MENU;
        System.gc();
    }

    void note(String s) {
        log.addElement(((System.currentTimeMillis() - start) / 1000) + "s " + s);
        if (log.size() > 7) log.removeElementAt(0);
    }

    boolean tryAlloc(int kb) {
        try {
            hog.addElement(new byte[kb * 1024]);
            heldKB += kb;
            return true;
        } catch (OutOfMemoryError e) {
            return false;
        }
    }

    void freeSome(boolean all) {
        int n = all ? hog.size() : 1 + rnd(3);
        for (int i = 0; i < n && hog.size() > 0; i++) {
            byte[] b = (byte[]) hog.elementAt(hog.size() - 1);
            heldKB -= b.length / 1024;
            hog.removeElementAt(hog.size() - 1);
        }
        System.gc();
    }

    void step(long now) {
        if (mode == M_LAG) {
            if (now >= nextEv) {
                int ms = 80 + rnd(520);
                burn = ms;
                note("lag " + ms + "ms");
                nextEv = now + 600 + rnd(3500);
            }
        } else if (mode == M_FREEZE) {
            if (now >= nextEv) {
                if (rnd(3) == 0) { burn = 100 + rnd(300); note("stutter"); }
                else { burn = 2000 + rnd(5000); note("freeze " + (burn / 1000) + "s"); }
                nextEv = now + 4000 + rnd(9000);
            }
        } else if (mode == M_MEM) {
            if (now >= nextEv) {
                if (rnd(5) < 3 || hog.size() < 3) {
                    int kb = 16 + rnd(180);
                    if (tryAlloc(kb)) note("+" + kb + "K held " + heldKB + "K");
                    else { freeSome(true); note("out of memory, freed all"); }
                } else {
                    freeSome(false);
                    note("freed, gc, held " + heldKB + "K");
                }
                nextEv = now + 150 + rnd(700);
            }
        } else if (mode == M_CRASH) {
            crashStep(now);
        }
    }

    void crashStep(long now) {
        int el = (int) ((now - start) / 1000);
        if (el >= crashAt) {
            if (!finalStage) { finalStage = true; note("final stage"); }
            try {
                while (true) hog.addElement(new byte[32768]);
            } catch (OutOfMemoryError e) {
            }
            crashNow = true;
            return;
        }
        if (now >= nextEv) {
            int act = rnd(4);
            if (act == 0) { burn = 100 + el * 8 + rnd(400); note("lag"); }
            else if (act == 1) { int kb = 40 + rnd(160); if (tryAlloc(kb)) note("+" + kb + "K"); }
            else if (act == 2 && nb < 4 && el > 6) {
                bs[nb] = new Burner();
                bs[nb].start();
                nb++;
                note("cpu thread " + nb);
            } else if (act == 3 && froze == 0 && el > 15) {
                froze = 1;
                burn = 3000 + rnd(4000);
                note("freeze");
            }
            nextEv = now + Math.max(250, 1600 - el * 40) + rnd(600);
        }
    }

    // called at the start of every paint: this is what blocks the phone's UI thread
    void block() {
        int ms = burn;
        if (ms > 0) {
            burn = 0;
            long end = System.currentTimeMillis() + ms;
            int a = 1;
            while (System.currentTimeMillis() < end) { a = a * 31 + (a >>> 3); }
        }
        if (crashNow) {
            long z = 1;
            while (true) {
                z = z * 31 + 7;
                if (z == 3) break;
            }
        }
    }

    boolean key(int k, int a, boolean left, boolean right) {
        if (mode == M_MENU) {
            if (right) return true;
            if (a == Canvas.UP && sel > 0) sel--;
            else if (a == Canvas.DOWN && sel < 3) sel++;
            else if (a == Canvas.FIRE || left) {
                if (sel == 3) mode = M_WARN;
                else startMode(M_LAG + sel);
            }
            return false;
        }
        if (mode == M_WARN) {
            if (right) mode = M_MENU;
            else if (a == Canvas.FIRE || k == Canvas.KEY_NUM5) startMode(M_CRASH);
            return false;
        }
        if (right) stopAll();
        return false;
    }
}
