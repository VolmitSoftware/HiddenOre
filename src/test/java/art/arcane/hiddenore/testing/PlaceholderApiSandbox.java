package art.arcane.hiddenore.testing;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

public final class PlaceholderApiSandbox implements AutoCloseable {
  private static final String FAKE_BINARY_NAME = "me.clip.placeholderapi.PlaceholderAPI";
  private static final String FAKE_SOURCE = """
      package me.clip.placeholderapi;

      import java.util.ArrayList;
      import java.util.LinkedHashMap;
      import java.util.List;
      import java.util.Map;
      import org.bukkit.entity.Player;

      public final class PlaceholderAPI {
        public static final Map<String, String> ANSWERS = new LinkedHashMap<>();
        public static final List<Object> SEEN_PLAYERS = new ArrayList<>();
        public static final List<String> SEEN_TEXTS = new ArrayList<>();

        private PlaceholderAPI() {
        }

        public static String setPlaceholders(Player player, String text) {
          SEEN_PLAYERS.add(player);
          SEEN_TEXTS.add(text);
          String result = text;

          for (Map.Entry<String, String> answer : ANSWERS.entrySet()) {
            result = result.replace(answer.getKey(), answer.getValue());
          }

          return result;
        }
      }
      """;

  private final Path stubDirectory;
  private final URLClassLoader loader;
  private final Class<?> fake;
  private final List<Object> pinned = new ArrayList<>();

  private PlaceholderApiSandbox(Path stubDirectory, URLClassLoader loader, Class<?> fake) {
    this.stubDirectory = stubDirectory;
    this.loader = loader;
    this.fake = fake;
  }

  public static PlaceholderApiSandbox open() throws Exception {
    Path stubDirectory = Files.createTempDirectory("hiddenore-papi-sandbox");

    try {
      compileFake(stubDirectory);
      URL[] urls = isolatedClasspath(stubDirectory);
      URLClassLoader loader = new URLClassLoader(urls, ClassLoader.getPlatformClassLoader());

      try {
        PlaceholderApiSandbox sandbox = new PlaceholderApiSandbox(stubDirectory, loader,
            loader.loadClass(FAKE_BINARY_NAME));
        sandbox.installServer();
        return sandbox;
      } catch (Throwable failure) {
        loader.close();
        throw failure;
      }
    } catch (Throwable failure) {
      deleteRecursively(stubDirectory);
      throw failure;
    }
  }

  public Class<?> load(String binaryName) throws Exception {
    return loader.loadClass(binaryName);
  }

  @SuppressWarnings("unchecked")
  public void reset() throws Exception {
    ((Map<String, String>) fake.getField("ANSWERS").get(null)).clear();
    ((List<Object>) fake.getField("SEEN_PLAYERS").get(null)).clear();
    ((List<String>) fake.getField("SEEN_TEXTS").get(null)).clear();
    pinned.clear();
  }

  @SuppressWarnings("unchecked")
  public void answer(String placeholder, String value) throws Exception {
    ((Map<String, String>) fake.getField("ANSWERS").get(null)).put(placeholder, value);
  }

  @SuppressWarnings("unchecked")
  public List<String> textsHandedToPlaceholderApi() throws Exception {
    return List.copyOf((List<String>) fake.getField("SEEN_TEXTS").get(null));
  }

  @SuppressWarnings("unchecked")
  public List<Object> playersHandedToPlaceholderApi() throws Exception {
    return List.copyOf((List<Object>) fake.getField("SEEN_PLAYERS").get(null));
  }

  public Object player(UUID id, String name) throws Exception {
    Class<?> playerType = load("org.bukkit.entity.Player");
    Object player = Proxy.newProxyInstance(loader, new Class<?>[]{playerType},
        (proxy, method, args) -> switch (method.getName()) {
          case "getUniqueId" -> id;
          case "getName" -> name;
          case "hashCode" -> System.identityHashCode(proxy);
          case "equals" -> proxy == args[0];
          case "toString" -> "sandbox-player(" + name + ")";
          default -> throw new UnsupportedOperationException("reward command resolution touched " + method.getName());
        });
    pinned.add(player);
    return player;
  }

