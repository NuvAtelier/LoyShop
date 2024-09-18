package com.snowgears.shop.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PriceNegotiatorTest {
    @Test
    public void noPurchase() {
        PriceNegotiator negotiator = new PriceNegotiator(false, 16, 64);

        // No Funds
        negotiator.negotiatePurchase(false, 0, 128, -1);

        Assertions.assertEquals(negotiator.getNegotiatedPrice(), -1);
        Assertions.assertEquals(negotiator.getNegotiatedAmountBeingSold(), -1);

        // Not enough inventory - NO partial sales
        negotiator.negotiatePurchase(false, 16, 63, -1);

        Assertions.assertEquals(negotiator.getNegotiatedPrice(), -1);
        Assertions.assertEquals(negotiator.getNegotiatedAmountBeingSold(), -1);

        // No inventory
        negotiator.negotiatePurchase(false, 16, 0, -1);

        Assertions.assertEquals(negotiator.getNegotiatedPrice(), -1);
        Assertions.assertEquals(negotiator.getNegotiatedAmountBeingSold(), -1);

        // No Funds - allow partial sales
        negotiator.negotiatePurchase(true, 0, 128, -1);

        Assertions.assertEquals(negotiator.getNegotiatedPrice(), -1);
        Assertions.assertEquals(negotiator.getNegotiatedAmountBeingSold(), -1);

        // Not enough inventory - allow partial sales
        negotiator.negotiatePurchase(true, 16, 3, -1);

        Assertions.assertEquals(negotiator.getNegotiatedPrice(), -1);
        Assertions.assertEquals(negotiator.getNegotiatedAmountBeingSold(), -1);

        // No inventory - allow partial sales
        negotiator.negotiatePurchase(true, 16, 0, -1);

        Assertions.assertEquals(negotiator.getNegotiatedPrice(), -1);
        Assertions.assertEquals(negotiator.getNegotiatedAmountBeingSold(), -1);
    }

    @Test
    public void normalPurchase() {
        PriceNegotiator negotiator = new PriceNegotiator(false,16,64);

        // More funds
        negotiator.negotiatePurchase(false,32,128,-1);

        Assertions.assertEquals(negotiator.getNegotiatedPrice(), 16);
        Assertions.assertEquals(negotiator.getNegotiatedAmountBeingSold(), 64);

        // Exact funds
        negotiator.negotiatePurchase(false,16,128,-1);

        Assertions.assertEquals(negotiator.getNegotiatedPrice(), 16);
        Assertions.assertEquals(negotiator.getNegotiatedAmountBeingSold(), 64);

        // Exact shop
        negotiator.negotiatePurchase(false,16,64,-1);

        Assertions.assertEquals(negotiator.getNegotiatedPrice(), 16);
        Assertions.assertEquals(negotiator.getNegotiatedAmountBeingSold(), 64);
    }

    @Test
    public void partialPurchaseBuyer() {
        // Test out whole division, price per unit = 4
        PriceNegotiator negotiator = new PriceNegotiator(false,16,64);

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
        PriceNegotiator negotiator = new PriceNegotiator(false,10,32);

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

    @Test
    public void partialPurchaseBuyer_priceFractionFlipped() {
        // Test out fractional price division, price per unit = 3.2
        PriceNegotiator negotiator = new PriceNegotiator(false,32,10);

        // Exact Funds Sale
        negotiator.negotiatePurchase(true,32,64,-1);

        Assertions.assertEquals(negotiator.getNegotiatedPrice(), 32);
        Assertions.assertEquals(negotiator.getNegotiatedAmountBeingSold(), 10);

        // Extra Funds Sale
        negotiator.negotiatePurchase(true,40,64,-1);

        Assertions.assertEquals(negotiator.getNegotiatedPrice(), 32);
        Assertions.assertEquals(negotiator.getNegotiatedAmountBeingSold(), 10);

        // Single Price - Should Fail & return -1
        negotiator.negotiatePurchase(true,1,64,-1);

        Assertions.assertEquals(negotiator.getNegotiatedPrice(), -1);
        Assertions.assertEquals(negotiator.getNegotiatedAmountBeingSold(), -1);

        // Single Partial
        negotiator.negotiatePurchase(true,4,64,-1);

        Assertions.assertEquals(negotiator.getNegotiatedPrice(), 4);
        Assertions.assertEquals(negotiator.getNegotiatedAmountBeingSold(), 1);

        // Low Partial
        negotiator.negotiatePurchase(true,10,64,-1);

        Assertions.assertEquals(negotiator.getNegotiatedPrice(), 10);
        Assertions.assertEquals(negotiator.getNegotiatedAmountBeingSold(), 3);

        // Half Sale
        negotiator.negotiatePurchase(true,16,64,-1);

        Assertions.assertEquals(negotiator.getNegotiatedPrice(), 16);
        Assertions.assertEquals(negotiator.getNegotiatedAmountBeingSold(), 5);

        // Half Sale - extra funds
        negotiator.negotiatePurchase(true,19,64,-1);

        Assertions.assertEquals(negotiator.getNegotiatedPrice(), 16);
        Assertions.assertEquals(negotiator.getNegotiatedAmountBeingSold(), 5);

        // Almost Full
        negotiator.negotiatePurchase(true,26,64,-1);

        Assertions.assertEquals(negotiator.getNegotiatedPrice(), 26);
        Assertions.assertEquals(negotiator.getNegotiatedAmountBeingSold(), 8);

        // Almost Full - extra funds
        negotiator.negotiatePurchase(true,28,64,-1);

        Assertions.assertEquals(negotiator.getNegotiatedPrice(), 26);
        Assertions.assertEquals(negotiator.getNegotiatedAmountBeingSold(), 8);
    }
}
