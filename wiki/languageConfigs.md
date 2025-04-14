# Language Configuration Files

The Shop plugin features a customizable text system that allows server administrators to modify messages, sign formats, and display elements. This enables localization to different languages and custom styling to match server themes.

## Overview

The plugin uses three main configuration files:

1. **chatConfig.yml** - Chat messages sent to players
2. **signConfig.yml** - Text displayed on shop signs
3. **displayConfig.yml** - Floating text tags above shops

## Color Codes and Formatting

All configuration files support both legacy color codes (using &) and hex color codes:

### Legacy Color Codes
Common codes: `&a` (green), `&b` (aqua), `&c` (red), `&e` (yellow), `&f` (white)  
Formatting: `&l` (bold), `&o` (italic), `&n` (underline), `&m` (strikethrough)

### Hex Color Codes
Format: `#RRGGBB` (e.g., `#FF00FF` for pink)

### Combining Formatting
You can combine codes: `&l&c` creates bold red text.

### Formatting Persistence and Reset
Minecraft's formatting codes persist until explicitly reset. When you apply a formatting code like `&l` (bold), all text that follows will remain bold until you use the reset code `&r`.

```yaml
# Without reset - all text is bold and red
message: '&l&cThis entire message stays bold and red'

# With reset - formatting stops after reset code
message: '&l&cThis part is bold and red.&r This part is normal.'
```

Always use `&r` when you want to return to normal text after applying formatting codes.

## Placeholder System

Placeholders dynamically insert content into messages. They're surrounded by square brackets like `[item]` or `[price]`.

### How Placeholders Work
When displayed, placeholders are replaced with real-time data from shops, transactions, or players. For example, `[price]` will show the actual price of an item.

### Common Placeholders

- **Item-related**: `[item]`, `[item amount]`, `[item type]`, `[item enchants]`
- **Price-related**: `[price]`, `[price per item]`
- **Player-related**: `[user]`, `[owner]`
- **Shop-related**: `[stock]`, `[shop type]`, `[location]`

For barter shops: `[barter item]`, `[barter item amount]`

## Message Organization

Messages follow a structured pattern to make finding and customizing them easier:

- **Transaction messages**: `{shopType}_{recipient}` (e.g., `SELL_user`)
- **Error messages**: `{shopType}_{errorType}` (e.g., `SELL_shopNoStock`)

Many placeholders create interactive features automatically:
- `[location]` - Clickable coordinates
- `[item]` - Includes hover text with details
- `[offline transactions]` - Hoverable transaction details

## Configuration Files

### chatConfig.yml

Controls chat messages for transactions, errors, and shop interactions.

**Key Sections:**
- `transaction:` - Success messages
- `transaction_issue:` - Error messages
- `interaction:` - Shop creation/management messages
- `description:` - Shop information panels
- `OFFLINE_TRANSACTIONS_NOTIFICATION:` - Summary of offline activity

### signConfig.yml

Controls shop sign text and formats.

**Key Sections:**
- `sign_creation:` - Keywords for creating shops
- `sign_text:` - Text layout with variations for shop types and display options
- Special settings: `zeroPrice` ("FREE"), `adminStock` ("unlimited")

### displayConfig.yml

Controls floating text tags above shops.

**Key Sections:**
- `display_tag_text:` - Text displayed by shop type
- Special positioning: `[lshift]` and `[rshift]` for side-by-side formatting

## Customization Tips

### Translating to Other Languages
1. Translate text outside placeholder brackets
2. Keep placeholders intact with original names
3. Test thoroughly after changes

### Style Consistency
- Use the same color scheme across files
- Keep sign text concise due to space limitations

## Advanced Tips

- **Unicode Symbols**: Use symbols like ✦, ❖, or • for visual distinction
- **Multi-column Layouts**: Use shift markers for complex displays
- **Multi-line Messages**: Use the `|-` syntax in YAML for cleaner formatting

## Implementation Guide

### Performance Considerations
- Complex messages with many placeholders may increase processing time
- Dense shop areas benefit from shorter display texts
- Excessive formatting affects client-side performance

### Applying Changes
1. Save the file
2. Restart your server (safest) or run `/shop reload` 
3. Test your changes

### Troubleshooting
- Check YAML syntax (indentation matters)
- Verify placeholders are intact with correct names
- Ensure color codes are properly formatted
- Confirm you've properly reloaded after changes