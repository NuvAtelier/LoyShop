package com.snowgears.shop.util;

import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class TransactionParty {
    // Party - The player that is a party in the transaction, could be the shop owner, or the player clicking the sign
    private OfflinePlayer party;

    // Inventory - the party inventory to access for currency/items
    private Inventory inventory;

    // The amount the party has to spend, could be vault currency, or from an item currency in their inventory
    private double availableFunds;

    // The item being used for currency in a trade (i.e. barter shop)
    private ItemStack currencyItem;

    // Are we the player who created the transaction (used for error handling)
    private boolean isPlayer;

    public TransactionParty(boolean isPlayer, OfflinePlayer party, Inventory inventory) {
        this.isPlayer = isPlayer;
        this.party = party;
        this.inventory = inventory;
    }

    // Allow creating a party that uses an item for it's currency/available funds.
    public TransactionParty(boolean isPlayer, OfflinePlayer party, Inventory inventory, ItemStack currencyItem) {
        this.isPlayer = isPlayer;
        this.party = party;
        this.inventory = inventory;
        this.currencyItem = currencyItem;
    }

    public boolean isPlayer() { return isPlayer; }

    public int getInventoryQuantity(ItemStack item) {
        return InventoryUtils.getAmount(this.inventory, item);
    }

    // Update the amount of currency the player has available (vault/currency item) and return it.
    public double getAvailableFunds() {
        // Check if we are using an item for our funds, this will happen if we are a seller in the transaction and we are selling an item
        if (this.currencyItem != null) {
            // We are using an item for our currency, so use that amount!
            this.availableFunds = InventoryUtils.getAmount(this.inventory, this.currencyItem);
        } else {
            // We are using the regular Shop currency for this transaction
            this.availableFunds = EconomyUtils.getFunds(party, this.inventory);
        }

        return this.availableFunds;
    }

    // Check if we have enough space to receive a payment, we might not have the inventory space for it!
    public boolean canAcceptPayment(double paymentAmount) {
        if (this.currencyItem != null) {
            // We are being paid with an item
            ItemStack payment = this.currencyItem.clone();
            payment.setAmount((int) paymentAmount);
            return InventoryUtils.hasRoom(this.inventory, payment, party);
        }

        // We are being paid through the normal economy
        return EconomyUtils.canAcceptFunds(party, this.inventory, paymentAmount);
    }

    // Receive a payment and add it to the players wallet/inventory
    public void depositFunds(double paymentAmount) {
        if (this.currencyItem != null) {
            // We are being paid with an item
            ItemStack payment = this.currencyItem.clone();
            payment.setAmount((int) paymentAmount);
            InventoryUtils.addItem(this.inventory, payment, party);
        } else {
            // We are being paid using our normal currency
            EconomyUtils.addFunds(party, this.inventory, paymentAmount);
        }
    }

    // Make a payment for a purchase
    public boolean deductFunds(double paymentAmount) {
        // Check if we have enough funds to make the payment
        if (this.getAvailableFunds() < paymentAmount) { return false; }

        // Check if we are being paid using an item instead of currency
        if (this.currencyItem != null) {
            ItemStack payment = this.currencyItem.clone();
            payment.setAmount((int) paymentAmount);
            InventoryUtils.removeItem(inventory, payment, party);
            return true;
        }

        // We are being paid using our normal currency
        EconomyUtils.removeFunds(party, this.inventory, paymentAmount);
        return true;
    }

    // Check if there is space in the inventory to recieve an item
    public boolean hasRoomForItem(ItemStack item){
        return InventoryUtils.hasRoom(this.inventory, item, party);
    }

    public boolean depositItem(ItemStack item) {
        // Check if we have room for the item in our inventory
        if (!this.hasRoomForItem(item)) { return false; }

        // We have the space, so add the item to our inventory!
        // @TODO: Maybe check how many items were unable to be added to the inv to make sure we actually deposited the item
        InventoryUtils.addItem(inventory, item, party);
        return true;
    }

    public boolean deductItem(ItemStack item) {
        // @TODO: Maybe check how many items were unable to be removed from the inv to verify tx occured successfully
        InventoryUtils.removeItem(inventory, item, party);
        return true;
    }
}
