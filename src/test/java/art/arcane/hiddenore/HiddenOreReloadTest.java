package art.arcane.hiddenore;

import art.arcane.volmlib.integration.ReloadAware.PreUnloadReason;
import art.arcane.volmlib.util.config.BukkitConfigEditor;
import art.arcane.volmlib.util.inventorygui.BukkitInventoryShutdown;
import art.arcane.volmlib.util.localization.BukkitLanguageSwitcher;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginLogger;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class HiddenOreReloadTest {
  private final Deque<Runnable> globalTasks = new ArrayDeque<>();
  private CompletableFuture<Void> configCleanup;
  private CompletableFuture<Void> languageCleanup;
  private HiddenOre plugin;
  private Set<UUID> debugPlayers;
  private Object previousServer;
  private boolean globalContext;
  private boolean rejectTasks;

  @Before
  public void setUp() throws Exception {
    Logger logger = Logger.getAnonymousLogger();
    logger.setLevel(Level.OFF);
    GlobalRegionScheduler global = proxy(GlobalRegionScheduler.class, (proxy, method, args) -> switch (method.getName()) {
      case "execute" -> {
        if (rejectTasks) {
          throw new UnsupportedOperationException("scheduler stopped");
        }
        globalTasks.add((Runnable) args[1]);
        yield null;
      }
      case "run" -> throw new UnsupportedOperationException("scheduler stopped");
      default -> null;
    });
    BukkitScheduler bukkit = proxy(BukkitScheduler.class, (proxy, method, args) -> {
      throw new UnsupportedOperationException("Bukkit fallback is unavailable");
    });
    Server server = proxy(Server.class, (proxy, method, args) -> switch (method.getName()) {
      case "getGlobalRegionScheduler" -> global;
      case "getScheduler" -> bukkit;
      case "getLogger" -> logger;
      case "isPrimaryThread", "isGlobalTickThread" -> globalContext;
      default -> null;
    });
    previousServer = field(Bukkit.class, "server").get(null);
    field(Bukkit.class, "server").set(null, server);
    plugin = (HiddenOre) allocate(HiddenOre.class);
    setField(plugin, "server", server);
    setField(plugin, "description", new PluginDescriptionFile("HiddenOre", "test", "art.arcane.hiddenore.HiddenOre"));
    setField(plugin, "logger", new PluginLogger(plugin));
    setField(plugin, "isEnabled", true);
    debugPlayers = new HashSet<>(Set.of(UUID.randomUUID())) {
      @Override
      public void clear() {
        assertTrue("Runtime drain must run in the global lifecycle context", globalContext);
        super.clear();
      }
    };
    setField(plugin, "debugPlayers", debugPlayers);
    installEditors();
  }

  @After
  public void tearDown() throws Exception {
    field(Bukkit.class, "server").set(null, previousServer);
  }

  @Test
  public void waitsForBothEditorsAndGlobalDrainBeforeCompleting() {
    CompletableFuture<Void> completion = reload();
    configCleanup.complete(null);
    assertFalse(completion.isDone());
    assertTrue(globalTasks.isEmpty());
    assertFalse(plugin.isDraining());

    languageCleanup.complete(null);
    assertFalse(completion.isDone());
    assertEquals(1, globalTasks.size());
    assertFalse(plugin.isDraining());
    assertEquals(1, debugPlayers.size());

    runGlobalTask();
    completion.join();
    assertTrue(plugin.isDraining());
    assertTrue(debugPlayers.isEmpty());
  }

  @Test
  public void languageCompletionAloneCannotDrain() {
    CompletableFuture<Void> completion = reload();
    languageCleanup.complete(null);
    assertFalse(completion.isDone());
    assertTrue(globalTasks.isEmpty());

    configCleanup.complete(null);
    runGlobalTask();
    completion.join();
  }

  @Test
  public void failuresPreserveRuntimeAndAllowCleanupRetry() throws Exception {
    IllegalStateException failure = new IllegalStateException("owner scheduler rejected cleanup");
    CompletableFuture<Void> completion = reload();
    configCleanup.completeExceptionally(failure);
    languageCleanup.complete(null);
    assertSame(failure, assertThrows(CompletionException.class, completion::join).getCause());
    assertFalse(plugin.isDraining());
    assertTrue(globalTasks.isEmpty());
    assertEquals(1, debugPlayers.size());

    installEditors();
    CompletableFuture<Void> retry = reload();
    configCleanup.complete(null);
    languageCleanup.complete(null);
    runGlobalTask();
    retry.join();
    assertTrue(debugPlayers.isEmpty());
  }

  @Test
  public void languageFailureAlsoPreventsDrain() {
    CompletableFuture<Void> completion = reload();
    configCleanup.complete(null);
    languageCleanup.completeExceptionally(new IllegalStateException("language inventory close failed"));
    assertThrows(CompletionException.class, completion::join);
    assertFalse(plugin.isDraining());
    assertTrue(globalTasks.isEmpty());
  }

  @Test
  public void repeatedReloadsShareCleanupWithoutCallerCancellation() {
    CompletableFuture<Void> first = reload();
    CompletableFuture<Void> second = reload();
    first.cancel(false);
    configCleanup.complete(null);
    languageCleanup.complete(null);
    assertEquals(1, globalTasks.size());
    runGlobalTask();
    second.join();
    reload().join();
    assertTrue(globalTasks.isEmpty());
  }

  @Test
  public void rejectedGlobalTaskFailsWithoutDraining() {
    rejectTasks = true;
    CompletableFuture<Void> completion = reload();
    configCleanup.complete(null);
    languageCleanup.complete(null);
    assertThrows(CompletionException.class, completion::join);
    assertFalse(plugin.isDraining());
    assertEquals(1, debugPlayers.size());
  }

  @Test
  public void directDisableTerminatesPendingReloadAndLateCallbacks() throws Exception {
    CompletableFuture<Void> completion = reload();
    disable();
    assertThrows(CompletionException.class, completion::join);
    assertTrue(plugin.isDraining());
    configCleanup.complete(null);
    languageCleanup.complete(null);
    assertTrue(globalTasks.isEmpty());
    assertThrows(CompletionException.class, () -> reload().join());
  }

  @Test
  public void directDisableInvalidatesAnAlreadyQueuedDrain() throws Exception {
    CompletableFuture<Void> completion = reload();
    configCleanup.complete(null);
    languageCleanup.complete(null);
    disable();
    debugPlayers.add(UUID.randomUUID());
    runGlobalTask();
    assertThrows(CompletionException.class, completion::join);
    assertEquals(1, debugPlayers.size());
  }

  private CompletableFuture<Void> reload() {
    return plugin.commitReload(PreUnloadReason.HOT_RELOAD).toCompletableFuture();
  }

  private void disable() throws Exception {
    setField(plugin, "isEnabled", false);
    globalContext = true;
    try {
      plugin.onDisable();
    } finally {
      globalContext = false;
    }
  }

  private void runGlobalTask() {
    globalContext = true;
    try {
      globalTasks.remove().run();
    } finally {
      globalContext = false;
    }
  }

  private void installEditors() throws Exception {
    configCleanup = new CompletableFuture<>();
    languageCleanup = new CompletableFuture<>();
    BukkitConfigEditor config = (BukkitConfigEditor) allocate(BukkitConfigEditor.class);
    prepareEditor(config, configCleanup);
    setField(plugin, "configEditor", config);
    Object language = allocate(Class.forName("art.arcane.volmlib.util.localization.BukkitLanguageEditor"));
    prepareEditor(language, languageCleanup);
    BukkitLanguageSwitcher switcher = (BukkitLanguageSwitcher) allocate(BukkitLanguageSwitcher.class);
    setField(switcher, "editor", language);
    setField(switcher, "closed", true);
    setField(plugin, "languageSwitcher", switcher);
  }

  private void prepareEditor(Object editor, CompletableFuture<Void> completion) throws Exception {
    setField(editor, "plugin", plugin);
    setField(editor, "inventoryShutdown", new BukkitInventoryShutdown(plugin, List.of()));
    setField(editor, "shutdownCompletion", completion);
    setField(editor, "shutdownFailureObserved", true);
  }

  private static Object allocate(Class<?> type) throws Exception {
    sun.reflect.ReflectionFactory factory = sun.reflect.ReflectionFactory.getReflectionFactory();
    Constructor<?> constructor = factory.newConstructorForSerialization(type, Object.class.getDeclaredConstructor());
    return constructor.newInstance();
  }

  private static void setField(Object target, String name, Object value) throws Exception {
    field(target.getClass(), name).set(target, value);
  }

  private static Field field(Class<?> type, String name) throws Exception {
    for (Class<?> current = type; current != null; current = current.getSuperclass()) {
      try {
        Field field = current.getDeclaredField(name);
        field.setAccessible(true);
        return field;
      } catch (NoSuchFieldException ignored) {
      }
    }
    throw new NoSuchFieldException(name);
  }

  private static <T> T proxy(Class<T> type, InvocationHandler handler) {
    return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
  }
}
