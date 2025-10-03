package com.krt.mod.block;

import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.sound.SoundCategory;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import com.krt.mod.blockentity.PlatformDoorBlockEntity;
import com.krt.mod.KRTMod;
import com.krt.mod.item.KeyItem;

public class PlatformDoorBlock extends Block implements BlockEntityProvider {
    public static final PlatformDoorBlock INSTANCE = new PlatformDoorBlock(FabricBlockSettings.copyOf(Blocks.IRON_BLOCK).strength(3.0f).requiresTool());
    public static final DirectionProperty FACING = HorizontalFacingBlock.FACING;
    public static final BooleanProperty OPEN = BooleanProperty.of("open");
    public static final EnumProperty<DoorType> DOOR_TYPE = EnumProperty.of("door_type", DoorType.class);

    public PlatformDoorBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState().with(FACING, Direction.NORTH).with(OPEN, false).with(DOOR_TYPE, DoorType.UNDERGROUND));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN, DOOR_TYPE);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return this.getDefaultState().with(FACING, ctx.getPlayerFacing());
    }
    
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new PlatformDoorBlockEntity(pos, state);
    }
    
    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (state.getBlock() != newState.getBlock()) {
            super.onStateReplaced(state, world, pos, newState, moved);
        }
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!world.isClient) {
            // 检查玩家是否手持钥匙
            if (player.getStackInHand(hand).getItem() instanceof KeyItem) {
                // 使用钥匙手动开关门
                toggleDoor(world, pos);
                return ActionResult.SUCCESS;
            }
        }
        return ActionResult.PASS;
    }

    // 切换门的开关状态
    public static void toggleDoor(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof PlatformDoorBlock) {
            boolean isOpen = state.get(OPEN);
            world.setBlockState(pos, state.with(OPEN, !isOpen));
            // 播放开关门的声音
            if (!isOpen) {
                world.playSound(null, pos, KRTMod.DOOR_OPEN_SOUND, SoundCategory.BLOCKS, 1.0F, 1.0F);
            } else {
                world.playSound(null, pos, KRTMod.DOOR_CLOSE_SOUND, SoundCategory.BLOCKS, 1.0F, 1.0F);
            }
        }
    }

    // 自动开门（当列车到站时）
    public static void autoOpenDoor(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof PlatformDoorBlock && !state.get(OPEN)) {
            world.setBlockState(pos, state.with(OPEN, true));
            world.playSound(null, pos, KRTMod.DOOR_OPEN_SOUND, SoundCategory.BLOCKS, 1.0F, 1.0F);
        }
    }

    // 自动关门（当列车离开时）
    public static void autoCloseDoor(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof PlatformDoorBlock && state.get(OPEN)) {
            world.setBlockState(pos, state.with(OPEN, false));
            world.playSound(null, pos, KRTMod.DOOR_CLOSE_SOUND, SoundCategory.BLOCKS, 1.0F, 1.0F);
        }
    }

    public boolean collidesWith(BlockState state, World world, BlockPos pos, Entity entity) {
        // 当门关闭时，阻止实体通过
        return !state.get(OPEN);
    }

    // 屏蔽门类型枚举
    public enum DoorType implements net.minecraft.util.StringIdentifiable {
        UNDERGROUND("underground"),
        ELEVATED("elevated");
        
        private final String name;
        
        private DoorType(String name) {
            this.name = name;
        }
        
        @Override
        public String asString() {
            return this.name;
        }
    }
}