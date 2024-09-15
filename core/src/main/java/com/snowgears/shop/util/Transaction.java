package com.snowgears.shop.util;

import com.snowgears.shop.Shop;
import com.snowgears.shop.event.PlayerExchangeShopEvent;
import com.snowgears.shop.shop.AbstractShop;
import com.snowgears.shop.shop.ComboShop;
import com.snowgears.shop.shop.GambleShop;
import com.snowgears.shop.shop.ShopType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class Transaction {
    boolean TX_DEBUG_LOGGING = false;

    private Player player;
    private AbstractShop shop;
    private ShopType transactionType;

    private TransactionError error;

    // The buyer in the transaction
    private TransactionParty buyer;
    // The seller in the transaction
    private TransactionParty seller;

    // The original price of the transaction in currency (single qty)
    private double originalPrice;
    // The current price of the tx, might be a higher or lower quantity than originalPrice
    private double price;

    // The item that is being sold in the transaction
    private ItemStack itemBeingSold;
    // The original amount of the item being sold (single qty)
    private int originalAmountBeingSold;
    // The amount of the item being sold in the tx, might be a higher or lower quantity than originalAmountBeingSold
    private int amountBeingSold;

    public Transaction(Player player, AbstractShop shop, ShopType transactionType) {
        this.error = null;

        this.player = player;
        this.shop = shop;
        this.transactionType = transactionType;

        this.price = shop.getPrice();
        this.itemBeingSold = shop.getItemStack();
        this.amountBeingSold = shop.getAmount();

        if (transactionType == ShopType.GAMBLE) {
            this.buyer = new TransactionParty(true, false, player, player.getInventory());
            this.seller = new TransactionParty(false, shop.isAdmin(), shop.getOwner(), shop.getInventory());

            ((GambleShop)shop).setGambleItem();
            this.itemBeingSold = ((GambleShop) shop).getGambleItem();
            this.amountBeingSold = ((GambleShop) shop).getGambleItem().getAmount();
        }
        else if (shop.getType() == ShopType.BARTER) {
            // Player is buying from the shop!
            // The buyer is going to use an item for their currency/payment
            this.buyer = new TransactionParty(true, false, player, player.getInventory(), shop.getSecondaryItemStack());
            this.seller = new TransactionParty(false, shop.isAdmin(), shop.getOwner(), shop.getInventory(), shop.getSecondaryItemStack());
        }
        else if (transactionType == ShopType.BUY) {
            // Shop is buying from the player
            this.buyer = new TransactionParty(false, shop.isAdmin(), shop.getOwner(), shop.getInventory());
            this.seller = new TransactionParty(true, false, player, player.getInventory());
        }
        else if (transactionType == ShopType.SELL) {
            // Shop is selling to the player
            this.buyer = new TransactionParty(true, false, player, player.getInventory());
            this.seller = new TransactionParty(false, shop.isAdmin(), shop.getOwner(), shop.getInventory());

            // If we are a combo shop, then we need to grab the sell price
            if (shop instanceof ComboShop) {
                this.price = ((ComboShop)shop).getPriceSell();
            }
        }

        // Store the original values for the price/amount that comes from the shop directly
        // the price and amountBeingSold variables will change if there is a higher or lower qty being transacted
        // i.e. a partial sale, or a sale of a full stack of items
        this.originalPrice = this.price;
        this.originalAmountBeingSold = this.amountBeingSold;
    }

    public void negotiatePurchase() {
        this.negotiatePurchase(-1);
    }

    // Calculate the maximum amount the transaction can spend/purchase
    public void negotiatePurchase(int desiredAmount) {
        // Don't perform this processing if we are a gamble shop!
        if (shop.getType() == ShopType.GAMBLE) { return; }

        int maxPurchaseAmount = this.originalAmountBeingSold;
        // Check if we passed in the amount that we want to purchase, sometimes we want to purchase more
        if (desiredAmount != -1) { maxPurchaseAmount = desiredAmount; };

        // Price for a single item
        // Greater than 1 if price is higher than amount being sold
        // Less than one if price is lower than amount being sold
        double pricePerItem = this.originalPrice / this.originalAmountBeingSold;
        // The number of items to equal one price unit
        double itemsPerPrice = 1;
        // If our price is less than 1, then we are buying multiple items with each order
        if (pricePerItem < 1) { itemsPerPrice = 1/pricePerItem; }

        // Calculate the maximum qty that the buyer can afford to buy
        double buyerMaxQtyPurchase = (this.buyer.getAvailableFunds() / pricePerItem) / itemsPerPrice;
        // Calculate the maximum items the seller has to sell
        double sellerMaxQtySale = this.seller.getInventoryQuantity(this.itemBeingSold) / itemsPerPrice;

        // The maximum qty that we can buy/sell with our available funds
        double maxPurchasableQuantity = Math.floor( Math.min(buyerMaxQtyPurchase, sellerMaxQtySale) );

        // The number of items we are buying
        int itemsBeingBought = (int) Math.floor(maxPurchasableQuantity * itemsPerPrice);
        // The overall price we are paying
        double priceBeingPaid = Math.ceil(itemsBeingBought * pricePerItem);

        if (TX_DEBUG_LOGGING) {
            System.out.println("* pricePerItem: " + pricePerItem);
            System.out.println("* itemsPerPrice: " + itemsPerPrice);
            System.out.println("* buyerMaxQtyPurchase: " + buyerMaxQtyPurchase);
            System.out.println("* this.seller.getInventoryQuantity(this.itemBeingSold): " + this.seller.getInventoryQuantity(this.itemBeingSold));
            System.out.println("* sellerMaxQtySale: " + sellerMaxQtySale);
            System.out.println("* maxPurchasableQuantity: " + maxPurchasableQuantity);
            System.out.println("* itemsBeingBought: " + itemsBeingBought);
            System.out.println("* priceBeingPaid: " + priceBeingPaid);
        }

        // If we don't have enough to buy/sell, then we can't negotiate a new price! Return so normal error handling can occur.
        if (maxPurchasableQuantity <= 0 || itemsBeingBought <= 0 || priceBeingPaid <= 0) {
            // Reset variables
            this.price = this.originalPrice;
            this.amountBeingSold = this.originalAmountBeingSold;
            return;
        }

        // Check if partial sales are not allowed
        if (!Shop.getPlugin().getAllowPartialSales()) {
            // Multiple Quantity of original amount sales code (for full stack sales)
            double quantityPerOriginalAmount = this.originalAmountBeingSold / itemsPerPrice;
            // Force the quantity to be a multiple of our original amount when performing multiple sales
            int roundedQuantity = (int) (Math.floor(maxPurchasableQuantity / quantityPerOriginalAmount) * quantityPerOriginalAmount);

            // Partial sales are not allowed, we need to default to a multiple of our default amount/price
            itemsBeingBought = (int) Math.floor(roundedQuantity * itemsPerPrice);
            priceBeingPaid = Math.ceil(roundedQuantity * pricePerItem);

            // Set max purchase amount to be rounded down to a multiple of our original amount
            maxPurchaseAmount = (int) (Math.floor(maxPurchaseAmount / this.originalAmountBeingSold) * originalAmountBeingSold);

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

        if (TX_DEBUG_LOGGING) {
            System.out.println("-* amountBeingSold: " + this.amountBeingSold);
            System.out.println("-* price: " + this.price);
            System.out.println("-* originalAmountBeingSold: " + this.originalAmountBeingSold);
            System.out.println("-* originalPrice: " + this.originalPrice);
        }
    }

    // Verify there are no errors with the transaction, note, you must run negotiatePurchase before verifying!
    public TransactionError verify() {
        // Check if the transaction has already been cancelled
        if (this.error == TransactionError.CANCELLED) { return this.error; }

        // Don't process another transaction if there is one in progress!
        if (shop.isPerformingTransaction()) { return this.setError(TransactionError.CANCELLED); }

        // Check if the buyer has enough funds to pay for the tx
        if (this.buyer.getAvailableFunds() < this.price) {
            // Failed Verification: The buyer does not have enough funds to pay for the transaction!
            if (this.buyer.isPlayer()) {
                return this.setError(TransactionError.INSUFFICIENT_FUNDS_PLAYER);
            } else {
                // Failed Verification: The buyer does not have enough funds to pay for the transaction!
                shop.updateStock(); // Update sign to show out of stock in case we were out of sync and the shop showed as in-stock
                return this.setError(TransactionError.INSUFFICIENT_FUNDS_SHOP);
            }
        }

        // Check if seller has enough items to sell
        if (this.seller.getInventoryQuantity(this.itemBeingSold) < this.amountBeingSold) {
            // Failed Verification: There was not enough items in the sellers inventory to cover the transaction
            if (this.seller.isPlayer()) {
                return this.setError(TransactionError.INSUFFICIENT_FUNDS_PLAYER);
            } else {
                shop.updateStock(); // Update sign to show out of stock in case we were out of sync and the shop showed as in-stock
                return this.setError(TransactionError.INSUFFICIENT_FUNDS_SHOP);
            }
        }

        // Create the item being sold and set the amount we are selling in the tx
        ItemStack itemToBeAdded = this.itemBeingSold.clone();
        itemToBeAdded.setAmount(this.amountBeingSold);
        // Check if the buyer has inventory space to receive the item
        if (!this.buyer.hasRoomForItem(itemToBeAdded)) {
            // Failed Verification: The buyer does not have space to receive the item being bought
            if (this.buyer.isPlayer()) { return this.setError(TransactionError.INVENTORY_FULL_PLAYER); }
            else {  return this.setError(TransactionError.INVENTORY_FULL_SHOP); }
        }

        // Check if the seller can accept the payment amount, aka if they have space for a currency item to be place into their inventory
        if (!this.seller.canAcceptPayment(this.price)) {
            // Failed Verification: The buyer does not have space to receive the item being bought
            if (this.seller.isPlayer()) { return this.setError(TransactionError.INVENTORY_FULL_PLAYER); }
            else { return this.setError(TransactionError.INVENTORY_FULL_SHOP); }
        }

        // Check if any other plugins want to cancel the transaction
        PlayerExchangeShopEvent e = new PlayerExchangeShopEvent(player, shop);
        Bukkit.getPluginManager().callEvent(e);
        if(e.isCancelled()) {
            if (TX_DEBUG_LOGGING) { System.out.println("!!! CANCELLED because of plugin hooking into shop!"); }
            return this.setError(TransactionError.CANCELLED);
        }

        // There were no errors, so we are good to proceed!
        if (TX_DEBUG_LOGGING) { System.out.println("Transaction Verified Successfully!"); }
        return this.setError(TransactionError.NONE);
    }

    public TransactionError execute() {
        // Check to see if we had any errors, you must do this before performing the transaction!
        if (this.verify() != TransactionError.NONE) { return this.error; }

        // Perform the transaction, we are fully verified, so we can just directly run through the transaction
        ItemStack itemSold = this.itemBeingSold.clone();
        itemSold.setAmount(this.amountBeingSold);

        // Swap funds
        boolean paymentSuccessful = this.buyer.deductFunds(this.price);
        // Verify we took the funds from the buyer
        if (!paymentSuccessful) {
            if (this.buyer.isPlayer()) { return this.setError(TransactionError.INSUFFICIENT_FUNDS_PLAYER); }
            else { return this.setError(TransactionError.INSUFFICIENT_FUNDS_SHOP); }
        }
        // Pay the seller the funds
        this.seller.depositFunds(this.price);

        // Swap Item
        this.seller.deductItem(itemSold);
        this.buyer.depositItem(itemSold);

        // Special handling for Gamble shops!
        if (shop.getType() == ShopType.GAMBLE) {
            // Cycle display to show won item
            if (shop instanceof GambleShop) { ((GambleShop) shop).shuffleGambleItem(player); }
            // Exit early
            return this.setError(TransactionError.NONE);
        }

        // Misc shop handling tasks
        // if the shop is connected to an ender inventory, save the contents as needed
        if(shop.getChestLocation() != null && shop.getChestLocation().getBlock().getType() == Material.ENDER_CHEST){
            Shop.getPlugin().getEnderChestHandler().saveInventory(shop.getOwner());
        }
        shop.setGuiIcon();

        // Successful!
        return this.setError(TransactionError.NONE);
    }

    public Player getPlayer() {
        return player;
    }

    public AbstractShop getShop() {
        return shop;
    }

    public ShopType getType() {
        return transactionType;
    }

    public ItemStack getItemStack() {
        ItemStack item = this.itemBeingSold.clone();
        item.setAmount(this.amountBeingSold);
        return item;
    }

    public int getAmount() {
        return this.amountBeingSold;
    }

    public TransactionError getError(){
        return error;
    }

    public double getPrice(){
        return price;
    }

    public TransactionError setError(TransactionError error){
        if (TX_DEBUG_LOGGING) { System.out.println("TX setError: TransactionError." + error.toString().toUpperCase()); }
        this.error = error;
        return this.error;
    }
}
