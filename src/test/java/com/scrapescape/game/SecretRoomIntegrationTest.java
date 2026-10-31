package com.scrapescape.game;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.concurrent.*;
import static com.scrapescape.game.GameModels.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.datasource.url=${MYSQL_TEST_URL}")
@AutoConfigureMockMvc
@Import(GameServiceIntegrationTest.RandomConfig.class)
@EnabledIfEnvironmentVariable(named="MYSQL_TEST_URL",matches=".*scrap_escape_test.*")
class SecretRoomIntegrationTest {
    @Autowired GameService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired GameServiceIntegrationTest.ScriptedRandom random;
    @Autowired ObjectMapper mapper;
    @Autowired MockMvc mvc;
    final List<UUID> created=new ArrayList<>();
    UUID fresh(){random.set();var id=service.create().gameId();created.add(id);return id;}
    JsonNode state(UUID id){return mapper.valueToTree(service.state(id));}
    String path(UUID id){return "/api/games/"+id;}
    @AfterEach void cleanup(){for(var id:created)jdbc.update("DELETE FROM game WHERE game_id=?",id.toString());}
    @Test void initialSecretStateAndOnlyDiscoveredCardsExposeQuestionMark() throws Exception {
        var id=fresh();mvc.perform(get(path(id))).andExpect(status().isOk())
            .andExpect(jsonPath("$.secrets.keyAmount").value(0)).andExpect(jsonPath("$.secrets.rooms").isEmpty());
        mvc.perform(get(path(id)+"/secret-rooms")).andExpect(status().isOk()).andExpect(content().json("[]"));
    }
    @Test void emptyExplorationCanFindKeyAndRememberRoomWithoutAutoOpening() throws Exception {
        var id=fresh();random.set(0,0,99,99,0,0);service.explore(id,1);
        mvc.perform(get(path(id))).andExpect(status().isOk()).andExpect(jsonPath("$.secrets.keyAmount").value(1))
            .andExpect(jsonPath("$.secrets.rooms[0].buttonLabel").value("?"))
            .andExpect(jsonPath("$.secrets.rooms[0].opened").value(false));
        mvc.perform(get(path(id)+"/secret-rooms/1")).andExpect(status().isOk())
            .andExpect(jsonPath("$.immediate.durationHours").value(1));
    }
    @Test void discoveredRoomRemainsReadableWithoutKeyAndDeferKeepsMemory() throws Exception {
        var id=fresh();random.set(0,0,99,99,99,0);service.explore(id,1);
        mvc.perform(get(path(id)+"/secret-rooms/1")).andExpect(status().isOk()).andExpect(jsonPath("$.immediate.allowed").value(false));
        mvc.perform(post(path(id)+"/secret-rooms/1/open").contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"IMMEDIATE\"}"))
            .andExpect(status().isConflict());
        mvc.perform(post(path(id)+"/secret-rooms/1/defer")).andExpect(status().isOk());
        assertEquals(6,service.state(id).elapsedHours());
        mvc.perform(get(path(id)+"/secret-rooms/1")).andExpect(status().isOk()).andExpect(jsonPath("$.immediateAvailable").value(false));
    }
    void room(UUID id,long location,boolean immediate,int keys){
        jdbc.update("INSERT INTO game_secret_room(game_id,location_id,discovered_hour,immediate_available) VALUES (?,?,0,?)",id.toString(),location,immediate);
        jdbc.update("UPDATE game SET secret_key_amount=?,secret_keys_acquired=? WHERE game_id=?",keys,keys,id.toString());
    }
    long batch(UUID id,int quantity,String condition,String storage) {
        jdbc.update("INSERT INTO item_batch(game_id,material_id,quantity,storage_type,acquired_hour,item_condition) VALUES (?,1,?,?,0,?)",id.toString(),quantity,storage,condition);
        return jdbc.queryForObject("SELECT MAX(batch_id) FROM item_batch WHERE game_id=?",Long.class,id.toString());
    }
    @ParameterizedTest @CsvSource({"9,14,1,true","10,14,0,true","9,15,1,false","10,15,0,false"})
    void discoveryAndKeyThresholdsAreIndependent(int keyRoll,int roomRoll,int keys,boolean discovered) {
        var id=fresh();random.set(0,0,99,99,keyRoll,roomRoll);var result=service.explore(id,1);
        assertEquals(keys,result.state().secrets().keyAmount());assertEquals(discovered,result.secretFindings().roomDiscovered());
        assertEquals(discovered?1:0,result.state().secrets().rooms().size());
        assertEquals(discovered,service.locations(id).get(0).secretRoom()!=null);
    }
    @Test void keyCapCountsOnlyStoredKeysAndRoomsDoNotDuplicate() {
        var id=fresh();
        for(int i=0;i<3;i++){random.set(0,0,99,99,0,0);service.explore(id,1);}
        var secret=service.state(id).secrets();assertEquals(2,secret.keyAmount());assertEquals(2,secret.statistics().keysAcquired());
        assertEquals(1,secret.rooms().size());assertFalse(secret.rooms().get(0).immediateAvailable());
    }
    @Test void immediateRewardConsumesExactlyOneKeyAndCannotBeRepeated() {
        var id=fresh();room(id,1,true,2);random.set(0,0,1,99);
        var result=service.openRoom(id,1,RoomMode.IMMEDIATE);
        assertEquals(1,result.state().elapsedHours());assertEquals(14,result.state().stamina());
        assertEquals(4,result.loot().collectedQuantity());assertEquals(1,result.state().secrets().keyAmount());
        assertEquals(4,result.state().statistics().collected());assertEquals(0,result.state().statistics().explorations());
        assertEquals(result.secretRoom(),result.state().secrets().rooms().get(0).lastVisit());
        var before=service.state(id);assertThrows(GameException.class,()->service.openRoom(id,1,RoomMode.IMMEDIATE));assertEquals(before,service.state(id));
    }
    @ParameterizedTest @CsvSource({"49,MATERIALS","50,CLEANER","79,CLEANER","80,MATERIAL_LOSS","89,MATERIAL_LOSS","90,CONTAMINATION","99,CONTAMINATION"})
    void everyOutcomeProbabilityBoundaryMatchesApprovedWeights(int roll,RoomOutcome outcome) {
        var id=fresh();room(id,1,true,1);batch(id,3,"DIRTY","INVENTORY");random.set(roll,0,0,99);
        assertEquals(outcome,service.openRoom(id,1,RoomMode.IMMEDIATE).secretRoom().outcome());
    }
    @Test void suppliesRespectCleanerAndMedicalCaps() {
        var id=fresh();room(id,1,true,2);room(id,2,false,2);
        jdbc.update("UPDATE game SET cleaner_amount=9,medical_kit_amount=2 WHERE game_id=?",id.toString());
        random.set(50,0,99);var cleaner=service.openRoom(id,1,RoomMode.IMMEDIATE);
        assertEquals(1,cleaner.loot().cleanerAdded());assertEquals(10,cleaner.state().cleanerAmount());
        random.set(50,1,99);var medical=service.openRoom(id,2,RoomMode.REVISIT);
        assertEquals(RoomOutcome.MEDICAL_KIT,medical.secretRoom().outcome());assertEquals(0,medical.loot().medicalKitsAdded());
        assertEquals(2,medical.state().medicalKitAmount());assertEquals(2,medical.state().secrets().statistics().goodOutcomes());
    }
    @Test void fullInventoryAllowsOpeningButReportsUnstoredMaterialReward() {
        var id=fresh();room(id,1,true,1);for(int i=0;i<4;i++)batch(id,1,"DIRTY","INVENTORY");
        random.set(0,0,1,99);var result=service.openRoom(id,1,RoomMode.IMMEDIATE);
        assertEquals(4,result.loot().quantity());assertEquals(0,result.loot().collectedQuantity());assertEquals(4,result.loot().leftBehindQuantity());
        assertEquals(4,result.state().inventory().size());assertEquals(0,result.state().statistics().collected());
    }
    @Test void lossesSpanDirtyInventoryOnlyAndDoNotIncreaseDiscardStatistics() {
        var id=fresh();room(id,1,true,1);batch(id,1,"DIRTY","INVENTORY");batch(id,1,"DIRTY","INVENTORY");
        long clean=batch(id,5,"CLEAN","INVENTORY"),unusable=batch(id,1,"UNUSABLE","INVENTORY"),chest=batch(id,4,"DIRTY","CHEST");
        random.set(80,1,0,99);var result=service.openRoom(id,1,RoomMode.IMMEDIATE);
        assertEquals(2,result.secretRoom().lostQuantity());assertEquals(2,result.state().inventory().size());
        assertTrue(result.state().inventory().stream().anyMatch(b->b.batchId()==clean&&b.quantity()==5));
        assertTrue(result.state().inventory().stream().anyMatch(b->b.batchId()==unusable));
        assertEquals(chest,result.state().chest().get(0).batchId());assertEquals(0,result.state().statistics().discarded());
        assertEquals(2,result.state().secrets().statistics().materialsLost());
    }
    @Test void noEligibleLossBecomesContaminationAndNeverTouchesChest() {
        var id=fresh();room(id,1,true,1);batch(id,4,"DIRTY","CHEST");random.set(80,99);
        var result=service.openRoom(id,1,RoomMode.IMMEDIATE);assertEquals(RoomOutcome.CONTAMINATION,result.secretRoom().outcome());
        assertEquals(15,result.state().contamination());assertEquals(0,result.secretRoom().lostQuantity());assertEquals(4,result.state().chest().get(0).quantity());
    }
    @Test void bonusMaterialHasNewAgeInsteadOfExtendingOldBatch() {
        var id=fresh();room(id,1,true,1);batch(id,3,"DIRTY","INVENTORY");random.set(0,0,0,99);
        var result=service.openRoom(id,1,RoomMode.IMMEDIATE);assertEquals(2,result.state().inventory().size());
        assertEquals(List.of(0,1),result.state().inventory().stream().map(Item::acquiredHour).toList());
        assertEquals(List.of(47,48),result.state().inventory().stream().map(Item::expiresInHours).toList());
    }
    @ParameterizedTest @CsvSource({"0,false,4,2","12,false,5,3","0,true,6,4","12,true,7,5"})
    void revisitCostsIncludeTimeOfDayAndDisease(int start,boolean diseased,int hours,int cost) {
        var id=fresh();room(id,6,false,1);jdbc.update("UPDATE game SET elapsed_hours=?,diseased=?,disease_started_hour=0 WHERE game_id=?",start,diseased,id.toString());
        random.set(90,99);var card=service.secretRoom(id,6);assertEquals(hours,card.revisit().durationHours());assertEquals(cost,card.revisit().staminaCost());
        var result=service.openRoom(id,6,RoomMode.REVISIT);assertEquals(start+hours,result.state().elapsedHours());assertEquals(15-cost,result.state().stamina());
        assertEquals(1,result.state().consecutiveExplorations());assertEquals(0,result.state().statistics().explorations());
        assertEquals(0,result.state().medicalKitAmount());assertEquals(0,result.state().secrets().keyAmount());
    }
    @Test void immediateStormAndBadOutcomeStackWhileRevisitUsesConsecutiveBase() {
        var id=fresh();room(id,1,true,2);room(id,2,false,2);jdbc.update("DELETE FROM radiation_storm WHERE game_id=?",id.toString());
        jdbc.update("INSERT INTO radiation_storm(game_id,start_hour,end_hour) VALUES (?,0,2)",id.toString());
        random.set(90,99);var immediate=service.openRoom(id,1,RoomMode.IMMEDIATE);
        assertEquals(20,immediate.state().contamination());assertEquals(1,immediate.secretRoom().stormExposureHours());
        jdbc.update("UPDATE game SET consecutive_explorations=2 WHERE game_id=?",id.toString());
        random.set(50,0,99);var revisit=service.openRoom(id,2,RoomMode.REVISIT);
        assertEquals(20,revisit.secretRoom().contaminationIncrease());assertEquals(40,revisit.state().contamination());
        assertEquals(3,revisit.state().consecutiveExplorations());
    }
    @Test void roomExposureCanCauseDiseaseAndStartsItsDeadlineAtCompletion() {
        var id=fresh();room(id,1,true,1);jdbc.update("UPDATE game SET contamination=55 WHERE game_id=?",id.toString());
        jdbc.update("INSERT INTO radiation_storm(game_id,start_hour,end_hour) VALUES (?,0,1)",id.toString());
        random.set(50,0,29);var result=service.openRoom(id,1,RoomMode.IMMEDIATE);
        assertTrue(result.state().diseased());assertEquals(60,result.state().contamination());assertEquals(72,result.state().treatmentRemainingHours());
    }
    @Test void deadlineFailureOverridesOpeningButIsRecordedOnlyOnce() {
        var id=fresh();room(id,1,true,1);jdbc.update("UPDATE game SET elapsed_hours=239 WHERE game_id=?",id.toString());random.set(90,99);
        var result=service.openRoom(id,1,RoomMode.IMMEDIATE);assertEquals(Status.FAILED,result.state().status());assertEquals("TIME_LIMIT",result.state().failureReason());
        assertEquals(1,result.state().secrets().statistics().openedRooms());assertEquals(1,result.state().secrets().statistics().keysUsed());
        assertThrows(GameException.class,()->service.openRoom(id,1,RoomMode.REVISIT));
    }
    @Test void untreatedDiseaseDeadlineAlsoAppliesToImmediateVisit() {
        var id=fresh();room(id,1,true,1);jdbc.update("UPDATE game SET elapsed_hours=71,diseased=TRUE,disease_started_hour=0 WHERE game_id=?",id.toString());random.set(50,0,99);
        assertTrue(service.secretRoom(id,1).immediate().deadlineRisk());
        assertEquals("UNTREATED_DISEASE",service.openRoom(id,1,RoomMode.IMMEDIATE).state().failureReason());
    }
    @Test void validInterveningActionExpiresImmediateButRejectedActionDoesNot() {
        var id=fresh();room(id,1,true,1);var before=service.state(id);
        assertThrows(GameException.class,()->service.move(id,999999,Storage.CHEST));assertEquals(before,service.state(id));
        service.rest(id,1);assertFalse(service.secretRoom(id,1).immediateAvailable());
        var error=assertThrows(GameException.class,()->service.openRoom(id,1,RoomMode.IMMEDIATE));
        assertEquals("IMMEDIATE_UNAVAILABLE",error.code);
    }
    @Test void endedExplorationDoesNotAwardNewKeysOrDiscoverRooms() {
        var id=fresh();jdbc.update("UPDATE game SET elapsed_hours=239 WHERE game_id=?",id.toString());random.set(0,0,99,99,0,0);
        var result=service.explore(id,1);assertEquals(Status.FAILED,result.state().status());
        assertFalse(result.secretFindings().keyFound());assertTrue(result.state().secrets().rooms().isEmpty());
    }
    @Test void invalidRequestsAndForeignGameCannotOpenOrRevealRoom() throws Exception {
        var id=fresh();room(id,1,true,1);var other=fresh();
        mvc.perform(get(path(other)+"/secret-rooms/1")).andExpect(status().isNotFound());
        for(String body:new String[]{"{}","{\"mode\":\"WRONG\"}"})mvc.perform(post(path(id)+"/secret-rooms/1/open").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        assertEquals(1,service.state(id).secrets().keyAmount());assertEquals(0,service.state(id).elapsedHours());
    }
    @Test void allSixRememberedLocationsExposeOnlyQuestionMarkButtons() {
        var id=fresh();for(int i=1;i<=6;i++)room(id,i,false,2);
        assertEquals(6,service.secretRooms(id).size());assertTrue(service.locations(id).stream().allMatch(c->c.secretRoom()!=null&&c.secretRoom().buttonLabel().equals("?")));
        assertEquals(6,service.state(id).secrets().statistics().unopenedRooms());
    }
    @Test void concurrentOpeningOfOneRoomConsumesKeyAndRewardOnlyOnce() throws Exception {
        var id=fresh();room(id,1,true,2);random.set(50,0,99);var executor=Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> call=()->{try{service.openRoom(id,1,RoomMode.IMMEDIATE);return true;}catch(GameException e){return false;}};
            int success=0;for(var f:executor.invokeAll(List.of(call,call)))if(f.get())success++;
            assertEquals(1,success);var state=service.state(id);assertEquals(1,state.elapsedHours());assertEquals(1,state.secrets().keyAmount());assertEquals(1,state.secrets().statistics().openedRooms());
        } finally {executor.shutdownNow();}
    }
    @Test void lateDatabaseFailureRollsBackKeyRewardTimeAndOpenedStatus() {
        var id=fresh();room(id,1,true,1);var before=service.state(id);random.set(0,0,1,99);
        jdbc.execute("CREATE TRIGGER test_secret_rollback BEFORE UPDATE ON game_secret_room FOR EACH ROW BEGIN IF NEW.opened_hour IS NOT NULL THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='secret rollback'; END IF; END");
        try {assertThrows(RuntimeException.class,()->service.openRoom(id,1,RoomMode.IMMEDIATE));assertEquals(before,service.state(id));}
        finally {jdbc.execute("DROP TRIGGER test_secret_rollback");}
    }

    @Test void discoveryFailureRollsBackExplorationLootKeyAndAllResourceChanges() {
        var id=fresh();var before=service.state(id);random.set(99,0,0,0,0,99,0,0);
        jdbc.execute("CREATE TRIGGER test_discovery_rollback BEFORE INSERT ON game_secret_room FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='discovery rollback'");
        try {assertThrows(RuntimeException.class,()->service.explore(id,1));assertEquals(before,service.state(id));}
        finally {jdbc.execute("DROP TRIGGER test_discovery_rollback");}
    }

    @Test void deferringOlderRoomAlsoEndsOtherImmediateOpportunity() {
        var id=fresh();room(id,1,false,1);room(id,2,true,1);
        service.deferRoom(id,1);assertFalse(service.secretRoom(id,2).immediateAvailable());
        assertThrows(GameException.class,()->service.openRoom(id,2,RoomMode.IMMEDIATE));
        assertEquals(0,service.state(id).elapsedHours());assertEquals(1,service.state(id).secrets().keyAmount());
    }

}
