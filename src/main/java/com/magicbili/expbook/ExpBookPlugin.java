package com.magicbili.expbook;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.logging.Level;

public class ExpBookPlugin extends JavaPlugin implements Listener {
    
    private Connection connection;
    private Map<String, BookConfig> bookConfigs = new HashMap<>();
    private NamespacedKey bookIdKey;
    private NamespacedKey bookTypeKey;
    private NamespacedKey storedExpKey;
    private NamespacedKey ownerKey;
    private LanguageManager languageManager;
    private ConfigManager configManager;
    private SchedulerAdapter scheduler;
    
    @Override
    public void onEnable() {
        // 初始化调度器适配器
        scheduler = new SchedulerAdapter(this);
        
        // 初始化配置管理器
        configManager = new ConfigManager(this);
        
        // 初始化语言管理器
        languageManager = new LanguageManager(this);
        
        // 加载配置（自动更新旧配置）
        configManager.loadConfig();
        
        // 加载语言文件
        languageManager.loadLanguage();
        
        // 检测运行环境
        if (scheduler.isFolia()) {
            getLogger().info(languageManager.getMessage("detected_folia"));
        } else {
            getLogger().info(languageManager.getMessage("detected_bukkit"));
        }
        
        // 初始化命名空间键
        bookIdKey = new NamespacedKey(this, "book_id");
        bookTypeKey = new NamespacedKey(this, "book_type");
        storedExpKey = new NamespacedKey(this, "stored_exp");
        ownerKey = new NamespacedKey(this, "owner_uuid");
        
        // 加载配置
        loadConfigs();
        
        // 注册事件监听器
        getServer().getPluginManager().registerEvents(this, this);
        
        // 注册命令
        Objects.requireNonNull(getCommand("expbook")).setExecutor(new ExpBookCommand(this));
        
        getLogger().info(languageManager.getMessage("plugin_enabled"));
    }
    
    @Override
    public void onDisable() {
        // 关闭数据库连接
        closeDatabaseConnection();
        getLogger().info(languageManager.getMessage("plugin_disabled"));
    }
    
    private void loadConfigs() {
        // 加载书籍配置
        loadBookConfigs();
        
        // 连接数据库
        setupDatabase();
    }
    
    private void loadBookConfigs() {
        bookConfigs.clear();
        for (String bookId : getConfig().getConfigurationSection("books").getKeys(false)) {
            String path = "books." + bookId + ".";
            BookConfig config = new BookConfig(
                bookId,
                getConfig().getString(path + "display_name"),
                Material.matchMaterial(Objects.requireNonNull(getConfig().getString(path + "material"))),
                getConfig().getInt(path + "custom_model_data"),
                getConfig().getInt(path + "max_storage"),
                getConfig().getString(path + "permission"),
                getConfig().getBoolean(path + "bind_player"),
                getConfig().getStringList(path + "lore")
            );
            bookConfigs.put(bookId, config);
        }
    }
    
