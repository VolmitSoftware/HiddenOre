package art.arcane.hiddenore.util.common;

import art.arcane.hiddenore.HiddenOre;
import art.arcane.volmlib.util.plugin.ComponentLog;
import art.arcane.volmlib.util.plugin.SplashScreenSupport;
import org.bukkit.Bukkit;
import net.md_5.bungee.api.ChatColor;

import java.util.logging.Level;

public final class SplashScreen {
  private static final String LOG_DISCRIMINATOR = ComponentLog.discriminator("HiddenOre", "&6");

  private SplashScreen() {
  }

  public static void print(HiddenOre plugin, boolean success) {
    ChatColor dark = ChatColor.DARK_GRAY;
    ChatColor accent = ChatColor.GOLD;
    ChatColor meta = ChatColor.GRAY;
    ChatColor statusColor = success ? ChatColor.GREEN : ChatColor.RED;
    String status = success ? "READY" : "DEGRADED";
    String pluginVersion = plugin.getDescription().getVersion();
    String releaseTrain = SplashScreenSupport.releaseTrain(pluginVersion);
    String serverVersion = SplashScreenSupport.serverVersionWithoutMcSuffix(Bukkit.getVersion());
    String startupDate = SplashScreenSupport.startupDate();
    String supportedMcVersion = "26.1.2 - 26.3";

    String splash =
        "\n"
            + dark + "██" + accent + "╗  " + dark + "██" + accent + "╗" + dark + "██" + accent + "╗" + dark + "██████" + accent + "╗ " + dark + "██████" + accent + "╗ " + dark + "███████" + accent + "╗" + dark + "███" + accent + "╗   " + dark + "██" + accent + "╗ " + dark + "██████" + accent + "╗ " + dark + "██████" + accent + "╗ " + dark + "███████" + accent + "╗\n"
            + dark + "██" + accent + "║  " + dark + "██" + accent + "║" + dark + "██" + accent + "║" + dark + "██" + accent + "╔══" + dark + "██" + accent + "╗" + dark + "██" + accent + "╔══" + dark + "██" + accent + "╗" + dark + "██" + accent + "╔════╝" + dark + "████" + accent + "╗  " + dark + "██" + accent + "║" + dark + "██" + accent + "╔═══" + dark + "██" + accent + "╗" + dark + "██" + accent + "╔══" + dark + "██" + accent + "╗" + dark + "██" + accent + "╔════╝" + accent + "   HiddenOre, " + ChatColor.YELLOW + "Mining Drop Control " + ChatColor.RED + "[" + releaseTrain + " RELEASE]\n"
            + dark + "███████" + accent + "║" + dark + "██" + accent + "║" + dark + "██" + accent + "║  " + dark + "██" + accent + "║" + dark + "██" + accent + "║  " + dark + "██" + accent + "║" + dark + "█████" + accent + "╗  " + dark + "██" + accent + "╔" + dark + "██" + accent + "╗ " + dark + "██" + accent + "║" + dark + "██" + accent + "║   " + dark + "██" + accent + "║" + dark + "██████" + accent + "╔╝" + dark + "█████" + accent + "╗" + meta + "   Version: " + accent + pluginVersion + "\n"
            + dark + "██" + accent + "╔══" + dark + "██" + accent + "║" + dark + "██" + accent + "║" + dark + "██" + accent + "║  " + dark + "██" + accent + "║" + dark + "██" + accent + "║  " + dark + "██" + accent + "║" + dark + "██" + accent + "╔══╝  " + dark + "██" + accent + "║╚" + dark + "██" + accent + "╗" + dark + "██" + accent + "║" + dark + "██" + accent + "║   " + dark + "██" + accent + "║" + dark + "██" + accent + "╔══" + dark + "██" + accent + "╗" + dark + "██" + accent + "╔══╝" + meta + "   By: " + rainbowStudioName() + meta + " | " + accent + "VolmitSoftware.com" + meta + " | Startup: " + statusColor + status + "\n"
            + dark + "██" + accent + "║  " + dark + "██" + accent + "║" + dark + "██" + accent + "║" + dark + "██████" + accent + "╔╝" + dark + "██████" + accent + "╔╝" + dark + "███████" + accent + "╗" + dark + "██" + accent + "║ ╚" + dark + "████" + accent + "║╚" + dark + "██████" + accent + "╔╝" + dark + "██" + accent + "║  " + dark + "██" + accent + "║" + dark + "███████" + accent + "╗" + meta + "   Server: " + accent + serverVersion + meta + " | MC Support: " + accent + supportedMcVersion + "\n"
            + accent + "╚═╝  ╚═╝╚═╝╚═════╝ ╚═════╝ ╚══════╝╚═╝  ╚═══╝ ╚═════╝ ╚═╝  ╚═╝╚══════╝" + meta + "   Java: " + accent + SplashScreenSupport.javaMajorVersion() + meta + " | Date: " + accent + startupDate + "\n";

    ComponentLog.logLegacy(plugin, plugin.getLogger(), LOG_DISCRIMINATOR, Level.INFO, splash, null);
  }

  private static String rainbowStudioName() {
    return ChatColor.DARK_AQUA + "Volmit Software (Arcane Arts)";
  }
}
