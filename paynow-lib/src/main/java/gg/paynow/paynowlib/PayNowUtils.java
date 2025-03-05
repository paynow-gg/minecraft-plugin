package gg.paynow.paynowlib;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PayNowUtils {
    public static final ExecutorService ASYNC_EXEC = Executors.newFixedThreadPool(4,
            runnable -> {
                Thread thread = Executors.defaultThreadFactory().newThread(runnable);
                thread.setName("PayNow-ThreadPool");
                thread.setDaemon(true);
                return thread;
            });

    public static boolean isSuccess(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

}
