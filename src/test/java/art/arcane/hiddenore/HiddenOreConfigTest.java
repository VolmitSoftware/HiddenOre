package art.arcane.hiddenore;

import art.arcane.hiddenore.generation.GenerationRules;
import art.arcane.hiddenore.rules.MiningRuleManager;
import art.arcane.hiddenore.vein.VeinConfig;
import art.arcane.volmlib.util.config.ConfigEditorDocument;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.bukkit.Material;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class HiddenOreConfigTest {
  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void bundledTomlBuildsTheConfiguredMiningAndGenerationPolicies() throws Exception {
    File file = new File("src/main/resources/hiddenore.toml");
    JsonObject configuration = HiddenOre.loadToml(Files.readString(file.toPath()), file);
    MiningRuleManager rules = new MiningRuleManager(configuration);
    GenerationRules.GenerationPolicy generation = GenerationRules.parsePolicy(configuration);

    assertEquals("en_US", configuration.get("language").getAsString());
    assertTrue(configuration.get("metrics").getAsBoolean());
    assertFalse(configuration.get("auto_pickup_drops").getAsBoolean());
    assertTrue(configuration.get("suppress_block_drop_on_custom_drop").getAsBoolean());
    assertEquals(9, rules.getAllDropRules().size());
    assertEquals(8, rules.getItemRules(0).size());
    assertEquals(1, rules.getCommandRules(0).size());
    assertEquals(Material.COBBLESTONE, rules.getGuaranteedDrop(Material.STONE));
    assertEquals(Material.COBBLED_DEEPSLATE, rules.getGuaranteedDrop(Material.DEEPSLATE));
    assertEquals(VeinConfig.GenerationMode.SEEDED, rules.getVeinConfig().generation);
    assertFalse(generation.enabled());
    assertTrue(generation.worldExceptions().containsKey("minecraft:the_end"));
    assertEquals(Material.NETHERRACK, generation.worldExceptions().get("minecraft:the_nether")
        .get(Material.NETHER_GOLD_ORE));
  }

  @Test
  public void languageSelectionPreservesBundledSettingsAndDropOrder() throws Exception {
    Path source = Path.of("src/main/resources/hiddenore.toml");
    File file = temporaryFolder.newFile("hiddenore.toml");
    String original = Files.readString(source);
    Files.writeString(file.toPath(), original);
    JsonObject expected = HiddenOre.loadToml(Files.readString(file.toPath()), file);
    expected.addProperty("language", "de_DE");

    HiddenOre.writeLanguage(file, "de_DE");

    JsonObject actual = HiddenOre.loadToml(Files.readString(file.toPath()), file);
    assertEquals(expected, actual);
    assertEquals(9, new MiningRuleManager(actual).getAllDropRules().size());
    assertEquals(GenerationRules.parsePolicy(expected), GenerationRules.parsePolicy(actual));
    assertEquals(original.replace("language = \"en_US\"", "language = \"de_DE\""), Files.readString(file.toPath()));

    HiddenOre.writeLanguage(file, "en_US");

    assertEquals(original, Files.readString(file.toPath()));
  }

  @Test
  public void languageSelectionPreservesQuotedKeysAndCommandStrings() throws Exception {
    File file = temporaryFolder.newFile("hiddenore.toml");
    String content = """
        # Server locale
        language = "en_US" # Keep this note
        metrics = true
        # Custom world policy
        [ore-removal.exceptions."custom:mines.v2"]
        default = false
        DIAMOND_ORE = true
        # Command reward
        [[drops]]
        type = "command"
        commands = ['console:say path C:\\mines\\bonus #1', 'player:say "bonus"']
        chance = 0.0005
        [[drops]]
        item = "diamond"
        veins_per_chunk = 0.5
        [custom."operator.settings"]
        enabled = true
        "message.with.dots" = "Keep these values"
        """;
    Files.writeString(file.toPath(), content);
    JsonObject expected = HiddenOre.loadToml(content, file);
    expected.addProperty("language", "fr_FR");

    HiddenOre.writeLanguage(file, "fr_FR");

    JsonObject actual = HiddenOre.loadToml(Files.readString(file.toPath()), file);
    assertEquals(expected, actual);
    assertTrue(actual.getAsJsonObject("ore-removal").getAsJsonObject("exceptions").has("custom:mines.v2"));
    assertEquals(content.replace("language = \"en_US\"", "language = \"fr_FR\""), Files.readString(file.toPath()));
  }

  @Test
  public void languageSelectionAddsAnOmittedRootSettingWithoutChangingNestedValues() throws Exception {
    File file = temporaryFolder.newFile("hiddenore.toml");
    String content = "[custom]\nlanguage = \"leave this value\"\n";
    Files.writeString(file.toPath(), content);
    JsonObject expected = HiddenOre.loadToml(content, file);
    expected.addProperty("language", "it_IT");

    HiddenOre.writeLanguage(file, "it_IT");

    assertEquals(expected, HiddenOre.loadToml(Files.readString(file.toPath()), file));
  }

  @Test
  public void malformedTomlIncludesItsPathAndRemainsUnchangedOnLanguageSave() throws Exception {
    File file = temporaryFolder.newFile("hiddenore.toml");
    String malformed = "language = \"unfinished\n";
    Files.writeString(file.toPath(), malformed);

    IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
        () -> HiddenOre.writeLanguage(file, "de_DE"));

    assertTrue(failure.getMessage().contains(file.getAbsolutePath()));
    assertNotNull(failure.getCause());
    assertEquals(malformed, Files.readString(file.toPath()));
  }

  @Test
  public void editorChangesOneDropSettingWithoutRewritingTheDocument() throws Exception {
    File file = bundledConfiguration();
    String original = Files.readString(file.toPath());
    ConfigEditorDocument document = ConfigEditorDocument.fromToml(original);

    ConfigEditorDocument saved = HiddenOre.writeConfiguration(file,
        document.edit(List.of("drops", "0", "veins_per_chunk"), new JsonPrimitive(2.3)));

    String expected = original.replace("veins_per_chunk = 2.2", "veins_per_chunk = 2.3");
    assertEquals(expected, saved.source());
    assertEquals(expected, Files.readString(file.toPath()));
    assertEquals(9, new MiningRuleManager(HiddenOre.loadToml(saved.source(), file)).getAllDropRules().size());
  }

  @Test
  public void editorChangesQuotedWorldSettingsWithoutMovingTheirComments() throws Exception {
    File file = bundledConfiguration();
    String original = Files.readString(file.toPath());
    ConfigEditorDocument document = ConfigEditorDocument.fromToml(original);

    ConfigEditorDocument saved = HiddenOre.writeConfiguration(file, document.edit(
        List.of("ore-removal", "exceptions", "minecraft:the_end", "default"), new JsonPrimitive(true)));

    String newline = original.contains("\r\n") ? "\r\n" : "\n";
    assertEquals(original.replace("[ore-removal.exceptions.\"minecraft:the_end\"]" + newline + "default = false",
        "[ore-removal.exceptions.\"minecraft:the_end\"]" + newline + "default = true"), saved.source());
    assertEquals(saved.source(), Files.readString(file.toPath()));
  }

  @Test
  public void editorSavesToolAndCommandListsWithoutDroppingTheirInternalComments() throws Exception {
    File file = temporaryFolder.newFile("hiddenore.toml");
    String original = """
        # Main settings
        language = "en_US"
        [blocks.stone]
        drop = "cobblestone"
        [veins]
        generation = "seeded"
        # Item reward
        [[drops]]
        item = "diamond"
        veins_per_chunk = 0.5
        tool_tiers = [
          "IRON_PICKAXE", # Iron tools
          "DIAMOND_PICKAXE" # Diamond tools
        ]
        # Command reward
        [[drops]]
        type = "command"
        chance = 0.0005
        commands = [
          "say first", # First command
          "say second" # Second command
        ]
        """;
    Files.writeString(file.toPath(), original);
    ConfigEditorDocument document = ConfigEditorDocument.fromToml(original);
    JsonArray tools = new JsonArray();
    tools.add("NETHERITE_PICKAXE");

    ConfigEditorDocument savedTools = HiddenOre.writeConfiguration(file,
        document.edit(List.of("drops", "0", "tool_tiers"), tools));

    String expectedTools = original.replace("\"IRON_PICKAXE\"", "\"NETHERITE_PICKAXE\"")
        .replace("\"DIAMOND_PICKAXE\"", "");
    assertEquals(expectedTools, savedTools.source());
    JsonArray commands = new JsonArray();
    commands.add("say updated");
    commands.add("say second");

    ConfigEditorDocument savedCommands = HiddenOre.writeConfiguration(file,
        savedTools.edit(List.of("drops", "1", "commands"), commands));

    assertEquals(expectedTools.replace("\"say first\"", "\"say updated\""), savedCommands.source());
    assertEquals(savedCommands.source(), Files.readString(file.toPath()));
  }

  @Test
  public void editorRejectsAnExternalChangeInsteadOfOverwritingIt() throws Exception {
    File file = bundledConfiguration();
    String original = Files.readString(file.toPath());
    ConfigEditorDocument document = ConfigEditorDocument.fromToml(original);
    String external = original + "\n# Operator note added while the editor was open\n";
    Files.writeString(file.toPath(), external);

    IOException failure = assertThrows(IOException.class, () -> HiddenOre.writeConfiguration(file,
        document.edit(List.of("metrics"), new JsonPrimitive(false))));

    assertTrue(failure.getMessage().contains("changed"));
    assertEquals(external, Files.readString(file.toPath()));
  }

  @Test
  public void editorRejectsInvalidLocaleNamesBeforeWriting() throws Exception {
    File file = bundledConfiguration();
    String original = Files.readString(file.toPath());
    ConfigEditorDocument document = ConfigEditorDocument.fromToml(original);
    for (String locale : List.of("../bad", "de DE", "x")) {
      assertThrows(IllegalArgumentException.class, () -> HiddenOre.writeConfiguration(file,
          document.edit(List.of("language"), new JsonPrimitive(locale))));
      assertEquals(original, Files.readString(file.toPath()));
    }
  }

  @Test
  public void editorRejectsAStaleSnapshotAfterAServerLanguageChange() throws Exception {
    File file = bundledConfiguration();
    String original = Files.readString(file.toPath());
    ConfigEditorDocument document = ConfigEditorDocument.fromToml(original);
    HiddenOre.writeLanguage(file, "de_DE");
    String selectedLanguage = Files.readString(file.toPath());

    assertThrows(IOException.class, () -> HiddenOre.writeConfiguration(file,
        document.edit(List.of("auto_pickup_drops"), new JsonPrimitive(true))));

    assertEquals(selectedLanguage, Files.readString(file.toPath()));
  }

  @Test
  public void editorRejectsInvalidMiningRulesWithoutChangingAnyBytes() throws Exception {
    File file = bundledConfiguration();
    String original = Files.readString(file.toPath());
    ConfigEditorDocument document = ConfigEditorDocument.fromToml(original);

    IllegalArgumentException chanceFailure = assertThrows(IllegalArgumentException.class,
        () -> HiddenOre.writeConfiguration(file,
            document.edit(List.of("drops", "8", "chance"), new JsonPrimitive(1.5))));
    assertTrue(chanceFailure.getMessage().contains(file.getAbsolutePath()));
    assertEquals(original, Files.readString(file.toPath()));

    assertThrows(IllegalArgumentException.class, () -> HiddenOre.writeConfiguration(file,
        document.edit(List.of("drops", "0", "vein_min_size"), new JsonPrimitive(100))));
    assertEquals(original, Files.readString(file.toPath()));

    assertThrows(IllegalArgumentException.class, () -> HiddenOre.writeConfiguration(file,
        document.edit(List.of("drops", "0", "item"), new JsonPrimitive("not_an_item"))));
    assertEquals(original, Files.readString(file.toPath()));
  }

  private File bundledConfiguration() throws IOException {
    File file = temporaryFolder.newFile("hiddenore.toml");
    Files.writeString(file.toPath(), Files.readString(Path.of("src/main/resources/hiddenore.toml")));
    return file;
  }
}
