package art.arcane.hiddenore.testing;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.tag.Tag;
import io.papermc.paper.registry.tag.TagKey;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.block.BlockType;
import org.bukkit.inventory.ItemType;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class TestRegistryAccess implements RegistryAccess {
  @Override
  @SuppressWarnings("removal")
  public <T extends Keyed> Registry<T> getRegistry(Class<T> type) {
    return new TestRegistry<>(RegistryKind.EMPTY);
  }

  @Override
  public <T extends Keyed> Registry<T> getRegistry(RegistryKey<T> registryKey) {
    if (registryKey == RegistryKey.BLOCK) {
      return new TestRegistry<>(RegistryKind.BLOCK);
    }
    if (registryKey == RegistryKey.ITEM) {
      return new TestRegistry<>(RegistryKind.ITEM);
    }
    if (registryKey == RegistryKey.SOUND_EVENT) {
      return new TestRegistry<>(RegistryKind.SOUND);
    }
    return new TestRegistry<>(RegistryKind.EMPTY);
  }

  private static Object registryValue(RegistryKind kind, NamespacedKey key) {
    if (kind == RegistryKind.BLOCK && hasPublicField(BlockType.class, key)) {
      return proxy(BlockType.Typed.class, key);
    }
    if (kind == RegistryKind.ITEM && hasPublicField(ItemType.class, key)) {
      return proxy(ItemType.Typed.class, key);
    }
    if (kind == RegistryKind.SOUND && "minecraft".equals(key.getNamespace())
        && (key.getKey().contains(".") || "intentionally_empty".equals(key.getKey()))) {
      return proxy(Sound.class, key);
    }
    return null;
  }

  private static boolean hasPublicField(Class<?> type, NamespacedKey key) {
    try {
      type.getField(key.getKey().toUpperCase(Locale.ROOT));
      return true;
    } catch (NoSuchFieldException exception) {
      return false;
    }
  }

  private static Object proxy(Class<?> type, NamespacedKey key) {
    InvocationHandler handler = (Object proxy, Method method, Object[] arguments) -> switch (method.getName()) {
      case "getKey" -> key;
      case "hashCode" -> key.hashCode();
      case "toString" -> key.toString();
      case "equals" -> proxy == arguments[0];
      default -> defaultValue(method.getReturnType());
    };
    return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
  }

  private static Object defaultValue(Class<?> type) {
    if (!type.isPrimitive()) {
      return null;
    }
    if (type == boolean.class) {
      return false;
    }
    if (type == char.class) {
      return '\0';
    }
    if (type == byte.class) {
      return (byte) 0;
    }
    if (type == short.class) {
      return (short) 0;
    }
    if (type == int.class) {
      return 0;
    }
    if (type == long.class) {
      return 0L;
    }
    if (type == float.class) {
      return 0.0f;
    }
    if (type == double.class) {
      return 0.0;
    }
    return null;
  }

  private enum RegistryKind {BLOCK, ITEM, SOUND, EMPTY}

  private static final class TestRegistry<T extends Keyed> implements Registry<T> {
    private final RegistryKind kind;

    private TestRegistry(RegistryKind kind) {
      this.kind = kind;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T get(NamespacedKey key) {
      return (T) registryValue(kind, key);
    }

    @Override
    public NamespacedKey getKey(T value) {
      return value.getKey();
    }

    @Override
    public boolean hasTag(TagKey<T> key) {
      return false;
    }

    @Override
    public Tag<T> getTag(TagKey<T> key) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Collection<Tag<T>> getTags() {
      return List.of();
    }

    @Override
    public Iterator<T> iterator() {
      return List.<T>of().iterator();
    }

    @Override
    public Stream<T> stream() {
      return Stream.empty();
    }

    @Override
    public Stream<NamespacedKey> keyStream() {
      return Stream.empty();
    }

    @Override
    public int size() {
      return 0;
    }
  }
}
