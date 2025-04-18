package com.snowgears.shop.util;

import com.snowgears.shop.Shop;
import com.snowgears.shop.shop.ShopType;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Handles price negotiations for shop transactions.
 * <p>
 * This class calculates the final price and quantity for shop transactions based on
 * available funds, inventory, and desired purchase amount.
 * <p>
 * The negotiator supports two payment modes:
 * <ul>
 *   <li>Integer payments (default): Prices are always rounded up to the next full unit.</li>
 *   <li>Fractional payments: Prices can have decimal precision (up to 2 decimal places).</li>
 * </ul>
 * <p>
 * The only difference between the two modes is how prices are rounded. Item quantities
 * are calculated the same way in both modes to ensure consistent behavior.
 */
public class PriceNegotiator {
    boolean TX_DEBUG_LOGGING = false;
    
    // Constant for price precision (cents)
    private static final int PRICE_PRECISION = 2;
    
    // Flag to determine if fractional payments are supported
    private final boolean supportsFractionalPayments;

    double price = 0;
    double originalPrice = 0;
    double negotiatedPrice = -1;
    int amountBeingSold = 0;
    int originalAmountBeingSold = 0;
    int negotiatedAmountBeingSold = -1;
    /**
     * Creates a new PriceNegotiator.
     *
     * @param debugLogging              Whether to enable debug logging
     * @param originalPrice             The original price for the items
     * @param originalAmountBeingSold   The original quantity of items
     * @param supportsFractionalPayments Whether to enable fractional payments (prices with decimal places)
     */
    public PriceNegotiator(boolean debugLogging, double originalPrice, int originalAmountBeingSold, boolean supportsFractionalPayments) {
        if (!TX_DEBUG_LOGGING) { TX_DEBUG_LOGGING = debugLogging; }
        this.originalPrice = originalPrice;
        this.originalAmountBeingSold = originalAmountBeingSold;
        this.supportsFractionalPayments = supportsFractionalPayments;
    }
    
