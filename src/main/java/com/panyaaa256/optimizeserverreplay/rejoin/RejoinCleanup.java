package com.panyaaa256.optimizeserverreplay.rejoin;

import com.panyaaa256.optimizeserverreplay.OptimizeServerReplay;
import org.jspecify.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * For every snapshot, Arcade makes a new player for the recorder with a configuration and a game
 * packet listener of its own ({@code RejoinedReplayPlayer.rejoin}), which go on a connection that
 * is never opened or closed. Fabric API gives each packet listener a network addon when it is
 * made, and keeps the addon in a set of its {@code GlobalReceiverRegistry} until the connection
 * moves on to the next protocol or is closed, neither of which ever happens to these. The set
 * then keeps the addons, and through them the listeners and the player of every snapshot, for as
 * long as the server runs.
 *
 * <p>Once Arcade is done with them, this ends their sessions the way Fabric API does when a
 * connection moves on to the next protocol: the addon only leaves the set, and no event is sent.
 *
 * <p>Fabric API is not on the compile classpath, and the classes are not part of its API, so they
 * are found through reflection, and nothing is done when they are not there.
 */
public final class RejoinCleanup {
	private static final String EXTENSIONS = "net.fabricmc.fabric.impl.networking.NetworkHandlerExtensions";
	private static final String ADDON = "net.fabricmc.fabric.impl.networking.AbstractNetworkAddon";

	private static final AtomicBoolean WARNED = new AtomicBoolean();
	private static final AtomicBoolean LOGGED = new AtomicBoolean();
	private static final @Nullable Handles HANDLES = findHandles();

	private RejoinCleanup() {
	}

	/**
	 * Called on the server thread, after Arcade has sent everything through the listeners.
	 */
	public static void endSessions(Object... listeners) {
		if (HANDLES == null) {
			return;
		}
		try {
			for (Object listener : listeners) {
				if (HANDLES.extensions().isInstance(listener)) {
					Object addon = HANDLES.getAddon().invoke(listener);
					if (addon != null) {
						HANDLES.endSession().invoke(addon);
					}
				}
			}
		} catch (Throwable e) {
			warn(e);
			return;
		}
		if (LOGGED.compareAndSet(false, true)) {
			OptimizeServerReplay.LOGGER.info("Ending the Fabric API network sessions of the players that Arcade makes for each snapshot");
		}
	}

	private static @Nullable Handles findHandles() {
		try {
			ClassLoader loader = RejoinCleanup.class.getClassLoader();
			Class<?> extensions = Class.forName(EXTENSIONS, false, loader);
			Class<?> addon = Class.forName(ADDON, false, loader);
			MethodHandles.Lookup lookup = MethodHandles.publicLookup();
			MethodHandle getAddon = lookup.findVirtual(extensions, "getAddon", MethodType.methodType(addon))
				.asType(MethodType.methodType(Object.class, Object.class));
			MethodHandle endSession = lookup.findVirtual(addon, "endSession", MethodType.methodType(void.class))
				.asType(MethodType.methodType(void.class, Object.class));
			return new Handles(extensions, getAddon, endSession);
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			warn(e);
			return null;
		}
	}

	private static void warn(Throwable e) {
		if (WARNED.compareAndSet(false, true)) {
			OptimizeServerReplay.LOGGER.warn(
				"Could not end the Fabric API network sessions of the players that Arcade makes for each snapshot, "
					+ "so they stay in memory until the server stops",
				e
			);
		}
	}

	private record Handles(Class<?> extensions, MethodHandle getAddon, MethodHandle endSession) {
	}
}
