package com.snowgears.shop.util;

import com.snowgears.shop.Shop;
import com.snowgears.shop.shop.AbstractShop;
import com.snowgears.shop.shop.ComboShop;
import com.snowgears.shop.shop.GambleShop;
import com.snowgears.shop.shop.ShopType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class Transaction {

    private Player player;
    private AbstractShop shop;
    private ShopType transactionType;
    private boolean isCheck;
    private ItemStack itemStack;
    private ItemStack secondaryItemStack;

    private int amount;
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
        this.isCheck = true;

        this.player = player;
        this.shop = shop;
        this.transactionType = transactionType;

        this.price = shop.getPrice();
        this.itemBeingSold = shop.getItemStack();
        this.amountBeingSold = shop.getAmount();

        if (transactionType == ShopType.GAMBLE) {
            this.buyer = new TransactionParty(true, player, player.getInventory());
            this.seller = new TransactionParty(false, shop.getOwner(), shop.getInventory());

            ((GambleShop)shop).setGambleItem();
            this.itemBeingSold = ((GambleShop) shop).getGambleItem();
            this.amountBeingSold = ((GambleShop) shop).getGambleItem().getAmount();
        }
        else if (shop.getType() == ShopType.BARTER) {
            // Player is buying from the shop!
            // The buyer is going to use an item for their currency/payment
            this.buyer = new TransactionParty(true, player, player.getInventory(), shop.getSecondaryItemStack());
            this.seller = new TransactionParty(false, shop.getOwner(), shop.getInventory());
        }
        else if (transactionType == ShopType.BUY) {
            // Shop is buying from the player
            this.buyer = new TransactionParty(false, shop.getOwner(), shop.getInventory());
            this.seller = new TransactionParty(true, player, player.getInventory());
        }
        else if (transactionType == ShopType.SELL) {
            // Shop is selling to the player
            this.buyer = new TransactionParty(true, player, player.getInventory());
            this.seller = new TransactionParty(false, shop.getOwner(), shop.getInventory());

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
        int maxPurchaseAmount = this.originalAmountBeingSold;
        // Check if we passed in the amount that we want to purchase, sometimes we want to purchase more
        if (desiredAmount != -1) { maxPurchaseAmount = desiredAmount; };

        // Price for a single item
        // Greater than 1 if price is higher than amount being sold
        // Less than one if price is lower than amount being sold
        double pricePerItem = this.originalPrice / this.originalAmountBeingSold;
        System.out.println("* pricePerItem: " + pricePerItem);
        // The number of items to equal one price unit
        double itemsPerPrice = 1 / pricePerItem;
        System.out.println("* itemsPerPrice: " + itemsPerPrice);

        // Calculate the maximum qty that the buyer can afford to buy
        double buyerMaxQtyPurchase = (this.buyer.getAvailableFunds() / (pricePerItem)) / itemsPerPrice;
        System.out.println("* buyerMaxQtyPurchase: " + buyerMaxQtyPurchase);
        // Calculate the maximum items the seller has to sell
        double sellerMaxQtySale = this.seller.getInventoryQuantity(this.itemBeingSold) / itemsPerPrice;
        System.out.println("* this.seller.getInventoryQuantity(this.itemBeingSold): " + this.seller.getInventoryQuantity(this.itemBeingSold));
        System.out.println("* sellerMaxQtySale: " + sellerMaxQtySale);

        // The maximum qty that we can buy/sell with our available funds
        double maxPurchasableQuantity = Math.floor( Math.min(buyerMaxQtyPurchase, sellerMaxQtySale) );
        System.out.println("* maxPurchasableQuantity: " + maxPurchasableQuantity);
        // If we don't have enough to buy/sell, then we can't negotiate a new price! Return so normal error handling can occur.
        if (maxPurchasableQuantity <= 0) { return; }

        // The number of items we are buying
        int itemsBeingBought = (int) Math.floor(maxPurchasableQuantity * itemsPerPrice);
        System.out.println("* itemsBeingBought: " + itemsBeingBought);
        // The overall price we are paying
        double priceBeingPaid = Math.ceil(maxPurchasableQuantity);
        System.out.println("* priceBeingPaid: " + priceBeingPaid);

        // Check if partial sales are not allowed
        if (!Shop.getPlugin().getAllowPartialSales()) {
            // Multiple Quantity of original amount sales code (for full stack sales)
            double quantityPerOriginalAmount = this.originalAmountBeingSold / itemsPerPrice;
            System.out.println("*** quantityPerOriginalAmount: " + quantityPerOriginalAmount);
            // Force the quantity to be a multiple of our original amount when performing multiple sales
            int roundedQuantity = (int) (Math.floor(maxPurchasableQuantity / quantityPerOriginalAmount) * quantityPerOriginalAmount);
            System.out.println("*** roundedQuantity: " + roundedQuantity);

            // Partial sales are not allowed, we need to default to a multiple of our default amount/price
            itemsBeingBought = (int) Math.floor(roundedQuantity * itemsPerPrice);
            System.out.println("*** itemsBeingBought: " + itemsBeingBought);
            priceBeingPaid = (int) Math.ceil(roundedQuantity);
            System.out.println("*** priceBeingPaid: " + priceBeingPaid);

            // Set max purchase amount to be rounded down to a multiple of our original amount
            maxPurchaseAmount = (int) (Math.floor(maxPurchaseAmount / this.originalAmountBeingSold) * originalAmountBeingSold);
            System.out.println("*** maxPurchaseAmount: " + maxPurchaseAmount);
        }

        // Make sure we only sell up to our maxPurchaseAmount (normally the original amount, but can be set higher)
        if (itemsBeingBought > maxPurchaseAmount) {
            System.out.println("itemsBeingBought > maxPurchaseAmount: " + itemsBeingBought + " > " + maxPurchaseAmount);
            itemsBeingBought = maxPurchaseAmount;
            System.out.println("*-* itemsBeingBought: " + itemsBeingBought);
            priceBeingPaid = Math.ceil(maxPurchaseAmount * pricePerItem);
            System.out.println("*-* priceBeingPaid: " + priceBeingPaid);
        }

        this.amountBeingSold = itemsBeingBought;
        this.price = priceBeingPaid;
        System.out.println("-* amountBeingSold: " + this.amountBeingSold);
        System.out.println("-* price: " + this.price);
        System.out.println("-* originalAmountBeingSold: " + this.originalAmountBeingSold);
        System.out.println("-* originalPrice: " + this.originalPrice);
    }

    // Verify there are no errors with the transaction
    public TransactionError verify() {
        // Check if the transaction has already been cancelled
        if (this.error == TransactionError.CANCELLED) { return this.error; }

        // Check if the buyer has enough funds to pay for the tx
        if (this.buyer.getAvailableFunds() < this.price) {
            // Set the error, check if the error comes from the player or shop side
            if (this.buyer.isPlayer()) { this.error = TransactionError.INSUFFICIENT_FUNDS_PLAYER; }
            else { this.error = TransactionError.INSUFFICIENT_FUNDS_SHOP; }

            // Failed Verification: The buyer does not have enough funds to pay for the transaction!
            return this.error;
        }

        // Check if seller has enough items to sell
        if (this.seller.getInventoryQuantity(this.itemBeingSold) < this.amountBeingSold) {
            // Set the error, check if the error comes from the player or shop side
            if (this.seller.isPlayer()) { this.error = TransactionError.INSUFFICIENT_FUNDS_PLAYER; }
            else { this.error = TransactionError.INSUFFICIENT_FUNDS_SHOP; }

            // Failed Verification: There was not enough items in the sellers inventory to cover the transaction
            return this.error;
        }

        // Create the item being sold and set the amount we are selling in the tx
        ItemStack itemToBeAdded = this.itemBeingSold.clone();
        itemToBeAdded.setAmount(this.amountBeingSold);
        // Check if the buyer has inventory space to receive the item
        if (!this.buyer.hasRoomForItem(itemToBeAdded)) {
            // Set the error, check if the error comes from the player or shop side
            if (this.buyer.isPlayer()) { this.error = TransactionError.INVENTORY_FULL_PLAYER; }
            else { this.error = TransactionError.INVENTORY_FULL_SHOP; }

            // Failed Verification: The buyer does not have space to receive the item being bought
            return this.error;
        }

        // Check if the seller can accept the payment amount, aka if they have space for a currency item to be place into their inventory
        if (!this.seller.canAcceptPayment(this.price)) {
            // Set the error, check if the error comes from the player or shop side
            if (this.seller.isPlayer()) { this.error = TransactionError.INVENTORY_FULL_PLAYER; }
            else { this.error = TransactionError.INVENTORY_FULL_SHOP; }

            // Failed Verification: The seller does not have space to receive the currency item!
            return this.error;
        }

        // There were no errors, so we are good to proceed!
        this.error = TransactionError.NONE;
        return this.error;
    }

    public TransactionError execute() {
        // Check to see if we had any errors, you must do this before performing the transaction!
        if (this.verify() != TransactionError.NONE) { return this.error; }

        // Perform the transaction, we are fully verified, so we can just directly run through the transaction
        ItemStack itemSold = this.itemBeingSold.clone();
        itemSold.setAmount(this.amountBeingSold);

        // Swap funds
        this.buyer.deductFunds(this.price);
        this.seller.depositFunds(this.price);
        // Swap item
        this.seller.deductItem(itemSold);
        this.buyer.depositItem(itemSold);

        // Successful!
        return TransactionError.NONE;
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

    public boolean isCheck() {
        return isCheck;
    }

    public void passCheck(){
        isCheck = false;
    }

    public ItemStack getItemStack() {
        ItemStack item = this.itemBeingSold.clone();
        item.setAmount(this.amountBeingSold);
        return item;
    }

    public ItemStack getSecondaryItemStack(){
        return secondaryItemStack;
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

    public void setError(TransactionError error){
        System.out.println("TransactionError - " + error);
        this.error = error;
    }
}
