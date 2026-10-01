package gg.paynow.paynowlib;

import java.io.IOException;

public class CheckoutException extends IOException {

    public CheckoutException(String message) {
        super(message);
    }

}
