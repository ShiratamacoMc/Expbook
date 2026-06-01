package com.magicbili.expbook;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

/**
 * 调度器适配器，兼容 Bukkit 和 Folia
 */
public class SchedulerAdapter {
    
    private final Plugin plugin;
    private final boolean isFolia;
    
    public SchedulerAdapter(Plugin plugin) {
        this.plugin = plugin;
        this.isFolia = checkFolia();
    }
    
    /**
     * 检测是否运行在 Folia 上
     */
    private boolean checkFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
    
    /**
     * 在实体所在区域运行任务
     */
    public void runAtEntity(Entity entity, Runnable task) {
        if (isFolia) {
            // Folia: 使用实体调度器
            entity.getScheduler().run(plugin, scheduledTask -> task.run(), null);
        } else {
            // Bukkit: 同步任务
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }
    
    /**
     * 在指定位置所在区域运行任务
     */
    public void runAtLocation(Location location, Runnable task) {
        if (isFolia) {
            // Folia: 使用区域调度器
            Bukkit.getRegionScheduler().run(plugin, location, scheduledTask -> task.run());
        } else {
            // Bukkit: 同步任务
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }
    
    /**
     * 运行异步任务
     */
    public void runAsync(Runnable task) {
        if (isFolia) {
            // Folia: 使用异步调度器
            Bukkit.getAsyncScheduler().runNow(plugin, scheduledTask -> task.run());
        } else {
            // Bukkit: 异步任务
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }
    }
    
    /**
     * 延迟运行任务（在实体所在区域）
     */
    public void runLaterAtEntity(Entity entity, Runnable task, long delayTicks) {
        if (isFolia) {
            // Folia: 使用实体调度器延迟执行
            entity.getScheduler().runDelayed(plugin, scheduledTask -> task.run(), null, delayTicks);
        } else {
            // Bukkit: 延迟同步任务
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }
    
    /**
     * 延迟运行任务（在指定位置所在区域）
     */
    public void runLaterAtLocation(Location location, Runnable task, long delayTicks) {
        if (isFolia) {
            // Folia: 使用区域调度器延迟执行
            Bukkit.getRegionScheduler().runDelayed(plugin, location, scheduledTask -> task.run(), delayTicks);
        } else {
            // Bukkit: 延迟同步任务
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }
    
    /**
     * 检查是否运行在 Folia 上
     */
    public boolean isFolia() {
        return isFolia;
    }
}
