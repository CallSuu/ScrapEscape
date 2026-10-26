package com.scrapescape.game;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import java.util.concurrent.*;
import java.util.random.RandomGenerator;
import static org.junit.jupiter.api.Assertions.*;
import static com.scrapescape.game.GameModels.*;

@SpringBootTest(properties="spring.datasource.url=${MYSQL_TEST_URL}")
@EnabledIfEnvironmentVariable(named="MYSQL_TEST_URL",matches=".*scrap_escape_test.*")
class GameServiceIntegrationTest {
    @Autowired GameService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired ScriptedRandom random;
    final List<UUID> created=new ArrayList<>();
    @TestConfiguration static class RandomConfig {
        @Bean @Primary ScriptedRandom testRandom() { return new ScriptedRandom(); }
    }
    static class ScriptedRandom extends Random implements RandomGenerator {
        final Queue<Integer> values=new ConcurrentLinkedQueue<>();
        void set(int... v) { values.clear();for(int i:v)values.add(i); }
        @Override public int nextInt(int bound) { Integer v=values.poll();return v==null?0:Math.floorMod(v,bound); }
    }
    @BeforeEach void resetRandom() { random.set(); }
    @AfterEach void cleanup() { for(var id:created)jdbc.update("DELETE FROM game WHERE game_id=?",id.toString()); }
    State fresh() { var s=service.create();created.add(s.gameId());return s; }
    long batch(UUID id,long material,int quantity,String condition,String storage,int hour) {
        jdbc.update("INSERT INTO item_batch(game_id,material_id,quantity,storage_type,acquired_hour,item_condition) VALUES (?,?,?,?,?,?)",
            id.toString(),material,quantity,storage,hour,condition);
        return jdbc.queryForObject("SELECT MAX(batch_id) FROM item_batch WHERE game_id=?",Long.class,id.toString());
    }
    void updateTime(UUID id,int hour) { jdbc.update("UPDATE game SET elapsed_hours=? WHERE game_id=?",hour,id.toString()); }
    long count(UUID id) { return jdbc.queryForObject("SELECT COUNT(*) FROM item_batch WHERE game_id=?",Long.class,id.toString()); }
    void fund(UUID id,Repair repair,int extra) {
        for(var i:repair.ingredients()) batch(id,i.materialId(),i.requiredQuantity()+extra,"CLEAN","CHEST",0);
    }
    @Test void createsInitialStateAndFourDifferentRepairs() {
        State s=fresh();assertEquals(10,s.stamina());assertEquals(1,s.cleanerAmount());
        assertEquals(8,s.hour());assertEquals(240,s.remainingHours());assertEquals(4,s.repairs().size());
        assertEquals(4,s.inventoryCapacity());assertEquals(16,s.chestCapacity());
        assertEquals(4,s.repairs().stream().map(Repair::partId).distinct().count());assertTrue(s.inventory().isEmpty());
    }
    @Test void missingGameAndForeignBatchCannotBeChanged() {
        var a=fresh();var b=fresh();long item=batch(b.gameId(),1,2,"DIRTY","INVENTORY",0);
        assertThrows(GameException.class,()->service.state(UUID.randomUUID()));
        assertThrows(GameException.class,()->service.discard(a.gameId(),item));assertEquals(1,count(b.gameId()));
        assertEquals(0,service.state(a.gameId()).elapsedHours());
    }
    @Test void zeroLootStillConsumesTimeAndCanFindCleaner() {
        var s=fresh();random.set(0,0,0); // 수량 0, 후보 0, 세척액 발견
        var result=service.explore(s.gameId(),1);assertEquals(0,result.loot().quantity());
        assertEquals(6,result.state().elapsedHours());assertEquals(8,result.state().stamina());
        assertEquals(2,result.state().cleanerAmount());assertEquals(1,result.state().statistics().emptyExplorations());assertEquals(0,count(s.gameId()));
    }
    @Test void nightLootUsesTwoTypesAndSevenHours() {
        var s=fresh();updateTime(s.gameId(),12);random.set(99,1,99);
        var result=service.explore(s.gameId(),1);assertEquals(4,result.loot().quantity());
        assertEquals(2,result.state().inventory().size());assertEquals(19,result.state().elapsedHours());assertEquals(7,result.state().stamina());
        assertEquals(1,result.state().statistics().nightExplorations());
    }
    @Test void fullInventoryOrInsufficientStaminaRejectsWithoutProgress() {
        var s=fresh();for(int i=0;i<4;i++)batch(s.gameId(),1,1,"DIRTY","INVENTORY",0);
        assertThrows(GameException.class,()->service.explore(s.gameId(),1));assertEquals(0,service.state(s.gameId()).elapsedHours());
        jdbc.update("DELETE FROM item_batch WHERE game_id=?",s.gameId().toString());
        jdbc.update("UPDATE game SET stamina=1 WHERE game_id=?",s.gameId().toString());
        assertThrows(GameException.class,()->service.explore(s.gameId(),1));assertEquals(0,service.state(s.gameId()).elapsedHours());
    }
    @Test void unknownLocationDoesNotConsumeAnything() {
        var s=fresh();assertThrows(GameException.class,()->service.explore(s.gameId(),999));assertEquals(10,service.state(s.gameId()).stamina());
    }
    @Test void movingPreservesQuantityAndAcquisitionAndChecksDestination() {
        var s=fresh();long b=batch(s.gameId(),1,4,"DIRTY","INVENTORY",0);
        var moved=service.move(s.gameId(),b,Storage.CHEST).state();assertEquals(4,moved.chest().get(0).quantity());
        assertEquals(0,moved.chest().get(0).acquiredHour());assertEquals(0,moved.elapsedHours());
        for(int i=0;i<4;i++)batch(s.gameId(),2,1,"DIRTY","INVENTORY",0);
        assertThrows(GameException.class,()->service.move(s.gameId(),b,Storage.INVENTORY));assertEquals(1,service.state(s.gameId()).chest().size());
    }
    @Test void cleansEntireBatchAndProtectsItDuringHourFortyEight() {
        var s=fresh();long b=batch(s.gameId(),1,4,"DIRTY","CHEST",0);updateTime(s.gameId(),47);
        var cleaned=service.clean(s.gameId(),b).state();assertEquals(Condition.CLEAN,cleaned.chest().get(0).condition());
        assertEquals(48,cleaned.elapsedHours());assertEquals(0,cleaned.cleanerAmount());assertEquals(9,cleaned.stamina());
        assertEquals(4,cleaned.statistics().cleaned());
    }
    @Test void restExpiresUnwashedItemsAndOldItemsNeedExplicitDiscard() {
        var s=fresh();long b=batch(s.gameId(),1,2,"DIRTY","CHEST",0);updateTime(s.gameId(),47);
        var rested=service.rest(s.gameId(),1).state();assertEquals(Condition.UNUSABLE,rested.chest().get(0).condition());
        assertThrows(GameException.class,()->service.clean(s.gameId(),b));assertEquals(1,count(s.gameId()));
        var discarded=service.discard(s.gameId(),b).state();assertEquals(0,count(s.gameId()));assertEquals(2,discarded.statistics().discarded());
    }
    @Test void cleanerAndRestNeverExceedCaps() {
        var s=fresh();jdbc.update("UPDATE game SET cleaner_amount=5,stamina=9 WHERE game_id=?",s.gameId().toString());
        random.set(0,0,0);var result=service.explore(s.gameId(),1);assertTrue(result.loot().cleanerFound());assertEquals(0,result.loot().cleanerAdded());
        var rested=service.rest(s.gameId(),6).state();assertEquals(10,rested.stamina());assertEquals(5,rested.cleanerAmount());
    }
    @Test void unavailableCleanerCannotConsumeTime() {
        var s=fresh();long b=batch(s.gameId(),1,2,"DIRTY","CHEST",0);
        jdbc.update("UPDATE game SET cleaner_amount=0 WHERE game_id=?",s.gameId().toString());
        assertThrows(GameException.class,()->service.clean(s.gameId(),b));assertEquals(0,service.state(s.gameId()).elapsedHours());
    }
    @Test void washedStacksMergeButDirtyStacksKeepSeparateAges() {
        var s=fresh();batch(s.gameId(),1,2,"CLEAN","CHEST",0);long b=batch(s.gameId(),1,3,"DIRTY","CHEST",0);
        var cleaned=service.clean(s.gameId(),b).state();assertEquals(1,cleaned.chest().size());assertEquals(5,cleaned.chest().get(0).quantity());
        batch(s.gameId(),1,2,"DIRTY","INVENTORY",1);batch(s.gameId(),1,3,"DIRTY","INVENTORY",2);
        assertEquals(2,service.state(s.gameId()).inventory().size());
    }
    @Test void missingIngredientsRollBackAndPartialConsumptionRetainsRows() {
        var s=fresh();var part=s.repairs().get(0);
        long b=batch(s.gameId(),part.ingredients().get(0).materialId(),6,"CLEAN","CHEST",0);
        assertThrows(GameException.class,()->service.craft(s.gameId(),part.partId()));assertEquals(6,service.state(s.gameId()).chest().get(0).quantity());
        jdbc.update("DELETE FROM item_batch WHERE batch_id=?",b);fund(s.gameId(),part,1);
        var crafted=service.craft(s.gameId(),part.partId()).state();assertEquals(3,crafted.elapsedHours());
        assertTrue(crafted.chest().stream().allMatch(i->i.quantity()==1));assertEquals(1,crafted.statistics().repairedParts());
    }
    @Test void wholeConsumptionDeletesRowsAndCompletesSuccess() {
        var s=fresh();for(var part:s.repairs()) {fund(s.gameId(),part,0);service.craft(s.gameId(),part.partId());}
        assertEquals(Status.RUNNING,service.state(s.gameId()).status());
        var ended=service.escape(s.gameId()).state();assertEquals(Status.SUCCESS,ended.status());assertEquals(0,count(s.gameId()));
        assertThrows(GameException.class,()->service.rest(s.gameId(),1));assertEquals(12,ended.elapsedHours());
    }
    @Test void deadlineOverridesLastRepairSuccess() {
        var s=fresh();var part=s.repairs().get(0);
        jdbc.update("UPDATE game_repair SET repaired=TRUE WHERE game_id=? AND part_id<>?",s.gameId().toString(),part.partId());
        fund(s.gameId(),part,0);updateTime(s.gameId(),237);
        var ended=service.craft(s.gameId(),part.partId()).state();assertEquals(Status.FAILED,ended.status());assertEquals(0,ended.remainingHours());
    }
    @Test void invalidRestHoursAreRejectedWithoutProgress() {
        var s=fresh();assertThrows(GameException.class,()->service.rest(s.gameId(),0));assertThrows(GameException.class,()->service.rest(s.gameId(),9));
        assertEquals(0,service.state(s.gameId()).elapsedHours());
    }
    @Test void twoConcurrentRepairsConsumeOnlyOnce() throws Exception {
        var s=fresh();var part=s.repairs().get(0);fund(s.gameId(),part,0);
        ExecutorService executor=Executors.newFixedThreadPool(2);
        try {
            var call=(Callable<Boolean>)()->{try {service.craft(s.gameId(),part.partId());return true;}catch(GameException e){return false;}};
            var results=executor.invokeAll(List.of(call,call));int success=0;for(var r:results)if(r.get())success++;
            assertEquals(1,success);assertEquals(3,service.state(s.gameId()).elapsedHours());assertEquals(0,count(s.gameId()));
        } finally {executor.shutdownNow();}
    }
    @Test void databaseFailureRollsBackEntireRepairTransaction() {
        var s=fresh();var part=s.repairs().get(0);fund(s.gameId(),part,0);
        String trigger="test_fail_repair";
        jdbc.execute("CREATE TRIGGER "+trigger+" BEFORE UPDATE ON game_repair FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='test rollback'");
        try {assertThrows(RuntimeException.class,()->service.craft(s.gameId(),part.partId()));
            assertEquals(part.ingredients().size(),count(s.gameId()));assertEquals(0,service.state(s.gameId()).elapsedHours());
            assertEquals(0,service.state(s.gameId()).statistics().repairedParts());
        } finally {jdbc.execute("DROP TRIGGER "+trigger);}
    }
    @Test void craftUsesBothStoragesAndRejectsUnwashedIngredients() {
        var s=fresh();var part=s.repairs().get(0);int index=0;
        for(var ingredient:part.ingredients())batch(s.gameId(),ingredient.materialId(),ingredient.requiredQuantity(),"DIRTY",index++==0?"INVENTORY":"CHEST",0);
        assertThrows(GameException.class,()->service.craft(s.gameId(),part.partId()));
        assertEquals(0,service.state(s.gameId()).elapsedHours());
        jdbc.update("UPDATE item_batch SET item_condition='CLEAN' WHERE game_id=?",s.gameId().toString());
        assertEquals(1,service.craft(s.gameId(),part.partId()).state().statistics().repairedParts());assertEquals(0,count(s.gameId()));
    }
    @Test void fullChestRejectsDirtyMoveButAllowsCleanMerge() {
        var s=fresh();batch(s.gameId(),1,1,"CLEAN","CHEST",0);
        for(int i=0;i<15;i++)batch(s.gameId(),2,1,"DIRTY","CHEST",0);
        long dirty=batch(s.gameId(),1,1,"DIRTY","INVENTORY",0);
        assertThrows(GameException.class,()->service.move(s.gameId(),dirty,Storage.CHEST));
        long clean=batch(s.gameId(),1,2,"CLEAN","INVENTORY",0);
        var moved=service.move(s.gameId(),clean,Storage.CHEST).state();assertEquals(16,moved.chest().size());
        assertEquals(3,moved.chest().stream().filter(b->b.materialId()==1).findFirst().orElseThrow().quantity());
    }
    @Test void foreignAndEndedActionsLeaveCompleteStateUnchanged() {
        var s=fresh();var other=fresh();long foreign=batch(other.gameId(),1,3,"DIRTY","INVENTORY",0);
        var before=service.state(other.gameId());
        assertThrows(GameException.class,()->service.clean(s.gameId(),foreign));
        assertThrows(GameException.class,()->service.move(s.gameId(),foreign,Storage.CHEST));
        assertEquals(before,service.state(other.gameId()));
        long b=batch(s.gameId(),1,2,"DIRTY","INVENTORY",0);
        jdbc.update("UPDATE game SET status='FAILED' WHERE game_id=?",s.gameId().toString());var ended=service.state(s.gameId());
        assertThrows(GameException.class,()->service.explore(s.gameId(),1));
        assertThrows(GameException.class,()->service.move(s.gameId(),b,Storage.CHEST));
        assertThrows(GameException.class,()->service.clean(s.gameId(),b));
        assertThrows(GameException.class,()->service.discard(s.gameId(),b));
        assertThrows(GameException.class,()->service.craft(s.gameId(),ended.repairs().get(0).partId()));
        assertThrows(GameException.class,()->service.rest(s.gameId(),1));assertEquals(ended,service.state(s.gameId()));
    }
    @Test void concurrentExplorationsCannotExceedInventoryCapacity() throws Exception {
        var s=fresh();batch(s.gameId(),1,1,"DIRTY","INVENTORY",0);batch(s.gameId(),2,1,"DIRTY","INVENTORY",0);batch(s.gameId(),3,1,"DIRTY","INVENTORY",0);
        random.set(99,0,99,99,0,99);
        ExecutorService executor=Executors.newFixedThreadPool(2);
        try {
            var call=(Callable<Boolean>)()->{try {service.explore(s.gameId(),1);return true;}catch(GameException e){return false;}};
            var results=executor.invokeAll(List.of(call,call));int success=0;for(var r:results)if(r.get())success++;
            assertEquals(1,success);assertEquals(4,service.state(s.gameId()).inventory().size());assertEquals(6,service.state(s.gameId()).elapsedHours());
        } finally {executor.shutdownNow();}
    }
    @Test void expandedCatalogContainsSixLocationsAndTenMaterials() {
        var s=fresh();assertEquals(6,service.locations(s.gameId()).size());
        assertEquals(10,jdbc.queryForObject("SELECT COUNT(*) FROM material",Integer.class));
        assertEquals(7,jdbc.queryForObject("SELECT COUNT(*) FROM part",Integer.class));
        assertTrue(service.locations(s.gameId()).stream().allMatch(c->c.materials().size()>=2));
    }
    @Test void cleanBatchesPackIntoSixWithoutLosingOverflow() {
        var s=fresh();batch(s.gameId(),1,4,"CLEAN","CHEST",0);
        long b=batch(s.gameId(),1,4,"DIRTY","CHEST",0);
        var state=service.clean(s.gameId(),b).state();
        assertEquals(List.of(6,2),state.chest().stream().map(Item::quantity).toList());
        assertEquals(8,state.chest().stream().mapToInt(Item::quantity).sum());
    }
    @Test void fullDestinationWithOnlyPartialCleanHeadroomRejectsWholeMove() {
        var s=fresh();batch(s.gameId(),1,5,"CLEAN","CHEST",0);
        for(int i=0;i<15;i++)batch(s.gameId(),2,1,"DIRTY","CHEST",0);
        long b=batch(s.gameId(),1,2,"CLEAN","INVENTORY",0);
        var before=service.state(s.gameId());
        assertThrows(GameException.class,()->service.move(s.gameId(),b,Storage.CHEST));
        assertEquals(before,service.state(s.gameId()));
    }
    @Test void databaseRejectsBatchesAboveSix() {
        var s=fresh();var error=assertThrows(org.springframework.dao.DataAccessException.class,
            ()->batch(s.gameId(),1,7,"DIRTY","INVENTORY",0));
        assertEquals(3819,((java.sql.SQLException)error.getRootCause()).getErrorCode());
    }

