package org.albi.core.modules.Duels.polar;

import live.minehub.polarpaper.BlockSelector;
import live.minehub.polarpaper.Polar;
import live.minehub.polarpaper.PolarReader;
import live.minehub.polarpaper.PolarWorld;
import live.minehub.polarpaper.PolarWorldAccess;
import live.minehub.polarpaper.PolarWriter;
import live.minehub.polarpaper.source.FilePolarSource;
import live.minehub.polarpaper.source.PolarSource;
import org.bukkit.World;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/** Keep optional PolarPaper types out of classes loaded by schematic-only servers. */
final class PolarBackend {
    PolarBackend() throws NoSuchMethodException {
        FilePolarSource.class.getConstructor(Path.class);
        Polar.class.getMethod("loadWorld", PolarSource.class, String.class);
        Polar.class.getMethod("saveWorld", World.class, PolarSource.class);
        Polar.class.getMethod("createWorld", PolarWorld.class, String.class);
    }

    CompletableFuture<World> load(Path path, String name) {
        return Polar.loadWorld(new FilePolarSource(path), name);
    }

    void save(World world, Path path) {
        Polar.saveWorld(world, new FilePolarSource(path));
    }

    CompletableFuture<World> create(byte[] origin, String name) {
        return Polar.createWorld(PolarReader.read(origin), name);
    }

    byte[] encode(World world) {
        PolarWorld polarWorld = PolarWorld.convert(world, PolarWorldAccess.POLAR_PAPER_FEATURES, BlockSelector.ALL);
        return PolarWriter.write(polarWorld);
    }
}
