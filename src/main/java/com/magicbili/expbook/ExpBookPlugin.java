package com.magicbili.expbook;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
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

import java.util.*;
import java.util.logging.Level;

/**
 * ExpBook 主插件类
 * 经验之书插件 - 允许玩家在书中存储和提取经验
 * 
 * @author MagicBili
 * @version 1.0.2
 */
public class ExpBookPlugin extends JavaPlugin implements Listener {
    
    // 管理器
    private DatabaseManager databaseManager;
    private CacheManager cacheManager;
    private LanguageManager languageManager;
    private ConfigManager configManager;
    private SchedulerAdapter scheduler;
    
    // 配置
    private Map<String, BookConfig> bookConfigs = new HashMap<>();
    
    // NBT 键
    private NamespacedKey bookIdKey;
    private NamespacedKey bookTypeKey;
    private NamespacedKey storedExpKey;
    private NamespacedKey ownerKey;
    
    @Override
    public void onEnable() {
        try {
            // 初始化调度器适配器
            scheduler = new SchedulerAdapter(this);
            
            // 初始化配置管理器
            configManager = new ConfigManager(this);
            
            // 初始化语言管理器
            languageManager = new LanguageManager(this);
            
            // 初始化缓存管理器
            cacheManager = new CacheManager();
            
            // 初始化数据库管理器
            databaseManager = new DatabaseManager(this);
            
            // 加载配置（自动更新旧配置）
            configManager.loadConfig();
            
            // 加载语言文件
            languageManager.loadLanguage();
            
            // 检测运行环境
            if (scheduler.isFolia()) {
                getLogger().info("Detected Folia environment, using regionalized scheduler");
            } else {
                getLogger().info("Detected Bukkit/Spigot/Paper environment, using traditional scheduler");
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
            Objects.requireNonNull(getCommand("expbook")).setTabCompleter(new ExpBookTabCompleter(this));
            
            getLogger().info("ExpBook Plugin v" + getDescription().getVersion() + " has been enabled!");
            getLogger().info("Database: " + databaseManager.getCurrentStorageType());
            
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to enable plugin", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }
    
    @Override
    public void onDisable() {
        try {
            // 关闭数据库连接池
            if (databaseManager != null) {
                databaseManager.close();
            }
            
            // 清除缓存
            if (cacheManager != null) {
                cacheManager.clearAll();
            }
            
            getLogger().info("ExpBook Plugin has been disabled!");
            
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error during plugin shutdown", e);
        }
    }
    
    /**
     * 加载配置
     */
    private void loadConfigs() {
        // 加载书籍配置
        loadBookConfigs();
        
        // 连接数据库
        String storageType = getConfig().getString("database.storage_type", "sqlite");
        String prefix = getConfig().getString("database.table_prefix", "expbook_");
        databaseManager.initialize(storageType, prefix);
    }
    
    /**
     * 加载书籍配置
     */
    private void loadBookConfigs() {
        bookConfigs.clear();
        
        if (getConfig().getConfigurationSection("books") == null) {
            getLogger().warning("No books configured in config.yml!");
            return;
        }
        
        for (String bookId : getConfig().getConfigurationSection("books").getKeys(false)) {
            try {
                String path = "books." + bookId + ".";
                
                String materialName = getConfig().getString(path + "material");
                if (materialName == null) {
                    getLogger().warning("Book '" + bookId + "' has no material defined, skipping");
                    continue;
                }
                
                Material material = Material.matchMaterial(materialName);
                if (material == null) {
                    getLogger().warning("Book '" + bookId + "' has invalid material '" + materialName + "', skipping");
                    continue;
                }
                
                BookConfig config = new BookConfig(
                    bookId,
                    getConfig().getString(path + "display_name", "&aExperience Book"),
                    material,
                    getConfig().getInt(path + "custom_model_data", 0),
                    getConfig().getInt(path + "max_storage", 1000),
                    getConfig().getString(path + "permission"),
                    getConfig().getBoolean(path + "bind_player", true),
                    getConfig().getStringList(path + "lore")
                );
                
                bookConfigs.put(bookId, config);
                getLogger().info("Loaded book config: " + bookId);
                
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Failed to load book config: " + bookId, e);
            }
        }
    }
    
    /**
     * 重新加载插件配置
     */
    public void reloadPlugin() {
        try {
            configManager.reload();
            languageManager.reload();
            loadBookConfigs();
            
            // 重新初始化数据库
            String storageType = getConfig().getString("database.storage_type", "sqlite");
            String prefix = getConfig().getString("database.table_prefix", "expbook_");
            databaseManager.initialize(storageType, prefix);
            
            // 清除缓存
            cacheManager.clearAll();
            
            getLogger().info("Configuration and database connection have been reloaded!");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to reload plugin", e);
            throw new RuntimeException("Reload failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * 创建一本新的经验之书
     */
    public ItemStack createNewBook(Player player, String bookType) {
        BookConfig config = bookConfigs.get(bookType);
        if (config == null) {
            getLogger().warning("Attempted to create book with invalid type: " + bookType);
            return null;
        }
        
        ItemStack book = new ItemStack(config.getMaterial());
        ItemMeta meta = book.getItemMeta();
        if (meta == null) {
            getLogger().severe("Failed to get ItemMeta for book material: " + config.getMaterial());
            return null;
        }
        
        // 设置显示名称
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', config.getDisplayName()));
        
        // 设置自定义模型数据
        if (config.getCustomModelData() > 0) {
            meta.setCustomModelData(config.getCustomModelData());
        }
        
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
        
        // 异步在数据库中创建记录
        scheduler.runAsync(() -> {
            databaseManager.createBookRecord(bookId, bookType, ownerUuid);
        });
        
        return book;
    }
    
    /**
     * 更新书籍的 Lore
     */
    private void updateBookLore(ItemMeta meta, BookConfig config, int storedExp, String ownerUuid) {
        // 确保 display name 始终使用配置文件中的名称
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', config.getDisplayName()));
        
        List<String> lore = new ArrayList<>();
        
        for (String line : config.getLore()) {
            line = line.replace("{stored_exp}", String.valueOf(storedExp))
                       .replace("{max_storage}", String.valueOf(config.getMaxStorage()));
            
            if (ownerUuid != null) {
                try {
                    UUID uuid = UUID.fromString(ownerUuid);
                    String ownerName = cacheManager.getPlayerName(uuid);
                    line = line.replace("{owner}", ownerName);
                } catch (IllegalArgumentException e) {
                    line = line.replace("{owner}", languageManager.getRawMessage("owner_unknown"));
                }
            } else {
                line = line.replace("{owner}", languageManager.getRawMessage("owner_none"));
            }
            
            lore.add(ChatColor.translateAlternateColorCodes('&', line));
        }
        
        meta.setLore(lore);
    }
    
    /**
     * 玩家交互事件处理器
     */
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
        if (meta == null) {
            return;
        }
        
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        
        String bookId = pdc.get(bookIdKey, PersistentDataType.STRING);
        String bookType = pdc.get(bookTypeKey, PersistentDataType.STRING);
        Integer storedExp = pdc.get(storedExpKey, PersistentDataType.INTEGER);
        String ownerUuid = pdc.get(ownerKey, PersistentDataType.STRING);
        
        // 基本数据校验
        if (bookId == null || bookType == null || storedExp == null) {
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
        
        // 检查操作冷却
        if (cacheManager.isOperationOnCooldown(player.getUniqueId(), bookId)) {
            // 静默拒绝，避免刷屏
            return;
        }
        
        // 记录操作类型
        final boolean isStoring = player.isSneaking();
        
        // 检查是否需要验证（使用缓存优化）
        boolean needsVerification = !cacheManager.isBookRecentlyVerified(bookId);
        
        if (needsVerification) {
            // 异步验证书籍完整性
            verifyBookIntegrityAsync(bookId, storedExp, ownerUuid, result -> {
                if (!result) {
                    // 验证失败，在玩家所在区域执行没收操作
                    scheduler.runAtEntity(player, () -> {
                        confiscateBook(player, item, "invalid_book");
                    });
                    return;
                }
                
                // 标记已验证
                cacheManager.markBookVerified(bookId);
                
                // 验证成功，执行操作
                executeBookOperation(player, item, bookId, isStoring, config);
            });
        } else {
            // 跳过验证，直接执行操作
            executeBookOperation(player, item, bookId, isStoring, config);
        }
    }
    
    /**
     * 执行书籍操作（存储或提取经验）
     */
    private void executeBookOperation(Player player, ItemStack item, String bookId, 
                                     boolean isStoring, BookConfig config) {
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
            if (currentMeta == null) {
                return;
            }
            
            PersistentDataContainer currentPdc = currentMeta.getPersistentDataContainer();
            Integer currentStoredExp = currentPdc.get(storedExpKey, PersistentDataType.INTEGER);
            
            if (currentStoredExp == null) {
                return; // 数据异常，取消操作
            }
            
            // 标记操作冷却
            cacheManager.markOperationExecuted(player.getUniqueId(), bookId);
            
            // 处理经验存储/提取
            if (isStoring) {
                storeExperience(player, currentItem, currentMeta, currentPdc, bookId, currentStoredExp, config);
            } else {
                withdrawExperience(player, currentItem, currentMeta, currentPdc, bookId, currentStoredExp, config);
            }
        });
    }
    
    /**
     * 没收无效的书籍
     */
    private void confiscateBook(Player player, ItemStack item, String reasonKey) {
        player.getInventory().removeItem(item);
        sendMessage(player, reasonKey);
        getLogger().warning("Confiscated experience book from " + player.getName() + " - Reason: " + reasonKey);
    }
    
    /**
     * 异步验证书籍完整性
     */
    private void verifyBookIntegrityAsync(String bookId, int storedExp, String ownerUuid, 
                                         java.util.function.Consumer<Boolean> callback) {
        // 异步执行数据库查询
        scheduler.runAsync(() -> {
            DatabaseManager.BookData bookData = databaseManager.verifyBookIntegrity(bookId, storedExp, ownerUuid);
            callback.accept(bookData != null);
        });
    }
    
    /**
     * 检查两个物品是否是同一本书
     */
    private boolean isSameBook(ItemStack item, String bookId) {
        if (item == null || bookId == null) {
            return false;
        }
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String itemBookId = pdc.get(bookIdKey, PersistentDataType.STRING);
        
        return bookId.equals(itemBookId);
    }
    
    /**
     * 检查物品是否为经验之书
     */
    private boolean isExpBook(ItemStack item) {
        if (item == null) {
            return false;
        }
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.has(bookTypeKey, PersistentDataType.STRING);
    }
    
    /**
     * 存储经验到书中
     */
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
        
        // 异步更新数据库
        scheduler.runAsync(() -> {
            databaseManager.updateBookStorage(bookId, newStoredExp);
        });
        
        // 清除验证缓存，强制下次验证
        cacheManager.clearBookCache(bookId);
        
        Map<String, String> params = new HashMap<>();
        params.put("{amount}", String.valueOf(expToStore));
        sendMessage(player, "stored_exp", params);
    }
    
    /**
     * 从书中提取经验
     */
    private void withdrawExperience(Player player, ItemStack item, ItemMeta meta, 
                                   PersistentDataContainer pdc, String bookId, 
                                   int storedExp, BookConfig config) {
        if (storedExp <= 0) {
            sendMessage(player, "book_empty");
            return;
        }
        
        // 提取全部经验
        int expToWithdraw = storedExp;
        
        player.giveExp(expToWithdraw);
        
        int newStoredExp = 0;
        pdc.set(storedExpKey, PersistentDataType.INTEGER, newStoredExp);
        
        // 更新Lore
        String ownerUuid = pdc.get(ownerKey, PersistentDataType.STRING);
        updateBookLore(meta, config, newStoredExp, ownerUuid);
        
        item.setItemMeta(meta);
        
        // 异步更新数据库
        scheduler.runAsync(() -> {
            databaseManager.updateBookStorage(bookId, newStoredExp);
        });
        
        // 清除验证缓存，强制下次验证
        cacheManager.clearBookCache(bookId);
        
        Map<String, String> params = new HashMap<>();
        params.put("{amount}", String.valueOf(expToWithdraw));
        sendMessage(player, "withdrawn_exp", params);
    }
    
    /**
     * 获取玩家的总经验值
     */
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
    
    /**
     * 计算达到指定等级所需的经验
     */
    private int getExpToLevel(int level) {
        if (level <= 15) {
            return 2 * level + 7;
        } else if (level <= 30) {
            return 5 * level - 38;
        } else {
            return 9 * level - 158;
        }
    }
    
    /**
     * 发送消息给玩家（带占位符）
     */
    private void sendMessage(Player player, String key, Map<String, String> replacements) {
        player.sendMessage(languageManager.getMessage(key, replacements));
    }
    
    /**
     * 发送消息给玩家（无占位符）
     */
    private void sendMessage(Player player, String key) {
        player.sendMessage(languageManager.getMessage(key));
    }
    
    /**
     * 获取语言管理器（用于其他类访问）
     */
    public LanguageManager getLanguageManager() {
        return languageManager;
    }
    
    /**
     * 获取所有书籍类型（用于 Tab Completer）
     */
    public List<String> getBookTypes() {
        return new ArrayList<>(bookConfigs.keySet());
    }
    
    /**
     * 获取缓存管理器
     */
    public CacheManager getCacheManager() {
        return cacheManager;
    }
    
    /**
     * 获取数据库管理器
     */
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
    
    /**
     * 书籍配置类
     */
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
    
    /**
     * 命令处理类
     */
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
        }
        
        private boolean giveBook(CommandSender sender, String[] args) {
            if (!sender.hasPermission("expbook.give") && !sender.hasPermission("expbook.admin")) {
                sender.sendMessage(plugin.languageManager.getMessage("no_permission"));
                return true;
            }
            
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
            if (!sender.hasPermission("expbook.reload") && !sender.hasPermission("expbook.admin")) {
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

}
