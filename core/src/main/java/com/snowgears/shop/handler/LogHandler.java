package com.snowgears.shop.handler;

import com.snowgears.shop.Shop;
import com.snowgears.shop.shop.AbstractShop;
import com.snowgears.shop.shop.ShopType;
import com.snowgears.shop.util.ShopActionType;
import com.snowgears.shop.util.UtilMethods;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.entity.Player;

import java.io.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.HashMap;
import java.util.stream.Collectors;

public class LogHandler {

    private Shop plugin;

    private HashMap<String, Boolean> logTypeMap = new HashMap<>();
    private HikariDataSource dataSource;
    private boolean enabled;

    public LogHandler(Shop plugin){
        this.plugin = plugin;
        defineDataSource();

        try {
            testDataSource();
        } catch (SQLException e){
            e.printStackTrace();
            enabled = false;
            System.out.println("[Shop] Error establishing connection to defined database. Logging will not be used.");
            return;
        }

        try {
            initDb();
        } catch (SQLException e){
            e.printStackTrace();
            enabled = false;
            System.out.println("[Shop] Error initializing tables in database. Logging will not be used.");
            return;
        }
    }

    public void defineDataSource(){
        File hikariPropertiesFile = new File(plugin.getDataFolder(), "hikari.properties");
        if (!hikariPropertiesFile.exists()) {
            hikariPropertiesFile.getParentFile().mkdirs();
            UtilMethods.copy(plugin.getResource("hikari.properties"), hikariPropertiesFile);
        }

        HikariConfig config = new HikariConfig(hikariPropertiesFile.getPath());
        dataSource = new HikariDataSource(config);
        System.out.println(dataSource.toString());
    }

    public boolean log(Player player, AbstractShop shop, ShopType shopType, ShopActionType actionType){
        if(shouldLog(shopType, actionType)){

            try {
                Connection conn = dataSource.getConnection();
                try {
                    PreparedStatement stmt = conn.prepareStatement("INSERT INTO shop_action(ts, player_uuid, shop_location, player_action) VALUES(?, ?, ?, ?);");
                    stmt.setTimestamp(1, new Timestamp(new Date().getTime()));
                    stmt.setString(2, player.getUniqueId().toString());
                    stmt.setString(3, UtilMethods.getCleanLocation(shop.getSignLocation(), true));
                    stmt.setString(4, actionType.toString());
                    stmt.execute();
                    stmt.close();
                    conn.close();
                    return true;
                } catch (SQLException e){
                    System.out.println("[Shop] SQL error occurred while trying to log player action.");
                    e.printStackTrace();
                    return false;
                }
            } catch (SQLException e) {
                System.out.println("[Shop] SQL error occurred while trying to log player action.");
                e.printStackTrace();
                return false;
            }
        }
        return false;
    }

    private boolean shouldLog(ShopType shopType, ShopActionType logType){
        if(!enabled)
            return false;
        return logTypeMap.get(shopType.toString().toLowerCase()+"_"+logType.toString().toLowerCase());
    }

    private void testDataSource() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            if (!conn.isValid(1000)) {
                enabled = false;
                throw new SQLException("Could not establish database connection.");
            }
            else{
                enabled = true;
                System.out.println("[Shop] Established connection to database. Logging is enabled.");
            }
        }
    }

    private void initDb() throws SQLException {
        // first lets read our setup file.
        // This file contains statements to create our inital tables.
        // it is located in the resources.
        String setup;
        try (InputStream in = plugin.getResource("dbsetup.sql")) { //TODO this is throwing an error. cannot access class jdk.xml.internal.SecuritySupport (in module java.xml) because module java.xml does not export jdk.xml.internal to unnamed module
//            // Java 9+ way
//            setup = new String(in.readAllBytes());
            // Legacy way
            setup = new BufferedReader(new InputStreamReader(in)).lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("[Shop] Could not read db setup file.");
            return;
        }
        // Mariadb can only handle a single query per statement. We need to split at ;.
        String[] queries = setup.split(";");
        // execute each query to the database.
        for (String query : queries) {
            // If you use the legacy way you have to check for empty queries here.
            //if (query.isBlank())) continue;
            if (query.isEmpty()) continue;
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.execute();
            }
        }
        System.out.println("[Shop] Successfully initialized database.");
    }
}
