package com.scrapescape.game;

import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;

public final class GameModels {
    private GameModels() {}
    public enum Storage { INVENTORY, CHEST }
    public enum Condition { DIRTY, CLEAN, UNUSABLE }
    public enum Status { RUNNING, SUCCESS, FAILED }
    public record ExploreRequest(@NotNull @Positive Long locationId) {}
    public record MoveRequest(@NotNull Storage storage) {}
    public record RestRequest(@NotNull @Min(1) @Max(8) Integer hours) {}
    public record Item(long batchId, long materialId, String name, int quantity,
                       Storage storage, int acquiredHour, Condition condition, Integer expiresInHours) {}
    public record Ingredient(long materialId, String name, int requiredQuantity, int availableQuantity) {}
    public record Repair(long partId, String name, boolean repaired, boolean canCraft, List<Ingredient> ingredients) {}
    public record Statistics(int explorations, int dayExplorations, int nightExplorations, int emptyExplorations,
                             int collected, int cleaned, int discarded, int repairedParts) {}
    public record State(UUID gameId, Status status, int elapsedHours, int day, int hour, int remainingHours,
                        boolean night, int stamina, int maxStamina, int cleanerAmount, int cleanerCapacity,
                        int inventoryCapacity, int chestCapacity, List<Item> inventory, List<Item> chest,
                        List<Repair> repairs, Statistics statistics) {}
    public record ActionResult(String message, State state, Loot loot) {}
    public record Loot(Long materialId, String materialName, int quantity, boolean cleanerFound, int cleanerAdded) {}
    public record Candidate(long materialId, String name, int probabilityPercent) {}
    public record RewardProbability(int quantity, int probabilityPercent) {}
    public record LocationCard(long locationId, String name, String description, List<Candidate> materials,
                               int durationHours, int staminaCost, List<RewardProbability> rewards,
                               int cleanerProbabilityPercent, boolean canExplore, String unavailableReason) {}
    record Game(UUID id, int elapsed, int stamina, int cleaner, Status status,
                int explorations, int dayExplorations, int nightExplorations,
                int emptyExplorations, int collected, int cleaned, int discarded) {}
}
