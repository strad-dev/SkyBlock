package manhunt.structures;

import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

public interface StructureWorld {
	RegistryAccess registryAccess();

	ChunkGeneratorStructureState structureState();

	ChunkGenerator generator();

	RandomState randomState();

	LevelHeightAccessor heightAccessor();

	StructureStart startAt(Structure structure, StructurePlacement placement, ChunkPos chunk);
}
