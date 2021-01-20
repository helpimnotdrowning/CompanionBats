package dev.fulmineo.companion_bats.entity;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;

import org.apache.logging.log4j.Level;
import org.jetbrains.annotations.Nullable;

import dev.fulmineo.companion_bats.CompanionBats;
import dev.fulmineo.companion_bats.entity.CompanionBatLevels.CompanionBatClassLevel;
import dev.fulmineo.companion_bats.entity.ai.control.CompanionBatMoveControl;
import dev.fulmineo.companion_bats.entity.ai.goal.CompanionBatFollowOwnerGoal;
import dev.fulmineo.companion_bats.entity.ai.goal.CompanionBatPickUpItemGoal;
import dev.fulmineo.companion_bats.entity.ai.goal.CompanionBatRoostGoal;
import dev.fulmineo.companion_bats.entity.ai.goal.CompanionBatTransferItemsToOwnerGoal;
import dev.fulmineo.companion_bats.item.CompanionBatAbility;
import dev.fulmineo.companion_bats.item.CompanionBatArmorItem;
import dev.fulmineo.companion_bats.item.CompanionBatClass;
import dev.fulmineo.companion_bats.item.CompanionBatItem;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.AttackWithOwnerGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.TrackOwnerAttackerGoal;
import net.minecraft.entity.ai.pathing.BirdNavigation;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.HorseBaseEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class CompanionBatEntity extends TameableEntity {
	private static final UUID BAT_ARMOR_BONUS_ID = UUID.fromString("556E1665-8B10-40C8-8F9D-CF9B1667F295");
	private static final TrackedData<Byte> BAT_FLAGS;
    private Map<CompanionBatAbility, Integer> abilities = new HashMap<>();
	private int healTicks;

	// Configurable values
	// TODO: Add configuration for these values

	private static final float BASE_HEALTH = 6.0F;
	private static final float BASE_ATTACK = 2.0F;
	private static final float BASE_SPEED =  0.3F;

	private static final int HEAL_TICKS = 600;
	private static final float LIFESTEAL_PERCENTAGE = 0.2F;

    public static final Predicate<ItemStack> IS_FOOD_ITEM;
    public static final Predicate<ItemEntity> IS_FOOD_ITEM_ENTITY;
    public BlockPos hangingPosition;

	private CompanionBatClass currentClass;
	private int exp = 0;
    private int classExp = 0;
    private int level = -1;

    public CompanionBatEntity(EntityType<? extends TameableEntity> entityType, World world) {
        super(entityType, world);
        this.moveControl = new CompanionBatMoveControl(this, 10);
        this.setPathfindingPenalty(PathNodeType.DANGER_FIRE, -1.0F);
        this.setPathfindingPenalty(PathNodeType.DAMAGE_FIRE, -1.0F);
        this.setRoosting(false);
        this.setSitting(false);
    }

    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(BAT_FLAGS, (byte)0);
    }

    protected void initGoals() {
        this.goalSelector.add(2, new CompanionBatPickUpItemGoal(this, 1.0D, 16.0F));
        this.goalSelector.add(3, new CompanionBatFollowOwnerGoal(this, 1.0D, 2.5F, 16.0F));
        this.goalSelector.add(4, new CompanionBatTransferItemsToOwnerGoal(this, 2.5F));
        this.goalSelector.add(5, new CompanionBatRoostGoal(this, 0.75F, 4.0F));
    }

    public void writeCustomDataToTag(CompoundTag tag) {
        super.writeCustomDataToTag(tag);
        tag.putInt("exp", this.getExp());
    }

    public void readCustomDataFromTag(CompoundTag tag) {
        super.readCustomDataFromTag(tag);
        this.setExp(tag.getInt("exp"));
    }

    protected float getSoundVolume() {
        return 0.1F;
    }

    protected float getSoundPitch() {
        return super.getSoundPitch() * 0.95F;
    }

    @Nullable
    public SoundEvent getAmbientSound() {
        return this.isRoosting() && this.random.nextInt(4) != 0 ? null : SoundEvents.ENTITY_BAT_AMBIENT;
    }

    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_BAT_HURT;
    }

    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_BAT_DEATH;
    }

    public boolean isPushable() {
        return false;
    }

    protected void pushAway(Entity entity) {
    }

    protected void tickCramming() {
    }

    public static DefaultAttributeContainer.Builder createMobAttributes() {
        return MobEntity.createMobAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, BASE_HEALTH).add(EntityAttributes.GENERIC_ATTACK_DAMAGE, BASE_ATTACK).add(EntityAttributes.GENERIC_MOVEMENT_SPEED, BASE_SPEED);
    }

    /**
    * Returns whether this bat is hanging upside-down under a block.
    */
    public boolean isRoosting() {
        return ((Byte)this.dataTracker.get(BAT_FLAGS) & 1) != 0;
    }

    public boolean isInjured() {
        return this.getHealth() < this.getMaxHealth();
    }

    public boolean isAboutToRoost() {
        return this.hangingPosition != null;
    }

    public void setRoosting(boolean roosting) {
        byte b = (Byte)this.dataTracker.get(BAT_FLAGS);
        if (roosting) {
           this.dataTracker.set(BAT_FLAGS, (byte)(b | 1));
           this.healTicks = HEAL_TICKS;
        } else {
           this.dataTracker.set(BAT_FLAGS, (byte)(b & -2));
           this.hangingPosition = null;
        }
    }

    public void tick() {
        super.tick();
        if (this.isRoosting()) {
            this.setVelocity(Vec3d.ZERO);
            this.setPos(this.getX(), (double)MathHelper.floor(this.getY()) + 1.0D - (double)this.getHeight(), this.getZ());
        } else {
            this.setVelocity(this.getVelocity().multiply(1.0D, 0.6D, 1.0D));
        }
    }

    public void onDeath(DamageSource source) {
        if (!this.returnToPlayerInventory()) super.onDeath(source);
     }

    protected boolean canClimb() {
        return false;
    }

    public boolean handleFallDamage(float fallDistance, float damageMultiplier) {
        return false;
    }

    protected void fall(double heightDifference, boolean onGround, BlockState landedState, BlockPos landedPosition) {
    }

    public boolean canAvoidTraps() {
        return true;
	}

    public boolean damage(DamageSource source, float amount) {
        if (this.isInvulnerableTo(source)) {
            return false;
        } else {
			if (!this.world.isClient) {
				if (this.isRoosting()) {
					this.setRoosting(false);
				}
			}
            return super.damage(source, amount);
        }
    }

    public boolean canAttackWithOwner(LivingEntity target, LivingEntity owner) {
        if (!(target instanceof CreeperEntity)) {
            if (target instanceof WolfEntity) {
                WolfEntity wolfEntity = (WolfEntity)target;
                return !wolfEntity.isTamed() || wolfEntity.getOwner() != owner;
            } else if (target instanceof CompanionBatEntity){
                CompanionBatEntity companionBatEntity = (CompanionBatEntity)target;
                return companionBatEntity.getOwner() != owner;
            } else if (target instanceof PlayerEntity && owner instanceof PlayerEntity && !((PlayerEntity)owner).shouldDamagePlayer((PlayerEntity)target)) {
                return false;
            } else if (target instanceof HorseBaseEntity && ((HorseBaseEntity)target).isTame()) {
                return false;
            } else {
                return !(target instanceof TameableEntity) || !((TameableEntity)target).isTamed();
            }
        } else {
           return false;
        }
    }

    @Override
    protected void mobTick() {
        if (this.isRoosting()) {
			if (this.getTarget() != null) this.setRoosting(false);
            this.healTicks--;
            if (this.hangingPosition == null || !this.world.getBlockState(this.hangingPosition).isSolidBlock(this.world, this.hangingPosition)) {
                this.setRoosting(false);
                if (!this.isSilent()) {
                    this.world.syncWorldEvent((PlayerEntity)null, 1025, this.getBlockPos(), 0);
                }
            } else if (this.healTicks <= 0){
                this.healTicks = HEAL_TICKS;
                if (this.isInjured()){
                    int val = Math.max(1, (int)(this.getMaxHealth() * 10 / 100));
                    this.heal(val);
                }
            }
        }
    }

    /*
    public void tickMovement() {
        super.mobTick();
    }
    */

    public CompanionBatEntity createChild(ServerWorld serverWorld, PassiveEntity passiveEntity) {
        return null;
    }

    public boolean tryAttack(Entity target) {
        float targetHealth = target instanceof LivingEntity ? ((LivingEntity)target).getHealth() : 0;
        boolean bl = target.damage(DamageSource.mob(this), (float)((int)this.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE)));
        if (bl) {
            this.dealDamage(this, target);
			float damageDealt = targetHealth - (target instanceof LivingEntity ? ((LivingEntity)target).getHealth() : 0);
			CompanionBats.info("damage dealt " + damageDealt);
            if (damageDealt > 0){
                this.gainExp();
                if (this.hasAbility(CompanionBatAbility.LIFESTEAL)) this.applyLifesteal(damageDealt);
            }
        }
        return bl;
    }

    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        ItemStack itemStack = player.getStackInHand(hand);
        if (this.world.isClient){
            if (this.isInjured() && IS_FOOD_ITEM.test(itemStack)){
                for (int i = 0; i < 3; i++) {
                    double d = this.random.nextGaussian() * 0.01D;
                    double e = this.random.nextGaussian() * 0.01D;
                    double f = this.random.nextGaussian() * 0.01D;
                    this.world.addParticle(ParticleTypes.HAPPY_VILLAGER, this.getParticleX(0.5D), this.getRandomBodyY(), this.getParticleZ(1.0D), d, e, f);
                }
                return ActionResult.CONSUME;
            } else {
                return ActionResult.PASS;
            }
        } else {
            boolean res = this.healWithItem(itemStack);
            if (res) {
                if (!player.getAbilities().creativeMode){
                    itemStack.decrement(1);
                }
                return ActionResult.SUCCESS;
            }
        }
        return ActionResult.PASS;
    }

    public boolean healWithItem(ItemStack stack){
        if (this.getHealth() == this.getMaxHealth()) return false;
        float amount = getItemHealAmount(stack);
        if (amount > 0){
            this.heal(amount);
            return true;
        }
        return false;
    }

    public static float getItemHealAmount(ItemStack stack){
        if (stack.isOf(Items.PUMPKIN_PIE)){
            return 6.0F;
        }
        return 0;
	}

	public static float getMaxLevelHealth(){
		return BASE_HEALTH + CompanionBatLevels.getLevelHealth(CompanionBatLevels.LEVELS.length-1);
	}

	public static float getLevelHealth(int level){
		return BASE_HEALTH + CompanionBatLevels.getLevelHealth(level);
	}

	public static float getLevelAttack(int level){
		return BASE_ATTACK + CompanionBatLevels.getLevelAttack(level);
	}

	public static float getLevelSpeed(int level){
		return BASE_SPEED + CompanionBatLevels.getLevelSpeed(level);
	}

    protected EntityNavigation createNavigation(World world) {
        BirdNavigation birdNavigation = new BirdNavigation(this, world);
        birdNavigation.setCanPathThroughDoors(false);
        birdNavigation.setCanSwim(true);
        birdNavigation.setCanEnterOpenDoors(true);
        return birdNavigation;
    }

    protected float getActiveEyeHeight(EntityPose pose, EntityDimensions dimensions) {
        return dimensions.height / 2.0F;
    }

    public boolean returnToPlayerInventory() {
        ServerPlayerEntity player = (ServerPlayerEntity)this.getOwner();
        if (player != null){
            PlayerInventory inventory = player.getInventory();
            ImmutableList<DefaultedList<ItemStack>> mainAndOffhand = ImmutableList.of(inventory.main, inventory.offHand);
            Iterator<DefaultedList<ItemStack>> iterator = mainAndOffhand.iterator();
            while(iterator.hasNext()) {
                DefaultedList<ItemStack> defaultedList = (DefaultedList<ItemStack>)iterator.next();
                for(int i = 0; i < defaultedList.size(); ++i) {
                    if (defaultedList.get(i).getItem() == CompanionBats.BAT_FLUTE_ITEM && defaultedList.get(i).getTag().getUuid("entityUuid").equals(this.getUuid())) {
                        // Get item in hand
                        this.discard();
                        defaultedList.set(i, this.toItem());
                        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_SLIME_ATTACK, SoundCategory.AMBIENT, 1F, 1F);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public int getExp(){
        return this.exp;
    }

    private void setExp(int exp){
        this.exp = exp;
        this.tryLevelUp();
    }

    private void addExp(int expToAdd){
        this.setExp(this.exp + expToAdd);
    }

    private void gainExp(){
		if (this.exp < CompanionBatLevels.LEVELS[CompanionBatLevels.LEVELS.length-1].totalExpNeeded){
			this.addExp(1);
		}
		if (this.currentClass != null){
			CompanionBatClassLevel[] classLevels = CompanionBatLevels.CLASS_LEVELS.get(this.currentClass);
			if (this.classExp < classLevels[classLevels.length-1].totalExpNeeded){
				this.addClassExp(1);
				CompanionBats.log(Level.INFO, "total class exp "+this.getClassExp());
			}
		}
    }

    private void applyLifesteal(float damageDealt){
		this.heal(damageDealt * LIFESTEAL_PERCENTAGE);
		CompanionBats.info("heal with lifesteal "+(damageDealt * LIFESTEAL_PERCENTAGE));
	}

	private int getClassExp() {
		return this.classExp;
	}

	private void setClassExp(int value) {
		this.classExp = value;
	}

	private void addClassExp(int expToAdd){
        this.setClassExp(this.exp + expToAdd);
    }

	private void setLevelAbilities(){
		for (CompanionBatClass batClass : CompanionBatClass.values()){
			int classExp = this.getClassExp();
			for (CompanionBatClassLevel level: CompanionBatLevels.CLASS_LEVELS.get(batClass)){
				if (level.totalExpNeeded > classExp) break;
				if (level.ability != null){
					this.abilities.put(level.ability, level.abilityLevel);
				}
			}
		}
	}

	private void setAbilitiesEffects(){
		if (!this.hasAbility(CompanionBatAbility.CANNOT_ATTACK)){
			this.goalSelector.add(1, new MeleeAttackGoal(this, 1.0D, true));
			this.targetSelector.add(1, new TrackOwnerAttackerGoal(this));
			this.targetSelector.add(2, new AttackWithOwnerGoal(this));
			this.targetSelector.add(3, (new RevengeGoal(this, new Class[0])).setGroupRevenge());
		}
		if (this.hasAbility(CompanionBatAbility.INCREASED_DAMAGE)){
			EntityAttributeInstance attr = this.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
			attr.addTemporaryModifier(new EntityAttributeModifier("Gem attack bonus", (double)(attr.getBaseValue() * 50 / 100), EntityAttributeModifier.Operation.ADDITION));
		}
		if (this.hasAbility(CompanionBatAbility.INCREASED_SPEED)){
			EntityAttributeInstance attr = this.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
			attr.addTemporaryModifier(new EntityAttributeModifier("Gem speed bonus", (double)(attr.getBaseValue() * 50 / 100), EntityAttributeModifier.Operation.ADDITION));
			CompanionBats.info(""+attr.getValue());
		}
		if (this.hasAbility(CompanionBatAbility.FIRE_RESISTANCE)){
			this.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 1000000000, 1, false, false));
		}
	}

    protected void tryLevelUp(){
        if (CompanionBatLevels.LEVELS.length > this.level + 1 && CompanionBatLevels.LEVELS[this.level + 1].totalExpNeeded <= this.exp) {
            this.level++;
            this.setLevelAttributes(this.level);
            this.heal(this.getMaxHealth());
            this.notifyLevelUp(this.level);
        }
    }

    protected void setLevelAttributes(int level){
        this.getAttributes().getCustomInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(getLevelHealth(level));
        this.getAttributes().getCustomInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE).setBaseValue(getLevelAttack(level));
        this.getAttributes().getCustomInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED).setBaseValue(getLevelSpeed(level));
    }

    protected void notifyLevelUp(int level){
        if (level > 0){
            MutableText message = new TranslatableText("entity.companion_bats.bat.level_up", this.hasCustomName() ? this.getCustomName() : new TranslatableText("entity.companion_bats.bat.your_bat"), level+1).append("\n");
            if (CompanionBatLevels.LEVELS[level].healthBonus > CompanionBatLevels.LEVELS[level-1].healthBonus){
                message.append(new TranslatableText("entity.companion_bats.bat.level_up_health", (int)(CompanionBatLevels.LEVELS[level].healthBonus - CompanionBatLevels.LEVELS[level-1].healthBonus))).append(" ");
            }
            if (CompanionBatLevels.LEVELS[level].attackBonus > CompanionBatLevels.LEVELS[level-1].attackBonus){
                message.append(new TranslatableText("entity.companion_bats.bat.level_up_attack", (int)(CompanionBatLevels.LEVELS[level].attackBonus - CompanionBatLevels.LEVELS[level-1].attackBonus))).append(" ");
            }
            if (CompanionBatLevels.LEVELS[level].speedBonus > CompanionBatLevels.LEVELS[level-1].speedBonus){
                message.append(new TranslatableText("entity.companion_bats.bat.level_up_speed", Math.round(100 - (CompanionBatLevels.LEVELS[level-1].speedBonus / CompanionBatLevels.LEVELS[level].speedBonus * 100)))).append(" ");
            }
            ((PlayerEntity)this.getOwner()).sendMessage(message, false);
        }
    }

    protected ItemStack toItem(){
		ItemStack batItemStack = new ItemStack(CompanionBats.BAT_ITEM);
		if (this.hasCustomName()){
			batItemStack.setCustomName(this.getCustomName());
		}
        // Set companion bat item durability realtive to the bat health
        float percentage = 1 - (this.getHealth() / this.getMaxHealth());
        batItemStack.setDamage(Math.round(percentage * batItemStack.getMaxDamage()));
        CompoundTag entityData = CompanionBatItem.createEntityData(batItemStack);
        entityData.putFloat("health", this.getHealth());
		entityData.putInt("exp", this.getExp());
        entityData.put("gem", this.getGem().toTag(new CompoundTag()));
        entityData.put("armor", this.getArmorType().toTag(new CompoundTag()));
        entityData.put("bundle", this.getBundle().toTag(new CompoundTag()));
        return batItemStack;
    }

    public void fromItem(PlayerEntity owner, CompoundTag entityData){
        this.setOwner(owner);
        this.exp = entityData.getInt("exp");
        this.level = getLevelByExp(this.exp);
		this.setLevelAttributes(this.level);
        this.equipGem(ItemStack.fromTag(entityData.getCompound("gem")));
        this.equipArmor(ItemStack.fromTag(entityData.getCompound("armor")));
        this.equipBundle(ItemStack.fromTag(entityData.getCompound("bundle")));
		this.setHealth(entityData.getFloat("health"));
		this.setLevelAbilities();
		this.setAbilitiesEffects();
    }

	private void equipGem(ItemStack stack) {
        this.equipStack(EquipmentSlot.HEAD, stack);
        this.setEquipmentDropChance(EquipmentSlot.HEAD, 0.0F);
    }

	private void equipArmor(ItemStack stack) {
		this.equipStack(EquipmentSlot.CHEST, stack);
		this.setEquipmentDropChance(EquipmentSlot.CHEST, 0.0F);
        if (!this.world.isClient) {
			this.getAttributeInstance(EntityAttributes.GENERIC_ARMOR).removeModifier(BAT_ARMOR_BONUS_ID);
			if (stack.getItem() instanceof CompanionBatArmorItem) {
				CompanionBatArmorItem armor = (CompanionBatArmorItem)stack.getItem();
				this.currentClass = armor.getBatClass();
				int armorToAdd = armor.getProtectionAmount();
				if (armorToAdd != 0) {
					this.getAttributeInstance(EntityAttributes.GENERIC_ARMOR).addTemporaryModifier(new EntityAttributeModifier(BAT_ARMOR_BONUS_ID, "Bat armor bonus", (double)armorToAdd, EntityAttributeModifier.Operation.ADDITION));
				}
			}
        }
	}

	private void equipBundle(ItemStack stack) {
        this.equipStack(EquipmentSlot.FEET, stack);
        this.setEquipmentDropChance(EquipmentSlot.FEET, 0.0F);
    }

	public ItemStack getGem(){
        return this.getEquippedStack(EquipmentSlot.HEAD);
    }

    public ItemStack getArmorType() {
        return this.getEquippedStack(EquipmentSlot.CHEST);
	}

	public ItemStack getBundle(){
        return this.getEquippedStack(EquipmentSlot.FEET);
    }

	public boolean hasAbility(CompanionBatAbility ability) {
		Integer abilityLevel = this.abilities.get(ability);
		return abilityLevel != null && abilityLevel > 0;
	}

    public static int getLevelByExp(int exp) {
        for (int i=CompanionBatLevels.LEVELS.length-1; i>=0; i--) {
            if (CompanionBatLevels.LEVELS[i].totalExpNeeded <= exp){
                return i;
            }
        }
        return CompanionBatLevels.LEVELS.length-1;
    }

    public static void setDefaultEntityData(CompoundTag tag){
        tag.putFloat("health", BASE_HEALTH);
        tag.putInt("exp", 0);
    }

    static {
        BAT_FLAGS = DataTracker.registerData(CompanionBatEntity.class, TrackedDataHandlerRegistry.BYTE);
        IS_FOOD_ITEM = (itemStack) -> itemStack.isOf(Items.PUMPKIN_PIE);
        IS_FOOD_ITEM_ENTITY = (itemEntity) -> IS_FOOD_ITEM.test(itemEntity.getStack());
    }
}