  public Object location(String worldName, int x, int y, int z) throws Exception {
    Class<?> worldType = load("org.bukkit.World");
    Object world = worldName == null ? null : Proxy.newProxyInstance(loader, new Class<?>[]{worldType},
        (proxy, method, args) -> switch (method.getName()) {
          case "getName" -> worldName;
          case "hashCode" -> System.identityHashCode(proxy);
          case "equals" -> proxy == args[0];
          case "toString" -> "sandbox-world(" + worldName + ")";
          default -> throw new UnsupportedOperationException("reward command resolution touched " + method.getName());
        });
    pinned.add(world);

    Class<?> locationType = load("org.bukkit.Location");
    Object location = locationType.getConstructor(worldType, double.class, double.class, double.class)
        .newInstance(world, (double) x, (double) y, (double) z);
    pinned.add(location);
    return location;
  }

  @Override
  public void close() throws Exception {
    pinned.clear();
    loader.close();
    deleteRecursively(stubDirectory);
  }

  private void installServer() throws Exception {
    Class<?> pluginManagerType = load("org.bukkit.plugin.PluginManager");
    Object pluginManager = Proxy.newProxyInstance(loader, new Class<?>[]{pluginManagerType},
        (proxy, method, args) -> switch (method.getName()) {
          case "isPluginEnabled" -> Boolean.TRUE;
          case "hashCode" -> System.identityHashCode(proxy);
          case "equals" -> proxy == args[0];
          case "toString" -> "sandbox-plugin-manager";
          default -> throw new UnsupportedOperationException("sandbox plugin manager touched " + method.getName());
        });
    pinned.add(pluginManager);

    Class<?> serverType = load("org.bukkit.Server");
    Object server = Proxy.newProxyInstance(loader, new Class<?>[]{serverType},
        (proxy, method, args) -> switch (method.getName()) {
          case "getPluginManager" -> pluginManager;
          case "hashCode" -> System.identityHashCode(proxy);
          case "equals" -> proxy == args[0];
          case "toString" -> "sandbox-server";
          default -> throw new UnsupportedOperationException("sandbox server touched " + method.getName());
        });
    pinned.add(server);

    Field field = load("org.bukkit.Bukkit").getDeclaredField("server");
    field.setAccessible(true);
    field.set(null, server);
  }

  private static void compileFake(Path stubDirectory) throws IOException {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

    if (compiler == null) {
      throw new IllegalStateException("a JDK java compiler is required to build the PlaceholderAPI sandbox");
    }

    JavaFileObject source = new SimpleJavaFileObject(URI.create("string:///me/clip/placeholderapi/PlaceholderAPI.java"),
        JavaFileObject.Kind.SOURCE) {
      @Override
      public CharSequence getCharContent(boolean ignoreEncodingErrors) {
        return FAKE_SOURCE;
      }
    };

    List<String> options = List.of("-classpath", System.getProperty("java.class.path"),
        "-d", stubDirectory.toString(), "-proc:none");

    try (StandardJavaFileManager files = compiler.getStandardFileManager(null, null, null)) {
      StringWriterDiagnostics diagnostics = new StringWriterDiagnostics();

      if (!compiler.getTask(diagnostics, files, null, options, null, List.of(source)).call()) {
        throw new IllegalStateException("failed to compile the PlaceholderAPI sandbox stub: " + diagnostics);
      }
    }
  }

  private static URL[] isolatedClasspath(Path stubDirectory) throws IOException {
    List<URL> urls = new ArrayList<>();
    urls.add(stubDirectory.toUri().toURL());

    for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
      if (!entry.isBlank()) {
        urls.add(Path.of(entry).toUri().toURL());
      }
    }

    return urls.toArray(new URL[0]);
  }

  private static void deleteRecursively(Path root) throws IOException {
    if (!Files.exists(root)) {
      return;
    }

    try (Stream<Path> paths = Files.walk(root)) {
      paths.sorted(Comparator.reverseOrder()).forEach(path -> {
        try {
          Files.deleteIfExists(path);
        } catch (IOException failure) {
          throw new UncheckedIOException(failure);
        }
      });
    }
  }

  private static final class StringWriterDiagnostics extends Writer {
    private final StringBuilder builder = new StringBuilder();

    @Override
    public void write(char[] buffer, int offset, int length) {
      builder.append(buffer, offset, length);
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
    }

    @Override
    public String toString() {
      return builder.toString();
    }
  }
}
