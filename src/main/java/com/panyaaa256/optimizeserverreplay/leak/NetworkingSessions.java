package com.panyaaa256.optimizeserverreplay.leak;

import com.panyaaa256.optimizeserverreplay.OptimizeServerReplay;
import org.jspecify.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fabric API starts a networking session for every packet listener when it is made, and keeps it
 * in a set that lives as long as the server until the connection is closed. Arcade makes packet
 * listeners that never connect to anything: one for the dummy player of every chunk recording,
 * and a new one for the player (another {@code ServerPlayer}) with each snapshot of every
 * recording. None of them was ever let go of, nor were the recordings they point to. Ending the
 * session takes them out of the set without telling anyone that a player left.
 *
 * <p>Fabric API is not on the compile classpath, so its classes are reached through reflection.
 */
public final class NetworkingSessions {
	private static final AtomicBoolean WARNED = new AtomicBoolean();
	private static final @Nullable Class<?> EXTENSIONS;
	private static final @Nullable MethodHandle GET_ADDON;
	private static final @Nullable MethodHandle END_SESSION;

	static {
		Class<?> extensions = null;
		MethodHandle getAddon = null;
		MethodHandle endSession = null;
		try {
			extensions = Class.forName("net.fabricmc.fabric.impl.networking.NetworkHandlerExtensions");
			Class<?> addon = Class.forName("net.fabricmc.fabric.impl.networking.AbstractNetworkAddon");
			MethodHandles.Lookup lookup = MethodHandles.publicLookup();
			getAddon = lookup.findVirtual(extensions, "getAddon", MethodType.methodType(addon));
			endSession = lookup.findVirtual(addon, "endSession", MethodType.methodType(void.class));
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			warn(e);
			extensions = null;
		}
		EXTENSIONS = extensions;
		GET_ADDON = getAddon;
		END_SESSION = endSession;
	}

	private NetworkingSessions() {
	}

	/**
	 * Ends the networking session of a packet listener that will not be used again.
	 */
	public static void end(@Nullable Object listener) {
		if (listener == null || EXTENSIONS == null || !EXTENSIONS.isInstance(listener)) {
			return;
		}
		try {
			Object addon = GET_ADDON.invoke(listener);
			if (addon != null) {
				END_SESSION.invoke(addon);
			}
		} catch (Throwable e) {
			warn(e);
		}
	}

	private static void warn(Throwable e) {
		if (WARNED.compareAndSet(false, true)) {
			OptimizeServerReplay.LOGGER.warn(
				"Could not end the Fabric API networking sessions of the packet listeners that Arcade makes, so they stay in memory", e
			);
		}
	}
}