    @Test void craftingAggregatesOneIngredientAcrossMultipleStacks() {
        var s=fresh();var repair=s.repairs().get(0);boolean split=false;
        for(var ingredient:repair.ingredients()) {
            if(!split&&ingredient.requiredQuantity()>=2) {
                batch(s.gameId(),ingredient.materialId(),1,"CLEAN","INVENTORY",0);
                batch(s.gameId(),ingredient.materialId(),ingredient.requiredQuantity()-1,"CLEAN","CHEST",0);split=true;
            } else batch(s.gameId(),ingredient.materialId(),ingredient.requiredQuantity(),"CLEAN","CHEST",0);
        }
        assertTrue(split);var state=service.craft(s.gameId(),repair.partId()).state();
        assertEquals(0,count(s.gameId()));assertEquals(1,state.statistics().repairedParts());
    }
    @Test void concurrentTreatmentsConsumeSingleKitOnlyOnce() throws Exception {
        var s=fresh();jdbc.update("UPDATE game SET medical_kit_amount=1,contamination=100,diseased=TRUE,disease_started_hour=0 WHERE game_id=?",s.gameId().toString());
        ExecutorService executor=Executors.newFixedThreadPool(2);
        try {
            var call=(Callable<Boolean>)()->{try {service.treat(s.gameId());return true;}catch(GameException e){return false;}};
            var results=executor.invokeAll(List.of(call,call));int success=0;for(var r:results)if(r.get())success++;
            var state=service.state(s.gameId());assertEquals(1,success);assertEquals(0,state.medicalKitAmount());
            assertEquals(40,state.contamination());assertEquals(1,state.elapsedHours());assertEquals(1,state.statistics().medicalKitsUsed());
        } finally {executor.shutdownNow();}
    }

}
