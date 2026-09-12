package art.arcane.hiddenore.util.common;

import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.DirectorTextResolver;
import art.arcane.volmlib.util.director.compat.DirectorEngineFactory;
import art.arcane.volmlib.util.director.help.DirectorHelpMessages;
import art.arcane.volmlib.util.director.help.DirectorMiniMenu;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeEngine;
import art.arcane.volmlib.util.localization.PluginLanguageEditor;
import art.arcane.volmlib.util.localization.TextValue;
import art.arcane.volmlib.util.localization.LocalizationSnapshot;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.localization.LanguageAudience;
import art.arcane.volmlib.util.localization.PluginLanguageService;
import art.arcane.volmlib.util.localization.RemoteLanguageCatalog;
import art.arcane.volmlib.util.localization.LocalizationReloadResult;
import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.VolmitLocales;
import art.arcane.volmlib.util.plugin.ComponentText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import art.arcane.volmlib.util.config.TomlCodec;
import art.arcane.volmlib.util.localization.TomlLanguageWriter;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import java.net.URI;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class MessagesTest {
  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();
  @Test
  public void editorPersistsEnglishMessagesInTheLanguageFile() throws Exception {
    Path data = temporaryFolder.newFolder().toPath();
    Path languages = Files.createDirectories(data.resolve("languages"));
    Messages messages = new Messages(null, languages);
    messages.reload("en_US");
    PluginLanguageEditor.Options editor = messages.editorOptions();
    LocalizationSnapshot original = editor.loader().load("en_US");
    TextValue replacement = new TextValue("&cEdited permission message");
    editor.writer().write(new PluginLanguageEditor.Edit("en_US", Messages.NO_PERMISSION.id(),
        original.value(Messages.NO_PERMISSION), replacement));
    TextValue editedReload = new TextValue("&6Reload complete&r");
    editor.writer().write(new PluginLanguageEditor.Edit("en_US", Messages.CONFIG_RELOADED_MESSAGE.id(),
        original.value(Messages.CONFIG_RELOADED_MESSAGE), editedReload));

    assertEquals(replacement, messages.defaultSnapshot().value(Messages.NO_PERMISSION));
    LocalizationSnapshot reloaded = editor.loader().load("en_US");
    assertEquals(replacement, reloaded.value(Messages.NO_PERMISSION));
    assertEquals(editedReload, reloaded.value(Messages.CONFIG_RELOADED_MESSAGE));
    assertTrue(Files.readString(languages.resolve("en_US.toml")).contains("&cEdited permission message"));
    assertTextStyle(messages.component(Messages.NO_PERMISSION), "Edited permission message", NamedTextColor.RED, false);
  }

  @Test
  public void editorLeavesActiveLocaleUnchangedAndRejectsInvalidOrStaleEdits() throws Exception {
    Path data = temporaryFolder.newFolder().toPath();
    Path languages = Files.createDirectories(data.resolve("languages"));
    Files.writeString(languages.resolve("fr_FR.toml"), "no_permission = \"Permission\"\n");
    try (RemoteLanguageCatalog remote = RemoteLanguageCatalog.load(new RemoteLanguageCatalog.Options(
        "HiddenOre", URI.create("https://raw.githubusercontent.com/VolmitSoftware/HiddenOre/"),
        "src/main/resources/languages", ".toml", "language-source.properties",
        Messages.class.getClassLoader()))) {
      Messages messages = new Messages(remote, languages);
      PluginLanguageEditor.Options editor = messages.editorOptions();
      LocalizationSnapshot original = editor.loader().load("fr_FR");
      Path file = languages.resolve("fr_FR.toml");
      assertThrows(IllegalArgumentException.class, () -> editor.writer().write(new PluginLanguageEditor.Edit(
          "fr_FR", Messages.NO_PERMISSION.id(), original.value(Messages.NO_PERMISSION), new TextValue("{unexpected}"))));
      assertEquals("no_permission = \"Permission\"\n", Files.readString(file));
      TextValue replacement = new TextValue("Permission modifiee");
      editor.writer().write(new PluginLanguageEditor.Edit("fr_FR", Messages.NO_PERMISSION.id(),
          original.value(Messages.NO_PERMISSION), replacement));
      byte[] saved = Files.readAllBytes(file);
      assertThrows(IOException.class, () -> editor.writer().write(new PluginLanguageEditor.Edit(
          "fr_FR", Messages.NO_PERMISSION.id(), original.value(Messages.NO_PERMISSION), new TextValue("Stale"))));
      assertArrayEquals(saved, Files.readAllBytes(file));
      assertEquals(Messages.NO_PERMISSION.englishValue(), messages.defaultSnapshot().value(Messages.NO_PERMISSION));
      assertEquals(replacement, editor.loader().load("fr_FR").value(Messages.NO_PERMISSION));
    }
  }

  @Test
  public void englishDefaultsLiveInTheTypedJavaCatalog() {
    Messages messages = new Messages();
    assertTrue(messages.reload("en_US").applied());

    assertEquals("&a[HiddenOre]&r ", Messages.PREFIX.english());
    assertTrue(Messages.NO_PERMISSION.english().contains("You do not have permission"));
    assertEquals("[HiddenOre] You do not have permission to use this command.", text(messages.component(Messages.NO_PERMISSION)));
  }

  @Test
  public void everyDownloadableLocaleFullyCoversTheTypedCatalog() throws Exception {
    Path languages = temporaryFolder.newFolder().toPath();
    Messages messages = new Messages(null, languages);
    for (String locale : VolmitLocales.nonEnglish()) {
      Files.copy(Path.of("src/main/resources/languages", locale + ".toml"), languages.resolve(locale + ".toml"));
      LocalizationReloadResult result = messages.reload(locale);

      assertTrue(locale, result.applied());
      for (MessageKey key : Messages.catalog().keys()) {
        assertEquals(locale + ":" + key.id(), locale, messages.snapshot().sourceLocale(key));
      }
    }
  }

  @Test
  public void downloadableResourceSetExactlyMatchesSharedManifest() throws Exception {
    Set<String> expected = VolmitLocales.nonEnglish().stream()
        .map(locale -> locale + ".toml")
        .collect(Collectors.toUnmodifiableSet());
    try (Stream<Path> paths = Files.list(Path.of("src/main/resources/languages"))) {
      Set<String> actual = paths
          .filter(Files::isRegularFile)
          .map(path -> path.getFileName().toString())
          .collect(Collectors.toUnmodifiableSet());
      assertEquals(expected, actual);
    }
    assertFalse(expected.contains(VolmitLocales.ENGLISH + ".toml"));
  }

  @Test
  public void installedCatalogAndPersonalChoiceUseTheSharedRuntime() throws Exception {
    Path directory = temporaryFolder.newFolder().toPath();
    Files.copy(Path.of("src/main/resources/languages/de_DE.toml"), directory.resolve("de_DE.toml"));
    Messages messages = new Messages();
    UUID player = UUID.randomUUID();
    try (RemoteLanguageCatalog remote = RemoteLanguageCatalog.load(new RemoteLanguageCatalog.Options(
        "HiddenOre", URI.create("https://raw.githubusercontent.com/VolmitSoftware/HiddenOre/"),
        "src/main/resources/languages", ".toml", "language-source.properties",
        Messages.class.getClassLoader()));
         PluginLanguageService service = new PluginLanguageService(new PluginLanguageService.Options(
             directory.resolve("preferences.properties"), VolmitLocales::all, () -> "en_US", messages::defaultSnapshot,
             locale -> {
               Messages selected = new Messages(remote, directory);
               selected.reload(locale);
               return selected.defaultSnapshot();
             }, (locale, prepared) -> messages.install(prepared), Logger.getAnonymousLogger()))) {
      messages.languageService(service);
      String english = text(messages.component(Messages.NO_PERMISSION));
      service.selectPlayer(player, "de_DE").get(5, TimeUnit.SECONDS);
      String translated = LanguageAudience.call(player, () -> text(messages.component(Messages.NO_PERMISSION)));
      assertFalse(english.equals(translated));
      assertEquals(english, text(messages.component(Messages.NO_PERMISSION)));
      service.clearPlayer(player).get(5, TimeUnit.SECONDS);
      assertEquals(english, LanguageAudience.call(player, () -> text(messages.component(Messages.NO_PERMISSION))));
    }
  }

  @Test
  public void generatedFilesKeepLanguageAndMetricsInTheMainConfig() throws Exception {
    JsonObject config = TomlCodec.toJsonElement(Files.readString(Path.of("src/main/resources/hiddenore.toml")))
        .getAsJsonObject();

    assertEquals("en_US", config.get("language").getAsString());
    assertTrue(config.get("metrics").getAsBoolean());
  }

  @Test
  public void localeSnapshotIsImmutableUntilTheNextReload() throws Exception {
    Path languages = temporaryFolder.newFolder().toPath();
    Path file = languages.resolve("de_DE.toml");
    Map<String, Object> language = new LinkedHashMap<>();

    language.put("prefix", "&6[Erz]&r ");
    language.put("no_permission", "&cKeine Berechtigung.");
    language.put("director.help.navigation.page", "Seite");
    language.put("command.description.debug", "Erz-Debug-Modus umschalten");

    saveLanguageFile(file, language);
    Messages messages = new Messages(null, languages);
    LocalizationReloadResult result = messages.reload("de_DE");
    language.put("prefix", "Changed after reload");
    language.put("no_permission", "Changed after reload");
    saveLanguageFile(file, language);

    assertTrue(result.applied());
    assertEquals("[Erz] Keine Berechtigung.", text(messages.component(Messages.NO_PERMISSION)));
    DirectorTextResolver resolver = messages.directorResolver();
    assertEquals("Seite", resolver.resolve(DirectorHelpMessages.PAGE));
    assertEquals("Erz-Debug-Modus umschalten", resolver.resolve(Messages.COMMAND_DEBUG_DESCRIPTION));
    messages.reload("de_DE");
    assertEquals("Changed after reloadChanged after reload", text(messages.component(Messages.NO_PERMISSION)));
  }

  @Test
  public void invalidEntriesFallBackIndividuallyAndKeepValidTranslations() throws Exception {
    Map<String, Object> language = new LinkedHashMap<>();
    language.put("no_permission", "&cBonjour {player}");
    language.put("config_reloaded_message", " ");
    language.put("player_only", List.of("Wrong shape"));
    language.put("unknown_message", "Ignored");
    language.put("debug_enabled", "&aDiagnose aktiv.");

    Messages messages = messagesWithLanguage("fr_FR", language);
    assertTrue(messages.reload("fr_FR").applied());
    assertEquals(Messages.NO_PERMISSION.englishValue(), messages.snapshot().value(Messages.NO_PERMISSION));
    assertEquals(Messages.CONFIG_RELOADED_MESSAGE.englishValue(), messages.snapshot().value(Messages.CONFIG_RELOADED_MESSAGE));
    assertEquals(Messages.PLAYER_ONLY.englishValue(), messages.snapshot().value(Messages.PLAYER_ONLY));
    assertEquals("[HiddenOre] Diagnose aktiv.", text(messages.component(Messages.DEBUG_ENABLED)));
  }

  @Test
  public void startupCreatesEditableEnglishAndPreservesEdits() throws Exception {
    Path languages = temporaryFolder.newFolder().toPath();
    new Messages(null, languages);
    Path english = languages.resolve("en_US.toml");
    assertTrue(Files.readString(english).contains("{material}  Dropped item type"));
    assertTrue(Files.readString(english).contains("&a[HiddenOre]&r "));
    Files.writeString(english, "no_permission = \"&cCustom English\"\n");
    Messages messages = new Messages(null, languages);
    messages.reload("en_US");
    assertEquals("&cCustom English", ((TextValue) messages.snapshot().value(Messages.NO_PERMISSION)).template());
    assertEquals("no_permission = \"&cCustom English\"\n", Files.readString(english));
    assertTextStyle(messages.component(Messages.NO_PERMISSION), "Custom English", NamedTextColor.RED, false);
  }

  @Test
  public void untrustedNamedArgumentsRemainLiteralAndCannotChangeFormatting() {
    Messages messages = new Messages();
    String maliciousMaterial = "&c&l&#12ab34\u00a7c\u00a7r{amount}<click:run_command:'/op @s'>diamond</click>";
    Component component = messages.component(
        Messages.DEBUG_RANDOM_DROP,
        MessageArgs.builder()
            .untrusted("material", maliciousMaterial)
            .untrusted("amount", 4)
            .build()
    );

    assertEquals("[HiddenOre] Random drop: " + maliciousMaterial + " x4", text(component));
    assertFalse(hasClickEvent(component));
    assertFalse(hasHoverEvent(component));
    assertTextStyle(component, maliciousMaterial, NamedTextColor.GREEN, false);
    assertTextStyle(component, " x", NamedTextColor.GREEN, false);
  }

  @Test
  public void malformedLanguageFileRetainsTheLastValidSnapshot() throws Exception {
    Path languages = temporaryFolder.newFolder().toPath();
    Path french = languages.resolve("fr_FR.toml");
    Files.writeString(french, "prefix = ''\nno_permission = 'Permission personnalisée'\n");
    Messages messages = new Messages(null, languages);
    messages.reload("fr_FR");
    Files.writeString(french, "no_permission = 'unterminated");

    assertThrows(IllegalArgumentException.class, () -> messages.reload("fr_FR"));
    assertEquals("Permission personnalisée", text(messages.component(Messages.NO_PERMISSION)));
    assertEquals("no_permission = 'unterminated", Files.readString(french));
  }

  @Test
  public void installedLocalePreparationRetainsValidTranslationsAndOmitsMalformedFiles() throws Exception {
    Path languages = temporaryFolder.newFolder().toPath();
    Messages messages = new Messages(null, languages);
    Path french = languages.resolve("fr_FR.toml");
    Files.writeString(french, "no_permission = 'Permission personnalisée'\n");
    Files.writeString(languages.resolve("de_DE.toml"), "no_permission = 'unterminated");

    Map<String, LocalizationSnapshot> prepared = messages.prepareInstalledLocales(Set.of());

    assertTrue(prepared.containsKey("en_US"));
    assertTrue(prepared.containsKey("fr_FR"));
    assertFalse(prepared.containsKey("de_DE"));
    assertEquals("Permission personnalisée", prepared.get("fr_FR").resolve(Messages.NO_PERMISSION).template());
    assertEquals(Messages.PLAYER_ONLY.english(), prepared.get("fr_FR").resolve(Messages.PLAYER_ONLY).template());
    assertEquals("en_US", prepared.get("fr_FR").sourceLocale(Messages.PLAYER_ONLY));
  }

  @Test
  public void malformedPersonalLanguageSurvivesUnrelatedDefaultReloadsUntilAValidEdit() throws Exception {
    Path languages = temporaryFolder.newFolder().toPath();
    Path french = languages.resolve("fr_FR.toml");
    Files.writeString(french, "prefix = ''\nno_permission = 'Initial'\n");
    Messages initial = new Messages(null, languages);
    initial.reload("en_US");
    AtomicReference<Messages> active = new AtomicReference<>(initial);
    UUID player = UUID.randomUUID();
    try (PluginLanguageService service = new PluginLanguageService(new PluginLanguageService.Options(
        languages.resolve("preferences.properties"), VolmitLocales::all, () -> "en_US",
        () -> active.get().defaultSnapshot(),
        locale -> {
          Messages prepared = new Messages(null, languages);
          prepared.reload(locale);
          return prepared.defaultSnapshot();
        }, (locale, prepared) -> {
          throw new AssertionError("Personal messages cannot change the server language");
        }, Logger.getAnonymousLogger()))) {
      initial.languageService(service);
      service.selectPlayer(player, "fr_FR").get(5, TimeUnit.SECONDS);
      Files.writeString(french, "prefix = ''\nno_permission = 'Live edit'\n");
      initial.publishPreparedLocales(initial.prepareInstalledLocales(Set.of()));
      assertEquals("Live edit", LanguageAudience.call(player,
          () -> text(active.get().component(Messages.NO_PERMISSION))));

      String malformed = "no_permission = 'unterminated";
      Files.writeString(french, malformed);
      initial.publishPreparedLocales(initial.prepareInstalledLocales(Set.of()));
      assertEquals("Live edit", LanguageAudience.call(player,
          () -> text(active.get().component(Messages.NO_PERMISSION))));

      Files.writeString(languages.resolve("en_US.toml"), "prefix = ''\nno_permission = 'Server edit'\n");
      Messages reloaded = new Messages(null, languages);
      reloaded.reload("en_US");
      reloaded.languageService(service);
      active.set(reloaded);
      reloaded.publishPreparedLocales(reloaded.prepareInstalledLocales(Set.of()));
      assertEquals("Server edit", text(reloaded.component(Messages.NO_PERMISSION)));
      assertEquals("Live edit", LanguageAudience.call(player,
          () -> text(reloaded.component(Messages.NO_PERMISSION))));
      assertEquals(malformed, Files.readString(french));

      Files.writeString(french, "prefix = ''\n");
      reloaded.publishPreparedLocales(reloaded.prepareInstalledLocales(Set.of()));
      assertEquals("You do not have permission to use this command.", LanguageAudience.call(player,
          () -> text(reloaded.component(Messages.NO_PERMISSION))));
      assertEquals("Server edit", text(reloaded.component(Messages.NO_PERMISSION)));
      assertEquals("en_US", service.defaultLocale());
      assertEquals("fr_FR", service.playerLocale(player).orElseThrow());

      Files.delete(french);
      reloaded.publishPreparedLocales(reloaded.prepareInstalledLocales(Set.of("fr_FR")));
      assertEquals("[HiddenOre] You do not have permission to use this command.", LanguageAudience.call(player,
          () -> text(reloaded.component(Messages.NO_PERMISSION))));
      assertEquals("Server edit", text(reloaded.component(Messages.NO_PERMISSION)));
      assertEquals("fr_FR", service.playerLocale(player).orElseThrow());
      assertFalse(Files.exists(french));

      Files.writeString(french, "prefix = ''\nno_permission = 'Recreated'\n");
      reloaded.publishPreparedLocales(reloaded.prepareInstalledLocales(Set.of("fr_FR")));
      assertEquals("Recreated", LanguageAudience.call(player,
          () -> text(reloaded.component(Messages.NO_PERMISSION))));
    }
  }

  @Test
  public void everyLanguageUsesTheStandardHeaderAndDocumentsItsVariables() throws Exception {
    Path languages = temporaryFolder.newFolder().toPath();
    new Messages(null, languages);
    Set<String> expected = new HashSet<>();
    for (MessageKey key : Messages.catalog().keys()) {
      expected.addAll(key.placeholders());
      expected.addAll(key.optionalPlaceholders());
    }
    List<String> locales = new ArrayList<>(VolmitLocales.nonEnglish());
    locales.add(VolmitLocales.ENGLISH);
    Pattern placeholder = Pattern.compile("(?<!\\{)\\{([A-Za-z][A-Za-z0-9_]*)\\}(?!\\})");
    for (String locale : locales) {
      Path path = VolmitLocales.ENGLISH.equals(locale)
          ? languages.resolve(locale + ".toml")
          : Path.of("src/main/resources/languages", locale + ".toml");
      String header = Files.readString(path).lines().takeWhile(line -> line.startsWith("#"))
          .collect(Collectors.joining("\n"));
      assertEquals(locale, 4L, header.lines().filter(line -> line.startsWith("# === ")).count());
      Set<String> documented = new HashSet<>();
      Matcher matcher = placeholder.matcher(header);
      while (matcher.find()) {
        documented.add(matcher.group(1));
      }
      assertEquals(locale, expected, documented);
    }
  }

  @Test
  public void sharedComponentBridgePreservesAmpersandColorsStylesAndResets() throws Exception {
    Map<String, Object> language = new LinkedHashMap<>();
    language.put("prefix", "");
    language.put("config_reloaded_message", "&aGreen &cRed &6Gold &lBold&r Plain &#12ab34Hex");
    Messages messages = messagesWithLanguage("en_US", language);
    messages.reload("en_US");

    ComponentText bridged = ComponentText.component(messages.component(Messages.CONFIG_RELOADED_MESSAGE));
    Component restored = MiniMessage.miniMessage().deserialize(bridged.miniMessage());

    assertEquals("Green Red Gold Bold Plain Hex", bridged.plain());
    assertTextStyle(restored, "Green", NamedTextColor.GREEN, false);
    assertTextStyle(restored, "Red", NamedTextColor.RED, false);
    assertTextStyle(restored, "Gold", NamedTextColor.GOLD, false);
    assertTextStyle(restored, "Bold", NamedTextColor.GOLD, true);
    assertTextStyle(restored, "Plain", null, false);
    assertTextStyle(restored, "Hex", TextColor.color(0x12ab34), false);
    assertFalse(hasClickEvent(restored));
    assertFalse(hasHoverEvent(restored));
  }

  @Test
  public void templatesKeepAngleBracketsAndEscapedBracesAndAmpersandsLiteral() throws Exception {
    Map<String, Object> language = new LinkedHashMap<>();
    language.put("prefix", "");
    language.put(
        "debug.random_drop",
        "&6<{material}> x{amount} {{literal}} \\&a"
    );

    Messages messages = messagesWithLanguage("en_US", language);
    assertTrue(messages.reload("en_US").applied());
    Component component = messages.component(
        Messages.DEBUG_RANDOM_DROP,
        MessageArgs.builder()
            .untrusted("material", "diamond")
            .untrusted("amount", 1)
            .build()
    );
    assertEquals("<diamond> x1 {literal} &a", text(component));
    assertTextStyle(component, "&a", NamedTextColor.GOLD, false);
    assertFalse(hasClickEvent(component));
    assertFalse(hasHoverEvent(component));
  }

  @Test
  public void prefixResetClearsFormattingBeforeTheMessage() throws Exception {
    Messages messages = messagesWithLanguage("en_US", Map.of(
        "prefix", "&a&l[HiddenOre]&r ",
        "no_permission", "Permission"
    ));
    messages.reload("en_US");
    Component component = messages.component(Messages.NO_PERMISSION);

    assertEquals("[HiddenOre] Permission", text(component));
    assertTextStyle(component, "[HiddenOre]", NamedTextColor.GREEN, true);
    assertTextStyle(component, "Permission", null, false);
  }

  @Test
  public void templateColorRestoresFormattingAfterTrustedArguments() throws Exception {
    Messages messages = messagesWithLanguage("en_US", Map.of(
        "debug.random_drop", "&aRandom drop: {material}&a x{amount}"
    ));
    messages.reload("en_US");
    Component component = messages.component(Messages.DEBUG_RANDOM_DROP, MessageArgs.builder()
        .trusted("material", "&c&lDiamond")
        .untrusted("amount", 4)
        .build());

    assertEquals("[HiddenOre] Random drop: Diamond x4", text(component));
    assertTextStyle(component, "Diamond", NamedTextColor.RED, true);
    assertTextStyle(component, " x", NamedTextColor.GREEN, false);
  }

  @Test
  public void trustedArgumentColorsAndResetsOverrideInheritedFormatting() throws Exception {
    Messages messages = messagesWithLanguage("en_US", Map.of(
        "prefix", "",
        "debug.random_drop", "&a&l{material}&a&l x{amount}"
    ));
    messages.reload("en_US");
    Map<String, TextStyle> expected = Map.of(
        "Stone", new TextStyle(NamedTextColor.GREEN, true),
        "&cStone", new TextStyle(NamedTextColor.RED, false),
        "&rStone", new TextStyle(null, false)
    );
    for (Map.Entry<String, TextStyle> entry : expected.entrySet()) {
      Component component = messages.component(Messages.DEBUG_RANDOM_DROP, MessageArgs.builder()
          .trusted("material", entry.getKey())
          .untrusted("amount", 4)
          .build());

      assertEquals("Stone x4", text(component));
      assertTextStyle(component, "Stone", entry.getValue().color(), entry.getValue().bold());
      assertTextStyle(component, " x", NamedTextColor.GREEN, true);
    }
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
  public void directorHelpUsesTheSameOverlayAndProducesValidMiniMessage() throws Exception {
    Map<String, Object> language = new LinkedHashMap<>();
    language.put("command.description.debug", "&6Erz-Debug-Modus umschalten");
    language.put("director.help.no_parameters", "&aKeine Parameter.");
    Messages messages = messagesWithLanguage("de_DE", language);
    messages.reload("de_DE");
    assertEquals("Erz-Debug-Modus umschalten", messages.directorResolver().resolve(Messages.COMMAND_DEBUG_DESCRIPTION));
    assertEquals("Keine Parameter.", messages.directorResolver().resolve(DirectorHelpMessages.NO_PARAMETERS));
    DirectorRuntimeEngine engine = DirectorEngineFactory.create(new HelpCommands());
    DirectorMiniMenu.DirectorHelpPage page = DirectorMiniMenu.resolveHelp(engine, List.of()).orElseThrow();
    List<String> rendered = DirectorMiniMenu.render(
        page,
        DirectorMiniMenu.Theme.reactBlue(),
        messages.directorResolver()
    );

    assertTrue(String.join("\n", rendered).contains("Erz-Debug-Modus umschalten"));
    assertTrue(String.join("\n", rendered).contains("Keine Parameter."));
    for (String line : rendered) {
      MiniMessage.miniMessage().deserialize(line);
    }
  }

  private Messages messagesWithLanguage(String locale, Map<String, Object> language) throws IOException {
    Path languages = temporaryFolder.newFolder().toPath();

    saveLanguageFile(languages.resolve(locale + ".toml"), language);
    return new Messages(null, languages);
  }

  private void saveLanguageFile(Path file, Map<String, Object> values) throws IOException {
    Files.writeString(file, TomlLanguageWriter.renderJson(new Gson().toJsonTree(values).getAsJsonObject(), List.of()));
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

  private static boolean hasHoverEvent(Component component) {
    if (component.hoverEvent() != null) {
      return true;
    }
    for (Component child : component.children()) {
      if (hasHoverEvent(child)) {
        return true;
      }
    }
    return false;
  }

  private static void assertTextStyle(Component component, String content, TextColor color, boolean bold) {
    assertTrue("Expected style for " + content + ": color=" + color + ", bold=" + bold,
        hasTextStyle(component, content, new TextStyle(color, bold), new TextStyle(null, false)));
  }

  private static boolean hasTextStyle(Component component, String content, TextStyle expected, TextStyle inherited) {
    TextColor color = component.color() == null ? inherited.color() : component.color();
    TextDecoration.State bold = component.decoration(TextDecoration.BOLD);
    TextStyle effective = new TextStyle(color,
        bold == TextDecoration.State.NOT_SET ? inherited.bold() : bold == TextDecoration.State.TRUE);
    if (component instanceof TextComponent textComponent && textComponent.content().contains(content)
        && effective.equals(expected)) {
      return true;
    }
    for (Component child : component.children()) {
      if (hasTextStyle(child, content, expected, effective)) {
        return true;
      }
    }
    return false;
  }

  private record TextStyle(TextColor color, boolean bold) {
  }

  @Director(name = "hiddenore", description = "HiddenOre command root", descriptionKey = "command.description.root")
  public static final class HelpCommands {
    @Director(name = "debug", description = "Toggle ore debug mode for yourself", descriptionKey = "command.description.debug")
    public void debug() {
    }
  }
}
