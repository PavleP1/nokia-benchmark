import javax.microedition.lcdui.*;
import java.util.Vector;

final class Tests {
    static final int COUNT = 13, MAXP = 600, BW = 80;
    static final String[] NAMES = {"Fill rate", "Lines", "Shapes", "Text", "Sprites", "Alpha blend", "Blur", "Particles", "Plasma", "CPU math", "Memory", "Sorting", "Editor"};
    static final boolean[] GFX = {true, true, true, true, true, true, true, true, true, false, false, false, true};
    static final int[] PC = {0xFF453A, 0xFF9F0A, 0xFFD60A, 0x30D158, 0x0A84FF, 0xBF5AF2, 0xFF375F, 0x2EC4B6};
    static final int TL = Graphics.TOP | Graphics.LEFT;

    static int seed = 12345, sink;
    static long ops;
    static boolean ready;
    static int[] sine = new int[256];
    static int[] tile = new int[64 * 64];
    static int[] ga = new int[BW * BW], gb = new int[BW * BW], gout = new int[BW * BW];
    static Image sprite;
    static int[] px = new int[MAXP], py = new int[MAXP], vx = new int[MAXP], vy = new int[MAXP];
    static int pn, collisions;
    static byte[] m1, m2;
    static int[] sortA;
    static StringBuffer ed;
    static Vector edLines;
    static Font f1 = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
    static Font f2 = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_SMALL);

    static int rnd(int n) {
        if (n < 1) return 0;
        seed = seed * 1103515245 + 12345;
        return ((seed >>> 16) & 0x7fff) % n;
    }
    static int col() { return (rnd(256) << 16) | (rnd(256) << 8) | rnd(256); }

    static void init(int id, int w, int h) {
        if (!ready) {
            for (int i = 0; i < 256; i++) {
                int t = i & 127;
                int v = t * (128 - t) * 127 / 4096;
                sine[i] = i < 128 ? v : -v;
            }
            sprite = Image.createImage(32, 32);
            Graphics g = sprite.getGraphics();
            g.setColor(0x2255DD);
            g.fillRoundRect(0, 0, 32, 32, 10, 10);
            g.setColor(0xFFCC00);
            g.fillArc(6, 6, 20, 20, 0, 360);
            for (int i = 0; i < tile.length; i++)
                tile[i] = (((i & 63) * 4) << 24) | 0xFF0000 | ((((i >> 6) * 4) & 255) << 8) | 0x40;
            ready = true;
        }
        seed = 12345;
        ops = 0;
        sink = 0;
        if (id == 6) {
            for (int i = 0; i < ga.length; i++) { ga[i] = 0; gb[i] = 0; }
        } else if (id == 7) {
            initParticles(w, h, 120);
        } else if (id == 10) {
            try { m1 = new byte[65536]; m2 = new byte[65536]; }
            catch (Throwable e) { m1 = new byte[8192]; m2 = new byte[8192]; }
        } else if (id == 11) {
            sortA = new int[1500];
        } else if (id == 12) {
            ed = new StringBuffer();
            edLines = new Vector();
        }
    }

    static void release() {
        m1 = null; m2 = null; sortA = null; ed = null; edLines = null;
    }

    // ---------- graphics workloads
    static void draw(int id, Graphics g, int w, int h, int f) {
        if (id != 0) {
            g.setColor(0x000000);
            g.fillRect(0, 0, w, h);
        }
        switch (id) {
            case 0:
                for (int i = 0; i < 6; i++) {
                    g.setColor((i * 0x2A1F37 + f * 0x010305) & 0xFFFFFF);
                    g.fillRect(i * 3, i * 3, w - i * 6, h - i * 6);
                }
                break;
            case 1:
                for (int i = 0; i < 250; i++) {
                    if (i % 10 == 0) g.setColor(col());
                    g.drawLine(rnd(w), rnd(h), rnd(w), rnd(h));
                }
                break;
            case 2:
                for (int i = 0; i < 40; i++) {
                    g.setColor(col());
                    g.fillRoundRect(rnd(w - 40), rnd(h - 40), 10 + rnd(30), 10 + rnd(30), 8, 8);
                }
                for (int i = 0; i < 40; i++) {
                    g.setColor(col());
                    g.fillArc(rnd(w - 40), rnd(h - 40), 10 + rnd(30), 10 + rnd(30), 0, 360);
                }
                break;
            case 3:
                for (int i = 0; i < 30; i++) {
                    g.setFont(i % 2 == 0 ? f1 : f2);
                    g.setColor(col());
                    g.drawString("Quick brown fox 0123", rnd(w / 2), rnd(h - 20), TL);
                }
                break;
            case 4:
                for (int i = 0; i < 120; i++) g.drawImage(sprite, rnd(w - 32), rnd(h - 32), TL);
                break;
            case 5:
                for (int i = 0; i < 10; i++) g.drawRGB(tile, 0, 64, rnd(w - 64), rnd(h - 64), 64, 64, true);
                break;
            case 6:
                blur(g, w, h);
                break;
            case 7:
                stepParticles(w, h);
                drawParticles(g);
                break;
            case 8:
                plasma(g, w, h, f);
                break;
            case 12:
                editor(g, w, h);
                break;
        }
    }

    static void blur(Graphics g, int w, int h) {
        for (int i = 0; i < 250; i++) ga[rnd(BW * BW)] = 255;
        for (int y = 1; y < BW - 1; y++)
            for (int x = 1; x < BW - 1; x++) {
                int i = y * BW + x;
                gb[i] = (ga[i - 1] + ga[i] + ga[i + 1] + ga[i - BW - 1] + ga[i - BW] + ga[i - BW + 1]
                        + ga[i + BW - 1] + ga[i + BW] + ga[i + BW + 1]) / 9;
            }
        int[] t = ga; ga = gb; gb = t;
        for (int i = 0; i < BW * BW; i++) {
            int v = ga[i];
            if (v > 255) v = 255;
            gout[i] = 0xFF000000 | (v << 16) | ((v >> 1) << 8) | (255 - v);
        }
        g.drawRGB(gout, 0, BW, (w - BW) / 2, (h - BW) / 2, BW, BW, false);
    }

    static void plasma(Graphics g, int w, int h, int f) {
        for (int y = 0; y < BW; y++)
            for (int x = 0; x < BW; x++) {
                int v = sine[(x * 3 + f) & 255] + sine[(y * 2 + f * 2) & 255] + sine[(x + y + f) & 255];
                int c = (v + 381) * 255 / 762;
                gout[y * BW + x] = 0xFF000000 | (c << 16) | (((c * 3) & 255) << 8) | (255 - c);
            }
        g.drawRGB(gout, 0, BW, (w - BW) / 2, (h - BW) / 2, BW, BW, false);
    }

    static void editor(Graphics g, int w, int h) {
        for (int k = 0; k < 6; k++) ed.append(rnd(6) == 0 ? ' ' : (char) ('a' + rnd(26)));
        ops += 6;
        if (ed.length() > 1500) ed.setLength(0);
        edLines.removeAllElements();
        String s = ed.toString();
        int start = 0, last = -1, maxW = w - 16;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == ' ') last = i;
            if (f1.substringWidth(s, start, i - start + 1) > maxW) {
                int brk = last > start ? last + 1 : i;
                edLines.addElement(s.substring(start, brk));
                start = brk;
                last = -1;
            }
        }
        edLines.addElement(s.substring(start));
        int lh = f1.getHeight() + 2, vis = (h - 50) / lh, n = edLines.size();
        g.setFont(f1);
        g.setColor(0xDDDDDD);
        int y = 40;
        for (int i = Math.max(0, n - vis); i < n; i++) {
            g.drawString((String) edLines.elementAt(i), 8, y, TL);
            y += lh;
        }
    }

    // ---------- particles (fixed-point 1/16 px)
    static void initParticles(int w, int h, int n) {
        pn = n;
        collisions = 0;
        for (int i = 0; i < MAXP; i++) {
            px[i] = rnd(w) * 16;
            py[i] = rnd(h / 2) * 16;
            vx[i] = rnd(64) - 32;
            vy[i] = rnd(32) - 16;
        }
    }

    static void addParticles(int k) {
        pn += k;
        if (pn > MAXP) pn = MAXP;
    }

    static void stepParticles(int w, int h) {
        int mx = (w - 6) * 16, my = (h - 6) * 16;
        for (int i = 0; i < pn; i++) {
            vy[i] += 2;
            px[i] += vx[i];
            py[i] += vy[i];
            if (px[i] < 0) { px[i] = 0; vx[i] = -vx[i]; }
            else if (px[i] > mx) { px[i] = mx; vx[i] = -vx[i]; }
            if (py[i] > my) {
                py[i] = my;
                vy[i] = -vy[i] * 3 / 4;
                if (vy[i] > -4) vy[i] = -(20 + rnd(30));
            }
        }
        for (int i = 0; i < pn; i++)
            for (int j = i + 1; j < pn; j++) {
                int dx = (px[i] - px[j]) >> 4;
                if (dx > 6 || dx < -6) continue;
                int dy = (py[i] - py[j]) >> 4;
                if (dx * dx + dy * dy < 36) {
                    int t = vx[i]; vx[i] = vx[j]; vx[j] = t;
                    t = vy[i]; vy[i] = vy[j]; vy[j] = t;
                    collisions++;
                }
            }
    }

    static void drawParticles(Graphics g) {
        for (int i = 0; i < pn; i++) {
            g.setColor(PC[i & 7]);
            g.fillArc(px[i] >> 4, py[i] >> 4, 6, 6, 0, 360);
        }
    }

    // ---------- CPU workloads
    static void cpu(int id) {
        if (id == 9) {
            int a = seed;
            for (int i = 0; i < 20000; i++) {
                a = a * 31 + i;
                a ^= (a >>> 7);
                a += (a << 3);
                if ((a & 15) == 0) a /= 3;
            }
            sink = a;
            ops += 100000;
        } else if (id == 10) {
            byte[] a = m1, b = m2;
            int n = a.length;
            for (int i = 0; i < n; i++) a[i] = (byte) (i + sink);
            System.arraycopy(a, 0, b, 0, n);
            sink += b[n - 1];
            ops += n * 2;
        } else if (id == 11) {
            for (int i = 0; i < sortA.length; i++) sortA[i] = rnd(30000) * 7 + rnd(7);
            qs(sortA, 0, sortA.length - 1);
            ops += 1;
        }
    }

    static void burn(int n) {
        int a = seed;
        for (int i = 0; i < n; i++) {
            a = a * 31 + i;
            a ^= (a >>> 5);
        }
        sink = a;
    }

    static void qs(int[] a, int lo, int hi) {
        while (lo < hi) {
            int p = a[(lo + hi) >>> 1], i = lo, j = hi;
            while (i <= j) {
                while (a[i] < p) i++;
                while (a[j] > p) j--;
                if (i <= j) { int t = a[i]; a[i] = a[j]; a[j] = t; i++; j--; }
            }
            if (j - lo < hi - i) { qs(a, lo, j); lo = i; }
            else { qs(a, i, hi); hi = j; }
        }
    }
}
