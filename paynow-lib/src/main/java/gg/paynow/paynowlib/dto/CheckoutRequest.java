package gg.paynow.paynowlib.dto;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CheckoutRequest {

    public List<Line> lines = new ArrayList<>();

    public CheckoutRequest(List<String> productIds) {
        Map<String, Integer> quantities = new LinkedHashMap<>();
        for(String productId : productIds) {
            quantities.merge(productId, 1, Integer::sum);
        }
        quantities.forEach((productId, quantity) -> this.lines.add(new Line(productId, quantity)));
    }

    public static class Line {

        @SerializedName("product_id")
        public String productId;
        public int quantity;

        public Line(String productId, int quantity) {
            this.productId = productId;
            this.quantity = quantity;
        }

    }

}
