import javax.microedition.midlet.MIDlet;
import javax.microedition.lcdui.Display;

public class PhoneBench extends MIDlet {
    private BenchCanvas c;

    public void startApp() {
        if (c == null) {
            c = new BenchCanvas(this);
            c.start();
        }
        Display.getDisplay(this).setCurrent(c);
    }
    public void pauseApp() { if (c != null) c.stop(false); }
    public void destroyApp(boolean unconditional) { if (c != null) c.stop(true); }
    void exit() { destroyApp(true); notifyDestroyed(); }
}
