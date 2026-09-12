package art.arcane.hiddenore.util.project;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class ConfigWatcherBaselineTest {
  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void startupEditAfterAppliedSnapshotRemainsDetectable() throws Exception {
    Path directory = temporaryFolder.newFolder("hiddenore-startup").toPath();
    String appliedConfig = "auto_pickup_drops = false\n";
    Files.writeString(directory.resolve("hiddenore.toml"), appliedConfig, StandardCharsets.UTF_8);
    Map<String, String> baseline = ConfigWatcher.appliedSignatures(appliedConfig, ConfigWatcher.languageSignatures(directory));

    assertEquals(baseline, ConfigWatcher.diskSignatures(directory));
    Files.writeString(directory.resolve("hiddenore.toml"), "auto_pickup_drops = true\n", StandardCharsets.UTF_8);

    assertNotEquals(baseline, ConfigWatcher.diskSignatures(directory));
  }

  @Test
  public void appliedBaselineUsesParsedConfigInsteadOfNewerDiskState() throws Exception {
    Path directory = temporaryFolder.newFolder("hiddenore-applied").toPath();
    String appliedConfig = "auto_pickup_drops = false\n";
    Map<String, String> baseline = ConfigWatcher.appliedSignatures(appliedConfig, ConfigWatcher.languageSignatures(directory));
    Files.writeString(directory.resolve("hiddenore.toml"), "auto_pickup_drops = true\n", StandardCharsets.UTF_8);

    assertNotEquals(baseline, ConfigWatcher.diskSignatures(directory));
  }

  @Test
  public void startupLanguageBaselinePreservesEditsDuringLoading() throws Exception {
    Path directory = temporaryFolder.newFolder("hiddenore-language-startup").toPath();
    Path languages = Files.createDirectory(directory.resolve("languages"));
    Path locale = languages.resolve("fr_FR.toml");
    String config = "language = 'fr_FR'\n";
    Files.writeString(directory.resolve("hiddenore.toml"), config);
    Files.writeString(locale, "no_permission = 'Before'\n");
    Map<String, String> initialLanguages = ConfigWatcher.languageSignatures(directory);
    Map<String, String> baseline = ConfigWatcher.appliedSignatures(config, initialLanguages);

    assertEquals(baseline, ConfigWatcher.diskSignatures(directory));
    Files.writeString(locale, "no_permission = 'After'\n");

    assertNotEquals(baseline, ConfigWatcher.diskSignatures(directory));
    assertEquals(initialLanguages.get("languages/fr_FR.toml"), baseline.get("languages/fr_FR.toml"));
  }

  @Test
  public void sameMetadataPersonalLanguageEditChangesDiskSignature() throws Exception {
    Path directory = temporaryFolder.newFolder("hiddenore-personal-language").toPath();
    Path languages = Files.createDirectory(directory.resolve("languages"));
    Path locale = languages.resolve("de_DE.toml");
    Files.writeString(directory.resolve("hiddenore.toml"), "language = 'en_US'\n");
    Files.writeString(locale, "no_permission = 'Before'\n");
    FileTime originalModified = Files.getLastModifiedTime(locale);
    Map<String, String> baseline = ConfigWatcher.diskSignatures(directory);

    Files.writeString(locale, "no_permission = 'Edited'\n");
    Files.setLastModifiedTime(locale, originalModified);

    assertNotEquals(baseline, ConfigWatcher.diskSignatures(directory));
  }

  @Test
  public void languageDirectoryCreationRemovalAndRecreationAreDetected() throws Exception {
    Path directory = temporaryFolder.newFolder("hiddenore-language-directory").toPath();
    Files.writeString(directory.resolve("hiddenore.toml"), "language = 'custom_locale'\n");
    Map<String, String> initial = ConfigWatcher.diskSignatures(directory);
    Path languages = Files.createDirectory(directory.resolve("languages"));
    Path locale = languages.resolve("custom_locale.toml");
    Files.writeString(locale, "no_permission = 'Custom'\n");
    Map<String, String> installed = ConfigWatcher.diskSignatures(directory);
    assertNotEquals(initial, installed);
    assertTrue(installed.containsKey("languages/custom_locale.toml"));

    Files.delete(locale);
    Files.delete(languages);
    assertEquals(initial, ConfigWatcher.diskSignatures(directory));
    Files.createDirectory(languages);
    Files.writeString(locale, "no_permission = 'Changed'\n");
    assertNotEquals(initial, ConfigWatcher.diskSignatures(directory));
    assertNotEquals(installed, ConfigWatcher.diskSignatures(directory));
  }

  @Test
  public void rememberedLocaleFilesAreRestrictedToCanonicalDirectChildren() {
    Map<String, String> signatures = Map.of(
        "languages/fr_FR.toml", "content:1",
        "languages/de_DE.toml", "content:2",
        "languages/en_US.toml", "content:3",
        "languages/custom_locale.toml", "content:4",
        "languages/nested/it_IT.toml", "content:5",
        "fr_FR.toml", "content:6");

    assertEquals(Set.of("fr_FR", "de_DE"), ConfigWatcher.installedCanonicalLocales(signatures));
  }

  @Test
  public void preferencesCachesAndNestedFilesDoNotChangeLanguageBaseline() throws Exception {
    Path directory = temporaryFolder.newFolder("hiddenore-language-exclusions").toPath();
    Path languages = Files.createDirectory(directory.resolve("languages"));
    Files.writeString(directory.resolve("hiddenore.toml"), "language = 'en_US'\n");
    Map<String, String> initial = ConfigWatcher.diskSignatures(directory);
    Files.writeString(languages.resolve("language-preferences.properties"), "player=fr_FR\n");
    Files.writeString(languages.resolve("catalog.cache"), "updated\n");
    Path cache = Files.createDirectory(languages.resolve("cache"));
    Files.writeString(cache.resolve("fr_FR.toml"), "no_permission = 'Cached'\n");

    assertEquals(initial, ConfigWatcher.diskSignatures(directory));
    assertTrue(ConfigWatcher.isWatchedFile(directory, languages, Path.of("custom_locale.toml")));
    assertTrue(ConfigWatcher.isWatchedFile(directory, directory, Path.of("hiddenore.toml")));
    assertFalse(ConfigWatcher.isWatchedFile(directory, languages, Path.of("language-preferences.properties")));
    assertFalse(ConfigWatcher.isWatchedFile(directory, languages, Path.of("cache/fr_FR.toml")));
    assertFalse(ConfigWatcher.isWatchedFile(directory, cache, Path.of("fr_FR.toml")));
    assertFalse(ConfigWatcher.isWatchedFile(directory, directory, Path.of("fr_FR.toml")));
  }

  @Test
  public void oversizedLocaleIsIdentifiedWithoutReadingItsContent() throws Exception {
    Path directory = temporaryFolder.newFolder("hiddenore-language-limit").toPath();
    Path languages = Files.createDirectory(directory.resolve("languages"));
    Files.write(languages.resolve("en_US.toml"), new byte[2 * 1024 * 1024 + 1]);

    assertTrue(ConfigWatcher.languageSignatures(directory).get("languages/en_US.toml").startsWith("oversized:"));
  }

  @Test
  public void sameMetadataEditChangesExactDiskSignature() throws Exception {
    Path directory = temporaryFolder.newFolder("hiddenore-same-metadata").toPath();
    Path configFile = directory.resolve("hiddenore.toml");
    String appliedConfig = "enabled = false\n";
    String changedConfig = "enabled = true \n";
    Files.writeString(configFile, appliedConfig, StandardCharsets.UTF_8);
    FileTime originalModified = Files.getLastModifiedTime(configFile);
    Map<String, String> baseline = ConfigWatcher.diskSignatures(directory);

    assertEquals(appliedConfig.length(), changedConfig.length());
    Files.writeString(configFile, changedConfig, StandardCharsets.UTF_8);
    Files.setLastModifiedTime(configFile, originalModified);

    assertNotEquals(baseline, ConfigWatcher.diskSignatures(directory));
  }

  @Test
  public void idleReconciliationHashesOnlyAtTwoAndAHalfSecondDeadline() {
    ConfigWatcher.SignatureReconciliation reconciliation = new ConfigWatcher.SignatureReconciliation(
        TimeUnit.MILLISECONDS.toNanos(2_500L)
    );
    AtomicInteger hashPasses = new AtomicInteger();
    reconciliation.reset(0L);

    assertFalse(reconciliation.reconcileIfDue(TimeUnit.SECONDS.toNanos(1L), () -> {
      hashPasses.incrementAndGet();
      return true;
    }));
    assertFalse(reconciliation.reconcileIfDue(TimeUnit.MILLISECONDS.toNanos(2_499L), () -> {
      hashPasses.incrementAndGet();
      return true;
    }));
    assertEquals(0, hashPasses.get());

    assertTrue(reconciliation.reconcileIfDue(TimeUnit.MILLISECONDS.toNanos(2_500L), () -> {
      hashPasses.incrementAndGet();
      return true;
    }));
    assertEquals(1, hashPasses.get());
    assertEquals(
        TimeUnit.SECONDS.toNanos(1L),
        reconciliation.pollTimeoutNanos(TimeUnit.SECONDS.toNanos(3L), TimeUnit.SECONDS.toNanos(1L))
    );
  }

  @Test
  public void deadlineLockIsReleasedBeforeContentHashing() throws Exception {
    ConfigWatcher.SignatureReconciliation reconciliation = new ConfigWatcher.SignatureReconciliation(1L);
    CountDownLatch hashing = new CountDownLatch(1);
    CountDownLatch releaseHashing = new CountDownLatch(1);
    CountDownLatch resetFinished = new CountDownLatch(1);
    reconciliation.reset(0L);
    Thread hashingThread = new Thread(() -> reconciliation.reconcileIfDue(1L, () -> {
      hashing.countDown();
      try {
        releaseHashing.await();
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
      }
      return false;
    }));
    hashingThread.start();
    assertTrue(hashing.await(1L, TimeUnit.SECONDS));

    Thread resetThread = new Thread(() -> {
      reconciliation.reset(2L);
      resetFinished.countDown();
    });
    resetThread.start();
    try {
      assertTrue(resetFinished.await(1L, TimeUnit.SECONDS));
    } finally {
      releaseHashing.countDown();
      hashingThread.join(1_000L);
      resetThread.join(1_000L);
    }
  }
}
