package com.snowgears.shop.util;

import com.snowgears.shop.Shop;
import com.snowgears.shop.shop.ShopType;

public class PriceNegotiator {
    boolean TX_DEBUG_LOGGING = false;

    double price = 0;
    double originalPrice = 0;
    double negotiatedPrice = -1;
    int amountBeingSold = 0;
    int originalAmountBeingSold = 0;
    int negotiatedAmountBeingSold = -1;

    public PriceNegotiator(boolean debugLogging, int originalAmountBeingSold, double originalPrice) {
        if (!TX_DEBUG_LOGGING) { TX_DEBUG_LOGGING = debugLogging; }
        this.originalAmountBeingSold = originalAmountBeingSold;
        this.originalPrice = originalPrice;
    }
    public void negotiatePurchase(boolean allowPartialSales, double buyerAvailableFunds, int sellerInventoryQuantity, int desiredAmount) {
        // Clear out negotiated price
        this.negotiatedPrice = -1;
        this.negotiatedAmountBeingSold = -1;

        // Check max amount being bought
        int maxPurchaseAmount = originalAmountBeingSold;
        // Check if we passed in the amount that we want to purchase, sometimes we want to purchase more
        if (desiredAmount != -1) {
            maxPurchaseAmount = desiredAmount;
        }

        // Price for a single item
        // Greater than 1 if price is higher than amount being sold
        // Less than one if price is lower than amount being sold
        double pricePerItem = originalPrice / originalAmountBeingSold;
        // The number of items to equal one price unit
        double itemsPerPrice = 1;
        // If our price is less than 1, then we are buying multiple items with each order
        if (pricePerItem < 1) {
            itemsPerPrice = 1 / pricePerItem;
        }

        // Calculate the maximum qty that the buyer can afford to buy
        double buyerMaxQtyPurchase = (buyerAvailableFunds / pricePerItem) / itemsPerPrice;
        // Calculate the maximum items the seller has to sell
        double sellerMaxQtySale = sellerInventoryQuantity / itemsPerPrice;

        // The maximum qty that we can buy/sell with our available funds
        double FIX_ROUNDING = 1e-5; // Fix rounding errors (where one of the quantities comes out as x.99999~, make sure that is rounded to a while number
        double maxPurchasableQuantity = Math.floor(Math.min(buyerMaxQtyPurchase, sellerMaxQtySale) + FIX_ROUNDING);

        // The number of items we are buying
        int itemsBeingBought = (int) Math.floor(maxPurchasableQuantity * itemsPerPrice);
        // The overall price we are paying
        double priceBeingPaid = Math.ceil(itemsBeingBought * pricePerItem);

        if (TX_DEBUG_LOGGING) {
            System.out.println("* pricePerItem: " + pricePerItem);
            System.out.println("* itemsPerPrice: " + itemsPerPrice);
            System.out.println("* buyerMaxQtyPurchase: " + buyerMaxQtyPurchase);
            System.out.println("* this.seller.getInventoryQuantity(this.itemBeingSold): " + sellerInventoryQuantity);
            System.out.println("* sellerMaxQtySale: " + sellerMaxQtySale);
            System.out.println("* maxPurchasableQuantity: " + maxPurchasableQuantity);
            System.out.println("* itemsBeingBought: " + itemsBeingBought);
            System.out.println("* priceBeingPaid: " + priceBeingPaid);
        }

        // If we don't have enough to buy/sell, then we can't negotiate a new price! Return so normal error handling can occur.
        if (maxPurchasableQuantity <= 0 || itemsBeingBought <= 0 || priceBeingPaid <= 0) {
            // Reset variables
            this.price = originalPrice;
            this.amountBeingSold = originalAmountBeingSold;
            return;
        }

        // Check if partial sales are not allowed
        if (!allowPartialSales) {
            // Multiple Quantity of original amount sales code (for full stack sales)
            double quantityPerOriginalAmount = originalAmountBeingSold / itemsPerPrice;
            // Force the quantity to be a multiple of our original amount when performing multiple sales
            int roundedQuantity = (int) (Math.floor(maxPurchasableQuantity / quantityPerOriginalAmount) * quantityPerOriginalAmount);

            // Partial sales are not allowed, we need to default to a multiple of our default amount/price
            itemsBeingBought = (int) Math.floor(roundedQuantity * itemsPerPrice);
            priceBeingPaid = Math.ceil(itemsBeingBought * pricePerItem);

            // Set max purchase amount to be rounded down to a multiple of our original amount
            maxPurchaseAmount = (int) (Math.floor(maxPurchaseAmount / originalAmountBeingSold) * originalAmountBeingSold);

            if (TX_DEBUG_LOGGING) {
                System.out.println("*** roundedQuantity: " + roundedQuantity);
                System.out.println("*** quantityPerOriginalAmount: " + quantityPerOriginalAmount);
                System.out.println("*** itemsBeingBought: " + itemsBeingBought);
                System.out.println("*** priceBeingPaid: " + priceBeingPaid);
                System.out.println("*** maxPurchaseAmount: " + maxPurchaseAmount);
            }
        }

        // Make sure we only sell up to our maxPurchaseAmount (normally the original amount, but can be set higher)
        if (itemsBeingBought > maxPurchaseAmount) {
            if (TX_DEBUG_LOGGING) { System.out.println("itemsBeingBought > maxPurchaseAmount: " + itemsBeingBought + " > " + maxPurchaseAmount); }
            itemsBeingBought = maxPurchaseAmount;
            priceBeingPaid = Math.ceil(maxPurchaseAmount * pricePerItem);
            if (TX_DEBUG_LOGGING) { System.out.println("*-* itemsBeingBought: " + itemsBeingBought); }
            if (TX_DEBUG_LOGGING) { System.out.println("*-* priceBeingPaid: " + priceBeingPaid); }
        }

        // If we are not able to buy/sell, just leave the price/amount being sold as-is for error handling
        if (itemsBeingBought == 0 || priceBeingPaid == 0) {
            return;
        }

        // We are a valid price, go ahead and set it!
        this.amountBeingSold = itemsBeingBought;
        this.price = priceBeingPaid;
        // Store explicitly negotiated price (used in tests)
        this.negotiatedAmountBeingSold = itemsBeingBought;
        this.negotiatedPrice = priceBeingPaid;

        if (TX_DEBUG_LOGGING) {
            System.out.println("-* amountBeingSold: " + this.amountBeingSold);
            System.out.println("-* price: " + this.price);
            System.out.println("-* originalAmountBeingSold: " + originalAmountBeingSold);
            System.out.println("-* originalPrice: " + originalPrice);
        }
    }

    public double getPrice() {
        return price;
    }

    public int getAmountBeingSold() {
        return amountBeingSold;
    }

    public double getNegotiatedPrice() {
        return negotiatedPrice;
    }

    public int getNegotiatedAmountBeingSold() {
        return negotiatedAmountBeingSold;
    }
}
