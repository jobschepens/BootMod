package com.boardmod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

public class SteeringWheelItem extends Item {

    private static final int MAX_BLOCKS = 20;

    public SteeringWheelItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        BlockPos clickedPos = context.getClickedPos();
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        // Clicked block must be a solid, non-fluid block
        BlockState clickedState = level.getBlockState(clickedPos);
        if (clickedState.isAir() || !clickedState.getFluidState().isEmpty()) {
            return InteractionResult.PASS;
        }

        // Scan connected structure via BFS
        List<BlockPos> structure = scanStructure(level, clickedPos);
        if (structure == null) {
            player.displayClientMessage(
                Component.literal("Das Gestell ist zu groß (max 20 Blöcke) oder berührt den Boden!"), true);
            return InteractionResult.FAIL;
        }

        // Structure must be adjacent to water
        if (!hasWaterAdjacent(level, structure)) {
            player.displayClientMessage(
                Component.literal("Das Gestell muss im Wasser sein!"), true);
            return InteractionResult.FAIL;
        }

        // Collect block data relative to clicked position (= future entity origin)
        List<RaftEntity.RaftBlock> raftBlocks = new ArrayList<>();
        for (BlockPos pos : structure) {
            raftBlocks.add(new RaftEntity.RaftBlock(
                pos.getX() - clickedPos.getX(),
                pos.getY() - clickedPos.getY(),
                pos.getZ() - clickedPos.getZ(),
                level.getBlockState(pos)
            ));
        }

        // Remove blocks from world
        for (BlockPos pos : structure) {
            level.removeBlock(pos, false);
        }

        // Spawn raft entity at the clicked block's position
        RaftEntity raft = BootMod.RAFT_ENTITY.get().create(level);
        if (raft == null) return InteractionResult.FAIL;

        raft.moveTo(
            clickedPos.getX() + 0.5,
            clickedPos.getY(),
            clickedPos.getZ() + 0.5,
            player.getYRot(), 0
        );
        raft.setRaftBlocks(raftBlocks);
        level.addFreshEntity(raft);
        player.startRiding(raft, true);

        if (!player.getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }

        return InteractionResult.CONSUME;
    }

    /**
     * BFS scan of all connected solid non-fluid blocks starting at {@code start}.
     * Returns null if the structure is invalid (too large or touching ground).
     */
    private static List<BlockPos> scanStructure(Level level, BlockPos start) {
        List<BlockPos> result = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();

        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            result.add(pos);

            if (result.size() > MAX_BLOCKS) return null;

            for (Direction dir : Direction.values()) {
                BlockPos neighbor = pos.relative(dir);
                if (!visited.contains(neighbor)) {
                    BlockState ns = level.getBlockState(neighbor);
                    if (!ns.isAir() && ns.getFluidState().isEmpty()) {
                        visited.add(neighbor);
                        queue.add(neighbor);
                    }
                }
            }
        }

        // Validate: no block in structure may have solid non-fluid ground below it
        Set<BlockPos> structureSet = new HashSet<>(result);
        for (BlockPos pos : result) {
            BlockPos below = pos.below();
            if (!structureSet.contains(below)) {
                BlockState belowState = level.getBlockState(below);
                // Water below is fine; solid ground is not
                if (!belowState.isAir() && belowState.getFluidState().isEmpty()) {
                    return null; // grounded
                }
            }
        }

        return result;
    }

    private static boolean hasWaterAdjacent(Level level, List<BlockPos> structure) {
        for (BlockPos pos : structure) {
            for (Direction dir : Direction.values()) {
                if (level.getFluidState(pos.relative(dir)).is(FluidTags.WATER)) {
                    return true;
                }
            }
        }
        return false;
    }
}
