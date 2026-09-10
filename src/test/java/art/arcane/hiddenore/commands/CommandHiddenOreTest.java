package art.arcane.hiddenore.commands;

import art.arcane.volmlib.util.director.compat.DirectorEngineFactory;
import art.arcane.volmlib.util.director.help.DirectorMiniMenu;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeEngine;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class CommandHiddenOreTest {
  @Test
  public void helpExposesConfigurationLanguageAndDiagnosticCommands() {
    DirectorRuntimeEngine engine = DirectorEngineFactory.create(new CommandHiddenOre(null));
    DirectorMiniMenu.DirectorHelpPage root = DirectorMiniMenu.resolveHelp(engine, List.of()).orElseThrow();
    DirectorMiniMenu.DirectorHelpPage debug = DirectorMiniMenu.resolveHelp(engine, List.of("debug")).orElseThrow();

    assertEquals("hiddenore", root.node().getDescriptor().getName());
    assertEquals(List.of("config", "debug", "language"),
        root.entries().stream().map(node -> node.getDescriptor().getName()).toList());
    assertEquals("debug", debug.node().getDescriptor().getName());
    assertEquals(List.of("dump", "mode"),
        debug.entries().stream().map(node -> node.getDescriptor().getName()).toList());
  }
}
