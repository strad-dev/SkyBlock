package testsupport;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.key.Key;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stands in for the server's registries so classes holding {@code Enchantment.SHARPNESS}-style constants
 * ({@code CustomDamage}, {@code Utils}) can load in a test. Paper finds its {@link RegistryAccess} with a
 * ServiceLoader and takes the first provider; test classes come first on surefire's classpath, so
 * {@code src/test/resources/META-INF/services} wins over paper-nms's real one.
 *
 * <p>Only Enchantment and Attribute hand out entries, as inert stand-ins keyed by id (two lookups of one id
 * get the same object, so equality and map keys work). Any other registry is empty and throws on lookup, so a
 * test reaching one fails loudly instead of reading a fake.
 */
public final class FakeRegistryAccess implements RegistryAccess {
	private static final Map<Class<?>, Registry<?>> REGISTRIES = new ConcurrentHashMap<>();

	@Override
	@SuppressWarnings("deprecation")
	public <T extends Keyed> Registry<T> getRegistry(Class<T> type) {
		return registry(type);
	}

	@Override
	public <T extends Keyed> Registry<T> getRegistry(RegistryKey<T> key) {
		if(key == RegistryKey.ENCHANTMENT) return registry(Enchantment.class);
		if(key == RegistryKey.ATTRIBUTE) return registry(Attribute.class);
		return registry(Keyed.class);
	}

	// Not computeIfAbsent: the first proxy runs Registry's static init, which asks for its own registries back.
	@SuppressWarnings("unchecked")
	private static <T extends Keyed> Registry<T> registry(Class<?> type) {
		Registry<?> registry = REGISTRIES.get(type);
		if(registry == null) {
			REGISTRIES.putIfAbsent(type, create(type));
			registry = REGISTRIES.get(type);
		}
		return (Registry<T>) registry;
	}

	private static Registry<?> create(Class<?> type) {
		Map<String, Keyed> entries = new ConcurrentHashMap<>();
		return (Registry<?>) Proxy.newProxyInstance(Registry.class.getClassLoader(), new Class<?>[]{Registry.class},
				(proxy, method, args) -> switch(method.getName()) {
					case "get", "getOrThrow" -> entries.computeIfAbsent(id(args[0]), id -> entry(type, id));
					case "iterator" -> entries.values().iterator();
					case "stream" -> entries.values().stream();
					case "hashCode" -> System.identityHashCode(proxy);
					case "equals" -> proxy == args[0];
					case "toString" -> "FakeRegistry[" + type.getSimpleName() + "]";
					default -> throw new UnsupportedOperationException("FakeRegistry." + method.getName());
				});
	}

	private static String id(Object key) {
		return key instanceof Key k ? k.asString() : String.valueOf(key);
	}

	private static Keyed entry(Class<?> type, String id) {
		NamespacedKey key = NamespacedKey.fromString(id);
		if(type == Enchantment.class) return new FakeEnchantment(key);
		if(type == Attribute.class) return fakeAttribute(key);
		throw new UnsupportedOperationException("no fake for a " + type.getSimpleName() + " registry entry: " + id);
	}

	private static Attribute fakeAttribute(NamespacedKey key) {
		return (Attribute) Proxy.newProxyInstance(Attribute.class.getClassLoader(), new Class<?>[]{Attribute.class},
				(proxy, method, args) -> switch(method.getName()) {
					case "getKey", "key" -> key;
					case "hashCode" -> key.hashCode();
					case "equals" -> proxy == args[0];
					case "toString" -> "FakeAttribute[" + key + "]";
					default -> throw new UnsupportedOperationException("FakeAttribute." + method.getName());
				});
	}
}
