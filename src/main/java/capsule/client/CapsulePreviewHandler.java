package capsule.client;

import capsule.CapsuleMod;
import capsule.Config;
import capsule.blocks.BlockCapsuleMarker;
import capsule.blocks.CaptureTESR;
import capsule.blocks.TileEntityCapture;
import capsule.helpers.Spacial;
import capsule.items.CapsuleItem;
import capsule.structure.CapsuleTemplate;
import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.model.IBakedModel;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.ILightReader;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.template.PlacementSettings;
import net.minecraft.world.gen.feature.template.Template;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL14;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.*;

import static capsule.client.RendererUtils.*;
import static capsule.items.CapsuleItem.CapsuleState.*;
import static capsule.structure.CapsuleTemplate.recenterRotation;
import static net.minecraft.client.renderer.RenderType.*;

@Mod.EventBusSubscriber(modid = CapsuleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class CapsulePreviewHandler {
    private static final Logger LOGGER = LogManager.getLogger();

    public static final Map<String, List<AxisAlignedBB>> currentPreview = new HashMap<>();
    public static final Map<String, CapsuleTemplate> currentFullPreview = new HashMap<>();
    private static int lastSize = 0;
    private static int lastColor = 0;

    static double time = 0;

    public CapsulePreviewHandler() {
    }

    /**
     * Render recall preview when deployed capsule in hand
     */
    @SubscribeEvent
    public static void onWorldRenderLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getInstance();
        time += 1;

        if (mc.player != null) {
            tryPreviewRecall(mc.player.getHeldItemMainhand());
            tryPreviewDeploy(mc.player, event.getPartialTicks(), mc.player.getHeldItemMainhand(), event.getMatrixStack());
            tryPreviewLinkedInventory(mc.player, mc.player.getHeldItemMainhand());
        }
    }

    /**
     * try to spot a templateDownload command for client
     */
    @SubscribeEvent
    public static void OnClientChatEvent(ClientChatEvent event) {
        if ("/capsule downloadTemplate".equals(event.getMessage())) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                String structureName = CapsuleItem.getStructureName(mc.player.getHeldItemMainhand());
                CapsuleTemplate template = currentFullPreview.get(structureName);
                Path path = new File("capsule_exports").toPath();
                try {
                    Files.createDirectories(Files.exists(path) ? path.toRealPath() : path);
                } catch (IOException var19) {
                    LOGGER.error("Failed to create parent directory: {}", path);
                }

                CompoundNBT compoundnbt = template.writeToNBT(new CompoundNBT());

                SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd-HH-mm");
                try {
                    CompressedStreamTools.write(compoundnbt, path.resolve(df.format(new Date()) + "-" + structureName + ".nbt").toFile());
                } catch (Throwable var21) {
                    LOGGER.error(var21);
                }
            }
        }


    }

    /**
     * set captureBlock data (clientside only ) when capsule is in hand.
     */
    @SubscribeEvent
    public static void onLivingUpdateEvent(TickEvent.PlayerTickEvent event) {
        // do something to player every update tick:
        if (event.player instanceof ClientPlayerEntity && event.phase.equals(TickEvent.Phase.START)) {
            ClientPlayerEntity player = (ClientPlayerEntity) event.player;
            tryPreviewCapture(player, player.getHeldItemMainhand());
        }
    }

    private static boolean tryPreviewCapture(ClientPlayerEntity player, ItemStack heldItem) {
        // an item is in hand
        if (!heldItem.isEmpty()) {
            Item heldItemItem = heldItem.getItem();
            // it's an empty capsule : show capture zones
            if (heldItemItem instanceof CapsuleItem && (CapsuleItem.hasState(heldItem, EMPTY) || CapsuleItem.hasState(heldItem, EMPTY_ACTIVATED))) {
                //noinspection ConstantConditions
                if (heldItem.hasTag() && heldItem.getTag().contains("size")) {
                    setCaptureTESizeColor(heldItem.getTag().getInt("size"), CapsuleItem.getBaseColor(heldItem), player.getEntityWorld());
                    return true;
                }

            } else {
                setCaptureTESizeColor(0, 0, player.getEntityWorld());
            }
        } else {
            setCaptureTESizeColor(0, 0, player.getEntityWorld());
        }

        return false;
    }


    @SuppressWarnings("ConstantConditions")
    private static void tryPreviewDeploy(ClientPlayerEntity thePlayer, float partialTicks, ItemStack heldItemMainhand, MatrixStack matrixStack) {
        if (heldItemMainhand.getItem() instanceof CapsuleItem
                && heldItemMainhand.hasTag()
                && isDeployable(heldItemMainhand)
        ) {
            int size = CapsuleItem.getSize(heldItemMainhand);
            BlockRayTraceResult rtc = Spacial.clientRayTracePreview(thePlayer, partialTicks, size);
            if (rtc != null && rtc.getType() == RayTraceResult.Type.BLOCK) {
                int extendSize = (size - 1) / 2;
                BlockPos destOriginPos = rtc.getPos().add(rtc.getFace().getDirectionVec()).add(-extendSize, 0.01, -extendSize);
                String structureName = heldItemMainhand.getTag().getString("structureName");

                AxisAlignedBB errorBoundingBox = new AxisAlignedBB(
                        0,
                        +0.01,
                        0,
                        1.01,
                        1.01,
                        1.01);

                synchronized (CapsulePreviewHandler.currentPreview) {
                    synchronized (CapsulePreviewHandler.currentFullPreview) {
                        boolean haveFullPreview = CapsulePreviewHandler.currentFullPreview.containsKey(structureName);
                        if (haveFullPreview) {
                            DisplayFullPreview(matrixStack, destOriginPos, structureName, extendSize, heldItemMainhand, thePlayer.world);
                        }

                        if (CapsulePreviewHandler.currentPreview.containsKey(structureName) || size == 1) {
                            DisplayWireframePreview(thePlayer, heldItemMainhand, size, rtc, extendSize, destOriginPos, structureName, errorBoundingBox, haveFullPreview);
                        }
                    }
                }
            }
        }
    }

    public static List<RenderType> getBlockRenderTypes() {
        return ImmutableList.of(getSolid(), getCutoutMipped(), getCutout(), getTranslucent());
    }

    private static void DisplayFullPreview(MatrixStack matrixStack, BlockPos destOriginPos, String structureName, int extendSize, ItemStack heldItemMainhand, ILightReader world) {
        ActiveRenderInfo info = Minecraft.getInstance().getRenderManager().info;
        CapsuleTemplate template = CapsulePreviewHandler.currentFullPreview.get(structureName);
        PlacementSettings placement = CapsuleItem.getPlacement(heldItemMainhand);

        BlockRendererDispatcher blockrendererdispatcher = Minecraft.getInstance().getBlockRendererDispatcher();

        for (Template.BlockInfo blockInfo : CapsuleTemplate.processBlockInfos(template, null, destOriginPos, placement, template.getBlocks())) {
            BlockPos blockpos = blockInfo.pos.add(recenterRotation(extendSize, placement));
            BlockState state = blockInfo.state.mirror(placement.getMirror()).rotate(placement.getRotation());

            if (state.getRenderType() != BlockRenderType.INVISIBLE) {
                if (state.getRenderType() == BlockRenderType.MODEL) {
                    matrixStack.push();
                    float glitchIntensity = (float) (Math.cos(time * 0.1f) * Math.cos(time * 0.08f) * Math.cos(time * 0.12f)) - 0.3f;
                    glitchIntensity = (float) Math.min(0.04, Math.max(0, glitchIntensity));
                    float glitchIntensity2 = ((float) (Math.cos(time * 0.12f) * Math.cos(time * 0.09f) * Math.cos(time * 0.14f))) * glitchIntensity;
                    float glitchValue = (float) Math.min(0.15, Math.max(0, Math.tan(time * 0.5)));
                    float glitchValuey = (float) Math.min(0.3, Math.max(0, Math.tan(time * 0.2)));
                    float glitchValuez = (float) Math.min(0.15, Math.max(0, Math.tan(time * 0.8)));
                    float rot = 0;
                    if (placement.getRotation() == Rotation.CLOCKWISE_90) rot = 90;
                    if (placement.getRotation() == Rotation.CLOCKWISE_180) rot = 180;
                    if (placement.getRotation() == Rotation.COUNTERCLOCKWISE_90) rot = 270;
                    matrixStack.translate(
                            blockpos.getX() - info.getProjectedView().getX() + glitchIntensity2 * glitchValue,
                            blockpos.getY() - info.getProjectedView().getY() + glitchIntensity * glitchValuey,
                            blockpos.getZ() - info.getProjectedView().getZ() + glitchIntensity2 * glitchValuez);
                    matrixStack.scale(1 + glitchIntensity2 * glitchValuez, 1 + glitchIntensity * glitchValuey, 1);
//
                    for (net.minecraft.client.renderer.RenderType type : getBlockRenderTypes()) {
                        if (RenderTypeLookup.canRenderInLayer(state, type)) {
                            net.minecraftforge.client.ForgeHooksClient.setRenderLayer(type);
                            type.setupRenderState();
                            RenderSystem.enableBlend();
                            GL14.glBlendColor(0, 0, 0, 0.9f - glitchIntensity * 2);
                            RenderSystem.blendFunc(GlStateManager.SourceFactor.CONSTANT_ALPHA, GlStateManager.DestFactor.ONE_MINUS_CONSTANT_ALPHA);

                            Tessellator tessellator = Tessellator.getInstance();
                            BufferBuilder buffer = tessellator.getBuffer();
                            buffer.begin(7, DefaultVertexFormats.BLOCK);

                            IBakedModel model = blockrendererdispatcher.getModelForState(state);
                            blockrendererdispatcher.getBlockModelRenderer().renderModel(world, model, state, destOriginPos, matrixStack, buffer, false, new Random(), 42, OverlayTexture.NO_OVERLAY);
                            tessellator.draw();

                            RenderSystem.disableBlend();
                            RenderSystem.defaultBlendFunc();
                            type.clearRenderState();
                        }
                    }
                    net.minecraftforge.client.ForgeHooksClient.setRenderLayer(null);
                    matrixStack.pop();
                }
                RenderSystem.enableLighting();
            }
        }
    }

    private static void DisplayWireframePreview(ClientPlayerEntity thePlayer, ItemStack heldItemMainhand, int size, BlockRayTraceResult rtc, int extendSize, BlockPos destOriginPos, String structureName, AxisAlignedBB errorBoundingBox, boolean haveFullPreview) {
        List<AxisAlignedBB> blockspos = new ArrayList<>();
        if (size > 1) {
            blockspos = CapsulePreviewHandler.currentPreview.get(structureName);
        } else if (CapsuleItem.hasState(heldItemMainhand, EMPTY)) {
            // (1/2) hack this renderer for specific case : capture of a 1-sized empty capsule
            BlockPos pos = rtc.getPos().subtract(destOriginPos);
            blockspos.add(new AxisAlignedBB(pos, pos));
        }
        if (blockspos.isEmpty()) {
            BlockPos pos = new BlockPos(extendSize, 0, extendSize);
            blockspos.add(new AxisAlignedBB(pos, pos));
        }

        doPositionPrologue(Minecraft.getInstance().getRenderManager().info);
        doWirePrologue();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder bufferBuilder = tessellator.getBuffer();

        PlacementSettings placement = CapsuleItem.getPlacement(heldItemMainhand);

        for (AxisAlignedBB bb : blockspos) {
            BlockPos recenter = recenterRotation(extendSize, placement);
            AxisAlignedBB dest = CapsuleTemplate.transformedAxisAlignedBB(placement, bb)
                    .offset(destOriginPos.getX(), destOriginPos.getY() + 0.01, destOriginPos.getZ())
                    .offset(recenter.getX(), recenter.getY(), recenter.getZ())
                    .expand(1, 1, 1);

            int color = 0xDDDDDD;
            if (CapsuleItem.hasState(heldItemMainhand, EMPTY)) {
                // (2/2) hack this renderer for specific case : capture of a 1-sized empty capsule
                GlStateManager.lineWidth(haveFullPreview ? 2.0F : 5.0F);
                color = CapsuleItem.getBaseColor(heldItemMainhand);
            } else {
                for (double j = dest.minZ; j < dest.maxZ; ++j) {
                    for (double k = dest.minY; k < dest.maxY; ++k) {
                        for (double l = dest.minX; l < dest.maxX; ++l) {
                            BlockPos pos = new BlockPos(l, k, j);
                            if (!Config.overridableBlocks.contains(thePlayer.getEntityWorld().getBlockState(pos).getBlock())) {
                                GlStateManager.lineWidth(5.0F);
                                bufferBuilder.begin(1, DefaultVertexFormats.POSITION);
                                setColor(0xaa0000, 255);
                                drawCapsuleCube(errorBoundingBox.offset(pos), bufferBuilder);
                                tessellator.draw();
                            }
                        }
                    }
                }
            }

            if (!haveFullPreview) {
                GlStateManager.lineWidth(1);
                RenderSystem.enableBlend();
                bufferBuilder.begin(1, DefaultVertexFormats.POSITION);
                setColor(color, 160);
                drawCapsuleCube(dest, bufferBuilder);
                tessellator.draw();
            }
        }

        setColor(0xFFFFFF, 255);
        doWireEpilogue();
        doPositionEpilogue();
    }

    private static boolean isDeployable(ItemStack heldItemMainhand) {
        return CapsuleItem.hasState(heldItemMainhand, ACTIVATED)
                || CapsuleItem.hasState(heldItemMainhand, ONE_USE_ACTIVATED)
                || CapsuleItem.hasState(heldItemMainhand, BLUEPRINT)
                || CapsuleItem.getSize(heldItemMainhand) == 1 && !CapsuleItem.hasState(heldItemMainhand, DEPLOYED);
    }

    private static void tryPreviewRecall(ItemStack heldItem) {
        // an item is in hand
        if (heldItem != null) {
            Item heldItemItem = heldItem.getItem();
            // it's an empty capsule : show capture zones
            //noinspection ConstantConditions
            if (heldItemItem instanceof CapsuleItem
                    && (CapsuleItem.hasState(heldItem, DEPLOYED) || CapsuleItem.hasState(heldItem, BLUEPRINT))
                    && heldItem.hasTag()
                    && heldItem.getTag().contains("spawnPosition")) {
                previewRecall(heldItem);
            }
        }
    }

    private static void tryPreviewLinkedInventory(ClientPlayerEntity player, ItemStack heldItem) {
        if (heldItem != null) {
            Item heldItemItem = heldItem.getItem();
            if (heldItemItem instanceof CapsuleItem
                    && CapsuleItem.isBlueprint(heldItem)
                    && CapsuleItem.hasSourceInventory(heldItem)) {
                BlockPos location = CapsuleItem.getSourceInventoryLocation(heldItem);
                Integer dimension = CapsuleItem.getSourceInventoryDimension(heldItem);
                if (location != null
                        && dimension != null
                        && dimension.equals(player.dimension.getId())
                        && location.distanceSq(player.getPosX(), player.getPosY(), player.getPosZ(), true) < 60 * 60) {
                    previewLinkedInventory(location, heldItem);
                }
            }
        }
    }

    private static void previewLinkedInventory(BlockPos location, ItemStack capsule) {
        doPositionPrologue(Minecraft.getInstance().getRenderManager().info);
        doOverlayPrologue();

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder bufferBuilder = tessellator.getBuffer();
        bufferBuilder.begin(7, DefaultVertexFormats.POSITION);
        setColor(0x5B9CFF, 80);
        drawCube(location, 0, bufferBuilder);
        tessellator.draw();

        doOverlayEpilogue();
        doPositionEpilogue();
    }

    private static void previewRecall(ItemStack capsule) {
        if (capsule.getTag() == null) return;
        CompoundNBT linkPos = capsule.getTag().getCompound("spawnPosition");

        int size = CapsuleItem.getSize(capsule);
        int extendSize = (size - 1) / 2;
        int color = CapsuleItem.getBaseColor(capsule);

        CaptureTESR.drawCaptureZone(
                linkPos.getInt("x") + extendSize,
                linkPos.getInt("y") - 1,
                linkPos.getInt("z") + extendSize, size,
                extendSize, color, Minecraft.getInstance().getRenderManager().info);
    }

    private static void setCaptureTESizeColor(int size, int color, World worldIn) {
        if (size == lastSize && color == lastColor) return;

        // change MinecraftNBT of all existing TileEntityCapture in the world to make them display the preview zone
        // remember it's client side only
        for (TileEntityCapture te : new ArrayList<>(TileEntityCapture.instances)) {
            if (te.getWorld() == worldIn) {
                TileEntityCapture tec = te;
                CompoundNBT teData = tec.getTileData();
                if (teData.getInt("size") != size || teData.getInt("color") != color) {
                    tec.getTileData().putInt("size", size);
                    tec.getTileData().putInt("color", color);
                    if (te.getBlockState().has(BlockCapsuleMarker.PROJECTING)) {
                        worldIn.setBlockState(te.getPos(), te.getBlockState().with(BlockCapsuleMarker.PROJECTING, size > 0));
                    }
                }
            }
        }
        lastSize = size;
        lastColor = color;
    }


}
