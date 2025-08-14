package com.snowgears.shop.hook;

/**
 * WorldGuard configuration class
 * Handles configuration settings for WorldGuard integration
 */
public class WorldGuardConfig {
    
    private boolean requireAllowShopFlag = false;
    
    /**
     * Check if the allow-shop flag is required for shop creation/usage
     * @return true if the flag is required, false otherwise
     */
    public boolean requireAllowShopFlag() {
        return requireAllowShopFlag;
    }
    
    /**
     * Set whether the allow-shop flag is required
     * @param require true to require the flag, false otherwise
     */
    public void setRequireAllowShopFlag(boolean require) {
        this.requireAllowShopFlag = require;
    }
}
