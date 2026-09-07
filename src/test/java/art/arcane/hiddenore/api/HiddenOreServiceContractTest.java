package art.arcane.hiddenore.api;

import art.arcane.hiddenore.api.event.HiddenOreBreakEvent;
import art.arcane.hiddenore.api.event.HiddenOreDropsEvent;
import art.arcane.volmlib.util.bukkit.ChunkPositionSet;
import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HiddenOreServiceContractTest {
  private static final List<Class<?>> CONTRACT_TYPES = List.of(
      HiddenOreService.class,
      ChunkProvenance.class,
      BlockOrigin.class,
      HiddenVein.class,
      HiddenOreBreakEvent.class,
      HiddenOreDropsEvent.class
  );

  @Test
  public void contractTypes_neverReferenceShadedRelocatedOrInternalTypes() {
    assertEquals(List.of(), inspect(CONTRACT_TYPES));
  }

  @Test
  public void contractInspection_catchesAGenericArgumentAndAConstructorParameter() {
    List<String> violations = inspect(List.of(LeakySample.class));

    assertEquals(3, violations.size());
    assertTrue(violations.toString(), violations.stream()
        .anyMatch(violation -> violation.contains("leakyReturn return type argument")));
    assertTrue(violations.toString(), violations.stream()
        .anyMatch(violation -> violation.contains("leakyParameter parameter type argument")));
    assertTrue(violations.toString(), violations.stream()
        .anyMatch(violation -> violation.contains("constructor parameter")));
  }

  @Test
  public void nearbyLimits_liveOnTheServiceInterfaceOnly() throws Exception {
    assertEquals(128, HiddenOreService.MAX_NEARBY_RADIUS);
    assertEquals(4096, HiddenOreService.MAX_NEARBY_RESULTS);

    for (Field field : HiddenOreAPI.class.getDeclaredFields()) {
      assertTrue(field.getName() + " must not shadow the interface constant",
          !"MAX_NEARBY_RADIUS".equals(field.getName()) && !"MAX_NEARBY_RESULTS".equals(field.getName()));
    }

    assertEquals(HiddenOreService.class, HiddenOreAPI.class.getField("MAX_NEARBY_RADIUS").getDeclaringClass());
    assertEquals(HiddenOreService.class, HiddenOreAPI.class.getField("MAX_NEARBY_RESULTS").getDeclaringClass());
  }

  @Test
  public void serviceSurface_exposesProvenanceAndRegionOwnership() throws Exception {
    assertEquals(BlockOrigin.class, HiddenOreService.class.getMethod("originOf", org.bukkit.block.Block.class).getReturnType());
    assertEquals(ChunkProvenance.class, HiddenOreService.class.getMethod("provenanceOf", org.bukkit.Chunk.class).getReturnType());
    assertEquals(boolean.class, HiddenOreService.class.getMethod("isVeinConsumed", org.bukkit.block.Block.class).getReturnType());
    assertEquals(boolean.class,
        HiddenOreService.class.getMethod("ownsRegion", org.bukkit.World.class, int.class, int.class).getReturnType());
  }

  @Test
  public void blockOrigin_keepsAllThreeStatesDistinctAndNamesThePresumption() {
    assertEquals(Set.of("PRESUMED_GENERATED", "PLAYER_PLACED", "UNTRACKED"),
        Arrays.stream(BlockOrigin.values()).map(Enum::name).collect(Collectors.toSet()));
  }

  private static List<String> inspect(List<Class<?>> types) {
    List<String> violations = new ArrayList<>();
    for (Class<?> type : types) {
      for (Method method : type.getDeclaredMethods()) {
        if (!Modifier.isPublic(method.getModifiers())) {
          continue;
        }
        checkGenericType(type, method.getName() + " return", method.getGenericReturnType(), violations);
        for (Type parameter : method.getGenericParameterTypes()) {
          checkGenericType(type, method.getName() + " parameter", parameter, violations);
        }
      }
      for (Constructor<?> constructor : type.getDeclaredConstructors()) {
        if (!Modifier.isPublic(constructor.getModifiers())) {
          continue;
        }
        for (Type parameter : constructor.getGenericParameterTypes()) {
          checkGenericType(type, "constructor parameter", parameter, violations);
        }
      }
      for (Field field : type.getDeclaredFields()) {
        if (!Modifier.isPublic(field.getModifiers())) {
          continue;
        }
        checkGenericType(type, "field " + field.getName(), field.getGenericType(), violations);
      }
    }
    return violations;
  }

  private static void checkGenericType(Class<?> owner, String what, Type type, List<String> violations) {
    if (type instanceof Class<?> raw) {
      checkType(owner, what, raw, violations);
      return;
    }
    if (type instanceof ParameterizedType parameterized) {
      checkGenericType(owner, what, parameterized.getRawType(), violations);
      for (Type argument : parameterized.getActualTypeArguments()) {
        checkGenericType(owner, what + " type argument", argument, violations);
      }
      return;
    }
    if (type instanceof GenericArrayType arrayType) {
      checkGenericType(owner, what, arrayType.getGenericComponentType(), violations);
      return;
    }
    if (type instanceof WildcardType wildcard) {
      for (Type bound : wildcard.getUpperBounds()) {
        checkGenericType(owner, what + " bound", bound, violations);
      }
      for (Type bound : wildcard.getLowerBounds()) {
        checkGenericType(owner, what + " bound", bound, violations);
      }
      return;
    }
    if (type instanceof TypeVariable<?> variable) {
      for (Type bound : variable.getBounds()) {
        checkGenericType(owner, what + " bound", bound, violations);
      }
    }
  }

  public static final class LeakySample {
    public LeakySample(ChunkPositionSet leaked) {
    }

    public List<ChunkPositionSet> leakyReturn() {
      return List.of();
    }

    public void leakyParameter(List<ChunkPositionSet> leaked) {
    }
  }

  private static void checkType(Class<?> owner, String what, Class<?> type, List<String> violations) {
    Class<?> component = type;
    while (component.isArray()) {
      component = component.getComponentType();
    }
    if (component.isPrimitive()) {
      return;
    }
    String name = component.getName();
    if (name.startsWith("java.") || name.startsWith("org.bukkit.") || name.startsWith("art.arcane.hiddenore.api.")) {
      return;
    }
    violations.add(owner.getName() + "#" + what + " -> " + name);
  }
}
