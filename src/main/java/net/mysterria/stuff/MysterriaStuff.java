package net.mysterria.stuff;

import net.mysterria.stuff.battlepass.ElytraBlocker;
import net.mysterria.stuff.battlepass.ElytraCommand;
import net.mysterria.stuff.fixes.HuskTownsLightning;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class MysterriaStuff extends JavaPlugin {

    private static MysterriaStuff instance;

    @Override
    public void onEnable() {
        instance = this;

        log("MysterriaStuff enabled!");
        log("Available thingies:");
        log(" - Reinforced Elytra Blocker");
        log("More to come soon...");

        if (getServer().getPluginCommand("mysterriastuff") != null) {
            getServer().getPluginCommand("mysterriastuff").setExecutor(new ElytraCommand());
        }

        getServer().getPluginManager().registerEvents(new ElytraBlocker(), this);
        getServer().getPluginManager().registerEvents(new HuskTownsLightning(), this);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    public static MysterriaStuff getInstance() {
        return instance;
    }

    public void log(String message) {
        ConsoleCommandSender console = getServer().getConsoleSender();
        var gradientPrefix = net.kyori.adventure.text.Component.text("[Stuff]", net.kyori.adventure.text.format.TextColor.color(0xFABCDE));
        var whiteText = net.kyori.adventure.text.Component.text(message, net.kyori.adventure.text.format.NamedTextColor.WHITE);
        console.sendMessage(gradientPrefix.append(net.kyori.adventure.text.Component.space()).append(whiteText));
    }

}
