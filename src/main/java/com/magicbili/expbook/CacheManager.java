package com.magicbili.expbook;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.bukkit.Bukkit;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 缓存管理器 - 使用 Caffeine 缓存
 * 减少数据库查询和磁盘 I/O
 */
public class CacheManager {
    
    // 玩家名称缓存 (UUID -> 玩家名)
    private final Cache<UUID, String> playerNameCache;
    
    // 书籍验证缓存 (bookId -> 最后验证时间)
    // 用于防止短时间内重复验证同一本书
    private final Cache<String, Long> bookVerificationCache;
    
    // 操作冷却缓存 (玩家UUID + bookId -> 最后操作时间)
    // 防止玩家快速重复操作导致的竞态条件
    private final Cache<String, Long> operationCooldownCache;
    
    public CacheManager() {
        // 玩家名称缓存：最多 1000 个条目，30 分钟过期
        playerNameCache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .build();
        
        // 书籍验证缓存：最多 5000 个条目，5 秒过期
        bookVerificationCache = Caffeine.newBuilder()
                .maximumSize(5000)
                .expireAfterWrite(5, TimeUnit.SECONDS)
                .build();
        
        // 操作冷却缓存：最多 2000 个条目，2 秒过期
        operationCooldownCache = Caffeine.newBuilder()
                .maximumSize(2000)
                .expireAfterWrite(2, TimeUnit.SECONDS)
                .build();
    }
    
    /**
     * 获取玩家名称（带缓存）
     */
    public String getPlayerName(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        
        return playerNameCache.get(uuid, key -> {
            // 缓存未命中，从 Bukkit 获取
            String name = Bukkit.getOfflinePlayer(key).getName();
            return name != null ? name : "Unknown";
        });
    }
    
    /**
     * 检查书籍是否最近已验证
     * @return true 如果最近已验证（可以跳过验证）
     */
    public boolean isBookRecentlyVerified(String bookId) {
        Long lastVerification = bookVerificationCache.getIfPresent(bookId);
        if (lastVerification == null) {
            return false;
        }
        
        // 如果在 5 秒内已验证过，返回 true
        return (System.currentTimeMillis() - lastVerification) < 5000;
    }
    
    /**
     * 标记书籍已验证
     */
    public void markBookVerified(String bookId) {
        bookVerificationCache.put(bookId, System.currentTimeMillis());
    }
    
    /**
     * 检查操作是否在冷却中
     * @return true 如果在冷却中（应该拒绝操作）
     */
    public boolean isOperationOnCooldown(UUID playerUuid, String bookId) {
        String key = playerUuid.toString() + ":" + bookId;
        Long lastOperation = operationCooldownCache.getIfPresent(key);
        
        if (lastOperation == null) {
            return false;
        }
        
        // 如果在 2 秒内已操作过，返回 true
        return (System.currentTimeMillis() - lastOperation) < 2000;
    }
    
    /**
     * 标记操作已执行
     */
    public void markOperationExecuted(UUID playerUuid, String bookId) {
        String key = playerUuid.toString() + ":" + bookId;
        operationCooldownCache.put(key, System.currentTimeMillis());
    }
    
    /**
     * 清除所有缓存
     */
    public void clearAll() {
        playerNameCache.invalidateAll();
        bookVerificationCache.invalidateAll();
        operationCooldownCache.invalidateAll();
    }
    
    /**
     * 清除特定书籍的缓存
     */
    public void clearBookCache(String bookId) {
        bookVerificationCache.invalidate(bookId);
    }
    
    /**
     * 清除特定玩家的缓存
     */
    public void clearPlayerCache(UUID playerUuid) {
        playerNameCache.invalidate(playerUuid);
    }
    
    /**
     * 获取缓存统计信息
     */
    public String getCacheStats() {
        return String.format(
            "Cache Stats - Players: %d, Verifications: %d, Cooldowns: %d",
            playerNameCache.estimatedSize(),
            bookVerificationCache.estimatedSize(),
            operationCooldownCache.estimatedSize()
        );
    }
}
