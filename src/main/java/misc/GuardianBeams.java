package misc;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.*;

public class GuardianBeams {
	private static final double MIN_SCALE = 0.0625;
	private final World world;
	private final Guardian[] guardians;
	private final ArmorStand[] targets;
	private final Set<UUID> viewers = new HashSet<>();
	private boolean removed = false;

	public GuardianBeams(World world, int count) {
		this.world = world;
		ServerLevel level = ((CraftWorld) world).getHandle();
		guardians = new Guardian[count];
		targets = new ArmorStand[count];
		for(int i = 0; i < count; i++) {
			ArmorStand target = new ArmorStand(EntityType.ARMOR_STAND, level);
			target.setMarker(true);
			target.setInvisible(true);
			target.setNoGravity(true);
			Guardian guardian = new Guardian(EntityType.GUARDIAN, level);
			guardian.setInvisible(true);
			guardian.setSilent(true);
			guardian.setNoGravity(true);
			guardian.setActiveAttackTarget(target.getId());
			guardian.getAttribute(Attributes.SCALE).setBaseValue(MIN_SCALE);
			guardian.refreshDimensions();
			targets[i] = target;
			guardians[i] = guardian;
		}
	}

	public void set(int i, Vector from, Vector to) {
		guardians[i].setPos(from.getX(), from.getY() - guardians[i].getEyeHeight(), from.getZ());
		targets[i].setPos(to.getX(), to.getY(), to.getZ());
	}

	public void update() {
		if(removed) return;
		List<Packet<? super ClientGamePacketListener>> spawn = new ArrayList<>();
		List<Packet<? super ClientGamePacketListener>> move = new ArrayList<>();
		for(int i = 0; i < guardians.length; i++) {
			for(Entity e : new Entity[]{targets[i], guardians[i]}) {
				Vec3 pos = e.position();
				spawn.add(new ClientboundAddEntityPacket(e.getId(), e.getUUID(), pos.x, pos.y, pos.z, 0.0F, 0.0F, e.getType(), 0, Vec3.ZERO, 0.0));
				spawn.add(new ClientboundSetEntityDataPacket(e.getId(), e.getEntityData().getNonDefaultValues()));
				move.add(ClientboundTeleportEntityPacket.teleport(e.getId(), PositionMoveRotation.of(e), Set.of(), false));
			}
			spawn.add(new ClientboundUpdateAttributesPacket(guardians[i].getId(), List.of(guardians[i].getAttribute(Attributes.SCALE))));
		}
		ClientboundBundlePacket spawnBundle = new ClientboundBundlePacket(spawn);
		ClientboundBundlePacket moveBundle = new ClientboundBundlePacket(move);

		Set<UUID> present = new HashSet<>();
		for(Player p : world.getPlayers()) {
			present.add(p.getUniqueId());
			((CraftPlayer) p).getHandle().connection.send(viewers.add(p.getUniqueId()) ? spawnBundle : moveBundle);
		}
		viewers.retainAll(present);
	}

	public void remove() {
		if(removed) return;
		removed = true;
		int[] ids = new int[guardians.length * 2];
		for(int i = 0; i < guardians.length; i++) {
			ids[2 * i] = guardians[i].getId();
			ids[2 * i + 1] = targets[i].getId();
		}
		ClientboundRemoveEntitiesPacket packet = new ClientboundRemoveEntitiesPacket(ids);
		for(UUID id : viewers) {
			Player p = Bukkit.getPlayer(id);
			if(p != null) {
				((CraftPlayer) p).getHandle().connection.send(packet);
			}
		}
		viewers.clear();
	}
}
