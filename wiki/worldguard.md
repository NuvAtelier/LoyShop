# WorldGuard Integration

The Shop plugin works with WorldGuard to control where players can create and use shops. When WorldGuard is installed, Shop automatically respects your region permissions.

## Quick Start (Most Servers)

**By default, shops work like this with WorldGuard:**
- Players can create shops anywhere they can **build blocks** AND **open chests**
- Players can use shops anywhere they're not blocked by the **USE** flag
- Server operators can always create/use shops everywhere

### The Allow-Shop Flag

**Want shops only in specific areas?** Enable the `allow-shop` flag 

When `requireAllowShopFlag: true`:
- Shop automatically creates an `allow-shop` flag in WorldGuard
- Players can ONLY create shops in regions where this flag is set to `allow`
- This works alongside the other flag checks (doesn't replace them)

Set the flag with: `/rg flag <region> allow-shop allow`

## Basic Troubleshooting

**Shop creation not working in regions?**
1. Check if players can build: `/rg flags <region>`
2. Check if players can open chests in the region
3. Look at server logs when starting - shows which flags are being checked

**Shop use not working in regions?**
1. Check if players can `USE`: `/rg flags <region>`
2. Check if if another protection flag might be defauting `USE` to `DENY`
3. Remove `USE` from `useShopFlagChecks.denyFlags` to allow players to use shops even if they do not have the `USE` region flag available

**"WorldGuard flag not found" warnings?**
- Check the flag name spelling in your config
- Make sure you're using actual WorldGuard flag names

**Players with operator permissions bypass ALL WorldGuard restrictions.** 

## How Shop Permission Checking Works

Shop uses a **priority system** to decide if a player can create or use shops in WorldGuard regions. 

It checks flags in this order:
1. **Hard Allow Flags** - If ANY of these are `allow`, shops are ALWAYS allowed (bypasses everything else)
2. **Deny Flags** - If ANY of these are `deny`, shops are blocked
3. **Allow Flags** - If ANY of these are `allow`, shops are allowed
4. **Default Action** - If no flags triggered above, use this setting (`ALLOW` or `DENY`)

## Default Configuration

Your config starts with these settings:

```yaml
worldGuard:
  requireAllowShopFlag: false
  
  # Shop Creation Rules
  createShopFlagChecks:
    hardAllowFlags: []
    denyFlags: 
    - 'BUILD'
    - 'CHEST_ACCESS
    allowFlags:
    - 'PASSTHROUGH'
    - 'BUILD'
    defaultAction: 'DENY'
  
  # Shop Usage Rules  
  useShopFlagChecks:
    hardAllowFlags: []
    denyFlags: 
    - 'USE'
    allowFlags: []
    defaultAction: 'ALLOW'
```

**What this means:**
- **Shop Creation**: Players need building permission AND chest access, OR they need PASSTHROUGH permission
- **Shop Usage**: Players can use shops unless blocked by the USE flag
- **Allow-Shop Flag**: Not required by default

## Advanced Configuration

**Only change these if the defaults don't work for your server.**

### Available WorldGuard Flags

You can use any WorldGuard 'state' flags in your lists. Common ones:
- `BUILD` - Place/break blocks
- `CHEST_ACCESS` - Open containers
- `USE` - Use doors, buttons, etc.
- `PASSTHROUGH` - default protection flag
- https://worldguard.enginehub.org/en/latest/regions/flags/#protection-related

### Custom Flag Lists

You can modify the flag lists in your config and also use custom flags from other plugins if you wish as long as they are State Flags.

```yaml
createShopFlagChecks:
  hardAllowFlags: ["VIP_SHOPS"]        # VIPs can shop anywhere
  denyFlags: ["BUILD", "NO_SHOPS"]     # Block if can't build OR has no-shops flag
  allowFlags: ["SHOP_ZONE"]            # Allow in shop zones
  defaultAction: "DENY"                # Default to blocking shops
```
