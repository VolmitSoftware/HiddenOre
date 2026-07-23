package art.arcane.hiddenore.service;

import java.util.concurrent.atomic.AtomicLong;

public final class HiddenOreTelemetry {
  private static final long RATE_WINDOW_MS = 1000L;
  private static final AtomicLong BREAKS = new AtomicLong();
  private static final AtomicLong DROPS = new AtomicLong();
  private static final AtomicLong VEIN_DISCOVERIES = new AtomicLong();
  private static final AtomicLong VEIN_CHUNK_COMPUTES = new AtomicLong();
  private static final AtomicLong PDC_READS = new AtomicLong();
  private static final AtomicLong PDC_WRITES = new AtomicLong();
  private static final AtomicLong ORE_REMOVAL_BLOCKS = new AtomicLong();
  private static final AtomicLong CONFIG_RELOADS = new AtomicLong();
  private static final Object RATE_LOCK = new Object();
  private static long windowStartMs;
  private static long windowBreaks;
  private static long windowDrops;
  private static long windowVeinDiscoveries;
  private static long windowVeinChunkComputes;
  private static long windowPdcReads;
  private static long windowPdcWrites;
  private static long windowOreRemovalBlocks;
  private static volatile double breaksPerSecond;
  private static volatile double dropsPerSecond;
  private static volatile double veinDiscoveriesPerSecond;
  private static volatile double veinChunkComputesPerSecond;
  private static volatile double pdcReadsPerSecond;
  private static volatile double pdcWritesPerSecond;
  private static volatile double oreRemovalBlocksPerSecond;

  private HiddenOreTelemetry() {
  }

  public static void countBreak() {
    BREAKS.incrementAndGet();
  }

  public static void countDrop() {
    DROPS.incrementAndGet();
  }

  public static void countVeinDiscovery() {
    VEIN_DISCOVERIES.incrementAndGet();
  }

  public static void countVeinChunkCompute() {
    VEIN_CHUNK_COMPUTES.incrementAndGet();
  }

  public static void countPdcRead() {
    PDC_READS.incrementAndGet();
  }

  public static void countPdcWrite() {
    PDC_WRITES.incrementAndGet();
  }

  public static void addOreRemovalBlocks(long blocks) {
    if (blocks > 0L) {
      ORE_REMOVAL_BLOCKS.addAndGet(blocks);
    }
  }

  public static void countConfigReload() {
    CONFIG_RELOADS.incrementAndGet();
  }

  public static long configReloadsTotal() {
    return CONFIG_RELOADS.get();
  }

  public static double breaksPerSecond(long now) {
    refreshRates(now);
    return breaksPerSecond;
  }

  public static double dropsPerSecond(long now) {
    refreshRates(now);
    return dropsPerSecond;
  }

  public static double veinDiscoveriesPerSecond(long now) {
    refreshRates(now);
    return veinDiscoveriesPerSecond;
  }

  public static double veinChunkComputesPerSecond(long now) {
    refreshRates(now);
    return veinChunkComputesPerSecond;
  }

  public static double pdcReadsPerSecond(long now) {
    refreshRates(now);
    return pdcReadsPerSecond;
  }

  public static double pdcWritesPerSecond(long now) {
    refreshRates(now);
    return pdcWritesPerSecond;
  }

  public static double oreRemovalBlocksPerSecond(long now) {
    refreshRates(now);
    return oreRemovalBlocksPerSecond;
  }

  public static void clear() {
    synchronized (RATE_LOCK) {
      BREAKS.set(0L);
      DROPS.set(0L);
      VEIN_DISCOVERIES.set(0L);
      VEIN_CHUNK_COMPUTES.set(0L);
      PDC_READS.set(0L);
      PDC_WRITES.set(0L);
      ORE_REMOVAL_BLOCKS.set(0L);
      CONFIG_RELOADS.set(0L);
      windowStartMs = 0L;
      windowBreaks = 0L;
      windowDrops = 0L;
      windowVeinDiscoveries = 0L;
      windowVeinChunkComputes = 0L;
      windowPdcReads = 0L;
      windowPdcWrites = 0L;
      windowOreRemovalBlocks = 0L;
      breaksPerSecond = 0D;
      dropsPerSecond = 0D;
      veinDiscoveriesPerSecond = 0D;
      veinChunkComputesPerSecond = 0D;
      pdcReadsPerSecond = 0D;
      pdcWritesPerSecond = 0D;
      oreRemovalBlocksPerSecond = 0D;
    }
  }

  private static void refreshRates(long now) {
    synchronized (RATE_LOCK) {
      if (windowStartMs == 0L) {
        windowStartMs = now;
        windowBreaks = BREAKS.get();
        windowDrops = DROPS.get();
        windowVeinDiscoveries = VEIN_DISCOVERIES.get();
        windowVeinChunkComputes = VEIN_CHUNK_COMPUTES.get();
        windowPdcReads = PDC_READS.get();
        windowPdcWrites = PDC_WRITES.get();
        windowOreRemovalBlocks = ORE_REMOVAL_BLOCKS.get();
        return;
      }

      long elapsed = now - windowStartMs;
      if (elapsed < RATE_WINDOW_MS) {
        return;
      }

      long breaks = BREAKS.get();
      long drops = DROPS.get();
      long veinDiscoveries = VEIN_DISCOVERIES.get();
      long veinChunkComputes = VEIN_CHUNK_COMPUTES.get();
      long pdcReads = PDC_READS.get();
      long pdcWrites = PDC_WRITES.get();
      long oreRemovalBlocks = ORE_REMOVAL_BLOCKS.get();
      double seconds = elapsed / 1000D;

      breaksPerSecond = (breaks - windowBreaks) / seconds;
      dropsPerSecond = (drops - windowDrops) / seconds;
      veinDiscoveriesPerSecond = (veinDiscoveries - windowVeinDiscoveries) / seconds;
      veinChunkComputesPerSecond = (veinChunkComputes - windowVeinChunkComputes) / seconds;
      pdcReadsPerSecond = (pdcReads - windowPdcReads) / seconds;
      pdcWritesPerSecond = (pdcWrites - windowPdcWrites) / seconds;
      oreRemovalBlocksPerSecond = (oreRemovalBlocks - windowOreRemovalBlocks) / seconds;

      windowStartMs = now;
      windowBreaks = breaks;
      windowDrops = drops;
      windowVeinDiscoveries = veinDiscoveries;
      windowVeinChunkComputes = veinChunkComputes;
      windowPdcReads = pdcReads;
      windowPdcWrites = pdcWrites;
      windowOreRemovalBlocks = oreRemovalBlocks;
    }
  }
}
