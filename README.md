# ExpBook Plugin

A Minecraft plugin that allows players to store and withdraw experience points using special books.

## Features

- 📚 **Multiple Book Types** - Common, Advanced, and Shared experience books with different capacities
- 🔒 **Player Binding** - Books can be bound to specific players to prevent theft
- 💾 **Database Storage** - MySQL database integration with anti-cheat verification
- ⚡ **Folia Compatible** - Full support for Folia's regionized threading system
- 🎨 **Custom Models** - Support for custom model data for resource packs
- 🌐 **Multi-Language Support** - English and Simplified Chinese (easily extensible)
- � **Auto Config Update** - Version-based configuration management with automatic updates

## Requirements

- **Minecraft**: 1.20.1+
- **Server**: Folia / Paper / Spigot / Bukkit
- **Java**: 17+
- **Database**: MySQL 5.7+ or MariaDB 10.2+

## Installation

1. Download `ExpBookPlugin-1.0.0.jar`
2. Place the jar file in your server's `plugins` folder
3. Start the server to generate configuration files
4. Edit `plugins/ExpBookPlugin/config.yml` to configure database connection and language
5. Run `/expbook reload` to apply changes

## Configuration

### Language Settings

Edit `config.yml` to set your preferred language:

```yaml
# Supported languages: en (English), zh_CN (Simplified Chinese)
language: en
```

### Database Configuration

```yaml
database:
  host: localhost
  port: 3306
  database: minecraft
  username: root
  password: ""
  table_prefix: expbook_
```

### Default Book Types

| Book Type | Capacity | Material | Bound | Permission |
|-----------|----------|----------|-------|------------|
| Common Book | 1000 XP | Paper | Yes | `expbook.common` |
| Advanced Book | 5000 XP | Book | Yes | `expbook.advanced` |
| Shared Book | 2000 XP | Enchanted Book | No | `expbook.shared` |

You can customize book names, lore, and properties in `config.yml`.

## Commands

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

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `expbook.admin` | Access to all admin commands | OP |
| `expbook.give` | Give experience books to players | OP |
| `expbook.reload` | Reload plugin configuration | OP |
| `expbook.common` | Use common experience books | Everyone |
| `expbook.advanced` | Use advanced experience books | Everyone |
| `expbook.shared` | Use shared experience books | Everyone |

## Usage

**For Players:**
- **Store XP**: Hold the book and `Sneak + Right Click`
- **Withdraw All XP**: Hold the book and `Right Click`

## Multi-Language Support

The plugin supports multiple languages. Language files are located in:
- `plugins/ExpBookPlugin/messages_en.yml` (English)
- `plugins/ExpBookPlugin/messages_zh_CN.yml` (Simplified Chinese)

You can customize any message in these files or create new language files following the same format.

## Configuration Version Management

The plugin uses version-based configuration management. When you update the plugin:
- Old configuration files are automatically backed up
- New configuration options are added automatically
- Your custom settings are preserved

## Building from Source

```bash
git clone https://github.com/addpromax/Expbook.git
cd Expbook
mvn clean package
```

The compiled jar will be in the `target/` directory.

## Support

For issues, questions, or suggestions, please open an issue on [GitHub](https://github.com/addpromax/Expbook/issues).

## License

This project is open source. Feel free to use and modify it for your server.
