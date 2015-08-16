package capsule.items;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import capsule.Helpers;
import capsule.blocks.BlockCapsuleMarker;
import capsule.dimension.CapsuleDimensionRegistrer;
import capsule.dimension.CapsuleSavedData;
import capsule.enchantments.Enchantments;
import capsule.gui.LabelGui;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import scala.actors.threadpool.Arrays;

public class CapsuleItem extends Item {

	public final static int STATE_EMPTY = 0;
	public final static int STATE_EMPTY_ACTIVATED = 4;
	public final static int STATE_ACTIVATED = 1;
	public final static int STATE_LINKED = 2;
	public final static int STATE_DEPLOYED = 3;
	public final static int STATE_ONE_USE = 5;
	public final static int STATE_ONE_USE_ACTIVATED = 6;

	private static final int CAPSULE_MAX_CAPTURE_SIZE = 69;

	@SuppressWarnings("unchecked")
	public static List<IBlockState> excludedBlocks = Arrays.asList(new IBlockState[] { Blocks.air.getDefaultState(), Blocks.bedrock.getDefaultState() });

	@SuppressWarnings("unchecked")
	public static List<IBlockState> overridableBlocks = Arrays.asList(new IBlockState[] { Blocks.air.getDefaultState(), Blocks.water.getDefaultState(),
			Blocks.leaves.getDefaultState(), Blocks.leaves2.getDefaultState(), Blocks.tallgrass.getStateFromMeta(0),Blocks.tallgrass.getStateFromMeta(1), Blocks.tallgrass.getStateFromMeta(2), Blocks.red_flower.getDefaultState(),
			Blocks.yellow_flower.getDefaultState(), Blocks.snow_layer.getDefaultState(),
			Blocks.brown_mushroom.getDefaultState(), Blocks.red_mushroom.getDefaultState() });

	public CapsuleItem(String unlocalizedName) {
		super();
		this.setHasSubtypes(true);
		this.setUnlocalizedName(unlocalizedName);
		this.setCreativeTab(CreativeTabs.tabMisc);
		this.setMaxStackSize(1);
		this.setMaxDamage(0);
	}

	@Override
	public String getItemStackDisplayName(ItemStack stack) {
		String name = StatCollector.translateToLocal("item.capsule.name");

		String state = "";
		switch (stack.getItemDamage()) {
		case CapsuleItem.STATE_ACTIVATED:
		case CapsuleItem.STATE_EMPTY_ACTIVATED:
		case CapsuleItem.STATE_ONE_USE_ACTIVATED:
			state = EnumChatFormatting.DARK_GREEN + StatCollector.translateToLocal("item.capsule.state_activated") + EnumChatFormatting.RESET;
			break;
		case CapsuleItem.STATE_LINKED:
			state = "";
			break;
		case CapsuleItem.STATE_DEPLOYED:
			state = StatCollector.translateToLocal("item.capsule.state_deployed");
			break;
		case CapsuleItem.STATE_ONE_USE:
			if (this.isReward(stack)) {
				state = StatCollector.translateToLocal("item.capsule.state_one_use");
			} else {
				state = StatCollector.translateToLocal("item.capsule.state_recovery");
			}

			break;
		}

		if (state.length() > 0) {
			state = state + " ";
		}
		String content = this.getLabel(stack);
		if (content.length() > 0) {
			content = content + " ";
		}

		return EnumChatFormatting.RESET + state + content + name;
	}

	public boolean isReward(ItemStack stack) {
		return (stack.hasTagCompound() && stack.getTagCompound().hasKey("isReward") && stack.getTagCompound().getBoolean("isReward"));
	}

	public void setIsReward(ItemStack stack, boolean isReward) {
		if (!stack.hasTagCompound()) {
			stack.setTagCompound(new NBTTagCompound());
		}
		stack.getTagCompound().setBoolean("isReward", isReward);
	}

	public String getLabel(ItemStack stack) {

		if (stack == null)
			return "";
		if (!this.isLinked(stack)) {
			return StatCollector.translateToLocal("item.capsule.content_empty");
		} else if (stack.hasTagCompound() && stack.getTagCompound().hasKey("label") && stack.getTagCompound().getString("label") != "") {
			return "“" + EnumChatFormatting.ITALIC + stack.getTagCompound().getString("label") + EnumChatFormatting.RESET + "”";
		}
		return StatCollector.translateToLocal("item.capsule.content_unlabeled");
	}

