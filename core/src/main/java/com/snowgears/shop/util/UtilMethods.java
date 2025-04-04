package com.snowgears.shop.util;

import net.md_5.bungee.api.ChatColor;
import com.snowgears.shop.Shop;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.block.Sign;
import net.md_5.bungee.api.chat.TranslatableComponent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.util.*;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.util.io.BukkitObjectOutputStream;
import org.bukkit.util.io.BukkitObjectInputStream;

public class UtilMethods {

    private static ArrayList<Material> nonIntrusiveMaterials = new ArrayList<Material>();

    public static String trimForSign(String text) {
        final int MAX_SIGN_WIDTH = 80; // Maximum width allowed on a sign line
        int currentWidth = 0;
        StringBuilder result = new StringBuilder();
        
        // Process each character
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            
            // Handle color codes (they don't take up width)
            if ((c == '§' || c == '&') && i + 1 < text.length()) {
                char nextChar = text.charAt(i + 1);
                if ("0123456789abcdefklmnorABCDEFKLMNOR".indexOf(nextChar) != -1) {
                    result.append(c).append(nextChar);
                    i++; // Skip the next character (color code)
                    continue;
                }
            }
            
            // Get the width of the current character
            int charWidth = getMinecraftCharWidth(c);
            
            // Check if adding this character would exceed the width
            if (currentWidth + charWidth >= MAX_SIGN_WIDTH) {
                break; // We've reached the maximum width for the sign
            }
            
            // Add the character and update the width
            result.append(c);
            currentWidth += charWidth;
        }
        
