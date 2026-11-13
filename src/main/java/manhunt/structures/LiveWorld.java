package manhunt.structures;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public record LiveWorld(ServerLevel level) implements StructureWorld {
	@Override
	public RegistryAccess registryAccess() {
		return level.registryAccess();
	}

	@Override
	public ChunkGeneratorStructureState structureState() {
		return level.getChunkSource().getGeneratorState();
	}

	@Override
	public ChunkGenerator generator() {
		return level.getChunkSource().getGenerator();
	}

	@Override
	public RandomState randomState() {
		return level.getChunkSource().randomState();
	}

	@Override
	public LevelHeightAccessor heightAccessor() {
		return level;
	}

	/** Vanilla's locate check (private there). */
	@Override
	public StructureStart startAt(Structure structure, StructurePlacement placement, ChunkPos chunk) {
		StructureCheckResult result = level.structureManager().checkStructurePresence(chunk, structure, placement, false);
		if(result == StructureCheckResult.START_NOT_PRESENT) return null;
		ChunkAccess access = level.getChunk(chunk.x(), chunk.z(), ChunkStatus.STRUCTURE_STARTS);
		StructureStart start = level.structureManager().getStartForStructure(SectionPos.bottomOf(access), structure, access);
		return start != null && start.isValid() ? start : null;
	}

	public LootRule.Match match(LootRule rule, StructureStart start) {
		List<BoundingBox> boxes = rule.boxes(start);
		for(ChunkPos chunk : rule.chunks(start)) {
			for(BlockEntity entity : level.getChunk(chunk.x(), chunk.z()).getBlockEntities().values()) {
				if(!(entity instanceof RandomizableContainer container)) continue;
				ResourceKey<LootTable> key = container.getLootTable();
				if(key == null || !rule.rolls(key)) continue;
				BlockPos pos = entity.getBlockPos();
				if(boxes.stream().noneMatch(box -> box.isInside(pos)) || !rule.reachable(level::getBlockState, level.getMaxY(), pos)) continue;
				LootParams params = new LootParams.Builder(level)
						.withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
						.create(LootContextParamSets.CHEST);
				LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
				LootRule.Match match = rule.firstWanted(pos, table.getRandomItems(params, RandomSource.create(container.getLootTableSeed())));
				if(match != null) return match;
			}
		}
		return null;
	}
}
