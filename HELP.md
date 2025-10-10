# MysterriaStuff - Feature Documentation

## Overview
MysterriaStuff is an enhanced Minecraft plugin providing utilities, fixes, and runtime features for the Mysterria server.

## Features

### 🎨 Enhanced Logging System
- **Beautiful gradient console output** with rainbow-colored prefix
- **Multiple log levels**: INFO, WARN, ERROR, DEBUG, SUCCESS
- **Debug mode** for detailed troubleshooting
- **Formatted headers** for startup/shutdown

### 🎮 Command System
All commands support tab completion!

#### `/mystuff help`
- Shows comprehensive help menu with all available commands

#### `/mystuff info` or `/mystuff status`
- Display plugin version
- Show debug mode status
- List all loaded features

#### `/mystuff debug`
- Toggle debug mode on/off
- See detailed debug messages when enabled
- **Permission**: `mysterriastuff.debug`

#### `/mystuff reload`
- Reload plugin configuration
- **Permission**: `mysterriastuff.reload`

#### `/mystuff give <item> <player>`
- Give special items to players
- Available items:
  - `elytra` - Reinforced Elytra
- **Permission**: `mysterriastuff.give`

#### `/mystuff export`
- Export held item as Base64 bytes
- Click to copy to clipboard
- Useful for creating static items
- **Permission**: `mysterriastuff.export`

#### `/mystuff recipe <list|reload>`
- `list` - Show all custom recipes
- `reload` - Reload recipe manager
- **Permission**: `mysterriastuff.recipe`

### 🛡️ Game Features

#### Reinforced Elytra Blocker
- Prevents reinforced elytras from being used inappropriately
- Event-driven protection system

#### Lightning Strike Fix (HuskTowns)
- Fixes cancelled Lightning Strike events via Listener mixin
- Ensures proper HuskTowns compatibility

#### CoI Dangerous Actions Listener
- Comprehensive protection for CircleOfImagination items
- Prevents:
  - Item pickup in nightmare worlds
  - Mystical item duplication
  - Unauthorized crafting with special items
  - Item frame placement of mystical items
  - Bundle storage of mystical items
  - And much more!
- Attribute reset system on player join
- Spectator movement restrictions

### 🔧 Runtime Recipe Manager
- Create custom recipes at runtime
- Add/remove recipes without restart
- Example recipes included:
  - Coal Blocks → Diamond (9 coal blocks in crafting grid)
- Supports both shaped and shapeless recipes
- Recipe persistence across reloads

## Permissions

### Permission Nodes
- `mysterriastuff.*` - All permissions (default: op)
- `mysterriastuff.use` - Basic command access (default: true)
- `mysterriastuff.reload` - Reload permission (default: op)
- `mysterriastuff.debug` - Debug toggle (default: op)
- `mysterriastuff.give` - Give items (default: op)
- `mysterriastuff.export` - Export items (default: op)
- `mysterriastuff.recipe` - Recipe management (default: op)

## Technical Details

### Namespace Management
- **MysterriaStuff namespace**: Used for plugin-specific data
- **CircleOfImagination namespace**: Properly references CoI items
- Dual namespace support in `AdventureUtil`:
  - `getNamespacedKey()` - MysterriaStuff namespace
  - `getCoINamespacedKey()` - CircleOfImagination namespace

### Logger Features
- Gradient text support
- Color-coded log levels
- Debug message filtering
- Formatted startup/shutdown headers
- Feature loading indicators

### API
Access the plugin instance:
```java
MysterriaStuff plugin = MysterriaStuff.getInstance();
```

Access the recipe manager:
```java
RecipeManager manager = plugin.getRecipeManager();
```

Use the logger:
```java
Logger.info("Information message");
Logger.warn("Warning message");
Logger.error("Error message");
Logger.debug("Debug message"); // Only shows when debug mode is on
Logger.success("Success message");
Logger.feature("Feature name"); // Special formatting for features
Logger.header("Header text"); // Create formatted headers
```

## Compatibility
- **Minecraft Version**: 1.21.8
- **API Version**: 1.21
- **Dependencies**:
  - Paper API
  - CircleOfImagination (optional, for CoI features)
  - HuskTowns (optional, for lightning fix)

## Building
```bash
./gradlew build
```

Built JAR will be in `build/libs/`

## Installation
1. Download the JAR file
2. Place in your server's `plugins/` folder
3. Restart the server
4. Use `/mystuff help` to see available commands

## Support
For issues or feature requests, contact the development team or check the repository.
