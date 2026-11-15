package manhunt.structures;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

public enum LootRule {
	RUINED_PORTAL("Ruined Portal", Set.of(BuiltInLootTables.RUINED_PORTAL),
			s -> s.is(Items.GOLDEN_AXE) || s.is(Items.GOLDEN_SWORD)),
	VILLAGE("Village", Set.of(BuiltInLootTables.VILLAGE_WEAPONSMITH),
			s -> s.is(Items.IRON_SWORD) || s.is(ItemTags.SPEARS)),
	OCEAN_RUIN("Ocean Ruin", Set.of(BuiltInLootTables.UNDERWATER_RUIN_SMALL, BuiltInLootTables.UNDERWATER_RUIN_BIG),
			s -> s.is(Items.STONE_AXE) || s.is(Items.STONE_SPEAR)),
	SHIPWRECK("Shipwreck", Set.of(BuiltInLootTables.SHIPWRECK_MAP, BuiltInLootTables.SHIPWRECK_SUPPLY, BuiltInLootTables.SHIPWRECK_TREASURE),
			s -> !s.isEmpty());

	public record Match(BlockPos chest, ItemStack item, boolean wanted) {}

	public final String type;
	private final Set<ResourceKey<LootTable>> tables;
	private final Predicate<ItemStack> wanted;

	LootRule(String type, Set<ResourceKey<LootTable>> tables, Predicate<ItemStack> wanted) {
		this.type = type;
		this.tables = tables;
		this.wanted = wanted;
	}

	public static LootRule of(String type) {
		for(LootRule rule : values()) {
			if(rule.type.equals(type)) return rule;
		}
		return null;
	}

	public boolean rolls(ResourceKey<LootTable> table) {
		return tables.contains(table);
	}

	public boolean anyItem() {
		return this == SHIPWRECK;
	}

	public boolean tierOne() {
		return this != SHIPWRECK;
	}

	public static int tier(String type, Match match) {
		LootRule rule = of(type);
		if(rule == null) return 2;
		if(match != null) return match.wanted() && rule.tierOne() ? 1 : 2;
		return rule == VILLAGE ? 2 : 0;
	}

	public boolean reachable(Function<BlockPos, BlockState> blocks, int maxY, BlockPos chest) {
		if(this == VILLAGE) return true;
		if(!open(blocks.apply(chest.above()))) return false;
		if(this != OCEAN_RUIN && Direction.Plane.HORIZONTAL.stream().noneMatch(side -> open(blocks.apply(chest.relative(side))))) return false;
		BlockPos.MutableBlockPos pos = chest.above(2).mutable();
		for(; pos.getY() <= maxY; pos.move(Direction.UP)) {
			BlockState state = blocks.apply(pos.immutable());
			if(open(state)) continue;
			return !state.is(Blocks.SAND) && !state.is(Blocks.RED_SAND) && !state.is(Blocks.GRAVEL)
					&& !state.is(Blocks.SUSPICIOUS_SAND) && !state.is(Blocks.SUSPICIOUS_GRAVEL);
		}
		return true;
	}

	private static boolean open(BlockState state) {
		return state.is(BlockTags.REPLACEABLE) && !state.is(Blocks.LAVA) || state.is(Blocks.KELP) || state.is(Blocks.KELP_PLANT)
				|| state.is(BlockTags.CORALS) || state.is(BlockTags.WALL_CORALS) || state.is(Blocks.SEA_PICKLE);
	}

	public Match best(BlockPos chest, Iterable<ItemStack> loot) {
		ItemStack first = null;
		for(ItemStack stack : loot) {
			if(wanted.test(stack)) return new Match(chest, stack, true);
			if(first == null && !stack.isEmpty()) first = stack;
		}
		return first == null ? null : new Match(chest, first, false);
	}

	public static Match better(Match a, Match b) {
		if(a == null) return b;
		if(b == null) return a;
		return b.wanted() && !a.wanted() ? b : a;
	}

	public List<ChunkPos> chunks(StructureStart start) {
		Set<ChunkPos> out = new LinkedHashSet<>();
		for(BoundingBox box : boxes(start)) {
			for(int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
				for(int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
					out.add(new ChunkPos(cx, cz));
				}
			}
		}
		return new ArrayList<>(out);
	}

	public static boolean under(List<BoundingBox> boxes, BlockPos pos) {
		for(BoundingBox box : boxes) {
			if(pos.getX() >= box.minX() && pos.getX() <= box.maxX() && pos.getZ() >= box.minZ() && pos.getZ() <= box.maxZ()) return true;
		}
		return false;
	}

	public List<BoundingBox> boxes(StructureStart start) {
		List<BoundingBox> out = new ArrayList<>();
		for(StructurePiece piece : start.getPieces()) {
			if(holdsChest(piece)) out.add(piece.getBoundingBox());
		}
		return out;
	}

	private boolean holdsChest(StructurePiece piece) {
		if(this != VILLAGE) return true;
		return piece instanceof PoolElementStructurePiece pool && pool.getElement() instanceof SinglePoolElement single
				&& single.getTemplateLocation().getPath().contains("weaponsmith");
	}
}
