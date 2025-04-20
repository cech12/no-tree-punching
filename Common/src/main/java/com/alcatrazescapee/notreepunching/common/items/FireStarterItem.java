package com.alcatrazescapee.notreepunching.common.items;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import com.alcatrazescapee.notreepunching.Config;
import com.alcatrazescapee.notreepunching.common.ModTags;
import com.alcatrazescapee.notreepunching.util.Helpers;
import com.alcatrazescapee.notreepunching.platform.Platform;
import com.alcatrazescapee.notreepunching.platform.PlatformOverride;

public class FireStarterItem extends TieredItem
{
    public FireStarterItem()
    {
        super(Tiers.WOOD, new Properties().durability(10));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)
    {
        player.startUsingItem(hand);
        return new InteractionResultHolder<>(InteractionResult.PASS, player.getItemInHand(hand));
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity)
    {
        if (level.isClientSide || !(entity instanceof Player player))
        {
            return stack;
        }
        BlockHitResult result = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);

        if (result.getType() != HitResult.Type.BLOCK)
        {
            return stack;
        }
        // If looking at a block
        BlockPos pos = result.getBlockPos();
        BlockState stateAt = level.getBlockState(pos);
        if (CampfireBlock.canLight(stateAt))
        {
            // Light campfire
            level.playSound(player, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, level.getRandom().nextFloat() * 0.4F + 0.8F);
            level.setBlock(pos, stateAt.setValue(BlockStateProperties.LIT, true), 11);
            return Helpers.hurtAndBreak(player, player.getUsedItemHand(), stack, 1);
        }
        BlockPos campfirePos = stateAt.canBeReplaced() ? pos : pos.relative(result.getDirection());
        if (level.getBlockState(campfirePos).canBeReplaced())
        {
            // try to make a fire pit
            List<ItemEntity> entities = level.getEntitiesOfClass(ItemEntity.class, new AABB(campfirePos, pos.offset(1, 2, 1)));
            List<ItemEntity> logEntities = new ArrayList<>(), kindlingEntities = new ArrayList<>(), soulFireEntities = new ArrayList<>();

            // Require 1 log, 3 kindling
            int logs = 0, kindling = 0, soulFire = 0;

            for (ItemEntity drop : entities)
            {
                ItemStack dropStack = drop.getItem();
                if (Helpers.isItem(dropStack.getItem(), ModTags.Items.FIRE_STARTER_LOGS))
                {
                    logs += dropStack.getCount();
                    logEntities.add(drop);
                }
                else if (Helpers.isItem(dropStack.getItem(), ModTags.Items.FIRE_STARTER_KINDLING))
                {
                    kindling += dropStack.getCount();
                    kindlingEntities.add(drop);
                }
                else if (Helpers.isItem(dropStack.getItem(), ModTags.Items.FIRE_STARTER_SOUL_FIRE_CATALYST))
                {
                    soulFire += dropStack.getCount();
                    soulFireEntities.add(drop);
                }
            }
            final boolean canMakeCampfire = Config.INSTANCE.fireStarterCanMakeCampfire.getAsBoolean();
            final boolean canMakeSoulCampfire = Config.INSTANCE.fireStarterCanMakeSoulCampfire.getAsBoolean() && soulFire >= 1;
            if (logs >= 1 && kindling >= 3 && (canMakeCampfire || canMakeSoulCampfire))
            {
                removeItems(logEntities, 1);
                removeItems(kindlingEntities, 3);

                Block resultBlock = Blocks.CAMPFIRE;
                if (canMakeSoulCampfire)
                {
                    resultBlock = Blocks.SOUL_CAMPFIRE;
                    removeItems(soulFireEntities, 1);
                }

                BlockPlaceContext context = new BlockPlaceContext(player, player.getUsedItemHand(), stack, result);
                BlockState resultBlockState = resultBlock.getStateForPlacement(context);
                if (resultBlockState != null) {
                    level.setBlockAndUpdate(campfirePos, resultBlockState.setValue(CampfireBlock.LIT, true));
                    return Helpers.hurtAndBreak(player, player.getUsedItemHand(), stack, 1);
                }
            }
        }
        BlockPos firePos = FireBlock.canBePlacedAt(level, pos, result.getDirection()) ? pos : pos.relative(result.getDirection());
        if (FireBlock.canBePlacedAt(level, firePos, result.getDirection()))
        {
            // No fire pit to make, try light a fire
            if (level.getRandom().nextFloat() < Config.INSTANCE.fireStarterFireStartChance.getAsFloat())
            {
                level.setBlockAndUpdate(firePos, Blocks.FIRE.defaultBlockState());
                return Helpers.hurtAndBreak(player, player.getUsedItemHand(), stack, 1);
            }
        }
        return stack;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack)
    {
        return UseAnim.BOW;
    }

    @Override
    public int getUseDuration(ItemStack stack)
    {
        return 30;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingTicks)
    {
        if (level.isClientSide && entity instanceof Player player)
        {
            BlockHitResult result = getPlayerPOVHitResult(player.level(), player, ClipContext.Fluid.NONE);
            if (player.level().getRandom().nextInt(5) == 0)
            {
                player.level().addParticle(ParticleTypes.SMOKE, result.getLocation().x, result.getLocation().y, result.getLocation().z, 0.0F, 0.1F, 0.0F);
            }
        }
    }

    @PlatformOverride(Platform.FORGE)
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment)
    {
        return enchantment.category == EnchantmentCategory.BREAKABLE;
    }

    private void removeItems(List<ItemEntity> itemEntities, int removeAmount)
    {
        for (ItemEntity logEntity : itemEntities)
        {
            ItemStack logStack = logEntity.getItem();
            int shrink = Math.min(logStack.getCount(), removeAmount);
            removeAmount -= shrink;
            logStack.shrink(shrink);
            if (logStack.getCount() == 0)
            {
                logEntity.remove(Entity.RemovalReason.KILLED);
            }
        }
    }
}