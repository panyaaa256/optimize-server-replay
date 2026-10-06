package com.panyaaa256.optimizeserverreplay.block;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.shorts.ShortIterator;
import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.ArrayDeque;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Collects the block updates of a recording, so that they can be recorded together every few ticks.
 *
 * <p>Only the positions are kept. The state of a block is read from the level when the updates
 * are flushed, so a position that changed many times is recorded once, with its latest state.
 *
 * <p>Only used on the server thread.
 */
public final class BlockUpdateBuffer {
	private static final int INITIAL_SECTIONS = 64;
	private static final int INITIAL_BLOCK_ENTITIES = 64;

	// These are created by the first capture, as most recordings never batch block updates.
	// Section position -> the positions in that section that changed (see SectionPos.sectionRelativePos).
	private Long2ObjectOpenHashMap<ShortOpenHashSet> sections;
	// Block positions (BlockPos.asLong) whose block entity data changed.
	private LongOpenHashSet blockEntities;
	// Emptied sets, so that they keep their arrays for the next flush.
	private ArrayDeque<ShortOpenHashSet> pool;
	// The positions of one section without the ones that are moving pistons. Reused by every section.
	private ShortOpenHashSet kept;
	// Kept in a field, so that capturing a section update does not allocate a lambda.
	private BiConsumer<BlockPos, BlockState> addBlock;

	/**
	 * Takes note of a packet that changes blocks.
	 *
	 * @return whether the packet was taken, in which case it must not be recorded
	 */
	public boolean capture(Packet<?> packet) {
		if (packet instanceof ClientboundBlockUpdatePacket update) {
			this.ensureCreated();
			this.addBlock(update.getPos());
			return true;
		}
		if (packet instanceof ClientboundSectionBlocksUpdatePacket update) {
			this.ensureCreated();
			update.runUpdates(this.addBlock);
			return true;
		}
		if (packet instanceof ClientboundBlockEntityDataPacket data) {
			this.ensureCreated();
			this.blockEntities.add(data.getPos().asLong());
			return true;
		}
		if (packet instanceof ClientboundLevelChunkWithLightPacket chunk) {
			// The chunk data is newer than anything collected for this chunk.
			this.discardChunk(chunk.getX(), chunk.getZ());
		}
		return false;
	}

	/**
	 * Hands the current state of every collected position to {@code recorder}, and forgets the positions.
	 *
	 * @param level the level that the recording is in
	 * @param recorder records a packet
	 */
	public void flush(ServerLevel level, Consumer<Packet<?>> recorder) {
		if (this.sections == null || (this.sections.isEmpty() && this.blockEntities.isEmpty())) {
			return;
		}

		try {
			for (Long2ObjectMap.Entry<ShortOpenHashSet> entry : this.sections.long2ObjectEntrySet()) {
				this.flushSection(level, entry.getLongKey(), entry.getValue(), recorder);
			}
			LongIterator iterator = this.blockEntities.iterator();
			while (iterator.hasNext()) {
				this.flushBlockEntity(level, iterator.nextLong(), recorder);
			}
		} finally {
			// Also when the recorder threw, so that the same positions are not left for the next flush.
			this.clear();
		}
	}

	private void ensureCreated() {
		if (this.sections == null) {
			this.sections = new Long2ObjectOpenHashMap<>(INITIAL_SECTIONS);
			this.blockEntities = new LongOpenHashSet(INITIAL_BLOCK_ENTITIES);
			this.pool = new ArrayDeque<>();
			this.kept = new ShortOpenHashSet();
			this.addBlock = (pos, state) -> this.addBlock(pos);
		}
	}

	private void addBlock(BlockPos pos) {
		long section = SectionPos.asLong(pos);
		ShortOpenHashSet positions = this.sections.get(section);
		if (positions == null) {
			positions = this.pool.pollLast();
			if (positions == null) {
				positions = new ShortOpenHashSet();
			}
			this.sections.put(section, positions);
		}
		positions.add(SectionPos.sectionRelativePos(pos));
	}

