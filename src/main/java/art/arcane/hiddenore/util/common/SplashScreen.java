package art.arcane.hiddenore.util.common;

import art.arcane.hiddenore.HiddenOre;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;

public final class SplashScreen {
    private SplashScreen() {
    }

    public static void print(HiddenOre plugin, boolean success, String errorMessage) {
        ChatColor dark = ChatColor.DARK_GRAY;
        ChatColor accent = ChatColor.GOLD;
        ChatColor meta = ChatColor.GRAY;
        ChatColor statusColor = success ? ChatColor.GREEN : ChatColor.RED;
        String status = success ? "READY" : "DEGRADED";

        String splash =
                "\n"
                        + dark + "██" + accent + "╗  " + dark + "██" + accent + "╗" + dark + "██" + accent + "╗" + dark + "██████" + accent + "╗ " + dark + "██████" + accent + "╗ " + dark + "███████" + accent + "╗" + dark + "███" + accent + "╗   " + dark + "██" + accent + "╗ " + dark + "██████" + accent + "╗ " + dark + "██████" + accent + "╗ " + dark + "███████" + accent + "╗\n"
                        + dark + "██" + accent + "║  " + dark + "██" + accent + "║" + dark + "██" + accent + "║" + dark + "██" + accent + "╔══" + dark + "██" + accent + "╗" + dark + "██" + accent + "╔══" + dark + "██" + accent + "╗" + dark + "██" + accent + "╔════╝" + dark + "████" + accent + "╗  " + dark + "██" + accent + "║" + dark + "██" + accent + "╔═══" + dark + "██" + accent + "╗" + dark + "██" + accent + "╔══" + dark + "██" + accent + "╗" + dark + "██" + accent + "╔════╝\n"
                        + dark + "███████" + accent + "║" + dark + "██" + accent + "║" + dark + "██" + accent + "║  " + dark + "██" + accent + "║" + dark + "██" + accent + "║  " + dark + "██" + accent + "║" + dark + "█████" + accent + "╗  " + dark + "██" + accent + "╔" + dark + "██" + accent + "╗ " + dark + "██" + accent + "║" + dark + "██" + accent + "║   " + dark + "██" + accent + "║" + dark + "██████" + accent + "╔╝" + dark + "█████" + accent + "╗\n"
                        + dark + "██" + accent + "╔══" + dark + "██" + accent + "║" + dark + "██" + accent + "║" + dark + "██" + accent + "║  " + dark + "██" + accent + "║" + dark + "██" + accent + "║  " + dark + "██" + accent + "║" + dark + "██" + accent + "╔══╝  " + dark + "██" + accent + "║╚" + dark + "██" + accent + "╗" + dark + "██" + accent + "║" + dark + "██" + accent + "║   " + dark + "██" + accent + "║" + dark + "██" + accent + "╔══" + dark + "██" + accent + "╗" + dark + "██" + accent + "╔══╝\n"
                        + dark + "██" + accent + "║  " + dark + "██" + accent + "║" + dark + "██" + accent + "║" + dark + "██████" + accent + "╔╝" + dark + "██████" + accent + "╔╝" + dark + "███████" + accent + "╗" + dark + "██" + accent + "║ ╚" + dark + "████" + accent + "║╚" + dark + "██████" + accent + "╔╝" + dark + "██" + accent + "║  " + dark + "██" + accent + "║" + dark + "███████" + accent + "╗" + meta + "   Version: " + accent + plugin.getDescription().getVersion() + "\n"
                        + accent + "╚═╝  ╚═╝╚═╝╚═════╝ ╚═════╝ ╚══════╝╚═╝  ╚═══╝ ╚═════╝ ╚═╝  ╚═╝╚══════╝" + meta + "   Java: " + accent + getJavaVersion() + "\n"
                        + meta + "   By: " + rainbowStudioName() + meta + " | Startup: " + statusColor + status + "\n";

        Bukkit.getConsoleSender().sendMessage(splash);
        if (!success && errorMessage != null && !errorMessage.isBlank()) {
            plugin.getLogger().warning("Startup error: " + errorMessage);
        }
    }

    private static int getJavaVersion() {
        String version = System.getProperty("java.version");
        if (version.startsWith("1.")) {
            version = version.substring(2, 3);
        } else {
            int dot = version.indexOf('.');
            if (dot != -1) {
                version = version.substring(0, dot);
            }
        }
        return Integer.parseInt(version);
    }

    private static String rainbowStudioName() {
        return ChatColor.RED + "A"
                + ChatColor.GOLD + "r"
                + ChatColor.YELLOW + "c"
                + ChatColor.GREEN + "a"
                + ChatColor.DARK_GRAY + "n"
                + ChatColor.AQUA + "e "
                + ChatColor.AQUA + "A"
                + ChatColor.BLUE + "r"
                + ChatColor.DARK_BLUE + "t"
                + ChatColor.DARK_PURPLE + "s"
                + ChatColor.DARK_AQUA + " (Volmit Software)";
    }
}
