package com.magicbili.expbook;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Tab completer for ExpBook commands
 * Provides auto-completion suggestions for commands and arguments
 */
public class ExpBookTabCompleter implements TabCompleter {
    
    private final ExpBookPlugin plugin;
    
    public ExpBookTabCompleter(ExpBookPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        // First argument: subcommands
        if (args.length == 1) {
            List<String> subcommands = Arrays.asList("give", "list", "reload");
            
            // Filter based on permissions
            for (String subcommand : subcommands) {
                if (hasPermissionForSubcommand(sender, subcommand)) {
                    if (subcommand.toLowerCase().startsWith(args[0].toLowerCase())) {
                        completions.add(subcommand);
                    }
                }
            }
            
            return completions;
        }
        
        // Second argument for "give" command: player names
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            if (sender.hasPermission("expbook.give") || sender.hasPermission("expbook.admin")) {
                String input = args[1].toLowerCase();
                
                // Add online player names
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(input)) {
                        completions.add(player.getName());
                    }
                }
            }
            
            return completions;
        }
        
        // Third argument for "give" command: book types
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            if (sender.hasPermission("expbook.give") || sender.hasPermission("expbook.admin")) {
                String input = args[2].toLowerCase();
                
                // Add all book types from config
                for (String bookType : plugin.getBookTypes()) {
                    if (bookType.toLowerCase().startsWith(input)) {
                        completions.add(bookType);
                    }
                }
            }
            
            return completions;
        }
        
        return completions;
    }
    
    /**
     * Check if sender has permission for a specific subcommand
     */
    private boolean hasPermissionForSubcommand(CommandSender sender, String subcommand) {
        switch (subcommand.toLowerCase()) {
            case "give":
                return sender.hasPermission("expbook.give") || sender.hasPermission("expbook.admin");
            case "list":
                return sender.hasPermission("expbook.admin");
            case "reload":
                return sender.hasPermission("expbook.reload") || sender.hasPermission("expbook.admin");
            default:
                return false;
        }
    }
}
