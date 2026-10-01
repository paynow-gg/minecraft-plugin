package gg.paynow.paynowlib.dto;

public class CustomerAuthRequest {

    public String platform;
    public String id;

    public CustomerAuthRequest(String platform, String id) {
        this.platform = platform;
        this.id = id;
    }

}