	private boolean isLinked(ItemStack stack) {
		return stack.hasTagCompound() && stack.getTagCompound().hasKey("linkPosition");
	}

	public void setLabel(ItemStack stack, String label) {
		if (!stack.hasTagCompound()) {
			stack.setTagCompound(new NBTTagCompound());
		}
		stack.getTagCompound().setString("label", label);
	}

	@Override
	public int getItemEnchantability() {
		return 5;
	}

	@Override
	public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
		return Helpers.getStoredEnchantmentLevel(Enchantments.recallEnchant.effectId, book) > 0;
	}

	@Override
	public int getItemEnchantability(ItemStack stack) {
		return getItemEnchantability();
	}

	@Override
	public boolean isItemTool(ItemStack stack) {
		return true;
	}

	@Override
	public boolean hasEffect(ItemStack stack) {
		return false;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public void addInformation(ItemStack stack, EntityPlayer playerIn, List tooltip, boolean advanced) {
		int size = getSize(stack);
		tooltip.add(StatCollector.translateToLocal("capsule.tooltip.size") + " : " + size + "x" + size + "x" + size);
		if (stack.getItemDamage() == CapsuleItem.STATE_ONE_USE) {
			StatCollector.translateToLocal("capsule.tooltip.one_use").trim();
		}
		super.addInformation(stack, playerIn, tooltip, advanced);
	}

	/**
	 * Register items in the creative tab
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public void getSubItems(Item itemIn, CreativeTabs tab, List subItems) {

		ItemStack ironCapsule = new ItemStack(CapsuleItemsRegistrer.capsule, 1, CapsuleItem.STATE_EMPTY);
		ironCapsule.setTagInfo("color", new NBTTagInt(0xCCCCCC));
		ironCapsule.setTagInfo("size", new NBTTagInt(3));

		ItemStack goldCapsule = new ItemStack(CapsuleItemsRegistrer.capsule, 1, CapsuleItem.STATE_EMPTY);
		goldCapsule.setTagInfo("color", new NBTTagInt(0xFFD700));
		goldCapsule.setTagInfo("size", new NBTTagInt(5));

		ItemStack diamondCapsule = new ItemStack(CapsuleItemsRegistrer.capsule, 1, CapsuleItem.STATE_EMPTY);
		diamondCapsule.setTagInfo("color", new NBTTagInt(0x00FFF2));
		diamondCapsule.setTagInfo("size", new NBTTagInt(7));

		subItems.add(ironCapsule);
		subItems.add(goldCapsule);
		subItems.add(diamondCapsule);

	}

	/**
	 * Activate or power throw on right click.
	 */
	@Override
	public ItemStack onItemRightClick(ItemStack itemStackIn, World worldIn, EntityPlayer playerIn) {

		ItemStack ret = super.onItemRightClick(itemStackIn, worldIn, playerIn);

		if (!worldIn.isRemote) {
			if (playerIn.isSneaking() && (itemStackIn.getItemDamage() == STATE_LINKED || itemStackIn.getItemDamage() == STATE_DEPLOYED)) {
				LabelGui screen = new LabelGui(playerIn);
				Minecraft.getMinecraft().displayGuiScreen(screen);

			} else {

				// an activated capsule is thrown farther on right click
				if (isActivated(itemStackIn)) {
					throwItem(itemStackIn, playerIn);
					playerIn.inventory.mainInventory[playerIn.inventory.currentItem] = null;
				}

				// an empty or a linked capsule is activated on right click
				else if (itemStackIn.getItemDamage() == STATE_EMPTY || itemStackIn.getItemDamage() == STATE_LINKED
						|| itemStackIn.getItemDamage() == STATE_ONE_USE) {
					if(itemStackIn.getItemDamage() == STATE_EMPTY ){
						this.setState(itemStackIn, STATE_EMPTY_ACTIVATED);
					}
					if(itemStackIn.getItemDamage() == STATE_LINKED ){
						this.setState(itemStackIn, STATE_ACTIVATED);
					}
					if(itemStackIn.getItemDamage() == STATE_ONE_USE ){
						this.setState(itemStackIn, STATE_ONE_USE_ACTIVATED);
					}

					NBTTagCompound timer = itemStackIn.getSubCompound("activetimer", true);
					timer.setInteger("starttime", playerIn.ticksExisted);
				}

				// an opened capsule revoke deployed content on right click
				else if (itemStackIn.getItemDamage() == STATE_DEPLOYED && !worldIn.isRemote) {
					resentToCapsule(itemStackIn, playerIn);
				}
			}
		}

		return ret;
	}

	private boolean isActivated(ItemStack itemStackIn) {
		return itemStackIn.getItemDamage() == STATE_ACTIVATED
				|| itemStackIn.getItemDamage() == STATE_EMPTY_ACTIVATED
				|| itemStackIn.getItemDamage() == STATE_ONE_USE_ACTIVATED;
	}

	/**
	 * Manage the "activated" state of the capsule.
	 */
	@Override
	public void onUpdate(ItemStack stack, World worldIn, Entity entityIn, int itemSlot, boolean isSelected) {
		super.onUpdate(stack, worldIn, entityIn, itemSlot, isSelected);

		if (!worldIn.isRemote) {

			// disable capsule after some time
			NBTTagCompound timer = stack.getSubCompound("activetimer", true);
			int tickDuration = 60; // 3 sec at 20 ticks/sec;
			if (this.isActivated(stack) && timer.hasKey("starttime")
					&& entityIn.ticksExisted >= timer.getInteger("starttime") + tickDuration) {

				revertStateFromActivated(stack);
			}
		}
	}

	/**
	 * Detect a collision and act accordingly (deploy or capture or break)
	 */
	@Override
	public boolean onEntityItemUpdate(EntityItem entityItem) {
		super.onEntityItemUpdate(entityItem);

		ItemStack capsule = entityItem.getEntityItem();

		// Deploying capsule content on collision with a block
		if (!entityItem.worldObj.isRemote && entityItem.isCollided && this.isActivated(capsule)
				&& entityItem.getEntityWorld() != null) {

			int size = getSize(capsule);
			int exdendLength = (size - 1) / 2;

			// get destination world available position
			WorldServer capsuleWorld = DimensionManager.getWorld(CapsuleDimensionRegistrer.dimensionId);
			WorldServer playerWorld = (WorldServer) entityItem.worldObj;

			// DEPLOY
			// is linked, deploy
			if (capsule.getTagCompound().hasKey("linkPosition")) {

				deployCapsule(entityItem, capsule, size, exdendLength, capsuleWorld, playerWorld);
				if (this.isOneUse(capsule)) {
					entityItem.setDead();
				}
				return true;

				// CAPTURE
				// is not linked, capture
			} else {

				captureContentIntoCapsule(entityItem, capsule, size, exdendLength, capsuleWorld, playerWorld);
				return true;
			}
		}

		return false;
	}

	/**
	 * Capture the content around the capsule entityItem, update capsule state.
	 * 
	 * @param entityItem
	 * @param capsule
	 * @param size
	 * @param exdendLength
	 * @param capsuleWorld
	 * @param playerWorld
	 */
	private void captureContentIntoCapsule(EntityItem entityItem, ItemStack capsule, int size, int exdendLength, WorldServer capsuleWorld,
			WorldServer playerWorld) {
		// get available space data
		CapsuleSavedData capsulePlacer = getCapsulePlacer(capsuleWorld);

		// specify target to capture
		BlockPos marker = Helpers.findSpecificBlock(entityItem, size, BlockCapsuleMarker.class);
		if (marker != null) {
			BlockPos source = marker.add(-exdendLength, 1, -exdendLength);

			// get free room to store
			BlockPos dest = capsulePlacer.reserveNextAvailablePositionForSize(size);

			// do the transportation. Can't fail because destination is always
			// empty
			Helpers.swapRegions(playerWorld, capsuleWorld, source, dest, size, overridableBlocks, excludedBlocks, false, null, null);

			// register the link in the capsule
			this.setState(capsule, STATE_LINKED);
			savePosition("linkPosition", capsule, dest);
		} else {

			revertStateFromActivated(capsule);
			if (entityItem == null || playerWorld == null) {
				return;
			}

			// send a chat message to explain failure
			EntityPlayer player = playerWorld.getPlayerEntityByName(entityItem.getThrower());
			if (player != null) {
				player.addChatMessage(new ChatComponentText(StatCollector.translateToLocal("capsule.error.noCaptureBase").trim()));
			}
		}
	}

	private Map<BlockPos, IBlockState> getOccupiedSourcePos(ItemStack capsule) {
		Map<BlockPos, IBlockState> occupiedSources = new HashMap<BlockPos, IBlockState>();
		if (capsule.hasTagCompound() && capsule.getTagCompound().hasKey("occupiedSpawnPositions")) {
			NBTTagList list = capsule.getTagCompound().getTagList("occupiedSpawnPositions", 10);
			for (int i = 0; i < list.tagCount(); i++) {
				NBTTagCompound entry = list.getCompoundTagAt(i);
				occupiedSources.put(BlockPos.fromLong(entry.getLong("pos")), Block.getStateById(entry.getInteger("state")));
			}
		}
		return occupiedSources;
	}

	private Map<BlockPos, IBlockState> setOccupiedSourcePos(ItemStack capsule, Map<BlockPos, IBlockState> occupiedSpawnPositions) {
		Map<BlockPos, IBlockState> occupiedSources = new HashMap<BlockPos, IBlockState>();
		NBTTagList entries = new NBTTagList();
		for (Entry<BlockPos, IBlockState> entry : occupiedSpawnPositions.entrySet()) {
			NBTTagCompound nbtEntry = new NBTTagCompound();
			nbtEntry.setLong("pos", entry.getKey().toLong());
			nbtEntry.setInteger("state", Block.getStateId(entry.getValue()));
			entries.appendTag(nbtEntry);
		}
		if (!capsule.hasTagCompound()) {
			capsule.setTagCompound(new NBTTagCompound());
		}
		capsule.getTagCompound().setTag("occupiedSpawnPositions", entries);
		return occupiedSources;
	}

	private void revertStateFromActivated(ItemStack capsule) {
		if (this.isOneUse(capsule)) {
			this.setState(capsule, STATE_ONE_USE);
		} else if (this.isLinked(capsule)) {
			this.setState(capsule, STATE_LINKED);
		} else {
			this.setState(capsule, STATE_EMPTY);
		}
	}

	private boolean isOneUse(ItemStack stack) {
		return stack.hasTagCompound() && stack.getTagCompound().getBoolean("oneUse");
	}

	/**
	 * Deploy the capsule at the entityItem position. update capsule state
	 * 
	 * @param entityItem
	 * @param capsule
	 * @param size
	 * @param exdendLength
	 * @param capsuleWorld
	 * @param playerWorld
	 */
	private void deployCapsule(EntityItem entityItem, ItemStack capsule, int size, int exdendLength, WorldServer capsuleWorld, WorldServer playerWorld) {
		// specify target to capture
		BlockPos bottomBlockPos = Helpers.findBottomBlock(entityItem, excludedBlocks);
		if (bottomBlockPos != null) {
			BlockPos dest = bottomBlockPos.add(-exdendLength, 1, -exdendLength);
			NBTTagCompound linkPos = capsule.getTagCompound().getCompoundTag("linkPosition");
			BlockPos source = new BlockPos(linkPos.getInteger("x"), linkPos.getInteger("y"), linkPos.getInteger("z"));

			// do the transportation
			Map<BlockPos, IBlockState> occupiedSpawnPositions = new HashMap<BlockPos, IBlockState>();
			boolean result = Helpers.swapRegions(capsuleWorld, playerWorld, source, dest, size, overridableBlocks, excludedBlocks, false, null,
					occupiedSpawnPositions);
			this.setOccupiedSourcePos(capsule, occupiedSpawnPositions);

			if (result) {
				// register the link in the capsule
				this.setState(capsule, STATE_DEPLOYED);
				savePosition("spawnPosition", capsule, dest);

			} else {
				revertStateFromActivated(capsule);
				if (entityItem == null || playerWorld == null) {
					return;
				}
				// send a chat message to explain failure
				EntityPlayer player = playerWorld.getPlayerEntityByName(entityItem.getThrower());
				if (player != null) {
					player.addChatMessage(new ChatComponentText(StatCollector.translateToLocal("capsule.error.cantMergeWithDestination").trim()));
				}
			}

		}
	}

	private void resentToCapsule(ItemStack itemStackIn, EntityPlayer playerIn) {
		// store again
		WorldServer capsuleWorld = DimensionManager.getWorld(CapsuleDimensionRegistrer.dimensionId);
		WorldServer playerWorld = (WorldServer) playerIn.worldObj;

		NBTTagCompound linkPos = itemStackIn.getTagCompound().getCompoundTag("linkPosition");
		BlockPos dest = new BlockPos(linkPos.getInteger("x"), linkPos.getInteger("y"), linkPos.getInteger("z"));
		NBTTagCompound spawnPos = itemStackIn.getTagCompound().getCompoundTag("spawnPosition");
		BlockPos source = new BlockPos(spawnPos.getInteger("x"), spawnPos.getInteger("y"), spawnPos.getInteger("z"));

		int size = getSize(itemStackIn);

		// do the transportation
		Helpers.swapRegions(playerWorld, capsuleWorld, source, dest, size, overridableBlocks, excludedBlocks, false, this.getOccupiedSourcePos(itemStackIn),
				null);

		this.setState(itemStackIn, STATE_LINKED);
		itemStackIn.getTagCompound().removeTag("spawnPosition");
	}

	public void setState(ItemStack stack, int state) {
		stack.setItemDamage(state);
	}

	/**
	 * The capsule capture size.
	 * 
	 * @param itemStackIn
	 * @return
	 */
	private int getSize(ItemStack itemStackIn) {
		int size = 1;
		if (itemStackIn.hasTagCompound() && itemStackIn.getTagCompound().hasKey("size")) {
			size = itemStackIn.getTagCompound().getInteger("size");
		}
		if (size > CAPSULE_MAX_CAPTURE_SIZE) {
			size = CAPSULE_MAX_CAPTURE_SIZE;
			itemStackIn.getTagCompound().setInteger("size", size);
			System.err.println("Capsule sizes are capped to " + CAPSULE_MAX_CAPTURE_SIZE + ". Resized to : " + size);
		} else if (size % 2 == 0) {
			size--;
			itemStackIn.getTagCompound().setInteger("size", size);
			System.err.println("Capsule size must be an odd number to achieve consistency on deployment. Resized to : " + size);
		}

		return size;
	}

	/**
	 * renderPass 0 => The material color renderPass 1 => The label color
	 */
	@Override
	public int getColorFromItemStack(ItemStack stack, int renderPass) {
		int color = 0xFFFFFF;

		// material color
		if (renderPass == 0) {
			if (stack.hasTagCompound() && stack.getTagCompound().hasKey("color")) {
				color = stack.getTagCompound().getInteger("color");
			}

			// label color
		} else if (renderPass == 1) {
			if (this.isLinked(stack)) {
				color = Helpers.getColor(stack);
			} else {
				return -1;
			}
			
		}
		return color;
	}

	/**
	 * Throw an item and return the new EntityItem created. Simulated a drop
	 * with stronger throw.
	 * 
	 * @param itemStackIn
	 * @param playerIn
	 * @return
	 */
	private EntityItem throwItem(ItemStack itemStackIn, EntityPlayer playerIn) {
		double d0 = playerIn.posY - 0.30000001192092896D + (double) playerIn.getEyeHeight();
		EntityItem entityitem = new EntityItem(playerIn.worldObj, playerIn.posX, d0, playerIn.posZ, itemStackIn);
		entityitem.setPickupDelay(10);
		entityitem.setThrower(playerIn.getName());
		float f = 0.5F;
		entityitem.motionX = (double) (-MathHelper.sin(playerIn.rotationYaw / 180.0F * (float) Math.PI)
				* MathHelper.cos(playerIn.rotationPitch / 180.0F * (float) Math.PI) * f);
		entityitem.motionZ = (double) (MathHelper.cos(playerIn.rotationYaw / 180.0F * (float) Math.PI)
				* MathHelper.cos(playerIn.rotationPitch / 180.0F * (float) Math.PI) * f);
		entityitem.motionY = (double) (-MathHelper.sin(playerIn.rotationPitch / 180.0F * (float) Math.PI) * f + 0.1F);

		playerIn.joinEntityItemWithWorld(entityitem);

		return entityitem;
	}

	/**
	 * Set the NBT tag "key" to be a BlockPos coordinates.
	 * 
	 * @param key
	 * @param capsule
	 * @param dest
	 */
	private void savePosition(String key, ItemStack capsule, BlockPos dest) {
		NBTTagCompound pos = new NBTTagCompound();
		pos.setInteger("x", dest.getX());
		pos.setInteger("y", dest.getY());
		pos.setInteger("z", dest.getZ());
		capsule.getTagCompound().setTag(key, pos);
	}

	/**
	 * Get the Capsule saving tool that can allocate a new Capsule zone in the
	 * capsule dimension.
	 * 
	 * @param capsuleWorld
	 * @return
	 */
	private CapsuleSavedData getCapsulePlacer(WorldServer capsuleWorld) {
		CapsuleSavedData capsulePlacer = (CapsuleSavedData) capsuleWorld.loadItemData(CapsuleSavedData.class, "capsulePositions");
		if (capsulePlacer == null) {
			capsulePlacer = new CapsuleSavedData("capsule");
			capsuleWorld.setItemData("capsulePositions", capsulePlacer);
		}
		return capsulePlacer;
	}

}
