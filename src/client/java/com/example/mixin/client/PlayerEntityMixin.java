package com.example.mixin.client;
import com.example.NovaTaggerClient;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(Player.class)
public class PlayerEntityMixin {
    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    public void prependTier(CallbackInfoReturnable<Component> cir) {
        Player player = (Player) (Object) this;
        String username = player.getGameProfile().getName();
        String lowerUsername = username.toLowerCase();
        NovaTaggerClient.PlayerTierData tierData = NovaTaggerClient.TIER_CACHE.get(lowerUsername);
        if (tierData == null) {
            NovaTaggerClient.fetchPlayerTiers(username);
            return;
        }
        NovaTaggerClient.KitTier highestTier = tierData.getHighestTier();
        if (highestTier != null) {
            Component original = cir.getReturnValue();
            int tierColor = getTierColor(highestTier.tierName);
            ResourceLocation iconFont = ResourceLocation.fromNamespaceAndPath("novatagger", "icons");
            Style iconStyle = Style.EMPTY.withFont(iconFont);
            MutableComponent newName = Component.empty();
            char icon = getKitIconChar(highestTier.kitName);
            if (icon != ' ') {
                newName.append(Component.literal(String.valueOf(icon)).setStyle(iconStyle));
            }
            newName.append(Component.literal(highestTier.tierName).setStyle(Style.EMPTY.withColor(tierColor)));
            newName.append(Component.literal(" | ").setStyle(Style.EMPTY.withColor(0x808080)));
            newName.append(original);
            cir.setReturnValue(newName);
        }
    }
    private char getKitIconChar(String kitName) {
        switch (kitName.toLowerCase()) {
            case "pufferfish": return '\uE701';
            case "axepotion": return '\uE702';
            case "diamond_mace": return '\uE703';
            case "diamond_op": return '\uE704';
            case "diamondcart": return '\uE705';
            case "iron_vanilla": return '\uE706';
            case "ironuhc": return '\uE707';
            case "modernsmp": return '\uE708';
            case "shieldlessnetherite": return '\uE709';
            default: return ' ';
        }
    }
    private int getTierColor(String tierName) {
        switch (tierName) {
            case "HT1": return 0xe8ba3a;
            case "LT1": return 0xd5b355;
            case "HT2": return 0xc4d3e7;
            case "LT2": return 0xa0a7b2;
            case "HT3": return 0xf89f5a;
            case "LT3": return 0xc67b42;
            case "HT4": return 0x81749a;
            case "LT4": return 0x655b79;
            case "HT5": return 0x8f82a8;
            case "LT5": return 0x655b79;
            default: return 0xD3D3D3;
        }
    }
}