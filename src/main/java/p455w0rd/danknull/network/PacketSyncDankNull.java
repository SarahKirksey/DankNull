package p455w0rd.danknull.network;

import java.util.Map;

import javax.annotation.Nonnull;

import com.google.common.collect.Maps;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import p455w0rd.danknull.DankNull;
import p455w0rd.danknull.blocks.tiles.TileDankNullDock;
import p455w0rd.danknull.client.gui.GuiDankNull;
import p455w0rd.danknull.container.ContainerDankNull;
import p455w0rd.danknull.inventory.InventoryDankNull;
import p455w0rd.danknull.util.DankNullUtils;
import p455w0rd.danknull.util.DankNullUtils.SlotExtractionMode;
import p455w0rdslib.util.EasyMappings;

/**
 * @author p455w0rd
 *
 */
public class PacketSyncDankNull implements IMessage {

	private NonNullList<ItemStack> itemStacks;
	private int[] stackSizes;
	private Map<ItemStack, SlotExtractionMode> extractionModes = Maps.<ItemStack, SlotExtractionMode>newHashMap();
	private BlockPos pos = null;
	private static final String TEMP_EXTRACT_TAG = "ExtractionMode";

	@Override
	public void fromBytes(ByteBuf buf) {
		int i = buf.readShort();
		itemStacks = NonNullList.<ItemStack>withSize(i, ItemStack.EMPTY);
		stackSizes = new int[i];
		for (int j = 0; j < i; ++j) {
			itemStacks.set(j, ByteBufUtils.readItemStack(buf));
		}
		for (int j = 0; j < i; ++j) {
			stackSizes[j] = buf.readInt();
		}
		int extractionModesSize = buf.readInt();
		if (extractionModesSize > 0) {
			for (int k = 0; k < extractionModesSize; k++) {
				ItemStack currentStack = ByteBufUtils.readItemStack(buf);
				currentStack.setCount(1);
				SlotExtractionMode mode = SlotExtractionMode.values()[currentStack.getTagCompound().getInteger(TEMP_EXTRACT_TAG)];
				extractionModes.put(currentStack, mode);
			}
		}
		if (buf.readBoolean()) {
			int x = buf.readInt();
			int y = buf.readInt();
			int z = buf.readInt();
			pos = new BlockPos(x, y, z);
		}
	}

	@Override
	public void toBytes(ByteBuf buf) {
		buf.writeShort(itemStacks.size());
		for (ItemStack itemstack : itemStacks) {
			ByteBufUtils.writeItemStack(buf, itemstack);
		}
		for (int i = 0; i < itemStacks.size(); i++) {
			buf.writeInt(stackSizes[i]);
		}
		buf.writeInt(extractionModes.size());
		if (!extractionModes.isEmpty()) {
			for (ItemStack stack : extractionModes.keySet()) {
				if (!stack.hasTagCompound()) {
					stack.setTagCompound(new NBTTagCompound());
				}
				stack.getTagCompound().setInteger(TEMP_EXTRACT_TAG, extractionModes.get(stack).ordinal());
				ByteBufUtils.writeItemStack(buf, stack);
			}
		}
		if (pos != null) {
			buf.writeBoolean(true);
			buf.writeInt(pos.getX());
			buf.writeInt(pos.getY());
			buf.writeInt(pos.getZ());
		}
		else {
			buf.writeBoolean(false);
		}
	}

	public PacketSyncDankNull() {
	}

	public PacketSyncDankNull(@Nonnull ItemStack dankNullIn) {
		this(DankNullUtils.getNewDankNullInventory(dankNullIn));
	}

	public PacketSyncDankNull(@Nonnull ItemStack dankNullIn, BlockPos posIn) {
		this(DankNullUtils.getNewDankNullInventory(dankNullIn), posIn);
	}

	public PacketSyncDankNull(@Nonnull InventoryDankNull inv) {
		this(inv, null);
	}

	public PacketSyncDankNull(@Nonnull InventoryDankNull inv, BlockPos posIn) {
		itemStacks = NonNullList.<ItemStack>withSize(inv.getStacks().size(), ItemStack.EMPTY);
		stackSizes = new int[inv.getStacks().size()];
		for (int i = 0; i < itemStacks.size(); i++) {
			ItemStack itemstack = inv.getStacks().get(i);
			itemStacks.set(i, itemstack.copy());
			stackSizes[i] = itemstack.getCount();
		}
		if (!DankNullUtils.getExtractionModes(inv.getDankNull()).isEmpty()) {
			extractionModes = DankNullUtils.getExtractionModes(inv.getDankNull());
		}
		if (posIn != null) {
			pos = posIn;
		}
	}

	public static class Handler implements IMessageHandler<PacketSyncDankNull, IMessage> {
		@Override
		public IMessage onMessage(PacketSyncDankNull message, MessageContext ctx) {
			FMLCommonHandler.instance().getWorldThread(ctx.netHandler).addScheduledTask(() -> {
				if (ctx.side == Side.CLIENT) {
					handleToClient(message, ctx);
				}
				else {
					handleToServer(message, ctx);
				}
			});
			return null;
		}

		private void handleToServer(PacketSyncDankNull message, MessageContext ctx) {
			handle(message, ctx.getServerHandler().player, ctx.side);
		}

		private void handleToClient(PacketSyncDankNull message, MessageContext ctx) {
			handle(message, EasyMappings.player(), ctx.side);
		}

		private void handle(PacketSyncDankNull message, EntityPlayer player, Side side) {
			InventoryDankNull inv = null;
			if (player.openContainer instanceof ContainerDankNull) {
				ContainerDankNull container = (ContainerDankNull) player.openContainer;
				inv = DankNullUtils.getNewDankNullInventory(container.getDankNull());
				//inv = container.getDankNullInventory();
				for (int i = 0; i < message.itemStacks.size(); i++) {
					message.itemStacks.get(i).setCount(message.stackSizes[i]);
					//inv.setInventorySlotContents(i, message.itemStacks.get(i));
					container.putStackInSlot(i + 36, message.itemStacks.get(i));
				}
				if (!message.extractionModes.isEmpty()) {
					DankNullUtils.setExtractionModes(inv.getDankNull(), message.extractionModes);
				}
				container.setDankNullInventory(inv);
				//container.detectAndSendChanges();

			}
			if (message.pos != null) {
				World world = player.getEntityWorld();
				TileEntity te = world.getTileEntity(message.pos);
				if (te != null && te instanceof TileDankNullDock) {
					TileDankNullDock dankDock = (TileDankNullDock) te;
					if (inv != null) {
						dankDock.setInventory(inv);
					}
					else {
						inv = dankDock.getInventory();
					}
					dankDock.setStack(inv.getDankNull());
					for (int i = 0; i < message.itemStacks.size(); i++) {
						message.itemStacks.get(i).setCount(message.stackSizes[i]);
						inv.setInventorySlotContents(i, message.itemStacks.get(i));
					}
					if (!message.extractionModes.isEmpty()) {
						DankNullUtils.setExtractionModes(inv.getDankNull(), message.extractionModes);
					}
					dankDock.setInventory(inv);

				}
			}
			if (side == Side.CLIENT && inv != null) {
				if (DankNull.PROXY.getScreen() != null && DankNull.PROXY.getScreen() instanceof GuiDankNull) {
					GuiDankNull screen = (GuiDankNull) DankNull.PROXY.getScreen();
					screen.setDankNull(inv.getDankNull());
				}
			}
			if (side == Side.SERVER) {
				//ModNetworking.getInstance().sendTo(message, (EntityPlayerMP) player);

				player.openContainer.detectAndSendChanges();
			}
		}
	}

}