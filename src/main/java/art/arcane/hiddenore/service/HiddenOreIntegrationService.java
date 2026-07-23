package art.arcane.hiddenore.service;

import art.arcane.hiddenore.HiddenOre;
import art.arcane.hiddenore.generation.GenerationRules;
import art.arcane.hiddenore.vein.VeinConfig;
import art.arcane.volmlib.integration.IntegrationHandshakeRequest;
import art.arcane.volmlib.integration.IntegrationHandshakeResponse;
import art.arcane.volmlib.integration.IntegrationHeartbeat;
import art.arcane.volmlib.integration.IntegrationMetricDescriptor;
import art.arcane.volmlib.integration.IntegrationMetricSample;
import art.arcane.volmlib.integration.IntegrationMetricSchema;
import art.arcane.volmlib.integration.IntegrationProtocolNegotiator;
import art.arcane.volmlib.integration.IntegrationProtocolVersion;
import art.arcane.volmlib.integration.IntegrationServiceContract;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class HiddenOreIntegrationService implements IntegrationServiceContract {
  private static final Set<IntegrationProtocolVersion> SUPPORTED_PROTOCOLS = Set.of(
      new IntegrationProtocolVersion(1, 0),
      new IntegrationProtocolVersion(1, 1)
  );
  private static final Set<String> CAPABILITIES = Set.of(
      "handshake",
      "heartbeat",
      "metrics"
  );

  private final HiddenOre plugin;
  private volatile IntegrationProtocolVersion negotiatedProtocol = new IntegrationProtocolVersion(1, 1);

  public HiddenOreIntegrationService(HiddenOre plugin) {
    this.plugin = plugin;
  }

  public void register() {
    Bukkit.getServicesManager().register(IntegrationServiceContract.class, this, plugin, ServicePriority.Normal);
    plugin.getLogger().info("Integration provider registered for HiddenOre");
  }

  public void unregister() {
    Bukkit.getServicesManager().unregister(IntegrationServiceContract.class, this);
    HiddenOreTelemetry.clear();
  }

  @Override
  public String pluginId() {
    return "hiddenore";
  }

  @Override
  public String pluginVersion() {
    return plugin.getDescription().getVersion();
  }

  @Override
  public Set<IntegrationProtocolVersion> supportedProtocols() {
    return SUPPORTED_PROTOCOLS;
  }

  @Override
  public Set<String> capabilities() {
    return CAPABILITIES;
  }

  @Override
  public Set<IntegrationMetricDescriptor> metricDescriptors() {
    return IntegrationMetricSchema.descriptors().stream()
        .filter(descriptor -> descriptor.key().startsWith("hiddenore."))
        .collect(Collectors.toSet());
  }

  @Override
  public IntegrationHandshakeResponse handshake(IntegrationHandshakeRequest request) {
    long now = System.currentTimeMillis();
    if (request == null) {
      return new IntegrationHandshakeResponse(
          pluginId(), pluginVersion(), false, null,
          SUPPORTED_PROTOCOLS, CAPABILITIES, "missing request", now
      );
    }

    Optional<IntegrationProtocolVersion> negotiated = IntegrationProtocolNegotiator.negotiate(
        SUPPORTED_PROTOCOLS,
        request.supportedProtocols()
    );
    if (negotiated.isEmpty()) {
      return new IntegrationHandshakeResponse(
          pluginId(), pluginVersion(), false, null,
          SUPPORTED_PROTOCOLS, CAPABILITIES, "no-common-protocol", now
      );
    }

    negotiatedProtocol = negotiated.get();
    return new IntegrationHandshakeResponse(
        pluginId(), pluginVersion(), true, negotiatedProtocol,
        SUPPORTED_PROTOCOLS, CAPABILITIES, "ok", now
    );
  }

  @Override
  public IntegrationHeartbeat heartbeat() {
    return new IntegrationHeartbeat(negotiatedProtocol, true, System.currentTimeMillis(), "ok");
  }

  @Override
  public Map<String, IntegrationMetricSample> sampleMetrics(Set<String> metricKeys) {
    Set<String> requested = metricKeys == null || metricKeys.isEmpty()
        ? IntegrationMetricSchema.hiddenoreKeys()
        : metricKeys;
    long now = System.currentTimeMillis();
    Map<String, IntegrationMetricSample> out = new HashMap<>();

    for (String key : requested) {
      switch (key) {
        case IntegrationMetricSchema.HIDDENORE_BLOCKS_BROKEN_PER_SECOND ->
            out.put(key, available(key, HiddenOreTelemetry.breaksPerSecond(now), now));
        case IntegrationMetricSchema.HIDDENORE_DROPS_INJECTED_PER_SECOND ->
            out.put(key, available(key, HiddenOreTelemetry.dropsPerSecond(now), now));
        case IntegrationMetricSchema.HIDDENORE_VEINS_DISCOVERED_PER_SECOND ->
            out.put(key, available(key, HiddenOreTelemetry.veinDiscoveriesPerSecond(now), now));
        case IntegrationMetricSchema.HIDDENORE_VEIN_CHUNKS_COMPUTED_PER_SECOND ->
            out.put(key, available(key, HiddenOreTelemetry.veinChunkComputesPerSecond(now), now));
        case IntegrationMetricSchema.HIDDENORE_VEIN_CACHE_CHUNKS ->
            out.put(key, sampleVeinCacheChunks(now));
        case IntegrationMetricSchema.HIDDENORE_PDC_READS_PER_SECOND ->
            out.put(key, available(key, HiddenOreTelemetry.pdcReadsPerSecond(now), now));
        case IntegrationMetricSchema.HIDDENORE_PDC_WRITES_PER_SECOND ->
            out.put(key, available(key, HiddenOreTelemetry.pdcWritesPerSecond(now), now));
        case IntegrationMetricSchema.HIDDENORE_ORE_REMOVAL_ENABLED ->
            out.put(key, sampleOreRemovalEnabled(now));
        case IntegrationMetricSchema.HIDDENORE_ORE_REMOVAL_BLOCKS_PER_SECOND ->
            out.put(key, available(key, HiddenOreTelemetry.oreRemovalBlocksPerSecond(now), now));
        case IntegrationMetricSchema.HIDDENORE_SEEDED_MODE ->
            out.put(key, sampleSeededMode(now));
        case IntegrationMetricSchema.HIDDENORE_DROP_RULES ->
            out.put(key, sampleDropRules(now));
        case IntegrationMetricSchema.HIDDENORE_CONFIG_RELOADS_TOTAL ->
            out.put(key, available(key, HiddenOreTelemetry.configReloadsTotal(), now));
        default -> out.put(key, IntegrationMetricSample.unavailable(
            IntegrationMetricSchema.descriptor(key),
            "unsupported-key",
            now
        ));
      }
    }

    return out;
  }

  private IntegrationMetricSample sampleVeinCacheChunks(long now) {
    IntegrationMetricDescriptor descriptor = IntegrationMetricSchema.descriptor(IntegrationMetricSchema.HIDDENORE_VEIN_CACHE_CHUNKS);
    HiddenOre.RuntimeState runtime = plugin.runtimeStateOrNull();
    if (runtime == null) {
      return IntegrationMetricSample.unavailable(descriptor, "runtime-not-ready", now);
    }
    return IntegrationMetricSample.available(descriptor, runtime.veinGenerator().cachedChunkCount(), now);
  }

  private IntegrationMetricSample sampleOreRemovalEnabled(long now) {
    IntegrationMetricDescriptor descriptor = IntegrationMetricSchema.descriptor(IntegrationMetricSchema.HIDDENORE_ORE_REMOVAL_ENABLED);
    GenerationRules generationRules = plugin.getGenerationRules();
    if (generationRules == null) {
      return IntegrationMetricSample.unavailable(descriptor, "generation-rules-not-ready", now);
    }
    if (plugin.runtimeStateOrNull() == null) {
      return IntegrationMetricSample.unavailable(descriptor, "runtime-not-ready", now);
    }
    return IntegrationMetricSample.available(descriptor, generationRules.isEnabled() ? 1D : 0D, now);
  }

  private IntegrationMetricSample sampleSeededMode(long now) {
    IntegrationMetricDescriptor descriptor = IntegrationMetricSchema.descriptor(IntegrationMetricSchema.HIDDENORE_SEEDED_MODE);
    HiddenOre.RuntimeState runtime = plugin.runtimeStateOrNull();
    if (runtime == null) {
      return IntegrationMetricSample.unavailable(descriptor, "runtime-not-ready", now);
    }
    boolean seeded = runtime.ruleManager().getVeinConfig().generation == VeinConfig.GenerationMode.SEEDED;
    return IntegrationMetricSample.available(descriptor, seeded ? 1D : 0D, now);
  }

  private IntegrationMetricSample sampleDropRules(long now) {
    IntegrationMetricDescriptor descriptor = IntegrationMetricSchema.descriptor(IntegrationMetricSchema.HIDDENORE_DROP_RULES);
    HiddenOre.RuntimeState runtime = plugin.runtimeStateOrNull();
    if (runtime == null) {
      return IntegrationMetricSample.unavailable(descriptor, "runtime-not-ready", now);
    }
    return IntegrationMetricSample.available(descriptor, runtime.ruleManager().getAllDropRules().size(), now);
  }

  private IntegrationMetricSample available(String key, double value, long now) {
    return IntegrationMetricSample.available(IntegrationMetricSchema.descriptor(key), value, now);
  }
}
