package gg.paynow.paynowlib;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PayNowUtils {
    private static final int ASYNC_THREADS = 4;

    public static final ExecutorService ASYNC_EXEC = Executors.newFixedThreadPool(ASYNC_THREADS,
            runnable -> {
                Thread thread = Executors.defaultThreadFactory().newThread(runnable);
                thread.setName("PayNow-ThreadPool");
                thread.setDaemon(true);
                return thread;
            });

    public static final CloseableHttpClient HTTP_CLIENT = HttpClients.custom()
            .setDefaultRequestConfig(RequestConfig.custom()
                    .setConnectTimeout(10_000)
                    .setConnectionRequestTimeout(10_000)
                    .setSocketTimeout(30_000)
                    .build())
            .setMaxConnPerRoute(ASYNC_THREADS)
            .setMaxConnTotal(ASYNC_THREADS)
            .build();

    public static boolean isSuccess(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    public static void shutdown() {
        ASYNC_EXEC.shutdown();
        try {
            HTTP_CLIENT.close();
        } catch (IOException ignored) {
        }
    }

}
