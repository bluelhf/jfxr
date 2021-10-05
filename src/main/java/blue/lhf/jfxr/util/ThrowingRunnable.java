package blue.lhf.jfxr.util;

import java.io.IOException;

public interface ThrowingRunnable extends Runnable {
    /**
     * When an object implementing interface {@code Runnable} is used
     * to create a thread, starting the thread causes the object's
     * {@code run} method to be called in that separately executing
     * thread.
     * <p>
     * The general contract of the method {@code run} is that it may
     * take any action whatsoever.
     *
     * @see     java.lang.Thread#run()
     */
    default void run() {
        try {
            run0();
        } catch (Throwable e) {
            if (e instanceof RuntimeException re) throw re;
            if (e instanceof Error err) throw err;
            throw new RuntimeException(e);
        }
    }

    void run0() throws IOException;
}
