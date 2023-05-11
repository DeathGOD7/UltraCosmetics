package be.isach.ultracosmetics.menu.buttons;

import be.isach.ultracosmetics.UltraCosmetics;
import be.isach.ultracosmetics.config.SettingsManager;
import be.isach.ultracosmetics.cosmetics.type.CosmeticType;
import be.isach.ultracosmetics.player.UltraPlayer;
import be.isach.ultracosmetics.util.ItemFactory;
import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class ToggleCosmeticButton extends CosmeticButton {
    private final boolean showPermissionInLore = SettingsManager.getConfig().getBoolean("No-Permission.Show-In-Lore");
    private final String permissionYes = SettingsManager.getConfig().getString("No-Permission.Lore-Message-Yes");
    private final String permissionNo = SettingsManager.getConfig().getString("No-Permission.Lore-Message-No");

    public ToggleCosmeticButton(UltraCosmetics ultraCosmetics, CosmeticType<?> cosmeticType) {
        super(ultraCosmetics, cosmeticType, false);
    }

    @Override
    protected ItemStack getBaseItem(UltraPlayer ultraPlayer) {
        String toggle = cosmeticType.getCategory().getActivateTooltip();
        boolean deactivate = ultraPlayer.hasCosmetic(cosmeticType.getCategory()) && ultraPlayer.getCosmetic(cosmeticType.getCategory()).getType() == cosmeticType;

        if (deactivate) {
            toggle = cosmeticType.getCategory().getDeactivateTooltip();
        }
        ItemStack stack = ItemFactory.rename(cosmeticType.getItemStack(), toggle + " " + cosmeticType.getName());
        if (deactivate) {
            ItemFactory.addGlow(stack);
        }
        ItemMeta meta = stack.getItemMeta();
        List<String> loreList = new ArrayList<>();
        if (cosmeticType.showsDescription()) {
            loreList.add("");
            loreList.addAll(cosmeticType.getDescription());
        }

        if (showPermissionInLore) {
            loreList.add("");
            String permissionLore = pm.hasPermission(ultraPlayer, cosmeticType) ? permissionYes : permissionNo;
            loreList.add(ChatColor.translateAlternateColorCodes('&', permissionLore));
        }
        meta.setLore(loreList);
        stack.setItemMeta(meta);
        return stack;
    }
}