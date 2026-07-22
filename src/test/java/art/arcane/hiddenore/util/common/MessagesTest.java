package art.arcane.hiddenore.util.common;

import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.DirectorTextResolver;
import art.arcane.volmlib.util.director.compat.DirectorEngineFactory;
import art.arcane.volmlib.util.director.help.DirectorHelpMessages;
import art.arcane.volmlib.util.director.help.DirectorMiniMenu;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeEngine;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.localization.LocalizationReloadResult;
import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.VolmitLocales;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class MessagesTest {
  @Test
  public void englishDefaultsLiveInTheTypedJavaCatalog() {
    Messages messages = new Messages();

    assertEquals("<green>[HiddenOre]</green> ", Messages.PREFIX.english());
    assertTrue(Messages.NO_PERMISSION.english().contains("You do not have permission"));
    assertEquals("[HiddenOre] You do not have permission to use this command.", text(messages.component(Messages.NO_PERMISSION)));
    assertEquals(3, messages.components(Messages.USAGE).size());
    assertThrows(UnsupportedOperationException.class, () -> messages.components(Messages.USAGE).clear());
  }

  @Test
  public void everyBundledLocaleFullyCoversTheTypedCatalog() {
    Messages messages = new Messages();
    for (String locale : VolmitLocales.nonEnglish()) {
      YamlConfiguration language = new YamlConfiguration();
      language.set("locale", locale);

      LocalizationReloadResult result = messages.reload(language, "language.yml");

      assertTrue(locale, result.applied());
      for (MessageKey key : Messages.catalog().keys()) {
        assertEquals(locale + ":" + key.id(), locale, messages.snapshot().sourceLocale(key));
      }
    }
  }

  @Test
  public void bundledResourceSetExactlyMatchesSharedManifest() throws Exception {
    Set<String> expected = VolmitLocales.nonEnglish().stream()
        .map(locale -> locale + ".yml")
        .collect(Collectors.toUnmodifiableSet());
    try (Stream<Path> paths = Files.list(Path.of("src/main/resources/languages"))) {
      Set<String> actual = paths
          .filter(Files::isRegularFile)
          .map(path -> path.getFileName().toString())
          .collect(Collectors.toUnmodifiableSet());
      assertEquals(expected, actual);
    }
    assertFalse(expected.contains(VolmitLocales.ENGLISH + ".yml"));
  }

  @Test
  public void externalOverlayIsImmutableAfterAtomicReload() {
    Messages messages = new Messages();
    YamlConfiguration language = new YamlConfiguration();
    language.set("locale", "de_DE");
    language.set("prefix", "<gold>[Erz]</gold> ");
    language.set("no_permission", "<red>Keine Berechtigung.</red>");
    language.set(
        "usage",
        List.of("<aqua>Hilfe</aqua>", "<yellow>Neu laden</yellow>", "<yellow>Debuggen</yellow>")
    );
    language.set("director.help.navigation.page", "Seite");
    language.set("command.description.reload", "HiddenOre-Konfiguration neu laden");

    LocalizationReloadResult result = messages.reload(language, "translations/de_DE.yml");
    language.set("prefix", "Changed after reload");
    language.set("no_permission", "Changed after reload");

    assertTrue(result.applied());
    assertEquals("[Erz] Keine Berechtigung.", text(messages.component(Messages.NO_PERMISSION)));
    assertEquals(
        List.of("[Erz] Hilfe", "[Erz] Neu laden", "[Erz] Debuggen"),
        texts(messages.components(Messages.USAGE))
    );
    DirectorTextResolver resolver = messages.directorResolver();
    assertEquals("Seite", resolver.resolve(DirectorHelpMessages.PAGE));
    assertEquals("HiddenOre-Konfiguration neu laden", resolver.resolve(Messages.COMMAND_RELOAD_DESCRIPTION));
  }

  @Test
  public void invalidReloadRetainsTheLastGoodSnapshot() {
    Messages messages = new Messages();
    YamlConfiguration valid = new YamlConfiguration();
    valid.set("locale", "fr_FR");
    valid.set("no_permission", "<red>Accès refusé.</red>");
    messages.reload(valid, "translations/fr_FR.yml");

    YamlConfiguration placeholderDrift = new YamlConfiguration();
    placeholderDrift.set("locale", "fr_FR");
    placeholderDrift.set("no_permission", "<red>Bonjour {player}</red>");

    IllegalArgumentException exception = assertThrows(
        IllegalArgumentException.class,
        () -> messages.reload(placeholderDrift, "translations/fr_FR.yml")
    );

    assertTrue(exception.getMessage().contains("localization reload rejected"));
    assertEquals("[HiddenOre] Accès refusé.", text(messages.component(Messages.NO_PERMISSION)));
  }

  @Test
  public void malformedMiniMessageAndWrongShapesAreRejected() {
    Messages messages = new Messages();
    YamlConfiguration valid = new YamlConfiguration();
    valid.set("reloaded", "<gold>Dernière bonne version.</gold>");
    messages.reload(valid, "language.yml");
    YamlConfiguration malformed = new YamlConfiguration();
    malformed.set("reloaded", "<red>Missing close");
    IllegalArgumentException malformedException = assertThrows(
        IllegalArgumentException.class,
        () -> messages.reload(malformed, "language.yml")
    );

    YamlConfiguration wrongShape = new YamlConfiguration();
    wrongShape.set("usage", "Not a list");
    IllegalArgumentException shapeException = assertThrows(
        IllegalArgumentException.class,
        () -> messages.reload(wrongShape, "language.yml")
    );

    assertTrue(malformedException.getMessage().contains("invalid MiniMessage"));
    assertEquals("[HiddenOre] Dernière bonne version.", text(messages.component(Messages.RELOADED)));
    assertTrue(shapeException.getMessage().contains("expected a non-empty list"));
    assertEquals("[HiddenOre] Dernière bonne version.", text(messages.component(Messages.RELOADED)));
  }

  @Test
  public void unknownOverlayKeysAreRejectedWithoutChangingDefaults() {
    Messages messages = new Messages();
    YamlConfiguration valid = new YamlConfiguration();
    valid.set("debug_enabled", "<green>Diagnose aktiv.</green>");
    messages.reload(valid, "language.yml");
    YamlConfiguration language = new YamlConfiguration();
    language.set("unknown_message", "Unexpected");

    IllegalArgumentException exception = assertThrows(
        IllegalArgumentException.class,
        () -> messages.reload(language, "language.yml")
    );

    assertTrue(exception.getMessage().contains("UNUSED_KEY"));
    assertEquals("[HiddenOre] Diagnose aktiv.", text(messages.component(Messages.DEBUG_ENABLED)));
  }

  @Test
  public void untrustedNamedArgumentsCannotInjectMiniMessage() {
    Messages messages = new Messages();
    String maliciousMaterial = "{amount}<click:run_command:'/op @s'>diamond</click>";
    Component component = messages.component(
        Messages.DEBUG_RANDOM_DROP,
        MessageArgs.builder()
            .untrusted("material", maliciousMaterial)
            .untrusted("amount", 4)
            .build()
    );

    assertEquals("[HiddenOre] Random drop: " + maliciousMaterial + " x4", text(component));
    assertFalse(hasClickEvent(component));
  }

  @Test
  public void overlaysCannotPlaceUntrustedArgumentsInsideMiniMessageTags() {
    Messages messages = new Messages();
    YamlConfiguration language = new YamlConfiguration();
    language.set(
        "debug.random_drop",
        "<click:run_command:'/{material}'>Random drop: {material} x{amount}</click>"
    );

    IllegalArgumentException exception = assertThrows(
        IllegalArgumentException.class,
        () -> messages.reload(language, "language.yml")
    );

    assertTrue(exception.getMessage().contains("placeholders cannot be used inside MiniMessage tags"));
    assertFalse(hasClickEvent(messages.component(
        Messages.DEBUG_RANDOM_DROP,
        MessageArgs.builder()
            .untrusted("material", "diamond")
            .untrusted("amount", 1)
            .build()
    )));
  }

  @Test
  public void namedArgumentsMustMatchTheWholeTemplate() {
    Messages messages = new Messages();

    assertThrows(
        IllegalArgumentException.class,
        () -> messages.component(
            Messages.DEBUG_RANDOM_DROP,
            MessageArgs.builder().untrusted("material", "diamond").build()
        )
    );
    assertThrows(
        IllegalArgumentException.class,
        () -> messages.component(
            Messages.DEBUG_RANDOM_DROP,
            MessageArgs.builder()
                .untrusted("material", "diamond")
                .untrusted("amount", 1)
                .untrusted("extra", "value")
                .build()
        )
    );
  }

  @Test
  public void operationalLanguageSettingsAreNotTreatedAsMessageKeys() {
    Messages messages = new Messages();
    YamlConfiguration language = new YamlConfiguration();
    language.set("locale", "en_US");
    language.set("config_reloaded_sound", "ENTITY_EXPERIENCE_ORB_PICKUP");
    language.set("config_reloaded_sound_volume", 1.0);
    language.set("config_reloaded_sound_pitch", 1.6);

    assertTrue(messages.reload(language, "language.yml").applied());
  }

  @Test
  public void directorHelpUsesTheSameOverlayAndProducesValidMiniMessage() {
    Messages messages = new Messages();
    YamlConfiguration language = new YamlConfiguration();
    language.set("locale", "de_DE");
    language.set("command.description.reload", "HiddenOre neu laden");
    language.set("director.help.no_parameters", "Keine Parameter.");
    messages.reload(language, "language.yml");
    DirectorRuntimeEngine engine = DirectorEngineFactory.create(new HelpCommands());
    DirectorMiniMenu.DirectorHelpPage page = DirectorMiniMenu.resolveHelp(engine, List.of(), 8).orElseThrow();
    List<String> rendered = DirectorMiniMenu.render(
        page,
        DirectorMiniMenu.Theme.reactBlue(),
        messages.directorResolver()
    );

    assertTrue(String.join("\n", rendered).contains("HiddenOre neu laden"));
    assertTrue(String.join("\n", rendered).contains("Keine Parameter."));
    for (String line : rendered) {
      MiniMessage.miniMessage().deserialize(line);
    }
  }

  private static List<String> texts(List<Component> components) {
    return components.stream().map(MessagesTest::text).toList();
  }

  private static String text(Component component) {
    StringBuilder text = new StringBuilder();
    appendText(component, text);
    return text.toString();
  }

  private static void appendText(Component component, StringBuilder text) {
    if (component instanceof TextComponent textComponent) {
      text.append(textComponent.content());
    }
    for (Component child : component.children()) {
      appendText(child, text);
    }
  }

  private static boolean hasClickEvent(Component component) {
    if (component.clickEvent() != null) {
      return true;
    }
    for (Component child : component.children()) {
      if (hasClickEvent(child)) {
        return true;
      }
    }
    return false;
  }

  @Director(name = "hiddenore", description = "HiddenOre command root", descriptionKey = "command.description.root")
  public static final class HelpCommands {
    @Director(name = "reload", description = "Reload HiddenOre configuration and language files", descriptionKey = "command.description.reload")
    public void reload() {
    }
  }
}