    private void setupDatabase() {
        // 关闭现有连接
        closeDatabaseConnection();
        
        String host = getConfig().getString("database.host");
        int port = getConfig().getInt("database.port");
        String database = getConfig().getString("database.database");
        String username = getConfig().getString("database.username");
        String password = getConfig().getString("database.password");
        String prefix = getConfig().getString("database.table_prefix");
        
        String url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false";
        
        try {
            connection = DriverManager.getConnection(url, username, password);
            createTables(prefix);
            getLogger().info(languageManager.getMessage("database_connected"));
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, languageManager.getMessage("database_connection_error"), e);
        }
    }
    
    private void closeDatabaseConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                getLogger().info(languageManager.getMessage("database_closed"));
            }
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, languageManager.getMessage("database_close_error"), e);
        }
    }
    
    private void createTables(String prefix) throws SQLException {
        String createTable = "CREATE TABLE IF NOT EXISTS " + prefix + "books (" +
                "id VARCHAR(36) PRIMARY KEY, " +
                "book_type VARCHAR(50) NOT NULL, " +
                "owner_uuid VARCHAR(36), " +
                "stored_exp INT NOT NULL DEFAULT 0, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
            ")";
        
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createTable);
        }
    }
    
    // 创建一本新的经验之书
    public ItemStack createNewBook(Player player, String bookType) {
        BookConfig config = bookConfigs.get(bookType);
        if (config == null) return null;
        
        ItemStack book = new ItemStack(config.getMaterial());
        ItemMeta meta = book.getItemMeta();
        
        // 设置显示名称
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', config.getDisplayName()));
        
        // 设置自定义模型数据
        meta.setCustomModelData(config.getCustomModelData());
        
        // 存储NBT数据
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        
        String bookId = UUID.randomUUID().toString();
        pdc.set(bookIdKey, PersistentDataType.STRING, bookId);
        pdc.set(bookTypeKey, PersistentDataType.STRING, bookType);
        pdc.set(storedExpKey, PersistentDataType.INTEGER, 0);
        
        String ownerUuid = config.isBindPlayer() ? player.getUniqueId().toString() : null;
        if (ownerUuid != null) {
            pdc.set(ownerKey, PersistentDataType.STRING, ownerUuid);
        }
        
        // 更新Lore
        updateBookLore(meta, config, 0, ownerUuid);
        
        book.setItemMeta(meta);
        
        // 在数据库中创建记录
        createBookRecord(bookId, bookType, ownerUuid);
        
        return book;
    }
    
    // 重新加载插件配置
    public void reloadPlugin() {
        configManager.reload();
        languageManager.reload();
        loadBookConfigs();
        setupDatabase();
        getLogger().info(languageManager.getMessage("config_reloaded"));
    }
    
    private void createBookRecord(String bookId, String bookType, String ownerUuid) {
        // 使用异步任务执行数据库操作
        scheduler.runAsync(() -> {
            // 检查数据库连接是否有效
            if (connection == null) {
                getLogger().severe(languageManager.getMessage("database_not_connected"));
                return;
            }
            
            String sql = "INSERT INTO " + getTablePrefix() + "books (id, book_type, owner_uuid) VALUES (?, ?, ?)";
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, bookId);
                stmt.setString(2, bookType);
                stmt.setString(3, ownerUuid);
                stmt.executeUpdate();
            } catch (SQLException e) {
                getLogger().log(Level.SEVERE, "创建书籍记录失败", e);
            }
        });
    }
    
    private void updateBookLore(ItemMeta meta, BookConfig config, int storedExp, String ownerUuid) {
        // 确保 display name 始终使用配置文件中的名称
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', config.getDisplayName()));
        
        List<String> lore = new ArrayList<>();
        
        for (String line : config.getLore()) {
            line = line.replace("{stored_exp}", String.valueOf(storedExp))
                       .replace("{max_storage}", String.valueOf(config.getMaxStorage()));
            
            if (ownerUuid != null) {
                String ownerName = Bukkit.getOfflinePlayer(UUID.fromString(ownerUuid)).getName();
                line = line.replace("{owner}", ownerName != null ? ownerName : languageManager.getRawMessage("owner_unknown"));
            } else {
                line = line.replace("{owner}", languageManager.getRawMessage("owner_none"));
            }
            
            lore.add(ChatColor.translateAlternateColorCodes('&', line));
        }
        
        meta.setLore(lore);
    }
    
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        // 检查物品是否为经验之书
        if (!isExpBook(item)) {
            return;
        }
        
        event.setCancelled(true);
        
        // 获取书籍信息
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        
        String bookId = pdc.get(bookIdKey, PersistentDataType.STRING);
        String bookType = pdc.get(bookTypeKey, PersistentDataType.STRING);
        Integer storedExp = pdc.get(storedExpKey, PersistentDataType.INTEGER);
        String ownerUuid = pdc.get(ownerKey, PersistentDataType.STRING);
        
        // 基本数据校验
        if (bookType == null || storedExp == null) {
            confiscateBook(player, item, "invalid_book_nbt");
            return;
        }
        
        BookConfig config = bookConfigs.get(bookType);
        if (config == null) {
            confiscateBook(player, item, "invalid_book_type");
            return;
        }
        
        // 检查权限
        if (config.getPermission() != null && !player.hasPermission(config.getPermission())) {
            sendMessage(player, "no_permission");
            return;
        }
        
        // 检查所有者
        if (config.isBindPlayer() && ownerUuid != null && 
            !ownerUuid.equals(player.getUniqueId().toString())) {
            sendMessage(player, "not_owner");
            return;
        }
        
        // 记录操作类型
        final boolean isStoring = player.isSneaking();
        
        // 异步验证书籍完整性
        verifyBookIntegrityAsync(bookId, storedExp, ownerUuid, result -> {
            if (!result) {
                // 验证失败，在玩家所在区域执行没收操作
                scheduler.runAtEntity(player, () -> {
                    confiscateBook(player, item, "invalid_book");
                });
                return;
            }
            
            // 验证成功，在玩家所在区域执行经验操作
            scheduler.runAtEntity(player, () -> {
                // 再次检查物品是否还在手中（防止玩家在验证期间切换物品）
                ItemStack currentItem = player.getInventory().getItemInMainHand();
                if (!isSameBook(currentItem, bookId)) {
                    currentItem = player.getInventory().getItemInOffHand();
                    if (!isSameBook(currentItem, bookId)) {
                        return; // 物品已经不在手中，取消操作
                    }
                }
                
                // 重新获取最新的物品数据
                ItemMeta currentMeta = currentItem.getItemMeta();
                PersistentDataContainer currentPdc = currentMeta.getPersistentDataContainer();
                Integer currentStoredExp = currentPdc.get(storedExpKey, PersistentDataType.INTEGER);
                
                if (currentStoredExp == null) {
                    return; // 数据异常，取消操作
                }
                
                // 处理经验存储/提取
                if (isStoring) {
                    storeExperience(player, currentItem, currentMeta, currentPdc, bookId, currentStoredExp, config);
                } else {
                    withdrawExperience(player, currentItem, currentMeta, currentPdc, bookId, currentStoredExp, config);
                }
            });
        });
    }
    
    private void confiscateBook(Player player, ItemStack item, String reasonKey) {
        player.getInventory().removeItem(item);
        sendMessage(player, reasonKey);
        getLogger().warning("Confiscated experience book from " + player.getName() + ", reason: " + reasonKey);
    }
    
    /**
     * 异步验证书籍完整性
     * @param bookId 书籍ID
     * @param storedExp 物品NBT中存储的经验值
     * @param ownerUuid 物品NBT中存储的所有者UUID
     * @param callback 验证完成后的回调，参数为验证结果（true=通过，false=失败）
     */
    private void verifyBookIntegrityAsync(String bookId, int storedExp, String ownerUuid, 
                                         java.util.function.Consumer<Boolean> callback) {
        if (bookId == null) {
            callback.accept(false);
            return;
        }
        
        // 异步执行数据库查询
        scheduler.runAsync(() -> {
            // 检查数据库连接是否有效
            if (connection == null) {
                getLogger().severe(languageManager.getMessage("database_not_connected"));
                callback.accept(false);
                return;
            }
            
            boolean result = false;
            String sql = "SELECT stored_exp, owner_uuid FROM " + getTablePrefix() + "books WHERE id = ?";
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, bookId);
                ResultSet rs = stmt.executeQuery();
                
                if (rs.next()) {
                    int dbStoredExp = rs.getInt("stored_exp");
                    String dbOwnerUuid = rs.getString("owner_uuid");
                    
                    // 比较数据库中的值与物品NBT数据
                    result = dbStoredExp == storedExp && Objects.equals(dbOwnerUuid, ownerUuid);
                }
            } catch (SQLException e) {
                getLogger().log(Level.SEVERE, "验证书籍完整性失败", e);
            }
            
            // 将结果传递给回调
            final boolean finalResult = result;
            callback.accept(finalResult);
        });
    }
    
    /**
     * 检查两个物品是否是同一本书
     */
    private boolean isSameBook(ItemStack item, String bookId) {
        if (item == null || bookId == null) return false;
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String itemBookId = pdc.get(bookIdKey, PersistentDataType.STRING);
        
        return bookId.equals(itemBookId);
    }
    
    private void storeExperience(Player player, ItemStack item, ItemMeta meta, 
                                PersistentDataContainer pdc, String bookId, 
                                int storedExp, BookConfig config) {
        int playerExp = getTotalExperience(player);
        
        if (playerExp <= 0) {
            sendMessage(player, "not_enough_exp");
            return;
        }
        
        int maxStorage = config.getMaxStorage();
        int availableSpace = maxStorage - storedExp;
        
        if (availableSpace <= 0) {
            sendMessage(player, "book_full");
            return;
        }
        
        int expToStore = Math.min(playerExp, availableSpace);
        player.giveExp(-expToStore);
        
        int newStoredExp = storedExp + expToStore;
        pdc.set(storedExpKey, PersistentDataType.INTEGER, newStoredExp);
        
        // 更新Lore
        String ownerUuid = pdc.get(ownerKey, PersistentDataType.STRING);
        updateBookLore(meta, config, newStoredExp, ownerUuid);
        
        item.setItemMeta(meta);
        
        // 更新数据库
        updateBookStorage(bookId, newStoredExp);
        
        Map<String, String> params = new HashMap<>();
        params.put("{amount}", String.valueOf(expToStore));
        sendMessage(player, "stored_exp", params);
    }
    
    private void withdrawExperience(Player player, ItemStack item, ItemMeta meta, 
                                   PersistentDataContainer pdc, String bookId, 
                                   int storedExp, BookConfig config) {
        if (storedExp <= 0) {
            sendMessage(player, "book_empty");
            return;
        }
        
        // 提取全部经验（不再支持提取一半，避免逻辑混乱）
        int expToWithdraw = storedExp;
        
        player.giveExp(expToWithdraw);
        
        int newStoredExp = 0;
        pdc.set(storedExpKey, PersistentDataType.INTEGER, newStoredExp);
        
        // 更新Lore
        String ownerUuid = pdc.get(ownerKey, PersistentDataType.STRING);
        updateBookLore(meta, config, newStoredExp, ownerUuid);
        
        item.setItemMeta(meta);
        
        // 更新数据库
        updateBookStorage(bookId, newStoredExp);
        
        Map<String, String> params = new HashMap<>();
        params.put("{amount}", String.valueOf(expToWithdraw));
        sendMessage(player, "withdrawn_exp", params);
    }
    
    private void updateBookStorage(String bookId, int newStoredExp) {
        // 使用异步任务执行数据库更新操作
        scheduler.runAsync(() -> {
            // 检查数据库连接是否有效
            if (connection == null) {
                getLogger().severe(languageManager.getMessage("database_not_connected"));
                return;
            }
            
            String sql = "UPDATE " + getTablePrefix() + "books SET stored_exp = ? WHERE id = ?";
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setInt(1, newStoredExp);
                stmt.setString(2, bookId);
                stmt.executeUpdate();
            } catch (SQLException e) {
                getLogger().log(Level.SEVERE, "更新书籍存储失败", e);
            }
        });
    }
    
    private boolean isExpBook(ItemStack item) {
        if (item == null) return false;
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.has(bookTypeKey, PersistentDataType.STRING);
    }
    
    
    private int getTotalExperience(Player player) {
        int level = player.getLevel();
        int totalExp = 0;
        
        // 计算从0级到当前等级所需的总经验
        for (int i = 0; i < level; i++) {
            totalExp += getExpToLevel(i);
        }
        
        // 精确计算当前等级已获得的经验值
        totalExp += Math.round(player.getExp() * getExpToLevel(level));
        return totalExp;
    }
    // 计算达到指定等级所需的经验（保持不变）
    private int getExpToLevel(int level) {
        if (level <= 15) {
            return 2 * level + 7;
        } else if (level <= 30) {
            return 5 * level - 38;
        } else {
            return 9 * level - 158;
        }
    }
    
    private String getTablePrefix() {
        return getConfig().getString("database.table_prefix");
    }
    
    // 书籍配置类
    public static class BookConfig {
        private final String id;
        private final String displayName;
        private final Material material;
        private final int customModelData;
        private final int maxStorage;
        private final String permission;
        private final boolean bindPlayer;
        private final List<String> lore;
        
        public BookConfig(String id, String displayName, Material material, 
                          int customModelData, int maxStorage, 
                          String permission, boolean bindPlayer, List<String> lore) {
            this.id = id;
            this.displayName = displayName;
            this.material = material;
            this.customModelData = customModelData;
            this.maxStorage = maxStorage;
            this.permission = permission;
            this.bindPlayer = bindPlayer;
            this.lore = lore;
        }
        
        public String getId() {
            return id;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public Material getMaterial() {
            return material;
        }
        
        public int getCustomModelData() {
            return customModelData;
        }
        
        public int getMaxStorage() {
            return maxStorage;
        }
        
        public String getPermission() {
            return permission;
        }
        
        public boolean isBindPlayer() {
            return bindPlayer;
        }
        
        public List<String> getLore() {
            return lore;
        }
    }
    
    // 命令处理类
    private static class ExpBookCommand implements CommandExecutor {
        
        private final ExpBookPlugin plugin;
        
        public ExpBookCommand(ExpBookPlugin plugin) {
            this.plugin = plugin;
        }
        
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (args.length == 0) {
                sendHelp(sender);
                return true;
            }
            
            switch (args[0].toLowerCase()) {
                case "give":
                    return giveBook(sender, args);
                case "list":
                    return listBooks(sender);
                case "reload":
                    return reloadPlugin(sender);
                default:
                    sendHelp(sender);
                    return true;
            }
        }
        
        private void sendHelp(CommandSender sender) {
            sender.sendMessage(plugin.languageManager.getMessage("command_help_header"));
            sender.sendMessage(plugin.languageManager.getMessage("command_help_give"));
            sender.sendMessage(plugin.languageManager.getMessage("command_help_list"));
            sender.sendMessage(plugin.languageManager.getMessage("command_help_reload"));
            if (sender.hasPermission("expbook.admin")) {
                sender.sendMessage(plugin.languageManager.getMessage("command_help_check"));
            }
        }
        
        private boolean giveBook(CommandSender sender, String[] args) {
            if (!(sender instanceof Player) && args.length < 3) {
                sender.sendMessage(plugin.languageManager.getMessage("command_usage_console"));
                return true;
            }
            
            if (args.length < 3) {
                sender.sendMessage(plugin.languageManager.getMessage("command_usage_give"));
                return true;
            }
            
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(plugin.languageManager.getMessage("command_player_not_found"));
                return true;
            }
            
            String bookType = args[2];
            if (!plugin.bookConfigs.containsKey(bookType)) {
                sender.sendMessage(plugin.languageManager.getMessage("command_invalid_book_type"));
                return true;
            }
            
            ItemStack book = plugin.createNewBook(target, bookType);
            if (book == null) {
                sender.sendMessage(plugin.languageManager.getMessage("command_book_creation_failed"));
                return true;
            }
            
            Map<String, String> replacements = new HashMap<>();
            replacements.put("{book}", plugin.bookConfigs.get(bookType).getDisplayName());
            
            target.getInventory().addItem(book);
            plugin.sendMessage(target, "book_given", replacements);
            
            Map<String, String> senderReplacements = new HashMap<>();
            senderReplacements.put("{player}", target.getName());
            senderReplacements.put("{book}", plugin.bookConfigs.get(bookType).getDisplayName());
            sender.sendMessage(plugin.languageManager.getMessage("command_book_given_sender", senderReplacements));
            return true;
        }
        
        private boolean listBooks(CommandSender sender) {
            sender.sendMessage(plugin.languageManager.getMessage("command_list_header"));
            for (BookConfig config : plugin.bookConfigs.values()) {
                Map<String, String> replacements = new HashMap<>();
                replacements.put("{id}", config.getId());
                replacements.put("{name}", ChatColor.translateAlternateColorCodes('&', config.getDisplayName()));
                replacements.put("{material}", config.getMaterial().toString());
                replacements.put("{model}", String.valueOf(config.getCustomModelData()));
                sender.sendMessage(plugin.languageManager.getMessage("command_list_entry", replacements));
            }
            return true;
        }
        
        private boolean reloadPlugin(CommandSender sender) {
            if (!sender.hasPermission("expbook.reload")) {
                sender.sendMessage(plugin.languageManager.getMessage("no_reload_permission"));
                return true;
            }
            
            try {
                plugin.reloadPlugin();
                sender.sendMessage(plugin.languageManager.getMessage("reload_success"));
            } catch (Exception e) {
                Map<String, String> replacements = new HashMap<>();
                replacements.put("{error}", e.getMessage());
                sender.sendMessage(plugin.languageManager.getMessage("command_reload_error", replacements));
                plugin.getLogger().log(Level.SEVERE, "Error while reloading plugin", e);
            }
            return true;
        }
    }
    
    private void sendMessage(Player player, String key, Map<String, String> replacements) {
        player.sendMessage(languageManager.getMessage(key, replacements));
    }
    
    // 添加重载方法：无占位符
    private void sendMessage(Player player, String key) {
        player.sendMessage(languageManager.getMessage(key));
    }
    
    // 获取语言管理器（用于其他类访问）
    public LanguageManager getLanguageManager() {
        return languageManager;
    }

}
