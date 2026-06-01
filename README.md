# ExpBook Plugin

A high-performance Minecraft plugin that allows players to store and withdraw experience points using special books.

## ✨ Features

- 📚 **Multiple Book Types** - Common, Advanced, and Shared experience books with different capacities
- 🔒 **Player Binding** - Books can be bound to specific players to prevent theft
- 💾 **Dual Database Support** - SQLite (default) and MySQL with automatic fallback
- ⚡ **Folia Compatible** - Full support for Folia's regionalized threading system
- 🚀 **High Performance** - HikariCP connection pool and Caffeine cache for optimal performance
- 🎨 **Custom Models** - Support for custom model data for resource packs
- 🌐 **Multi-Language Support** - English and Simplified Chinese (easily extensible)
- 🔄 **Auto Config Update** - Version-based configuration management with automatic updates
- 🛡️ **Anti-Cheat Protection** - Database verification to prevent item duplication
- 🔥 **Operation Cooldown** - Prevents rapid duplicate operations and race conditions

## 🎯 Performance Highlights

- **HikariCP Connection Pool** - Professional-grade database connection management
- **Caffeine Cache** - High-performance caching reduces database queries by ~85%
- **Async Operations** - All database operations run asynchronously
- **Smart Verification** - Cached validation skips redundant checks
- **Thread-Safe** - Fully thread-safe for concurrent operations

## 📋 Requirements

- **Minecraft**: 1.20.1+
- **Server**: Folia / Paper / Spigot / Bukkit
- **Java**: 17+
- **Database**: SQLite (built-in) or MySQL 5.7+ / MariaDB 10.2+ (optional)

## 📦 Installation

1. Download `ExpBookPlugin-1.0.2.jar`
2. Place the jar file in your server's `plugins` folder
3. Start the server - **Paper/Folia will automatically download required libraries**
4. Configuration files will be generated in `plugins/ExpBookPlugin/`
5. Edit `plugins/ExpBookPlugin/config.yml` to configure database connection and language
6. Run `/expbook reload` to apply changes

**Note**: This plugin uses Paper's library loader feature. All dependencies (HikariCP, Caffeine, SQLite, MySQL driver) will be automatically downloaded on first startup. This keeps the plugin JAR very small (~50KB).

**Requirements for automatic library loading**:
- Paper 1.16.5+ or Folia
- Internet connection on first startup (for downloading libraries)
- Libraries are cached in `libraries/` folder and reused

## ⚙️ Configuration

### Language Settings

Edit `config.yml` to set your preferred language:

```yaml
# Supported languages: en (English), zh_CN (Simplified Chinese)
language: en
```

### Database Configuration

The plugin supports two storage types: **SQLite** (default) and **MySQL**.

**SQLite (Recommended for small servers):**
```yaml
database:
  storage_type: sqlite
  table_prefix: expbook_
```

**MySQL (Recommended for large servers or networks):**
```yaml
database:
  storage_type: mysql
  host: localhost
  port: 3306
  database: minecraft
  username: root
  password: "your_password"
  table_prefix: expbook_
```

**Automatic Fallback:**
If MySQL connection fails, the plugin will automatically fall back to SQLite and log a warning. This ensures your server continues to function even if the database is temporarily unavailable.

### Performance Configuration

```yaml
performance:
  # Enable caching to reduce database queries
  enable_cache: true
  # Operation cooldown in milliseconds (prevents rapid duplicate operations)
  operation_cooldown_ms: 2000
  # Book verification cache duration in seconds
  verification_cache_seconds: 5
```

### Default Book Types

| Book Type | Capacity | Material | Bound | Permission |
|-----------|----------|----------|-------|------------|
| Common Book | 1000 XP | Paper | Yes | `expbook.common` |
| Advanced Book | 5000 XP | Book | Yes | `expbook.advanced` |
| Shared Book | 2000 XP | Enchanted Book | No | `expbook.shared` |

You can customize book names, lore, and properties in `config.yml`.

## 🎮 Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/expbook give <player> <type>` | Give an experience book to a player | `expbook.give` |
| `/expbook list` | List all available book types | `expbook.admin` |
| `/expbook reload` | Reload plugin configuration | `expbook.reload` |

**Examples:**
```
/expbook give Steve common_book
/expbook give Alex advanced_book
/expbook list
```

## 🔐 Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `expbook.admin` | Access to all admin commands | OP |
| `expbook.give` | Give experience books to players | OP |
| `expbook.reload` | Reload plugin configuration | OP |
| `expbook.common` | Use common experience books | Everyone |
| `expbook.advanced` | Use advanced experience books | Everyone |
| `expbook.shared` | Use shared experience books | Everyone |

## 📖 Usage

**For Players:**
- **Store XP**: Hold the book and `Sneak + Right Click`
- **Withdraw All XP**: Hold the book and `Right Click`

**Note:** There is a 2-second cooldown between operations to prevent accidental duplicate actions.

## 🌐 Multi-Language Support

The plugin supports multiple languages. Language files are located in:
- `plugins/ExpBookPlugin/messages_en.yml` (English)
- `plugins/ExpBookPlugin/messages_zh_CN.yml` (Simplified Chinese)

You can customize any message in these files or create new language files following the same format.

## 🔄 Configuration Version Management

The plugin uses version-based configuration management. When you update the plugin:
- Old configuration files are automatically backed up
- New configuration options are added automatically
- Your custom settings are preserved

## 🏗️ Building from Source

```bash
git clone https://github.com/addpromax/Expbook.git
cd Expbook
mvn clean package
```

The compiled jar will be in the `target/` directory.

## 📊 Technical Details

### Architecture
- **DatabaseManager** - HikariCP connection pool with automatic failover
- **CacheManager** - Caffeine-based multi-level caching
- **SchedulerAdapter** - Folia/Bukkit compatibility layer
- **ConfigManager** - Version-aware configuration system
- **LanguageManager** - I18n support with hot-reload

### Performance Optimizations
- Connection pooling (10 connections for MySQL, 5 for SQLite)
- Smart caching with TTL (Time To Live)
- Async database operations
- Indexed database queries
- Resource leak prevention with try-with-resources

### Security Features
- SQL injection protection (PreparedStatement)
- Item duplication prevention (database verification)
- Permission-based access control
- Operation cooldown (anti-spam)

## 📝 Changelog

See [CHANGELOG.md](CHANGELOG.md) for detailed version history.

## 💬 Support

For issues, questions, or suggestions, please open an issue on [GitHub](https://github.com/addpromax/Expbook/issues).

## 📄 License

This project is open source. Feel free to use and modify it for your server.

---

**Made with ❤️ by MagicBili**