	private void discardChunk(int chunkX, int chunkZ) {
		if (this.sections == null) {
			return;
		}

		if (!this.sections.isEmpty()) {
			ObjectIterator<Long2ObjectMap.Entry<ShortOpenHashSet>> iterator = this.sections.long2ObjectEntrySet().fastIterator();
			while (iterator.hasNext()) {
				Long2ObjectMap.Entry<ShortOpenHashSet> entry = iterator.next();
				long section = entry.getLongKey();
				if (SectionPos.x(section) == chunkX && SectionPos.z(section) == chunkZ) {
					ShortOpenHashSet positions = entry.getValue();
					positions.clear();
					this.pool.addLast(positions);
					iterator.remove();
				}
			}
		}

		if (!this.blockEntities.isEmpty()) {
			LongIterator iterator = this.blockEntities.iterator();
			while (iterator.hasNext()) {
				long pos = iterator.nextLong();
				if (SectionPos.blockToSectionCoord(BlockPos.getX(pos)) == chunkX
					&& SectionPos.blockToSectionCoord(BlockPos.getZ(pos)) == chunkZ) {
					iterator.remove();
				}
			}
		}
	}

	private void flushSection(ServerLevel level, long sectionKey, ShortOpenHashSet changed, Consumer<Packet<?>> recorder) {
		int chunkX = SectionPos.x(sectionKey);
		int sectionY = SectionPos.y(sectionKey);
		int chunkZ = SectionPos.z(sectionKey);

		LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
		if (chunk == null) {
			return;
		}
		int index = chunk.getSectionIndexFromSectionY(sectionY);
		if (index < 0 || index >= chunk.getSectionsCount()) {
			return;
		}
		LevelChunkSection section = chunk.getSection(index);
		SectionPos sectionPos = SectionPos.of(sectionKey);

		// Skip the positions that are moving pistons (a block being pushed by a piston), and do not
		// carry them over. The block actions of pistons are not recorded, so the replay has nothing
		// that draws a moving block, and recording only the moving_piston state would make the
		// position look empty. When the move ends, the game sends the block change of that
		// position again, which is collected for the next flush, so the right state is recorded then.
		this.kept.clear();
		ShortIterator iterator = changed.iterator();
		while (iterator.hasNext()) {
			short relative = iterator.nextShort();
			int x = SectionPos.sectionRelativeX(relative);
			int y = SectionPos.sectionRelativeY(relative);
			int z = SectionPos.sectionRelativeZ(relative);
			if (!section.getBlockState(x, y, z).is(Blocks.MOVING_PISTON)) {
				this.kept.add(relative);
			}
		}

		if (this.kept.isEmpty()) {
			return;
		}
		if (this.kept.size() == 1) {
			BlockPos pos = sectionPos.relativeToBlockPos(this.kept.iterator().nextShort());
			recorder.accept(new ClientboundBlockUpdatePacket(level, pos));
		} else {
			recorder.accept(new ClientboundSectionBlocksUpdatePacket(sectionPos, this.kept, section));
		}
	}

	private void flushBlockEntity(ServerLevel level, long posKey, Consumer<Packet<?>> recorder) {
		BlockPos pos = BlockPos.of(posKey);
		LevelChunk chunk = level.getChunkSource().getChunkNow(
			SectionPos.blockToSectionCoord(pos.getX()),
			SectionPos.blockToSectionCoord(pos.getZ())
		);
		if (chunk == null) {
			return;
		}

		// Read the map directly, as getBlockEntity could create a block entity that does not exist yet.
		BlockEntity blockEntity = chunk.getBlockEntities().get(pos);
		if (blockEntity == null || blockEntity.isRemoved()) {
			return;
		}
		Packet<?> packet = blockEntity.getUpdatePacket();
		if (packet != null) {
			recorder.accept(packet);
		}
	}

	private void clear() {
		for (ShortOpenHashSet positions : this.sections.values()) {
			positions.clear();
			this.pool.addLast(positions);
		}
		this.sections.clear();
		this.blockEntities.clear();
	}
}
