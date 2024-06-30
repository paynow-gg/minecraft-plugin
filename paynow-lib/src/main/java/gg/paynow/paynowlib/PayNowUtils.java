package gg.paynow.paynowlib;

public class PayNowUtils {

    public static boolean isSuccess(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

}
