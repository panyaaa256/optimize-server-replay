package com.panyaaa256.optimizeserverreplay.mixin;

import com.panyaaa256.optimizeserverreplay.HookStatus;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Mixin names the handler of an injection like
 * {@code handler$zpb000$optimize-server-replay$osr$onTick} and calls it from the target method,
 * so a call to such a method in the applied class means the hook is in place.
 *
 * <p>This runs very early, so it must not refer to any Minecraft class.
 */
public class OptimizeServerReplayMixinPlugin implements IMixinConfigPlugin {
	private static final String HANDLER_PREFIX = "handler$";
	private static final String HANDLER_MARKER = "$osr$on";

	@Override
	public void onLoad(String mixinPackage) {
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		return true;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
	}

	@Override
	public List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
		String className = targetClassName.substring(targetClassName.lastIndexOf('.') + 1);
		for (MethodNode method : targetClass.methods) {
			String hook = hookName(className, method);
			if (!HookStatus.ALL_HOOKS.contains(hook)) {
				continue;
			}
			for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if (insn instanceof MethodInsnNode call && isHandlerCall(call, targetClass)) {
					HookStatus.markApplied(hook);
					break;
				}
			}
		}
	}

	private static boolean isHandlerCall(MethodInsnNode call, ClassNode targetClass) {
		return call.owner.equals(targetClass.name)
			&& call.name.startsWith(HANDLER_PREFIX)
			&& call.name.contains(HANDLER_MARKER);
	}

	// pause and stop each have an overload without parameters, which the hooks are not in.
	private static String hookName(String className, MethodNode method) {
		if ((method.name.equals("pause") || method.name.equals("stop")) && method.desc.startsWith("(Z)")) {
			return className + "#" + method.name + "(Z)";
		}
		return className + "#" + method.name;
	}
}
