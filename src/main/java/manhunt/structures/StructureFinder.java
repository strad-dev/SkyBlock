package manhunt.structures;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

public final class StructureFinder {
	/** Same radius as /locate, in grid cells. */
	public static final int SEARCH_RINGS = 100;

	private static final int MIN_OCEAN_FLOOR_Y = 41;

	public record Found(String type, BlockPos pos, double distance, StructureStart start) {}

	private record Grid(RandomSpreadStructurePlacement spread, Map<Holder<Structure>, String> structures) {}

	private record Cell(Grid grid, ChunkPos chunk, BlockPos pos, double distSq) {}

	private final StructureWorld world;
	private final double x;
	private final double z;
	private final double maxDistSq;
	private final int chunkX;
	private final int chunkZ;
	private final long seed;
	private final List<Grid> grids = new ArrayList<>();
	private final int[] next;
	private final PriorityQueue<Cell> cells = new PriorityQueue<>(Comparator.comparingDouble(Cell::distSq));

	public StructureFinder(StructureWorld world, double x, double z, double maxDistance) {
		this.world = world;
		this.x = x;
		this.z = z;
		this.maxDistSq = maxDistance * maxDistance;
		this.chunkX = SectionPos.blockToSectionCoord(x);
		this.chunkZ = SectionPos.blockToSectionCoord(z);
		ChunkGeneratorStructureState state = world.structureState();
		this.seed = state.getLevelSeed();
		Map<RandomSpreadStructurePlacement, Map<Holder<Structure>, String>> byGrid = new LinkedHashMap<>();
		for(Map.Entry<Holder<Structure>, String> entry : interest(world.registryAccess().lookupOrThrow(Registries.STRUCTURE)).entrySet()) {
			for(StructurePlacement placement : state.getPlacementsForStructure(entry.getKey())) {
				if(placement instanceof RandomSpreadStructurePlacement spread) {
					byGrid.computeIfAbsent(spread, k -> new LinkedHashMap<>()).put(entry.getKey(), entry.getValue());
				}
			}
		}
		byGrid.forEach((spread, structures) -> grids.add(new Grid(spread, structures)));
		this.next = new int[grids.size()];
	}

	/**
	 * Next structure by distance, or null. Not findNearestMapStructure: it returns the first hit in the first ring
	 * with one, so a portal 428 away beat one 134 away in the next ring. Grids advance together, nearest ring first,
	 * and a cell's presence check (it samples terrain noise) only runs once nothing unsearched could be closer.
	 */
	public Found next() {
		while(true) {
			int pick = -1;
			double closest = 0;
			for(int i = 0; i < grids.size(); i++) {
				if(next[i] > SEARCH_RINGS) continue;
				// Ring r is at least (r - 1) * spacing chunks away on one axis.
				double bound = Math.max(0, next[i] - 1) * grids.get(i).spread().spacing() * 16.0;
				if(bound * bound > maxDistSq) continue;
				if(pick == -1 || bound < closest) {
					pick = i;
					closest = bound;
				}
			}
			Cell cell = cells.peek();
			if(cell != null && (pick == -1 || cell.distSq() <= closest * closest)) {
				cells.poll();
				for(Map.Entry<Holder<Structure>, String> entry : cell.grid().structures().entrySet()) {
					StructureStart start = world.startAt(entry.getKey().value(), cell.grid().spread(), cell.chunk());
					if(start != null && reasonableHeight(entry.getKey(), entry.getValue(), start)) return new Found(entry.getValue(), cell.pos(), Math.sqrt(cell.distSq()), start);
				}
				continue;
			}
			if(pick == -1) return null;
			expand(pick);
		}
	}

	private void expand(int i) {
		Grid grid = grids.get(i);
		int spacing = grid.spread().spacing();
		int ring = next[i]++;
		for(int dx = -ring; dx <= ring; dx++) {
			for(int dz = -ring; dz <= ring; dz++) {
				if(Math.abs(dx) != ring && Math.abs(dz) != ring) continue;
				ChunkPos chunk = grid.spread().getPotentialStructureChunk(seed, chunkX + spacing * dx, chunkZ + spacing * dz);
				BlockPos pos = grid.spread().getLocatePos(chunk);
				double offX = pos.getX() - x;
				double offZ = pos.getZ() - z;
				double distSq = offX * offX + offZ * offZ;
				if(distSq <= maxDistSq) cells.add(new Cell(grid, chunk, pos, distSq));
			}
		}
	}

	private boolean reasonableHeight(Holder<Structure> structure, String type, StructureStart start) {
		BoundingBox box = start.getBoundingBox();
		if(structure.is(BuiltinStructures.RUINED_PORTAL_OCEAN) || structure.is(BuiltinStructures.SHIPWRECK)) {
			return shallowOcean(box.getCenter().getX(), box.getCenter().getZ());
		}
		if(type.equals(LootRule.RUINED_PORTAL.type)) return box.maxY() >= world.generator().getSeaLevel();
		if(type.equals(LootRule.OCEAN_RUIN.type) && start.getPieces().getFirst() instanceof TemplateStructurePiece ruin) {
			return shallowOcean(ruin.templatePosition().getX(), ruin.templatePosition().getZ());
		}
		return true;
	}

	private boolean shallowOcean(int x, int z) {
		int floor = world.generator().getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, world.heightAccessor(), world.randomState());
		if(floor < MIN_OCEAN_FLOOR_Y) return false;
		Holder<Biome> biome = world.generator().getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(floor),
				QuartPos.fromBlock(z), world.randomState().sampler());
		return !biome.is(BiomeTags.IS_DEEP_OCEAN);
	}

	/** Structure to display name. The nether ruined portal is left out: it never generates in the overworld. */
	private static Map<Holder<Structure>, String> interest(HolderGetter<Structure> structures) {
		Map<Holder<Structure>, String> out = new LinkedHashMap<>();
		name(out, structures, "Desert Pyramid", BuiltinStructures.DESERT_PYRAMID);
		name(out, structures, LootRule.OCEAN_RUIN.type, BuiltinStructures.OCEAN_RUIN_COLD, BuiltinStructures.OCEAN_RUIN_WARM);
		name(out, structures, "Pillager Outpost", BuiltinStructures.PILLAGER_OUTPOST);
		name(out, structures, LootRule.RUINED_PORTAL.type, BuiltinStructures.RUINED_PORTAL_STANDARD, BuiltinStructures.RUINED_PORTAL_DESERT,
				BuiltinStructures.RUINED_PORTAL_JUNGLE, BuiltinStructures.RUINED_PORTAL_SWAMP, BuiltinStructures.RUINED_PORTAL_MOUNTAIN,
				BuiltinStructures.RUINED_PORTAL_OCEAN);
		name(out, structures, LootRule.SHIPWRECK.type, BuiltinStructures.SHIPWRECK, BuiltinStructures.SHIPWRECK_BEACHED);
		name(out, structures, LootRule.VILLAGE.type, BuiltinStructures.VILLAGE_PLAINS, BuiltinStructures.VILLAGE_DESERT,
				BuiltinStructures.VILLAGE_SAVANNA, BuiltinStructures.VILLAGE_SNOWY, BuiltinStructures.VILLAGE_TAIGA);
		return out;
	}

	@SafeVarargs
	private static void name(Map<Holder<Structure>, String> out, HolderGetter<Structure> structures, String name, ResourceKey<Structure>... keys) {
		for(ResourceKey<Structure> key : keys) {
			out.put(structures.getOrThrow(key), name);
		}
	}
}
