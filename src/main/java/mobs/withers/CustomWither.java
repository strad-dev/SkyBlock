package mobs.withers;

import misc.Random;
import mobs.CustomMob;
import org.bukkit.World;
import org.bukkit.entity.WitherSkull;

public interface CustomWither extends CustomMob {
	static CustomWither spawnRandom(World world) {
		Random random = Random.of(world);
		switch(random.nextInt(4)) {
			case 0 -> {
				return new Maxor();
			}
			case 1 -> {
				return new Storm();
			}
			case 2 -> {
				return new Goldor();
			}
			case 3 -> {
				return new Necron();
			}
		}
		return new Default();
	}

	void whenShootingSkull(WitherSkull skull);
}