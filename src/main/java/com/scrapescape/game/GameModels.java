package com.scrapescape.game;

import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;

public final class GameModels {
    private GameModels() {}
    public enum Storage { INVENTORY, CHEST }
    public enum Condition { DIRTY, CLEAN, UNUSABLE }
    public enum Status { RUNNING, SUCCESS, FAILED }
    public enum RoomMode { IMMEDIATE, REVISIT }
    public enum RoomOutcome { MATERIALS, CLEANER, MEDICAL_KIT, MATERIAL_LOSS, CONTAMINATION }
    public record OpenRoomRequest(@NotNull RoomMode mode) {}
    public record SecretFindings(boolean keyFound,int keysAdded,boolean roomDiscovered,Long roomLocationId) {}
    public record SecretStatistics(int discoveredRooms,int openedRooms,int unopenedRooms,int keysAcquired,int keysUsed,
                                   int goodOutcomes,int badOutcomes,int materialsCollected,int materialsLost) {}
    public record SecretState(int keyAmount,int keyCapacity,int keyProbabilityPercent,int discoveryProbabilityPercent,
                              List<SecretRoomCard> rooms,SecretStatistics statistics) {}
    public record RoomVisitPreview(boolean allowed,String unavailableReason,int durationHours,int staminaCost,int keyCost,
                                   int baseContamination,int stormExposureHours,int stormContamination,int expectedContaminationIncrease,
                                   int possibleExtraContamination,int rewardFreeSlots,boolean deadlineRisk) {}
    public record SecretRoomCard(long locationId,String locationName,String buttonLabel,int discoveredHour,boolean opened,
                                 boolean immediateAvailable,RoomVisitPreview immediate,RoomVisitPreview revisit,
                                 Integer openedHour,SecretRoomOutcome lastVisit) {}
    public record SecretRoomOutcome(long locationId,RoomMode mode,RoomOutcome outcome,int durationHours,int staminaCost,
                                    int contaminationIncrease,int stormExposureHours,Loot loot,int lostQuantity) {}
    public record ExploreRequest(@NotNull @Positive Long locationId) {}
    public record MoveRequest(@NotNull Storage storage) {}
    public record MoveManyRequest(@NotNull @Size(min=1,max=20) List<@NotNull @Positive Long> batchIds,@NotNull Storage storage) {}
    public record BatchItemsRequest(@NotNull @Size(min=1,max=20) List<@NotNull @Positive Long> batchIds) {}
    public record RestRequest(@NotNull @Min(1) @Max(8) Integer hours) {}
    public record Item(long batchId,long materialId,String name,int quantity,Storage storage,int acquiredHour,
                       Condition condition,Integer expiresInHours) {}
    public record Ingredient(long materialId,String name,int requiredQuantity,int availableQuantity) {}
    public record Repair(long partId,String name,boolean repaired,boolean canCraft,List<Ingredient> ingredients) {}
    public record Statistics(int explorations,int dayExplorations,int nightExplorations,int emptyExplorations,
                             int collected,int cleaned,int discarded,int repairedParts,int diseaseCount,
                             int treatmentCount,int medicalKitsUsed,int maxContamination,int stormExposureHours) {}
    public record Storm(int startHour,int endHour) {}
    public record ActionAvailability(boolean allowed,String unavailableReason,int durationHours,int staminaCost) {}
    public record State(UUID gameId,Status status,int elapsedHours,int day,int hour,int remainingHours,
                        boolean night,int stamina,int maxStamina,int cleanerAmount,int cleanerCapacity,
                        int inventoryCapacity,int chestCapacity,List<Item> inventory,List<Item> chest,
                        List<Repair> repairs,Statistics statistics,int stackLimit,int medicalKitAmount,int medicalKitCapacity,
                        int contamination,boolean diseased,Integer treatmentRemainingHours,int consecutiveExplorations,
                        Storm currentStorm,Storm nextStorm,ActionAvailability treatment,ActionAvailability escape,
                        String failureReason,int rulesVersion,SecretState secrets) {}
    public record ActionResult(String message,State state,Loot loot,SecretFindings secretFindings,SecretRoomOutcome secretRoom) {}
    public record MaterialLoot(long materialId,String name,int quantity,int collectedQuantity) {}
    public record Loot(int quantity,int collectedQuantity,int leftBehindQuantity,List<MaterialLoot> materials,
                       boolean cleanerFound,int cleanerAdded,int medicalKitsAdded) {}
    public record Candidate(long materialId,String name,int weight) {}
    public record RewardProbability(int quantity,int probabilityPercent) {}
    public record LocationCard(long locationId,String name,String description,List<Candidate> materials,
                               int durationHours,int staminaCost,List<RewardProbability> rewards,
                               int cleanerProbabilityPercent,boolean canExplore,String unavailableReason,
                               int medicalKitReward,int baseContamination,int consecutiveContamination,
                               int stormExposureHours,int stormContamination,int expectedContaminationIncrease,
                               int diseaseProbabilityAfterExploration,SecretRoomCard secretRoom) {
        LocationCard withSecretRoom(SecretRoomCard room) {
            return new LocationCard(locationId,name,description,materials,durationHours,staminaCost,rewards,cleanerProbabilityPercent,
                canExplore,unavailableReason,medicalKitReward,baseContamination,consecutiveContamination,stormExposureHours,
                stormContamination,expectedContaminationIncrease,diseaseProbabilityAfterExploration,room);
        }
    }
    public record ActionProjection(int elapsedHours,int stamina,int contamination,int cleanerAmount,int medicalKitAmount,
                                   boolean diseased,Status status,String failureReason,List<Long> expiringBatchIds) {}
    public record SafeActionPreview(boolean allowed,String unavailableCode,String unavailableReason,
                                    int durationHours,int staminaCost,ActionProjection projected) {}
    public record RestPreview(int hours,SafeActionPreview preview) {}
    public record ItemActionPreview(long batchId,Storage moveTo,SafeActionPreview move,SafeActionPreview clean,SafeActionPreview discard) {}
    public record RepairActionPreview(long partId,SafeActionPreview preview) {}
    public record SafeActions(List<RestPreview> rests,List<ItemActionPreview> items,List<RepairActionPreview> repairs,
                              SafeActionPreview treatment,SafeActionPreview escape) {}
    public record GameScreen(State state,List<LocationCard> locations,SafeActions actions) {}
    record Game(UUID id,int elapsed,int stamina,int cleaner,Status status,int explorations,int dayExplorations,
                int nightExplorations,int emptyExplorations,int collected,int cleaned,int discarded,int rulesVersion,
                int medicalKits,int contamination,boolean diseased,Integer diseaseStarted,int consecutive,
                int diseaseCount,int treatmentCount,int medicalKitsUsed,int maxContamination,int stormExposure,
                String failureReason) {}
}
