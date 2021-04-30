package dev.fulmineo.companion_bats.item;

import dev.fulmineo.companion_bats.CompanionBats;
import dev.fulmineo.companion_bats.entity.CompanionBatEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.TargetPredicate;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class CompanionBatCommandFluteItem extends Item {

	public CompanionBatCommandFluteItem(Settings settings) {
        super(settings);
    }

	@Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack itemStack = user.getStackInHand(hand);

		/*  Modes:
			Null - attack
			1 - rest
			2 - toggle defend method
		*/

		if (user.isSneaking()){
			if (itemStack.isOf(CompanionBats.COMMAND_FLUTE_1))  {
				itemStack = new ItemStack(CompanionBats.COMMAND_FLUTE);
			} else {
				itemStack = new ItemStack(CompanionBats.COMMAND_FLUTE_1);
			}
		} else {
			if (world instanceof ServerWorld) {
				double distance = 20.0D;
				Vec3d vec3d = user.getCameraPosVec(1.0F);
				Vec3d vec3d2 = user.getRotationVec(1.0F);
				Vec3d vec3d3 = vec3d.add(vec3d2.x * distance, vec3d2.y * distance, vec3d2.z * distance);
				Box box = user.getBoundingBox().stretch(vec3d2.multiply(distance)).expand(1.0D, 1.0D, 1.0D);
				EntityHitResult entityHitResult = ProjectileUtil.raycast(user, vec3d, vec3d3, box, (entityx) -> !entityx.isSpectator() && entityx.collides() && entityx instanceof LivingEntity && CompanionBatEntity.canAttackWithOwnerStatic((LivingEntity)entityx, user), distance * distance);
				if (entityHitResult != null) {
					LivingEntity entity = (LivingEntity)entityHitResult.getEntity();
					entity.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 10, 0, false, false));
					user.setAttacker(entity);
				} else {
					TargetPredicate predicate = new TargetPredicate().setPredicate((livingEntity) -> !livingEntity.isSpectator() && livingEntity != user && CompanionBatEntity.canAttackWithOwnerStatic(livingEntity, user));
					LivingEntity entity = world.getClosestEntity(LivingEntity.class, predicate, user, user.getX(), user.getY(), user.getZ(), box);
					if (entity != null) {
						entity.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 10, 0, false, false));
						user.setAttacker(entity);
					}
				}
			}

			user.getItemCooldownManager().set(CompanionBats.COMMAND_FLUTE, 10);
		}

		return TypedActionResult.success(itemStack);
    }

}
