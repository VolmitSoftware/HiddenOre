package art.arcane.hiddenore.util.common;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

public class MessagesTest {
  @Test
  public void constructor_buildsImmutableDefaultSnapshot() {
    Messages messages = new Messages(new YamlConfiguration());

    assertNotNull(messages.get("reloaded"));
    assertEquals(3, messages.getList("usage").size());
    assertThrows(UnsupportedOperationException.class, () -> messages.getList("usage").clear());
  }

  @Test
  public void constructor_rejectsMalformedScalarMessages() {
    YamlConfiguration prefix = new YamlConfiguration();
    prefix.set("prefix", 1);
    assertInvalid("prefix: expected a string", prefix);

    YamlConfiguration blankMessage = new YamlConfiguration();
    blankMessage.set("reloaded", " ");
    assertInvalid("reloaded: expected a non-empty message", blankMessage);

    YamlConfiguration malformedMiniMessage = new YamlConfiguration();
    malformedMiniMessage.set("reloaded", "<red>missing close");
    assertInvalid("reloaded: invalid MiniMessage", malformedMiniMessage);
  }

  @Test
  public void constructor_rejectsMalformedUsage() {
    YamlConfiguration scalar = new YamlConfiguration();
    scalar.set("usage", "help");
    assertInvalid("usage: expected a non-empty list of messages", scalar);

    YamlConfiguration empty = new YamlConfiguration();
    empty.set("usage", List.of());
    assertInvalid("usage: expected a non-empty list of messages", empty);

    YamlConfiguration malformedEntry = new YamlConfiguration();
    malformedEntry.set("usage", List.of("valid", 1));
    assertInvalid("usage[1]: expected a non-empty message", malformedEntry);
  }

  private static void assertInvalid(String message, YamlConfiguration configuration) {
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> new Messages(configuration));
    assertEquals(message, exception.getMessage());
  }
}
