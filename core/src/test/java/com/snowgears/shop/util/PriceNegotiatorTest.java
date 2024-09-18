package com.snowgears.shop.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PriceNegotiatorTest {
    @Test
    public void normalPurchase() {
        PriceNegotiator negotiator = new PriceNegotiator(false,64,16);

        // More funds
        negotiator.negotiatePurchase(false,32,128,-1);

        Assertions.assertEquals(negotiator.getNegotiatedPrice(), 16);
        Assertions.assertEquals(negotiator.getNegotiatedAmountBeingSold(), 64);

        // Exact funds
        negotiator.negotiatePurchase(false,16,128,-1);

        Assertions.assertEquals(negotiator.getNegotiatedPrice(), 16);
        Assertions.assertEquals(negotiator.getNegotiatedAmountBeingSold(), 64);
    }

    @Test
    public void partialPurchaseBuyer() {
        // Test out whole division, price per unit = 4
        PriceNegotiator negotiator = new PriceNegotiator(false,64,16);

        // Exact Funds Sale
        negotiator.negotiatePurchase(true,16,128,-1);

        Assertions.assertEquals(negotiator.getNegotiatedPrice(), 16);
        Assertions.assertEquals(negotiator.getNegotiatedAmountBeingSold(), 64);

        // Extra Funds Sale
        negotiator.negotiatePurchase(true,18,128,-1);

        Assertions.assertEquals(negotiator.getNegotiatedPrice(), 16);
        Assertions.assertEquals(negotiator.getNegotiatedAmountBeingSold(), 64);

        // Single Partial
        negotiator.negotiatePurchase(true,1,128,-1);

        Assertions.assertEquals(negotiator.getNegotiatedPrice(), 1);
        Assertions.assertEquals(negotiator.getNegotiatedAmountBeingSold(), 4);

        // Low Partial
        negotiator.negotiatePurchase(true,3,128,-1);

        Assertions.assertEquals(negotiator.getNegotiatedPrice(), 3);
        Assertions.assertEquals(negotiator.getNegotiatedAmountBeingSold(), 12);

        // Half Sale
        negotiator.negotiatePurchase(true,8,128,-1);

        Assertions.assertEquals(negotiator.getNegotiatedPrice(), 8);
        Assertions.assertEquals(negotiator.getNegotiatedAmountBeingSold(), 32);

        // Almost Full
        negotiator.negotiatePurchase(true,15,128,-1);

        Assertions.assertEquals(negotiator.getNegotiatedPrice(), 15);
        Assertions.assertEquals(negotiator.getNegotiatedAmountBeingSold(), 60);
    }

    @Test
    public void partialPurchaseBuyer_priceFraction() {
        // Test out fractional price division, price per unit = 3.2
        PriceNegotiator negotiator = new PriceNegotiator(true,32,10);

        // Exact Funds Sale
        negotiator.negotiatePurchase(true,10,128,-1);

        Assertions.assertEquals(negotiator.getNegotiatedPrice(), 10);
        Assertions.assertEquals(negotiator.getNegotiatedAmountBeingSold(), 32);

        // Extra Funds Sale
        negotiator.negotiatePurchase(true,15,128,-1);

        Assertions.assertEquals(negotiator.getNegotiatedPrice(), 10);
        Assertions.assertEquals(negotiator.getNegotiatedAmountBeingSold(), 32);

        // Single Partial
        negotiator.negotiatePurchase(true,1,128,-1);

        Assertions.assertEquals(negotiator.getNegotiatedPrice(), 1);
        Assertions.assertEquals(negotiator.getNegotiatedAmountBeingSold(), 3);

        // Low Partial
        negotiator.negotiatePurchase(true,3,128,-1);

        Assertions.assertEquals(negotiator.getNegotiatedPrice(), 3);
        Assertions.assertEquals(negotiator.getNegotiatedAmountBeingSold(), 9);

        // Half Sale
        negotiator.negotiatePurchase(true,5,128,-1);

        Assertions.assertEquals(negotiator.getNegotiatedPrice(), 5);
        Assertions.assertEquals(negotiator.getNegotiatedAmountBeingSold(), 16);

        // Almost Full
        negotiator.negotiatePurchase(true,9,128,-1);

        Assertions.assertEquals(negotiator.getNegotiatedPrice(), 9);
        Assertions.assertEquals(negotiator.getNegotiatedAmountBeingSold(), 28);
    }
}
