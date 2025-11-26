package com.example;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.api.ClientModInitializer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class NovaTaggerClient implements ClientModInitializer {

	private static final String API_URL = "https://managing-jaquith-lmaozeekid-5bf1cd1b.koyeb.app/api/v1/overall?search="; // Added semicolon here
	private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
	private static final Gson GSON = new Gson();

	public static final Map<String, PlayerTierData> TIER_CACHE = new ConcurrentHashMap<>();
	private static final Map<String, Long> CACHE_TIMESTAMPS = new ConcurrentHashMap<>();
	private static final long CACHE_DURATION = 300000; // 5 minutes

	@Override
	public void onInitializeClient() {
		NovaTagger.LOGGER.info("NovaTagger Client initialized!");
	}

	public static CompletableFuture<PlayerTierData> fetchPlayerTiers(String username) {
		// Hardcoded test data for player754
		if (username.equalsIgnoreCase("player754")) {
			PlayerTierData testData = new PlayerTierData();
			testData.username = "player754";
			testData.totalPoints = 100.0;
			testData.position = 1;
			testData.region = "TEST";

			KitTier testKit = new KitTier();
			testKit.kitName = "diamond_op";
			testKit.tierName = "LT1";
			testKit.points = 100.0;
			testData.kits.put("diamond_op", testKit);

			TIER_CACHE.put(username.toLowerCase(), testData);
			return CompletableFuture.completedFuture(testData);
		}

		// Check cache
		Long cachedTime = CACHE_TIMESTAMPS.get(username.toLowerCase());
		if (cachedTime != null && (System.currentTimeMillis() - cachedTime) < CACHE_DURATION) {
			PlayerTierData cached = TIER_CACHE.get(username.toLowerCase());
			if (cached != null) {
				return CompletableFuture.completedFuture(cached);
			}
		}

		// Fetch from API
		return CompletableFuture.supplyAsync(() -> {
			try {
				HttpRequest request = HttpRequest.newBuilder()
						.uri(URI.create(API_URL + username))
						.GET()
						.build();

				HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

				if (response.statusCode() == 200) {
					JsonArray jsonArray = GSON.fromJson(response.body(), JsonArray.class);
					if (jsonArray != null && jsonArray.size() > 0) {
						JsonObject playerData = jsonArray.get(0).getAsJsonObject();
						PlayerTierData tierData = parsePlayerData(playerData);

						TIER_CACHE.put(username.toLowerCase(), tierData);
						CACHE_TIMESTAMPS.put(username.toLowerCase(), System.currentTimeMillis());

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