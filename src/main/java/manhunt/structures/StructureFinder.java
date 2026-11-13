package manhunt.structures;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
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

	/** Share of footprint columns that must be open to the sky. */
	private static final double MIN_EXPOSED = 0.25;

	public record Found(String type, BlockPos pos, double distance, StructureStart start) {}

	private record Grid(RandomSpreadStructurePlacement spread, Map<Holder<Structure>, String> structures) {}

	private record Cell(Grid grid, ChunkPos chunk, BlockPos pos, double distSq) {}

	private final ServerLevel world;
	private final double x;
	private final double z;
	private final double maxDistSq;
	private final int chunkX;
	private final int chunkZ;
	private final long seed;
	private final List<Grid> grids = new ArrayList<>();
	private final int[] next;
	private final PriorityQueue<Cell> cells = new PriorityQueue<>(Comparator.comparingDouble(Cell::distSq));

	public StructureFinder(ServerLevel world, double x, double z, double maxDistance) {
		this.world = world;
		this.x = x;
		this.z = z;
		this.maxDistSq = maxDistance * maxDistance;
		this.chunkX = SectionPos.blockToSectionCoord(x);
		this.chunkZ = SectionPos.blockToSectionCoord(z);
		ChunkGeneratorStructureState state = world.getChunkSource().getGeneratorState();
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
					StructureStart start = startAt(entry.getKey().value(), cell.grid().spread(), cell.chunk());
					if(start != null) return new Found(entry.getValue(), cell.pos(), Math.sqrt(cell.distSq()), start);
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

	/** Vanilla's locate check (private there), plus {@link #exposed}. */
	private StructureStart startAt(Structure structure, StructurePlacement placement, ChunkPos chunk) {
		StructureCheckResult result = world.structureManager().checkStructurePresence(chunk, structure, placement, false);
		if(result == StructureCheckResult.START_NOT_PRESENT) return null;
		ChunkAccess access = world.getChunk(chunk.x(), chunk.z(), ChunkStatus.STRUCTURE_STARTS);
		StructureStart start = world.structureManager().getStartForStructure(SectionPos.bottomOf(access), structure, access);
		return start != null && start.isValid() && exposed(start) ? start : null;
	}

	/** Enough of the footprint reaches the predicted surface. Water counts as sky; trees don't exist yet. */
	private boolean exposed(StructureStart start) {
		ChunkGenerator generator = world.getChunkSource().getGenerator();
		RandomState random = world.getChunkSource().randomState();
		BoundingBox bounds = start.getBoundingBox();
		int columns = 0;
		int open = 0;
		for(int bx = bounds.minX(); bx <= bounds.maxX(); bx += 4) {
			for(int bz = bounds.minZ(); bz <= bounds.maxZ(); bz += 4) {
				int top = Integer.MIN_VALUE;
				for(StructurePiece piece : start.getPieces()) {
					BoundingBox box = piece.getBoundingBox();
					if(box.intersects(bx, bz, bx, bz)) top = Math.max(top, box.maxY());
				}
				if(top == Integer.MIN_VALUE) continue;
				columns++;
				if(top + 1 >= generator.getBaseHeight(bx, bz, Heightmap.Types.OCEAN_FLOOR_WG, world, random)) open++;
			}
		}
		return columns > 0 && open >= columns * MIN_EXPOSED;
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
		name(out, structures, "Shipwreck", BuiltinStructures.SHIPWRECK, BuiltinStructures.SHIPWRECK_BEACHED);
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
