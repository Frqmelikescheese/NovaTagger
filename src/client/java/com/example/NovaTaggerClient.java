package com.example;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
public class NovaTaggerClient implements ClientModInitializer {
	private static final String API_URL = "https://managing-jaquith-lmaozeekid-5bf1cd1b.koyeb.app/api/v1/overall?search=";
	private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
	private static final Gson GSON = new Gson();
	public static final Map<String, PlayerTierData> TIER_CACHE = new ConcurrentHashMap<>();
	private static final Map<String, Long> CACHE_TIMESTAMPS = new ConcurrentHashMap<>();
	private static final long CACHE_DURATION = 300000;
	@Override
	public void onInitializeClient() {
		NovaTagger.LOGGER.info("NovaTagger Client initialized!");
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(ClientCommandManager.literal("tiers")
					.then(ClientCommandManager.argument("username", StringArgumentType.string())
							.executes(context -> {
								String username = StringArgumentType.getString(context, "username");
								Minecraft client = Minecraft.getInstance();
								sendMessage(client, "§7§m                                                    ");
								sendMessage(client, "§b§lNovaTagger §8| §7Fetching data for §f" + username + "§7...");
								sendMessage(client, "§7§m                                                    ");
								fetchPlayerTiers(username).thenAccept(data -> {
									client.execute(() -> {
										if (data != null) {
											displayPlayerData(client, data);
										} else {
											sendMessage(client, "§7§m                                                    ");
											sendMessage(client, "§c✖ Player not found: §f" + username);
											sendMessage(client, "§7§m                                                    ");
										}
									});
								}).exceptionally(e -> {
									client.execute(() -> {
										sendMessage(client, "§7§m                                                    ");
										sendMessage(client, "§c✖ Error fetching data: §f" + e.getMessage());
										sendMessage(client, "§7§m                                                    ");
									});
									return null;
								});
								return 1;
							}))
					.executes(context -> {
						Minecraft client = Minecraft.getInstance();
						sendMessage(client, "§c✖ Usage: §f/tiers <username>");
						return 1;
					}));
		});
	}
	private static void displayPlayerData(Minecraft client, PlayerTierData data) {
		sendMessage(client, "§7§m                                                    ");
		KitTier highest = data.getHighestTier();
		String nameColor = highest != null ? getTierColorCode(highest.tierName) : "§f";
		sendMessage(client, "§b§l" + data.username + nameColor + " (" + (highest != null ? highest.tierName : "No Tier") + ")");
		sendMessage(client, "§7§m                                                    ");
		sendMessage(client, "§e⭐ Total Points: §f" + String.format("%.1f", data.totalPoints));
		sendMessage(client, "§a📊 Global Rank: §f#" + data.position);
		sendMessage(client, getRegionColorCode(data.region) + "🌍 Region: §f" + data.region);
		sendMessage(client, "§7§m                                                    ");
		if (!data.kits.isEmpty()) {
			sendMessage(client, "§6§lKit Tiers:");
			sendMessage(client, "");
			List<KitTier> sortedKits = new ArrayList<>(data.kits.values());
			sortedKits.sort((a, b) -> Double.compare(b.points, a.points));
			int count = 0;
			int maxDisplay = 10; 
			for (KitTier kit : sortedKits) {
				if (count >= maxDisplay) {
					int remaining = sortedKits.size() - maxDisplay;
					sendMessage(client, "  §8... and " + remaining + " more kit" + (remaining == 1 ? "" : "s"));
					break;
				}
				String tierColor = getTierColorCode(kit.tierName);
				String kitName = formatKitName(kit.kitName);
				String retired = kit.retired ? " §8[RETIRED]" : "";
				String badge = count == 0 ? "§e★ " : "  §7• ";
				sendMessage(client, badge + "§f" + kitName + ": " + tierColor + "§l" + kit.tierName +
						" §7(" + String.format("%.1f", kit.points) + " pts)" + retired);
				count++;
			}
		} else {
			sendMessage(client, "§7No kit data available");
		}
		sendMessage(client, "§7§m                                                    ");
		if (highest != null) {
			sendMessage(client, "§7Best Kit: §f" + formatKitName(highest.kitName) + " " +
					getTierColorCode(highest.tierName) + "§l" + highest.tierName);
			sendMessage(client, "§7§m                                                    ");
		}
	}
	private static void sendMessage(Minecraft client, String message) {
		if (client.player != null) {
			client.player.displayClientMessage(Component.literal(message), false);
		}
	}
	public static CompletableFuture<PlayerTierData> fetchPlayerTiers(String username) {
		String lowerUsername = username.toLowerCase();
		Long cachedTime = CACHE_TIMESTAMPS.get(lowerUsername);
		if (cachedTime != null && (System.currentTimeMillis() - cachedTime) < CACHE_DURATION) {
			PlayerTierData cached = TIER_CACHE.get(lowerUsername);
			if (cached != null) {
				return CompletableFuture.completedFuture(cached);
			}
		}
		return CompletableFuture.supplyAsync(() -> {
			try {
				HttpRequest request = HttpRequest.newBuilder()
						.uri(URI.create(API_URL + username))
						.GET()
						.build();
				HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
				if (response.statusCode() == 200) {
					JsonArray jsonArray = GSON.fromJson(response.body(), JsonArray.class);
					if (jsonArray != null && !jsonArray.isEmpty()) {
						JsonObject playerData = jsonArray.get(0).getAsJsonObject();
						PlayerTierData tierData = parsePlayerData(playerData);
						TIER_CACHE.put(lowerUsername, tierData);
						CACHE_TIMESTAMPS.put(lowerUsername, System.currentTimeMillis());
						return tierData;
					}
				}
			} catch (Exception e) {
				NovaTagger.LOGGER.error("Failed to fetch tiers for {}: {}", username, e.getMessage());
			}
			return null;
		});
	}
	private static PlayerTierData parsePlayerData(JsonObject data) {
		PlayerTierData tierData = new PlayerTierData();
		tierData.username = data.get("username").getAsString();
		tierData.totalPoints = data.get("total_points").getAsDouble();
		tierData.position = data.get("position").getAsInt();
		tierData.region = data.get("region").getAsString();
		JsonArray kits = data.getAsJsonArray("kits");
		for (int i = 0; i < kits.size(); i++) {
			JsonObject kit = kits.get(i).getAsJsonObject();
			KitTier kitTier = new KitTier();
			kitTier.kitName = kit.get("kit_name").getAsString();
			kitTier.tierName = kit.get("tier_name").getAsString();
			kitTier.points = kit.get("points").getAsDouble();
			kitTier.retired = kit.get("retired").getAsBoolean();
			tierData.kits.put(kitTier.kitName, kitTier);
		}
		return tierData;
	}
	private static String getTierColorCode(String tierName) {
		switch (tierName) {
			case "HT1": return "§6";      
			case "LT1": return "§e";      
			case "HT2": return "§f";      
			case "LT2": return "§7";      
			case "HT3": return "§c";      
			case "LT3": return "§6";      
			case "HT4": return "§5";      
			case "LT4": return "§8";      
			case "HT5": return "§d";      
			case "LT5": return "§8";      
			default: return "§7";         
		}
	}
	private static String getRegionColorCode(String region) {
		switch (region.toUpperCase()) {
			case "NA": return "§c";       
			case "EU": return "§a";       
			case "SA": return "§6";       
			case "AU": return "§e";       
			case "ME": return "§6";       
			case "AS": return "§d";       
			case "AF": return "§5";       
			default: return "§f";         
		}
	}
	private static String formatKitName(String kitName) {
		String[] parts = kitName.split("_");
		StringBuilder result = new StringBuilder();
		for (String part : parts) {
			if (result.length() > 0) result.append(" ");
			if (!part.isEmpty()) {
				result.append(part.substring(0, 1).toUpperCase()).append(part.substring(1).toLowerCase());
			}
		}
		return result.toString();
	}
	public static class PlayerTierData {
		public String username;
		public double totalPoints;
		public int position;
		public String region;
		public Map<String, KitTier> kits = new HashMap<>();
		public KitTier getHighestTier() {
			return kits.values().stream()
					.filter(k -> !k.retired)
					.max((a, b) -> Double.compare(a.points, b.points))
					.orElse(null);
		}
	}
	public static class KitTier {
		public String kitName;
		public String tierName;
		public double points;
		public boolean retired;
	}
}