        return result.toString();
    }

    /**
     * Returns the width of a character in the Minecraft font.
     * Based on the width data from Minecraft's font.
     * From: https://bukkit.org/threads/formatting-plugin-output-text-into-columns.8481/#post-133295
     */
    private static int getMinecraftCharWidth(char c) {
        switch (c) {
            // Narrow characters (width = 2)
            case '!': case ',': case '.': case ':': case ';': case 'i': case '|': case '¡':
                return 3;
                // return 2; // For some reason, this width of 2 is not working as expected!
            
            // Width = 3
            case '\'': case 'l': case 'ì': case 'í':
                return 3;
            
            // Width = 4
            case ' ': case 'I': case '[': case ']': case 'ï': case '×':
                return 4;
            
            // Width = 5
            case '"': case '(': case ')': case '<': case '>': case 'f': case 'k': case '{': case '}':
                return 5;
            
            // Width = 7
            case '@': case '~': case '®':
                return 7;
            
            // All other characters (width = 6)
            default:
                return 6;
        }
    }

    //this is used for formatting numbers like 5000 to 5k
    public static String formatLongToKString(double value, boolean formatZeros) {
        //Long.MIN_VALUE == -Long.MIN_VALUE so we need an adjustment here
        if (value == Double.MIN_VALUE) return formatLongToKString(Double.MIN_VALUE + 1, formatZeros);
        if (value < 0) return "-" + formatLongToKString(-value, formatZeros);

        //System.out.println("formatting value: "+value);
        //NavigableMap<Double, Pair<Double, Double>> e = Shop.getPlugin().getPriceSuffixes();

        Map.Entry<Double, String> e = Shop.getPlugin().getPriceSuffixes().floorEntry(value);
        Double minimumValue = Shop.getPlugin().getPriceSuffixMinimumValue();;

        if (value < 1000 || e == null || value < minimumValue){
            if(isDecimal(value))
                return new DecimalFormat("0.00").format(value);
            else
                return new DecimalFormat("#.##").format(value);
        }

        //Map.Entry<Double, String> e = suffixes.floorEntry(value);
        Double divideBy = e.getKey();
        String suffix = e.getValue();

        //System.out.println("divideBy: "+divideBy+", suffix: "+suffix);

        double truncated = value / (divideBy / 10); //the number part of the output times 10
        boolean hasDecimal = truncated < 100 && (truncated / 10d) != (truncated / 10);

        String builtString = "";
        double fPrice;
        if(hasDecimal){
            fPrice = (truncated / 10d);
        }
        else{
            fPrice = (truncated / 10);
        }

//        if(formatZeros)
//            builtString = new DecimalFormat("0.00").format(fPrice);
//        else
//            builtString = new DecimalFormat("#.##").format(fPrice);
        builtString = new DecimalFormat("#.##").format(fPrice);
        builtString += suffix;
        return builtString;
    }

    public static boolean isDecimal(double d){
        return (d % 1 != 0);
    }

    public static boolean isNumber(String s) {
        try {
            Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return false;
        }
        return true;
    }

    public static boolean isInteger(String s) {
        try {
            Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return false;
        }
        return true;
    }

    public static boolean isDouble(String s) {
        try {
            Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return false;
        }
        return true;
    }

    public static BlockFace yawToFace(float yaw) {
        final BlockFace[] axis = {BlockFace.SOUTH, BlockFace.WEST, BlockFace.NORTH, BlockFace.EAST};
        return axis[Math.round(yaw / 90f) & 0x3];
    }

    public static float faceToYaw(BlockFace bf) {
        switch(bf){
            case NORTH:
                return 180;
            case NORTH_EAST:
                return 225;
            case EAST:
                return 270;
            case SOUTH_EAST:
                return 315;
            case SOUTH:
                return 0;
            case SOUTH_WEST:
                return 45;
            case WEST:
                return 90;
            case NORTH_WEST:
                return 135;
        }
        return 180;
    }

    public static String capitalize(String line) {
        String[] spaces = line.split("\\s+");
        String capped = "";
        for (String s : spaces) {
            if (s.length() > 1)
                capped = capped + Character.toUpperCase(s.charAt(0)) + s.substring(1) + " ";
            else {
                capped = capped + s.toUpperCase() + " ";
            }
        }
        return capped.substring(0, capped.length()-1);
    }

    public static String getCleanLocation(Location loc, boolean includeWorld){
        String text = "";
        if (loc == null) { return text; }
        if(includeWorld && loc.getWorld() != null)
            text = loc.getWorld().getName() + " - ";
        text = text + "("+ loc.getBlockX() + ", "+loc.getBlockY() + ", "+loc.getBlockZ() + ")";
        return text;
    }

    public static Location getLocation(String cleanLocation){
        World world = null;

        if(cleanLocation.contains(" - ")) {
            int dashIndex = cleanLocation.indexOf(" - ");
            world = Bukkit.getWorld(cleanLocation.substring(0, dashIndex));
            cleanLocation = cleanLocation.substring(dashIndex+1, cleanLocation.length());
        }
        else {
            world = Bukkit.getWorld("world");
        }
        cleanLocation = cleanLocation.replaceAll("[^\\d-]", " ");

        String[] sp = cleanLocation.split("\\s+");

        try {
            return new Location(world, Integer.valueOf(sp[1]), Integer.valueOf(sp[2]), Integer.valueOf(sp[3]));
        } catch (Exception e){
            return null;
        }
    }

    public static int floor(double num) {
        int floor = (int) num;
        return floor == num ? floor : floor - (int) (Double.doubleToRawLongBits(num) >>> 63);
    }

    public static String getEulerAngleString(EulerAngle angle){
        return "EulerAngle("+angle.getX() + ", " + angle.getY() + ", " + angle.getZ() + ")";
    }

    // Returns whether or not a player clicked the left or right side of a wall sign
    // 1 - LEFT SIDE
    // -1 - RIGHT SIDE
    // 0 - EXACT CENTER
    public static int calculateSideFromClickedSign(Player player, Block signBlock){
        if(!(signBlock.getBlockData() instanceof WallSign))
            return 0;
        WallSign s = (WallSign)signBlock.getBlockData();
        BlockFace attachedFace = s.getFacing().getOppositeFace();
        Location chest = signBlock.getRelative(attachedFace).getLocation().add(0.5,0.5,0.5);
        Location head = player.getLocation().add(0, player.getEyeHeight(), 0);

        Vector direction = head.subtract(chest).toVector().normalize();
        Vector look = player.getLocation().getDirection().normalize();

        Vector cp = direction.crossProduct(look);

        double d = 0;
        switch(attachedFace){
            case NORTH:
                d = cp.getZ();
                break;
            case SOUTH:
                d = cp.getZ() * -1;
                break;
            case EAST:
                d = cp.getX() * -1;
                break;
            case WEST:
                d = cp.getX();
                break;
            default:
                break;
        }

        if(player.getLocation().getPitch() < 0)
            d = -d;
        //System.out.println("Side clicked: "+d);

        if(d > 0)
            return 1;
        else if(d < 0)
            return -1;
        else
            return 0;
    }

    public static String convertDurationToString(int duration) {
        duration = duration / 20;
        if (duration < 10)
            return "0:0" + duration;
        else if (duration < 60)
            return "0:" + duration;
        double mins = duration / 60;
        double secs = (mins - (int) mins);
        secs = (double) Math.round(secs * 100000) / 100000; //round to 2 decimal places
        if (secs == 0)
            return (int) mins + ":00";
        else if (secs < 10)
            return (int) mins + ":0" + (int) secs;
        else
            return (int) mins + ":" + (int) secs;
    }

    public static Location pushLocationInDirection(Location location, BlockFace direction, double add){
        switch (direction){
            case NORTH:
                location = location.add(-add, 0, -add); //subtract x as a hack for display tags being shifted
            case EAST:
                location = location.add(add, 0, -add); //subtract z as a hack for display tags being shifted
            case SOUTH:
                location = location.add(add, 0, add);  //add to x as a hack for display tags being shifted
            case WEST:
                location = location.add(-add, 0, 0);
        }
        return location;
    }

    public static int getDurabilityPercent(ItemStack item) {
        if (item.getType().getMaxDurability() > 0) {
            double dur = ((double)(item.getType().getMaxDurability() - item.getDurability()) / (double)item.getType().getMaxDurability());
            return (int)(dur * 100);
        }
        return 100;
    }

    public static String getItemName(ItemStack is){
        ItemMeta itemMeta = is.getItemMeta();

        if (itemMeta.getDisplayName() == null || itemMeta.getDisplayName().isEmpty())
            return capitalize(is.getType().name().replace("_", " ").toLowerCase());
        else
            return itemMeta.getDisplayName();
    }

    public static boolean stringStartsWithUUID(String name){
        if (name != null && name.length() > 35){
            try {
                if (UUID.fromString(name.substring(0, 36)) != null)
                    return true;
            } catch (Exception ex) {
                return false;
            }
        }
        return false;
    }

    public static boolean containsLocation(String s){
        if(s == null)
            return false;
        if(s.startsWith("***{")){
            if((s.indexOf(',') != s.lastIndexOf(',')) && s.indexOf('}') != -1)
                return true;
        }
        return false;
    }

    public static boolean basicLocationMatch(Location loc1, Location loc2){
        return (loc1.getBlockX() == loc2.getBlockX() && loc1.getBlockY() == loc2.getBlockY() && loc1.getBlockZ() == loc2.getBlockZ());
    }

    public static boolean materialIsNonIntrusive(Material material){
        if(nonIntrusiveMaterials.isEmpty()){
            initializeNonIntrusiveMaterials();
        }

        return (nonIntrusiveMaterials.contains(material));
    }

    public static String getLoreString(ItemStack is){
        if(is.getItemMeta() == null || is.getItemMeta().getLore() == null || is.getItemMeta().getLore().isEmpty())
            return "";
        return is.getItemMeta().getLore().toString();
    }

    public static String translate(String key){
        return new TranslatableComponent(key).toPlainText();
    }

    public static String formatTickTime(int ticks){
        // Convert ticks to seconds (20 ticks = 1 second)
        int totalSeconds = ticks / 20;
        
        // Calculate hours, minutes, and seconds
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;
        
        // Format the time string
        if (hours > 0) {
            return " " + String.format("%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return " " + String.format("%d:%02d", minutes, seconds);
        }
    }

    public static String formatRomanNumerals(int number){
        // only format 2-5, after that just show the number
        if (number < 2) return ""; // dont return on 1
        if(number > 5)
            return " " + String.valueOf(number);
        String[] romanNumerals = {"I", "II", "III", "IV", "V"};
        return " " + romanNumerals[number - 1];
    }

    public static TextComponent getEnchantmentsComponent(ItemStack item){
        TextComponent formattedMessage = new TextComponent("");

        if(item.getItemMeta() instanceof EnchantmentStorageMeta || item.getEnchantments().size() > 0){
            Map<Enchantment, Integer> enchantsMap;
            if(item.getItemMeta() instanceof EnchantmentStorageMeta){
                enchantsMap = ((EnchantmentStorageMeta) item.getItemMeta()).getStoredEnchants();
            }
            else { enchantsMap = item.getEnchantments(); }

            if(enchantsMap == null || enchantsMap.isEmpty()) return formattedMessage;

            formattedMessage.addExtra(" [");
            int i=0;
            for(Map.Entry<Enchantment, Integer> entry : enchantsMap.entrySet()){
                formattedMessage.addExtra(new TranslatableComponent(entry.getKey().getTranslationKey()));
                formattedMessage.addExtra(formatRomanNumerals(entry.getValue()));
                i++;
                if(i != enchantsMap.size()) formattedMessage.addExtra(", ");
                else formattedMessage.addExtra("]");
            }
        }

        if(item.getItemMeta() != null && item.getItemMeta() instanceof ArmorMeta){
            ArmorMeta armorMeta = (ArmorMeta) item.getItemMeta();
            if (armorMeta.getTrim() != null) {
                String material = translate(armorMeta.getTrim().getMaterial().getTranslationKey());
                String pattern = translate(armorMeta.getTrim().getPattern().getTranslationKey());
                // Since we want to remove the "Armor Trim" and "Material" from the string, we have to translate it first
                // causing translatable components to not work clientside.
                formattedMessage.addExtra(" [" + pattern.replace(" Armor Trim", ""));
                formattedMessage.addExtra(" (" + material.replace(" Material", "") + ")]");
            }
        }

        // Add custom potion formatting
        if(item.getItemMeta() != null && item.getItemMeta() instanceof PotionMeta){
            PotionMeta potionMeta = (PotionMeta) item.getItemMeta();
            String formattedName = "";
            if (potionMeta.getBasePotionType() != null) {
                formattedName = UtilMethods.capitalize(potionMeta.getBasePotionType().toString().replace("_", " ").toLowerCase());
                formattedMessage.addExtra(" [" + formattedName + "]");
                formattedMessage.addExtra(getPotionEffects(potionMeta.getBasePotionType().getPotionEffects()));
            }
            if (potionMeta.getCustomEffects().size() > 0) {
                formattedMessage.addExtra(getPotionEffects(potionMeta.getCustomEffects()));
            }
        }

        if(item.getItemMeta() != null && item.getItemMeta() instanceof FireworkMeta){
            FireworkMeta fireworkMeta = (FireworkMeta) item.getItemMeta();
            int power = fireworkMeta.getPower();
            if (power == 0) power = 1;
            formattedMessage.addExtra(" [Duration " + power + "]");
        }
        
        return formattedMessage;
    }

    private static TextComponent getPotionEffects(List<PotionEffect> effects){
        TextComponent formattedEffects = new TextComponent("");
        int numEffects = effects.size();
        if (numEffects == 0) return formattedEffects;
        formattedEffects.addExtra(" (");
        for (int i = 0; i < numEffects; i++) {
            PotionEffect effect = effects.get(i);
            formattedEffects.addExtra(new TranslatableComponent(effect.getType().getTranslationKey()));
            if(effect.getAmplifier() > 0) formattedEffects.addExtra(formatRomanNumerals(effect.getAmplifier()));
            if(effect.getDuration() > 0) formattedEffects.addExtra(formatTickTime(effect.getDuration()));
            // if we have more than one effect, add a comma, dont add a comma after the last effect
            if(i < numEffects - 1)
                formattedEffects.addExtra(", ");
        }
        formattedEffects.addExtra(")");
        return formattedEffects;
    }

    private static void initializeNonIntrusiveMaterials(){
        for(Material m : Material.values()){
            if(!m.isSolid())
                nonIntrusiveMaterials.add(m);
        }
        try{
            nonIntrusiveMaterials.add(Material.WARPED_WALL_SIGN);
            nonIntrusiveMaterials.add(Material.ACACIA_WALL_SIGN);
            nonIntrusiveMaterials.add(Material.BIRCH_WALL_SIGN);
            nonIntrusiveMaterials.add(Material.CRIMSON_WALL_SIGN);
            nonIntrusiveMaterials.add(Material.DARK_OAK_WALL_SIGN);
            nonIntrusiveMaterials.add(Material.JUNGLE_WALL_SIGN);
            nonIntrusiveMaterials.add(Material.OAK_WALL_SIGN);
            nonIntrusiveMaterials.add(Material.SPRUCE_WALL_SIGN);
        } catch(NoSuchFieldError e){
            nonIntrusiveMaterials.add(Material.LEGACY_WALL_SIGN);
        }
        nonIntrusiveMaterials.remove(Material.WATER);
        nonIntrusiveMaterials.remove(Material.LAVA);
        nonIntrusiveMaterials.remove(Material.FIRE);
        nonIntrusiveMaterials.remove(Material.END_PORTAL);
        nonIntrusiveMaterials.remove(Material.NETHER_PORTAL);
        nonIntrusiveMaterials.remove(Material.SKELETON_SKULL);
        nonIntrusiveMaterials.remove(Material.WITHER_SKELETON_SKULL);
        nonIntrusiveMaterials.remove(Material.PLAYER_HEAD);
        nonIntrusiveMaterials.remove(Material.CREEPER_HEAD);

        try{ nonIntrusiveMaterials.add(Material.LIGHT); } catch(NoSuchFieldError e){}
    }

    public static BlockFace getDirectionOfChest(Block block){
        if(block.getBlockData() instanceof Directional){
            return ((Directional)block.getBlockData()).getFacing();
        }
        return null;
    }

    //returns if Minecraft version 1.17 or above
    public static boolean isMCVersion17Plus(){
        //LIGHT only available in MC 1.17+
        try {
            if(Material.LIGHT != null)
                return true;
        } catch (NoSuchFieldError e) {
            return false;
        }
        return false;
    }

    //returns if Minecraft version 1.14 or above
    public static boolean isMCVersion14Plus(){
        //LIGHT only available in MC 1.17+
        try {
            if(Material.BARREL != null)
                return true;
        } catch (NoSuchFieldError e) {
            return false;
        }
        return false;
    }

    //this takes a dirty (pre-cleaned) string and finds how much to multiply the final by
    //this utility allows the input of numbers like 1.2k (1200)
    public static double getMultiplyValue(String text){
        // Remove color formatting, whitespace, and make sure the string is lowercase for matching our suffixes below
        String priceString = ChatColor.stripColor(text).replaceAll("\\s", "").toLowerCase();
        // Get just the suffix from the price string, remove all numbers and decimals
        String priceSuffix = priceString.replaceAll("[0-9.]", "");

        // Load the suffixes from the config values
        NavigableMap<Double, String> configPriceSuffixes = Shop.getPlugin().getPriceSuffixes();

        // Search for a suffix match
        for (Map.Entry<Double, String> entry : configPriceSuffixes.entrySet()) {
            Double configPriceValue = entry.getKey();
            String configSuffix = entry.getValue().toLowerCase();

            if (priceSuffix.equals(configSuffix)) {
                // Return the value for the suffix from the config
                return configPriceValue;
            }
        }

        // No match so our multiplier is just 1
        return 1;
    }

    public static String cleanNumberText(String text){
        String cleaned = "";
        String toClean = ChatColor.stripColor(text);
        for(int i=0; i<toClean.length(); i++) {
            if(Character.isDigit(toClean.charAt(i)))
                cleaned += toClean.charAt(i);
            else if(toClean.charAt(i) == '.')
                cleaned += toClean.charAt(i);
            else if(toClean.charAt(i) == ' ')
                cleaned += toClean.charAt(i);
        }
        return cleaned;
    }

    public static ChatColor getChatColorByCode(String colorCode) {
        switch (colorCode) {
            case "&b":
                return ChatColor.AQUA;
            case "&0":
                return ChatColor.BLACK;
            case "&9":
                return ChatColor.BLUE;
            case "&l":
                return ChatColor.BOLD;
            case "&3":
                return ChatColor.DARK_AQUA;
            case "&1":
                return ChatColor.DARK_BLUE;
            case "&8":
                return ChatColor.DARK_GRAY;
            case "&2":
                return ChatColor.DARK_GREEN;
            case "&5":
                return ChatColor.DARK_PURPLE;
            case "&4":
                return ChatColor.DARK_RED;
            case "&6":
                return ChatColor.GOLD;
            case "&7":
                return ChatColor.GRAY;
            case "&a":
                return ChatColor.GREEN;
            case "&o":
                return ChatColor.ITALIC;
            case "&d":
                return ChatColor.LIGHT_PURPLE;
            case "&k":
                return ChatColor.MAGIC;
            case "&c":
                return ChatColor.RED;
            case "&r":
                return ChatColor.RESET;
            case "&m":
                return ChatColor.STRIKETHROUGH;
            case "&n":
                return ChatColor.UNDERLINE;
            case "&f" :
                return ChatColor.WHITE;
            case "&e":
                return ChatColor.YELLOW;
            default:
                return ChatColor.RESET;
        }
    }

    public static ChatColor getChatColor(String message) {
        if(message.startsWith("&") && message.length() > 1){
            ChatColor cc = getChatColorByCode(message.substring(0,2));
            if(cc != ChatColor.RESET)
                return cc;
        }
        return null;
    }

    public static boolean deleteDirectory(File directory) {
        if(directory.exists()){
            File[] files = directory.listFiles();
            if(null!=files){
                for(int i=0; i<files.length; i++) {
                    if(files[i].isDirectory()) {
                        deleteDirectory(files[i]);
                    }
                    else {
                        files[i].delete();
                    }
                }
            }
        }
        return(directory.delete());
    }

    public static void copy(InputStream in, File file) {
        try {
            OutputStream out = new FileOutputStream(file);
            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            out.close();
            in.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String itemStackToBase64(ItemStack item) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);

        // Write the ItemStack to the ObjectOutputStream
        dataOutput.writeObject(item);
        dataOutput.close();

        // Encode the byte array to a Base64 string
        return Base64.getEncoder().encodeToString(outputStream.toByteArray());
    }

    public static ItemStack itemStackFromBase64(String data) throws IOException, ClassNotFoundException {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
        BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);

        // Read the ItemStack from the ObjectInputStream
        ItemStack item = (ItemStack) dataInput.readObject();
        dataInput.close();
        return item;
    }

    public static List<String> splitStringIntoLines(String text, int maxLineLength) {
        final String COLOR_CODE_REGEX = "([&§][0-9A-FK-ORa-fk-or])";

        Matcher matcher = Pattern.compile(COLOR_CODE_REGEX + "| |[^&§\\s]+").matcher(text);
        List<String> words = new ArrayList<>();
        while (matcher.find()) {
            words.add(matcher.group());
        }

        StringBuilder currentLine = new StringBuilder();
        List<String> linesByColor = new ArrayList<>();

        String latestColors = "";
        ChatColor latestColor = ChatColor.WHITE;
        boolean isBold = false;
        boolean isItalic = false;
        boolean isStrikethrough = false;
        boolean isUnderlined = false;
        boolean isObfuscated = false;
        for (String word : words) {
            if (word.matches(COLOR_CODE_REGEX)) {
                ChatColor newColor = ChatColor.getByChar(word.charAt(1));
                if (newColor == ChatColor.BOLD) isBold = true;
                else if (newColor == ChatColor.ITALIC) isItalic = true;
                else if (newColor == ChatColor.STRIKETHROUGH) isStrikethrough = true;
                else if (newColor == ChatColor.UNDERLINE) isUnderlined = true;
                else if (newColor == ChatColor.MAGIC) isObfuscated = true;
                else if (newColor == ChatColor.RESET) {
                    Shop.getPlugin().getLogger().hyper("[ShopMessage.format]     matched RESET color code: " + word);
                    latestColor = ChatColor.WHITE;
                    isBold = false;
                    isItalic = false;
                    isStrikethrough = false;
                    isUnderlined = false;
                    isObfuscated = false;
                } else {
                    latestColor = newColor;
                }

                String newColors = latestColor.toString();
                if (isBold) newColors += ChatColor.BOLD;
                if (isItalic) newColors += ChatColor.ITALIC;
                if (isStrikethrough) newColors += ChatColor.STRIKETHROUGH;
                if (isUnderlined) newColors += ChatColor.UNDERLINE;
                if (isObfuscated) newColors += ChatColor.MAGIC;

                if (!latestColors.equals(newColors)) {
                    latestColors = newColors;
                    // New color, add the line and start a new line
                    linesByColor.add(currentLine.toString().trim());
                    currentLine = new StringBuilder(latestColors);
                }

                continue;
            }

            // Also split if the single color line is too long!
            int potentialLength = ChatColor.stripColor(currentLine.toString()).length() + ChatColor.stripColor(word).length() + 1;
            if (word.matches(" ") && potentialLength > maxLineLength) {
                linesByColor.add(currentLine.toString().trim());
                Shop.getPlugin().getLogger().hyper("[ShopMessage.format]     matched RESET color code: " + word);
                currentLine = new StringBuilder(latestColors);
            } else {
                currentLine.append(word);
            }
        }

        // Append the last line if there's any content left
        if (currentLine.length() > 0) {
            linesByColor.add(currentLine.toString().trim());
        }

        // Now we need to start taking the "blocks" of text and combining them into lines, limited by maxLineLength
        List<String> result = new ArrayList<>();
        currentLine = new StringBuilder();
        for (String line : linesByColor) {
            // Add it if we are less than the max line length or if the line is only a color
            if (currentLine.length() + line.length() <= maxLineLength || ChatColor.stripColor(line).length() == 0) {
                if (currentLine.toString().trim().length() == 0 || ChatColor.stripColor(line).trim().length() == 0) currentLine.append(line);
                else currentLine.append(" " + line);
            } else {
                result.add(currentLine.toString().trim());
                currentLine = new StringBuilder(line);
            }
        }
        if (currentLine.length() > 0) {
            result.add(currentLine.toString().trim());
        }
        return result;
    }
}