    /**
     * Rounds a decimal value to cents precision (2 decimal places).
     * Uses half-up rounding to ensure proper currency handling.
     *
     * @param value The value to round
     * @return The value rounded to 2 decimal places
     */
    private double roundToCents(double value) {
        BigDecimal bd = new BigDecimal(Double.toString(value)); // Use string to avoid floating point precision issues
        bd = bd.setScale(PRICE_PRECISION, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }
    
    /**
     * Calculates the exact price based on the items and price per item.
     * Uses different rounding strategies depending on the payment mode.
     *
     * @param items The number of items to purchase
     * @param pricePerItem The price per individual item
     * @return The calculated price, either rounded to cents (fractional) or rounded up (integer).
     *         Returns -1 if the calculation would result in a price too small (zero).
     */
    private double calculateExactPrice(int items, double pricePerItem) {
        // Check for extreme edge cases that would result in a price that's too small
        // For both fractional and non-fractional, we need to check this for edge cases
        if (items * pricePerItem < 0.01) {
            return -1; // Indicate that this is an invalid price (will trigger "no purchase")
        }
        
        // Always use BigDecimal for the calculation to avoid floating-point precision issues
        BigDecimal itemsBD = BigDecimal.valueOf(items);
        BigDecimal pricePerItemBD = new BigDecimal(Double.toString(pricePerItem));
        BigDecimal result = itemsBD.multiply(pricePerItemBD);
        
        if (supportsFractionalPayments) {
            // Round to 2 decimal places (cents) using HALF_UP rounding
            result = result.setScale(PRICE_PRECISION, RoundingMode.HALF_UP);
            double finalPrice = result.doubleValue();
            
            // Double-check if the final price is too small to be valid (would be zero)
            if (finalPrice < 0.01) {
                return -1; // Indicate "no purchase" for extremely small prices
            }
            return finalPrice;
        } else {
            // For integer payments, round up to the next whole number
            // But first check if the original value is too small
            double rawValue = result.doubleValue();
            if (rawValue < 0.01) {
                return -1; // Avoid prices that are too small
            }
            result = result.setScale(0, RoundingMode.HALF_UP);
            return result.doubleValue();
        }
    }
    
    public void negotiatePurchase(boolean allowPartialSales, double buyerAvailableFunds, int sellerInventoryQuantity, int desiredAmount) {
        // Clear out negotiated price
        this.negotiatedPrice = -1;
        this.negotiatedAmountBeingSold = -1;

        // Check max amount being bought
        int maxPurchaseAmount = this.originalAmountBeingSold;
        // Check if we passed in the amount that we want to purchase, sometimes we want to purchase more
        if (desiredAmount != -1 && desiredAmount > 0 && desiredAmount > this.originalAmountBeingSold) {
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

        if (TX_DEBUG_LOGGING) {
            System.out.println("* pricePerItem: " + pricePerItem);
            System.out.println("* itemsPerPrice: " + itemsPerPrice);
        }

        // Special case for extreme ratios: if price per item is too small, cancel the purchase
        if (supportsFractionalPayments && pricePerItem < 0.00001) {
            if (desiredAmount == originalAmountBeingSold) {
                // Only allow the full purchase in this case
                this.negotiatedAmountBeingSold = originalAmountBeingSold;
                this.negotiatedPrice = originalPrice;
                this.amountBeingSold = originalAmountBeingSold;
                this.price = originalPrice;
                return;
            } else if (desiredAmount < originalAmountBeingSold) {
                // For partial purchases of extremely low-price items, ensure the minimum price is 0.01
                double calculatedPrice = (double)desiredAmount / originalAmountBeingSold * originalPrice;
                if (calculatedPrice < 0.01) {
                    // Can't complete the purchase, price would be too small
                    return;
                }
            }
        }

        // Special handling for fixed desiredAmount when using fractional payments
        if (supportsFractionalPayments && desiredAmount > 0 && desiredAmount <= sellerInventoryQuantity) {
            // Use BigDecimal for exact price calculation
            BigDecimal desiredAmountBD = BigDecimal.valueOf(desiredAmount);
            BigDecimal originalAmountBeingSoldBD = BigDecimal.valueOf(originalAmountBeingSold);
            BigDecimal originalPriceBD = new BigDecimal(Double.toString(originalPrice));
            
            // Calculate exact proportion
            BigDecimal proportion = desiredAmountBD.divide(originalAmountBeingSoldBD, 10, RoundingMode.HALF_UP);
            BigDecimal exactPrice = originalPriceBD.multiply(proportion);
            
            // Round to cents
            exactPrice = exactPrice.setScale(PRICE_PRECISION, RoundingMode.HALF_UP);
            
            // Check for extremely small prices that would be invalid
            if (exactPrice.compareTo(BigDecimal.valueOf(0.01)) < 0) {
                // Price is too small (less than 0.01), can't complete purchase
                return;
            }
            
            // Check if buyer can afford this exact amount
            if (buyerAvailableFunds >= exactPrice.doubleValue()) {
                // Buyer can afford the exact amount
                this.amountBeingSold = desiredAmount;
                this.price = exactPrice.doubleValue();
                this.negotiatedAmountBeingSold = desiredAmount;
                this.negotiatedPrice = exactPrice.doubleValue();
                return;
            }
        }

        // Calculate the maximum qty that the buyer can afford to buy
        double buyerMaxQtyPurchase;
        if (supportsFractionalPayments) {
            // For fractional payments, we use the same approach as integer payments
            // to ensure consistent behavior - the only difference is in the price rounding
            // This ensures that both modes buy the same number of items with the same funds
            buyerMaxQtyPurchase = (buyerAvailableFunds / pricePerItem) / itemsPerPrice;
        } else {
            // For integer payments, use original calculation
            buyerMaxQtyPurchase = (buyerAvailableFunds / pricePerItem) / itemsPerPrice;
        }
        
        // Calculate the maximum items the seller has to sell
        double sellerMaxQtySale = sellerInventoryQuantity / itemsPerPrice;

        // The maximum qty that we can buy/sell with our available funds
        double FIX_ROUNDING = 1e-5; // Fix rounding errors (where one of the quantities comes out as x.99999~, make sure that is rounded to a while number
        double maxPurchasableQuantity = Math.floor(Math.min(buyerMaxQtyPurchase, sellerMaxQtySale) + FIX_ROUNDING);

        // The number of items we are buying
        int itemsBeingBought = (int) Math.floor(maxPurchasableQuantity * itemsPerPrice);
        if (TX_DEBUG_LOGGING) { System.out.println("*-* itemsBeingBought (pre-round): " + (maxPurchasableQuantity * itemsPerPrice)); }
        
        // The overall price we are paying
        double priceBeingPaid = calculateExactPrice(itemsBeingBought, pricePerItem);
        
        // Check if the calculated price indicates a "no purchase" situation
        if (priceBeingPaid == -1) {
            // The price is too small or otherwise invalid, can't complete the purchase
            return;
        }
        
        if (TX_DEBUG_LOGGING) { 
            System.out.println("*-* priceBeingPaid (pre-round): " + (itemsBeingBought * pricePerItem));
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
            
            // Calculate price with our helper method
            priceBeingPaid = calculateExactPrice(itemsBeingBought, pricePerItem);
            
            // Check again for "no purchase" response
            if (priceBeingPaid == -1) {
                return;
            }

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
            
            // Calculate price with our helper method
            priceBeingPaid = calculateExactPrice(itemsBeingBought, pricePerItem);
            
            // Check again for "no purchase" response
            if (priceBeingPaid == -1) {
                return;
            }
            
            if (TX_DEBUG_LOGGING) { System.out.println("*-* itemsBeingBought: " + itemsBeingBought); }
            if (TX_DEBUG_LOGGING) { System.out.println("*-* priceBeingPaid: " + priceBeingPaid); }
        }

        // If we are not able to buy/sell, just leave the price/amount being sold as-is for error handling
        if (itemsBeingBought == 0 || priceBeingPaid <= 0) {
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
    
    /**
     * Checks if this negotiator supports fractional payments.
     *
     * @return true if fractional payments (with decimal precision) are enabled, false otherwise
     */
    public boolean supportsFractionalPayments() {
        return supportsFractionalPayments;
    }
}
