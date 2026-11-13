package manhunt.structures;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public enum LootRule {
	RUINED_PORTAL("Ruined Portal", Set.of(BuiltInLootTables.RUINED_PORTAL),
			s -> s.is(Items.GOLDEN_AXE) || s.is(Items.GOLDEN_SWORD)),
	VILLAGE("Village", Set.of(BuiltInLootTables.VILLAGE_WEAPONSMITH),
			s -> s.is(Items.IRON_SWORD) || s.is(ItemTags.SPEARS)),
	OCEAN_RUIN("Ocean Ruin", Set.of(BuiltInLootTables.UNDERWATER_RUIN_SMALL, BuiltInLootTables.UNDERWATER_RUIN_BIG),
			s -> s.is(Items.STONE_AXE) || s.is(Items.STONE_SPEAR));

	public record Match(BlockPos chest, ItemStack item) {}

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

	public Match match(ServerLevel world, StructureStart start) {
		List<BoundingBox> boxes = boxes(start);
		for(ChunkPos chunk : chunks(start)) {
			for(BlockEntity entity : world.getChunk(chunk.x(), chunk.z()).getBlockEntities().values()) {
				if(!(entity instanceof RandomizableContainer container)) continue;
				ResourceKey<LootTable> key = container.getLootTable();
				if(key == null || !tables.contains(key)) continue;
				BlockPos pos = entity.getBlockPos();
				if(boxes.stream().noneMatch(box -> box.isInside(pos))) continue;
				LootParams params = new LootParams.Builder(world)
						.withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
						.create(LootContextParamSets.CHEST);
				LootTable table = world.getServer().reloadableRegistries().getLootTable(key);
				for(ItemStack stack : table.getRandomItems(params, RandomSource.create(container.getLootTableSeed()))) {
					if(wanted.test(stack)) return new Match(pos, stack);
				}
			}
		}
		return null;
	}

	private List<BoundingBox> boxes(StructureStart start) {
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
