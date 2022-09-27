package be.isach.ultracosmetics.cosmetics.pets;

import be.isach.ultracosmetics.UltraCosmetics;
import be.isach.ultracosmetics.UltraCosmeticsData;
import be.isach.ultracosmetics.config.MessageManager;
import be.isach.ultracosmetics.config.SettingsManager;
import be.isach.ultracosmetics.cosmetics.EntityCosmetic;
import be.isach.ultracosmetics.cosmetics.Updatable;
import be.isach.ultracosmetics.cosmetics.type.PetType;
import be.isach.ultracosmetics.player.UltraPlayer;
import be.isach.ultracosmetics.util.ItemFactory;
import be.isach.ultracosmetics.util.ServerVersion;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Vector;

import com.cryptomorin.xseries.XMaterial;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import me.gamercoder215.mobchip.EntityBrain;
import me.gamercoder215.mobchip.ai.memories.Memory;
import me.gamercoder215.mobchip.bukkit.BukkitBrain;

/**
 * Represents an instance of a pet summoned by a player.
 *
 * @author iSach
 * @since 03-08-2015
 */
public abstract class Pet extends EntityCosmetic<PetType,Mob> implements Updatable {

    private static final Set<Memory<?>> MEMORIES = new HashSet<>();

    static {
        try {
            for (Field field : Memory.class.getDeclaredFields()) {
                if (field.getModifiers() == (Modifier.PUBLIC | Modifier.STATIC | Modifier.FINAL)) {
                    MEMORIES.add((Memory<?>) field.get(null));
                    Bukkit.getLogger().info("found memory");
                }
            }
        } catch (IllegalArgumentException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * List of items popping out from Pet.
     */
    protected List<Item> items = new ArrayList<>();

    /**
     * ArmorStand for nametags. Only custom entity pets use this.
     */
    protected ArmorStand armorStand;

    /**
     * The {@link org.bukkit.inventory.ItemStack ItemStack} this pet drops, null if none.
     * Sometimes modified before dropping to change what is dropped
     */
    protected ItemStack dropItem;

    public Pet(UltraPlayer owner, PetType petType, UltraCosmetics ultraCosmetics, ItemStack dropItem) {
        super(owner, petType, ultraCosmetics);
        this.dropItem = dropItem;
    }

    public Pet(UltraPlayer owner, PetType petType, UltraCosmetics ultraCosmetics, XMaterial dropItem) {
        this(owner, petType, ultraCosmetics, dropItem.parseItem());
    }

    public Pet(UltraPlayer owner, PetType petType, UltraCosmetics ultraCosmetics) {
        this(owner, petType, ultraCosmetics, petType.getItemStack());
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void onEquip() {

        entity = spawnEntity();

        EntityBrain brain = BukkitBrain.getBrain(entity);
        brain.getGoalAI().clear();
        brain.getTargetAI().clear();
        brain.getScheduleManager().clear();
        MEMORIES.forEach(brain::removeMemory);

        brain.getGoalAI().put(new PetPathfinder(entity, getPlayer()), 0);
        getPlayer().sendMessage(ChatColor.RED + "Pathfinders:");
        BukkitBrain.getBrain(entity).getGoalAI().stream().forEach(w -> getPlayer().sendMessage(w.getPathfinder().getName() + ":" + w.getPriority()));

        if (entity instanceof Ageable) {
            Ageable ageable = (Ageable) entity;
            if (SettingsManager.getConfig().getBoolean("Pets-Are-Babies")) {
                ageable.setBaby();
            } else {
                ageable.setAdult();
            }
            ageable.setAgeLock(true);
        }

        if (entity instanceof Tameable) {
            ((Tameable) entity).setTamed(true);
        }

        // setCustomNameVisible(true) doesn't seem to work on 1.8, so we'll just use armor stands in that case
        if (isCustomEntity() || UltraCosmeticsData.get().getServerVersion() == ServerVersion.v1_8) {
            armorStand = (ArmorStand) entity.getWorld().spawnEntity(entity.getLocation(), EntityType.ARMOR_STAND);
            armorStand.setVisible(false);
            armorStand.setSmall(true);
            armorStand.setMarker(true);
            armorStand.setCustomNameVisible(true);
            FixedMetadataValue metadataValue = new FixedMetadataValue(getUltraCosmetics(), "C_AD_ArmorStand");
            armorStand.setMetadata("C_AD_ArmorStand", metadataValue);
            entity.setPassenger(armorStand);
        } else {
            getEntity().setCustomNameVisible(true);
        }

        updateName();

        entity.setRemoveWhenFarAway(false);
        if (SettingsManager.getConfig().getBoolean("Pets-Are-Silent") && UltraCosmeticsData.get().getServerVersion().isAtLeast(ServerVersion.v1_9)) {
            entity.setSilent(true);
        }

        entity.setMetadata("Pet", new FixedMetadataValue(getUltraCosmetics(), "UltraCosmetics"));
        setupEntity();
    }

    @Override
    protected void scheduleTask() {
        runTaskTimer(getUltraCosmetics(), 0, 3);
    }

    @Override
    public boolean tryEquip() {
        if (getType().isMonster() && getPlayer().getWorld().getDifficulty() == Difficulty.PEACEFUL) {
            getOwner().sendMessage(MessageManager.getMessage("Mounts.Cant-Spawn"));
            return false;
        }
        return true;
    }

    @Override
    public void run() {
        if (entity != null && !entity.isValid()) {
            clear();
            return;
        }

        if (!getOwner().isOnline() || getOwner().getCurrentPet() != this) {
            clear();
            return;
        }

        onUpdate();

    }

    protected void move(EntityBrain brain, Location loc, double speed) {
        brain.getController().moveTo(loc, speed);
    }

    @Override
    protected void onClear() {
        // Remove Armor Stand.
        if (armorStand != null) {
            armorStand.remove();
        }

        // Remove Pet Entity.
        removeEntity();

        // Remove items.
        items.stream().filter(Entity::isValid).forEach(Entity::remove);

        // Clear items.
        items.clear();
    }

    public ArmorStand getArmorStand() {
        return armorStand;
    }

    public boolean hasArmorStand() {
        return armorStand != null;
    }

    public List<Item> getItems() {
        return items;
    }

    public void updateName() {
        Entity rename;
        if (armorStand == null) {
            rename = entity;
        } else {
            rename = armorStand;
        }
        if (getOwner().getPetName(getType()) != null) {
            rename.setCustomName(getOwner().getPetName(getType()));
        } else {
            rename.setCustomName(getType().getEntityName(getPlayer()));
        }
    }

    @Override
    public void onUpdate() {
        if (SettingsManager.getConfig().getBoolean("Pets-Drop-Items")) {
            dropItem();
        }
    }

    public void dropItem() {
        // Not using the ItemFactory variance method for this one
        // because we want to bump the Y velocity a bit between calcs.
        Vector velocity = new Vector(RANDOM.nextDouble() - 0.5, RANDOM.nextDouble() / 2.0 + 0.3, RANDOM.nextDouble() - 0.5).multiply(0.4);
        final Item drop = ItemFactory.spawnUnpickableItem(dropItem, ((LivingEntity) entity).getEyeLocation(), velocity);
        items.add(drop);
        Bukkit.getScheduler().runTaskLater(getUltraCosmetics(), () -> {
            drop.remove();
            items.remove(drop);
        }, 5);
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() == getEntity()) event.setCancelled(true);
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() == getEntity()) event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (event.getPlayer() == getPlayer()) getEntity().teleport(getPlayer());
    }

    @Override
    protected String filterPlaceholders(String message) {
        String filtered = super.filterPlaceholders(message);
        String name = getOwner().getPetName(getType());
        if (name != null) {
            filtered += " " + ChatColor.GRAY + "(" + name + ChatColor.GRAY + ")";
        }
        return filtered;
    }

    public boolean isCustomEntity() {
        return false;
    }

    public boolean customize(String customization) {
        return false;
    }

    protected ItemStack parseCustomItem(String customization) {
        String[] parts = customization.split(":", 2);
        Material mat = Material.matchMaterial(parts[0]);
        if (mat == null) return null;
        ItemStack stack = new ItemStack(mat);
        if (parts.length > 1) {
            int model;
            try {
                model = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                return null;
            }
            ItemMeta meta = stack.getItemMeta();
            meta.setCustomModelData(model);
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
