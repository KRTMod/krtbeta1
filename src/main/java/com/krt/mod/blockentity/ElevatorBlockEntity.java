package com.krt.mod.blockentity;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
// import net.minecraft.network.listener.ClientPlayPacketListener;
// import net.minecraft.network.protocol.Packet;
// import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import com.krt.mod.block.ElevatorBlock;

import java.util.ArrayList;
import java.util.List;

public class ElevatorBlockEntity extends BlockEntity implements NamedScreenHandlerFactory {
    private int currentFloor = 1;
    private int targetFloor = 1;
    private boolean doorsOpen = false;
    private List<Integer> requestQueue = new ArrayList<>();
    private final PropertyDelegate propertyDelegate = new PropertyDelegate() {
        @Override
        public int get(int index) {
            switch (index) {
                case 0: return currentFloor;
                case 1: return targetFloor;
                case 2: return doorsOpen ? 1 : 0;
                default: return 0;
            }
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0: currentFloor = value;
                case 1: targetFloor = value;
                case 2: doorsOpen = value > 0;
            }
        }

        @Override
        public int size() {
            return 3;
        }
    };

    public ElevatorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ELEVATOR_BLOCK_ENTITY, pos, state);
    }

    public void updateState() {
        if (world != null && !world.isClient()) {
            BlockState state = getCachedState();
            world.setBlockState(pos, state
                    .with(ElevatorBlock.CURRENT_FLOOR, currentFloor)
                    .with(ElevatorBlock.TARGET_FLOOR, targetFloor)
                    .with(ElevatorBlock.DOORS_OPEN, doorsOpen));
            markDirty();
            world.updateListeners(pos, state, state, 3);
        }
    }

    public void requestFloor(int floor) {
        if (!requestQueue.contains(floor) && floor != currentFloor) {
            requestQueue.add(floor);
            if (targetFloor == currentFloor) {
                targetFloor = floor;
                updateState();
            }
        }
    }

    public void openDoors() {
        doorsOpen = true;
        updateState();
    }

    public void closeDoors() {
        doorsOpen = false;
        updateState();
    }

    public void moveToFloor(int floor) {
        this.targetFloor = floor;
        updateState();
    }

    public int getCurrentFloor() {
        return currentFloor;
    }

    public int getTargetFloor() {
        return targetFloor;
    }

    public boolean areDoorsOpen() {
        return doorsOpen;
    }

    @Override
    public void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putInt("CurrentFloor", currentFloor);
        nbt.putInt("TargetFloor", targetFloor);
        nbt.putBoolean("DoorsOpen", doorsOpen);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        currentFloor = nbt.getInt("CurrentFloor");
        targetFloor = nbt.getInt("TargetFloor");
        doorsOpen = nbt.getBoolean("DoorsOpen");
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable("container.elevator");
    }

    // @Override
    // public Packet<ClientPlayPacketListener> toUpdatePacket() {
    //     return ClientboundBlockEntityDataPacket.create(this);
    // }

    @Override
    public NbtCompound toInitialChunkDataNbt() {
        return createNbt();
    }

    public void copyDataFrom(ElevatorBlockEntity source) {
        this.currentFloor = source.currentFloor;
        this.targetFloor = source.targetFloor;
        this.doorsOpen = source.doorsOpen;
        this.requestQueue.addAll(source.requestQueue);
    }
}