package com.magicbili.expbook;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.*;
import java.util.logging.Level;

/**
 * 数据库管理器 - 使用 HikariCP 连接池
 * 解决线程安全和性能问题
 */
public class DatabaseManager {
    
    private final JavaPlugin plugin;
    private HikariDataSource dataSource;
    private String currentStorageType; // "mysql" or "sqlite"
    private String tablePrefix;
    
    public DatabaseManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * 初始化数据库连接
     */
    public void initialize(String storageType, String prefix) {
        this.tablePrefix = prefix;
        
        // 关闭现有连接
        close();
        
        // 尝试连接数据库
        if ("mysql".equals(storageType.toLowerCase())) {
            if (!setupMySQL()) {
                plugin.getLogger().warning("MySQL connection failed, falling back to SQLite...");
                setupSQLite();
            }
        } else {
            setupSQLite();
        }
        
        // 创建表
        if (dataSource != null) {
            createTables();
        }
    }
    
    /**
     * 设置 MySQL 连接池
     */
    private boolean setupMySQL() {
        try {
            HikariConfig config = new HikariConfig();
            
            String host = plugin.getConfig().getString("database.host");
            int port = plugin.getConfig().getInt("database.port");
            String database = plugin.getConfig().getString("database.database");
            String username = plugin.getConfig().getString("database.username");
            String password = plugin.getConfig().getString("database.password");
            
            config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database);
            config.setUsername(username);
            config.setPassword(password);
            
            // 连接池配置
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setConnectionTimeout(30000); // 30 秒
            config.setIdleTimeout(600000); // 10 分钟
            config.setMaxLifetime(1800000); // 30 分钟
            config.setConnectionTestQuery("SELECT 1");
            
            // MySQL 优化参数
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            config.addDataSourceProperty("useServerPrepStmts", "true");
            config.addDataSourceProperty("useLocalSessionState", "true");
            config.addDataSourceProperty("rewriteBatchedStatements", "true");
            config.addDataSourceProperty("cacheResultSetMetadata", "true");
            config.addDataSourceProperty("cacheServerConfiguration", "true");
            config.addDataSourceProperty("elideSetAutoCommits", "true");
            config.addDataSourceProperty("maintainTimeStats", "false");
            
            dataSource = new HikariDataSource(config);
            currentStorageType = "mysql";
            
            plugin.getLogger().info("Successfully connected to MySQL database with connection pool");
            return true;
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "MySQL connection failed", e);
            return false;
        }
    }
    
    /**
     * 设置 SQLite 连接池
     */
    private void setupSQLite() {
        try {
            // 创建数据文件夹
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }
            
            // SQLite 数据库文件路径
            File dbFile = new File(dataFolder, "expbook.db");
            
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            config.setDriverClassName("org.sqlite.JDBC");
            
            // SQLite 连接池配置（较小）
            config.setMaximumPoolSize(5);
            config.setMinimumIdle(1);
            config.setConnectionTimeout(30000);
            config.setIdleTimeout(600000);
            config.setMaxLifetime(1800000);
            
            // SQLite 优化参数
            config.addDataSourceProperty("journal_mode", "WAL");
            config.addDataSourceProperty("synchronous", "NORMAL");
            
            dataSource = new HikariDataSource(config);
            currentStorageType = "sqlite";
            
            plugin.getLogger().info("Successfully connected to SQLite database at: " + dbFile.getAbsolutePath());
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "SQLite connection failed", e);
        }
    }
    
    /**
     * 创建数据表
     */
    private void createTables() {
        String createTable;
        
        if ("mysql".equals(currentStorageType)) {
            // MySQL 语法
            createTable = "CREATE TABLE IF NOT EXISTS " + tablePrefix + "books (" +
                    "id VARCHAR(36) PRIMARY KEY, " +
                    "book_type VARCHAR(50) NOT NULL, " +
                    "owner_uuid VARCHAR(36), " +
                    "stored_exp INT NOT NULL DEFAULT 0, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                    "INDEX idx_owner (owner_uuid), " +
                    "INDEX idx_type (book_type)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        } else {
            // SQLite 语法
            createTable = "CREATE TABLE IF NOT EXISTS " + tablePrefix + "books (" +
                    "id TEXT PRIMARY KEY, " +
                    "book_type TEXT NOT NULL, " +
                    "owner_uuid TEXT, " +
                    "stored_exp INTEGER NOT NULL DEFAULT 0, " +
                    "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                    "last_updated DATETIME DEFAULT CURRENT_TIMESTAMP" +
                    ")";
        }
        
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            
            stmt.execute(createTable);
            
            // 为 SQLite 创建索引和触发器
            if ("sqlite".equals(currentStorageType)) {
                // 创建索引
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_owner ON " + tablePrefix + "books(owner_uuid)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_type ON " + tablePrefix + "books(book_type)");
                
                // 创建触发器来模拟 ON UPDATE CURRENT_TIMESTAMP
                String trigger = "CREATE TRIGGER IF NOT EXISTS " + tablePrefix + "books_update_trigger " +
                        "AFTER UPDATE ON " + tablePrefix + "books " +
                        "FOR EACH ROW " +
                        "BEGIN " +
                        "UPDATE " + tablePrefix + "books SET last_updated = CURRENT_TIMESTAMP WHERE id = NEW.id; " +
                        "END";
                stmt.execute(trigger);
            }
            
            plugin.getLogger().info("Database tables created successfully");
            
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create database tables", e);
        }
    }
    
    /**
     * 获取数据库连接（从连接池）
     * @return 数据库连接
     * @throws SQLException 如果无法获取连接
     */
    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("Database connection pool is not initialized or closed");
        }
        
        Connection conn = dataSource.getConnection();
        
        // 验证连接是否有效
        if (!conn.isValid(5)) {
            conn.close();
            throw new SQLException("Database connection is not valid");
        }
        
        return conn;
    }
    
    /**
     * 检查数据库是否已连接
     */
    public boolean isConnected() {
        return dataSource != null && !dataSource.isClosed();
    }
    
    /**
     * 获取当前存储类型
     */
    public String getCurrentStorageType() {
        return currentStorageType;
    }
    
    /**
     * 获取表前缀
     */
    public String getTablePrefix() {
        return tablePrefix;
    }
    
    /**
     * 创建书籍记录
     */
    public void createBookRecord(String bookId, String bookType, String ownerUuid) {
        String sql = "INSERT INTO " + tablePrefix + "books (id, book_type, owner_uuid) VALUES (?, ?, ?)";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, bookId);
            stmt.setString(2, bookType);
            stmt.setString(3, ownerUuid);
            stmt.executeUpdate();
            
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create book record in database", e);
        }
    }
    
    /**
     * 更新书籍存储的经验值
     */
    public void updateBookStorage(String bookId, int newStoredExp) {
        String sql = "UPDATE " + tablePrefix + "books SET stored_exp = ? WHERE id = ?";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, newStoredExp);
            stmt.setString(2, bookId);
            stmt.executeUpdate();
            
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to update book storage in database", e);
        }
    }
    
    /**
     * 验证书籍完整性
     * @return BookData 对象，如果书籍不存在或数据不匹配则返回 null
     */
    public BookData verifyBookIntegrity(String bookId, int expectedStoredExp, String expectedOwnerUuid) {
        if (bookId == null) {
            return null;
        }
        
        String sql = "SELECT stored_exp, owner_uuid FROM " + tablePrefix + "books WHERE id = ?";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, bookId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int dbStoredExp = rs.getInt("stored_exp");
                    String dbOwnerUuid = rs.getString("owner_uuid");
                    
                    // 比较数据库中的值与物品NBT数据
                    if (dbStoredExp == expectedStoredExp && 
                        java.util.Objects.equals(dbOwnerUuid, expectedOwnerUuid)) {
                        return new BookData(dbStoredExp, dbOwnerUuid);
                    }
                }
            }
            
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to verify book integrity", e);
        }
        
        return null;
    }
    
    /**
     * 删除书籍记录
     */
    public void deleteBookRecord(String bookId) {
        String sql = "DELETE FROM " + tablePrefix + "books WHERE id = ?";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, bookId);
            stmt.executeUpdate();
            
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to delete book record", e);
        }
    }
    
    /**
     * 关闭数据库连接池
     */
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("Database connection pool closed successfully");
        }
    }
    
    /**
     * 书籍数据类
     */
    public static class BookData {
        private final int storedExp;
        private final String ownerUuid;
        
        public BookData(int storedExp, String ownerUuid) {
            this.storedExp = storedExp;
            this.ownerUuid = ownerUuid;
        }
        
        public int getStoredExp() {
            return storedExp;
        }
        
        public String getOwnerUuid() {
            return ownerUuid;
        }
    }
}
