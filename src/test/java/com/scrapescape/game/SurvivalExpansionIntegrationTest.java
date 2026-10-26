package com.scrapescape.game;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
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
class SurvivalExpansionIntegrationTest {
    @Autowired GameService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired GameServiceIntegrationTest.ScriptedRandom random;
    @Autowired ObjectMapper mapper;
    @Autowired MockMvc mvc;
    final List<UUID> games=new ArrayList<>();
    UUID fresh() { random.set();var id=service.create().gameId();games.add(id);return id; }
    @AfterEach void cleanup(){for(var id:games)jdbc.update("DELETE FROM game WHERE game_id=?",id.toString());}
    int value(UUID id,String key){return mapper.valueToTree(service.state(id)).get(key).asInt();}
    @Test void hospitalGuaranteesKitEvenWithNoMaterialLoot() {
        var id=fresh();random.set(0,0,99,99);
        var result=service.explore(id,6);assertEquals(0,result.loot().quantity());
        assertEquals(1,value(id,"medicalKitAmount"));assertEquals(10,value(id,"contamination"));
    }
    @Test void longRestReducesContaminationAndResetsConsecutiveButDoesNotCure() {
        var id=fresh();jdbc.update("UPDATE game SET contamination=70,consecutive_explorations=3,diseased=TRUE,disease_started_hour=0 WHERE game_id=?",id.toString());
        service.rest(id,3);assertEquals(70,value(id,"contamination"));assertEquals(3,value(id,"consecutiveExplorations"));
        service.rest(id,4);assertEquals(60,value(id,"contamination"));assertEquals(0,value(id,"consecutiveExplorations"));
        assertTrue(mapper.valueToTree(service.state(id)).get("diseased").asBoolean());assertEquals(65,value(id,"treatmentRemainingHours"));
    }
    @Test void diseaseCostsTimeAndStaminaWithoutBlockingExploration() {
        var id=fresh();jdbc.update("UPDATE game SET diseased=TRUE,disease_started_hour=0 WHERE game_id=?",id.toString());
        random.set(0,0,99,99);var state=service.explore(id,1).state();
        assertEquals(8,state.elapsedHours());assertEquals(6,state.stamina());
    }
    @Test void cureDropsContaminationBySixtyAndDoesNotImmediatelyRelapse() throws Exception {
        var id=fresh();jdbc.update("UPDATE game SET contamination=100,diseased=TRUE,disease_started_hour=0,medical_kit_amount=1 WHERE game_id=?",id.toString());
        mvc.perform(post("/api/games/"+id+"/treatments")).andExpect(status().isOk())
            .andExpect(jsonPath("$.state.contamination").value(40)).andExpect(jsonPath("$.state.diseased").value(false))
            .andExpect(jsonPath("$.state.medicalKitAmount").value(0)).andExpect(jsonPath("$.state.elapsedHours").value(1));
    }
    @Test void treatmentAtSeventyTwoHourDeadlineCannotRescueGame() throws Exception {
        var id=fresh();jdbc.update("UPDATE game SET elapsed_hours=71,diseased=TRUE,disease_started_hour=0,medical_kit_amount=1 WHERE game_id=?",id.toString());
        mvc.perform(post("/api/games/"+id+"/treatments")).andExpect(status().isOk())
            .andExpect(jsonPath("$.state.status").value("FAILED")).andExpect(jsonPath("$.state.failureReason").value("UNTREATED_DISEASE"));
    }
    @Test void stormUsesOverlapAndSafeRestHasNoExposure() {
        var id=fresh();jdbc.update("DELETE FROM radiation_storm WHERE game_id=?",id.toString());
        jdbc.update("INSERT INTO radiation_storm(game_id,start_hour,end_hour) VALUES (?,2,4)",id.toString());
        random.set(0,0,99,99);service.explore(id,1);assertEquals(20,value(id,"contamination"));
        service.rest(id,4);assertEquals(10,value(id,"contamination"));
    }
    @Test void escapeRequiresAllRepairsAndIsExplicit() throws Exception {
        var id=fresh();mvc.perform(post("/api/games/"+id+"/escape")).andExpect(status().isConflict());
        jdbc.update("UPDATE game_repair SET repaired=TRUE WHERE game_id=?",id.toString());
        assertEquals(GameModels.Status.RUNNING,service.state(id).status());
        mvc.perform(post("/api/games/"+id+"/escape")).andExpect(status().isOk()).andExpect(jsonPath("$.state.status").value("SUCCESS"));
        mvc.perform(post("/api/games/"+id+"/escape")).andExpect(status().isConflict());
    }
    @Test void legacyGamesRemainReadableButActionsAreRejected() {
        var id=fresh();jdbc.update("UPDATE game SET rules_version=1 WHERE game_id=?",id.toString());
        assertNotNull(service.state(id));assertThrows(GameException.class,()->service.rest(id,1));
    }
    @Test void previewsMatchActualStormExposureAndDiseaseCosts() {
        var id=fresh();jdbc.update("DELETE FROM radiation_storm WHERE game_id=?",id.toString());
        jdbc.update("INSERT INTO radiation_storm(game_id,start_hour,end_hour) VALUES (?,2,4),(?,5,8)",id.toString(),id.toString());
        jdbc.update("UPDATE game SET diseased=TRUE,disease_started_hour=0,consecutive_explorations=2 WHERE game_id=?",id.toString());
        var card=service.locations(id).get(0);assertEquals(8,card.durationHours());assertEquals(4,card.staminaCost());
        assertEquals(5,card.stormExposureHours());assertEquals(40,card.expectedContaminationIncrease());
        random.set(0,0,99,99);var result=service.explore(id,1).state();
        assertEquals(40,result.contamination());assertEquals(5,result.statistics().stormExposureHours());
    }
    @Test void diseaseRollBoundariesAndExistingIllnessDoNotResetDeadline() {
        var id=fresh();jdbc.update("UPDATE game SET contamination=50 WHERE game_id=?",id.toString());
        random.set(0,0,99,30);service.explore(id,1);assertFalse(service.state(id).diseased());
        jdbc.update("UPDATE game SET contamination=50 WHERE game_id=?",id.toString());
        random.set(0,0,99,29);service.explore(id,1);assertTrue(service.state(id).diseased());
        assertEquals(72,service.state(id).treatmentRemainingHours());
        service.rest(id,8);random.set(0,0,99,0);service.explore(id,1);
        assertEquals(55,service.state(id).treatmentRemainingHours());
        assertEquals(1,service.state(id).statistics().diseaseCount());
    }
    @Test void treatmentBeforeDeadlineSucceedsAndMissingKitChangesNothing() throws Exception {
        var id=fresh();var before=service.state(id);
        mvc.perform(post("/api/games/"+id+"/treatments")).andExpect(status().isConflict());
        assertEquals(before,service.state(id));
        jdbc.update("UPDATE game SET elapsed_hours=70,diseased=TRUE,disease_started_hour=0,medical_kit_amount=1,contamination=20 WHERE game_id=?",id.toString());
        mvc.perform(post("/api/games/"+id+"/treatments")).andExpect(status().isOk());
        assertFalse(service.state(id).diseased());assertEquals(0,service.state(id).contamination());
        assertNull(service.state(id).treatmentRemainingHours());assertEquals(71,service.state(id).elapsedHours());
    }
    @Test void hospitalCounterCapsAndPartialLootReportsOnlyStoredMaterials() {
        var id=fresh();jdbc.update("UPDATE game SET medical_kit_amount=2 WHERE game_id=?",id.toString());
        for(int i=0;i<3;i++)jdbc.update("INSERT INTO item_batch(game_id,material_id,quantity,storage_type,acquired_hour,item_condition) VALUES (?,1,1,'INVENTORY',0,'DIRTY')",id.toString());
        random.set(99,0,0,0,99,99);var result=service.explore(id,6);
        assertEquals(3,result.loot().quantity());assertEquals(1,result.loot().collectedQuantity());assertEquals(2,result.loot().leftBehindQuantity());
        assertEquals(1,result.state().statistics().collected());assertEquals(4,result.state().inventory().size());
        assertEquals(2,result.state().medicalKitAmount());assertEquals(0,result.loot().medicalKitsAdded());
    }
    @Test void stormSchedulesAreBoundedAndFailureOverridesEscape() throws Exception {
        var id=fresh();int previous=0;
        for(var row:jdbc.queryForList("SELECT start_hour,end_hour FROM radiation_storm WHERE game_id=? ORDER BY start_hour",id.toString())) {
            int start=((Number)row.get("start_hour")).intValue(),end=((Number)row.get("end_hour")).intValue();
            assertTrue(start-previous>=24&&start-previous<=48);assertTrue(end-start>=1&&end-start<=3);previous=start;
        }
        jdbc.update("UPDATE game_repair SET repaired=TRUE WHERE game_id=?",id.toString());
        jdbc.update("UPDATE game SET elapsed_hours=240 WHERE game_id=?",id.toString());
        mvc.perform(post("/api/games/"+id+"/escape")).andExpect(status().isOk()).andExpect(jsonPath("$.state.status").value("FAILED"));
    }

}
