package art.arcane.hiddenore.util.common;

import art.arcane.hiddenore.HiddenOre;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public final class SplashScreen {
  private SplashScreen() {
  }

  public static void print(HiddenOre plugin, boolean success, String errorMessage) {
    ChatColor dark = ChatColor.DARK_GRAY;
    ChatColor accent = ChatColor.GOLD;
    ChatColor meta = ChatColor.GRAY;
    ChatColor statusColor = success ? ChatColor.GREEN : ChatColor.RED;
    String status = success ? "READY" : "DEGRADED";
    String pluginVersion = plugin.getDescription().getVersion();
    String releaseTrain = getReleaseTrain(pluginVersion);
    String serverVersion = getServerVersion();
    String startupDate = getStartupDate();

    String splash =
        "\n"
            + dark + "██" + accent + "╗  " + dark + "██" + accent + "╗" + dark + "██" + accent + "╗" + dark + "██████" + accent + "╗ " + dark + "██████" + accent + "╗ " + dark + "███████" + accent + "╗" + dark + "███" + accent + "╗   " + dark + "██" + accent + "╗ " + dark + "██████" + accent + "╗ " + dark + "██████" + accent + "╗ " + dark + "███████" + accent + "╗\n"
            + dark + "██" + accent + "║  " + dark + "██" + accent + "║" + dark + "██" + accent + "║" + dark + "██" + accent + "╔══" + dark + "██" + accent + "╗" + dark + "██" + accent + "╔══" + dark + "██" + accent + "╗" + dark + "██" + accent + "╔════╝" + dark + "████" + accent + "╗  " + dark + "██" + accent + "║" + dark + "██" + accent + "╔═══" + dark + "██" + accent + "╗" + dark + "██" + accent + "╔══" + dark + "██" + accent + "╗" + dark + "██" + accent + "╔════╝" + accent + "   HiddenOre, " + ChatColor.YELLOW + "Mining Drop Control " + ChatColor.RED + "[" + releaseTrain + " RELEASE]\n"
            + dark + "███████" + accent + "║" + dark + "██" + accent + "║" + dark + "██" + accent + "║  " + dark + "██" + accent + "║" + dark + "██" + accent + "║  " + dark + "██" + accent + "║" + dark + "█████" + accent + "╗  " + dark + "██" + accent + "╔" + dark + "██" + accent + "╗ " + dark + "██" + accent + "║" + dark + "██" + accent + "║   " + dark + "██" + accent + "║" + dark + "██████" + accent + "╔╝" + dark + "█████" + accent + "╗" + meta + "   Version: " + accent + pluginVersion + "\n"
            + dark + "██" + accent + "╔══" + dark + "██" + accent + "║" + dark + "██" + accent + "║" + dark + "██" + accent + "║  " + dark + "██" + accent + "║" + dark + "██" + accent + "║  " + dark + "██" + accent + "║" + dark + "██" + accent + "╔══╝  " + dark + "██" + accent + "║╚" + dark + "██" + accent + "╗" + dark + "██" + accent + "║" + dark + "██" + accent + "║   " + dark + "██" + accent + "║" + dark + "██" + accent + "╔══" + dark + "██" + accent + "╗" + dark + "██" + accent + "╔══╝" + meta + "   By: " + rainbowStudioName() + meta + " | Startup: " + statusColor + status + "\n"
            + dark + "██" + accent + "║  " + dark + "██" + accent + "║" + dark + "██" + accent + "║" + dark + "██████" + accent + "╔╝" + dark + "██████" + accent + "╔╝" + dark + "███████" + accent + "╗" + dark + "██" + accent + "║ ╚" + dark + "████" + accent + "║╚" + dark + "██████" + accent + "╔╝" + dark + "██" + accent + "║  " + dark + "██" + accent + "║" + dark + "███████" + accent + "╗" + meta + "   Server: " + accent + serverVersion + "\n"
            + accent + "╚═╝  ╚═╝╚═╝╚═════╝ ╚═════╝ ╚══════╝╚═╝  ╚═══╝ ╚═════╝ ╚═╝  ╚═╝╚══════╝" + meta + "   Java: " + accent + getJavaVersion() + meta + " | Date: " + accent + startupDate + "\n";

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

  private static String getServerVersion() {
    String version = Bukkit.getVersion();
    int mcMarkerIndex = version.indexOf(" (MC:");
    if (mcMarkerIndex != -1) {
      version = version.substring(0, mcMarkerIndex);
    }
    return version;
  }

  private static String getStartupDate() {
    return LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
  }

  private static String getReleaseTrain(String version) {
    String value = version;
    int suffixIndex = value.indexOf('-');
    if (suffixIndex >= 0) {
      value = value.substring(0, suffixIndex);
    }
    String[] split = value.split("\\.");
    if (split.length >= 2) {
      return split[0] + "." + split[1];
    }
    return value;
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
