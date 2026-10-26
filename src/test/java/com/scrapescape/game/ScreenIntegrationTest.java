package com.scrapescape.game;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.datasource.url=${MYSQL_TEST_URL}")
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="MYSQL_TEST_URL",matches=".*scrap_escape_test.*")
class ScreenIntegrationTest {
    @Autowired GameService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    final List<UUID> games=new ArrayList<>();
    UUID fresh(){var id=service.create().gameId();games.add(id);return id;}
    @AfterEach void cleanup(){for(var id:games)jdbc.update("DELETE FROM game WHERE game_id=?",id.toString());}
    JsonNode screen(UUID id) throws Exception {
        return json.readTree(mvc.perform(get("/api/games/"+id+"/screen")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    long batch(UUID id,long material,int quantity,String condition,String storage,int hour){
        jdbc.update("INSERT INTO item_batch(game_id,material_id,quantity,storage_type,acquired_hour,item_condition) VALUES (?,?,?,?,?,?)",id.toString(),material,quantity,storage,hour,condition);
        return jdbc.queryForObject("SELECT MAX(batch_id) FROM item_batch WHERE game_id=?",Long.class,id.toString());
    }
    JsonNode item(JsonNode screen,long id){for(var i:screen.path("actions").path("items"))if(i.path("batchId").asLong()==id)return i;throw new AssertionError("missing batch");}
    void matches(JsonNode projection,UUID id){
        var state=json.valueToTree(service.state(id));
        for(String key:List.of("elapsedHours","stamina","contamination","cleanerAmount","medicalKitAmount","diseased","status","failureReason"))assertEquals(state.get(key),projection.get(key),key);
    }
    @Test void initialScreenProvidesAllCardsAndEightRestChoicesWithoutMutation() throws Exception {
        var id=fresh();jdbc.update("INSERT INTO game_secret_room(game_id,location_id,discovered_hour) VALUES (?,1,0)",id.toString());
        var before=service.state(id);var s=screen(id);assertEquals(6,s.path("locations").size());assertEquals(8,s.path("actions").path("rests").size());
        assertEquals(4,s.path("actions").path("repairs").size());assertFalse(s.path("actions").path("treatment").path("allowed").asBoolean());
        assertEquals("INSUFFICIENT_MEDICAL_KIT",s.path("actions").path("treatment").path("unavailableCode").asText());
        assertEquals(before,service.state(id));assertTrue(service.secretRoom(id,1).immediateAvailable());
    }
    @Test void restPreviewCapsRecoveryAndMatchesExecutedAction() throws Exception {
        var id=fresh();jdbc.update("UPDATE game SET stamina=3,contamination=25,consecutive_explorations=3 WHERE game_id=?",id.toString());
        var rests=screen(id).path("actions").path("rests");
        assertEquals(6,rests.get(2).path("preview").path("projected").path("stamina").asInt());
        assertEquals(25,rests.get(2).path("preview").path("projected").path("contamination").asInt());
        var p=rests.get(5).path("preview").path("projected");assertEquals(10,p.path("stamina").asInt());assertEquals(15,p.path("contamination").asInt());
        service.rest(id,6);matches(p,id);assertEquals(0,service.state(id).consecutiveExplorations());
    }
    @Test void deadlinesWarnWithoutBlockingAndKeepTimeLimitPriority() throws Exception {
        var id=fresh();jdbc.update("UPDATE game SET elapsed_hours=239,diseased=TRUE,disease_started_hour=167 WHERE game_id=?",id.toString());
        var rest=screen(id).path("actions").path("rests").get(0).path("preview");assertTrue(rest.path("allowed").asBoolean());
        assertEquals("TIME_LIMIT",rest.path("projected").path("failureReason").asText());service.rest(id,1);matches(rest.path("projected"),id);
    }
    @Test void treatmentAtDiseaseDeadlineDoesNotPromiseCureOrConsumeKit() throws Exception {
        var id=fresh();jdbc.update("UPDATE game SET elapsed_hours=71,diseased=TRUE,disease_started_hour=0,contamination=100,medical_kit_amount=1 WHERE game_id=?",id.toString());
        var p=screen(id).path("actions").path("treatment").path("projected");assertEquals("UNTREATED_DISEASE",p.path("failureReason").asText());
        assertEquals(1,p.path("medicalKitAmount").asInt());assertTrue(p.path("diseased").asBoolean());service.treat(id);matches(p,id);
    }
    @Test void timelyTreatmentPreviewMatchesCure() throws Exception {
        var id=fresh();jdbc.update("UPDATE game SET diseased=TRUE,disease_started_hour=0,contamination=100,medical_kit_amount=1 WHERE game_id=?",id.toString());
        var p=screen(id).path("actions").path("treatment").path("projected");assertEquals(40,p.path("contamination").asInt());assertFalse(p.path("diseased").asBoolean());
        service.treat(id);matches(p,id);
    }
    @Test void cleaningAtFortyEightProtectsChosenBatchAndWarnsAboutOthers() throws Exception {
        var id=fresh();long a=batch(id,1,2,"DIRTY","INVENTORY",0),b=batch(id,2,1,"DIRTY","CHEST",0);
        jdbc.update("UPDATE game SET elapsed_hours=47 WHERE game_id=?",id.toString());var s=screen(id);var p=item(s,a).path("clean").path("projected");
        assertEquals(1,p.path("expiringBatchIds").size());assertEquals(b,p.path("expiringBatchIds").get(0).asLong());service.clean(id,a);matches(p,id);
        assertEquals("NOT_CLEANABLE",item(screen(id),b).path("clean").path("unavailableCode").asText());
    }
    @Test void fullChestAllowsCleanMergeButRejectsDirtyMove() throws Exception {
        var id=fresh();long clean=batch(id,1,2,"CLEAN","INVENTORY",0),dirty=batch(id,2,1,"DIRTY","INVENTORY",0);
        batch(id,1,4,"CLEAN","CHEST",0);for(int i=0;i<15;i++)batch(id,2,1,"DIRTY","CHEST",0);
        var s=screen(id);assertTrue(item(s,clean).path("move").path("allowed").asBoolean());
        assertEquals("STORAGE_FULL",item(s,dirty).path("move").path("unavailableCode").asText());service.move(id,clean,GameModels.Storage.CHEST);
        assertEquals(16,service.state(id).chest().size());
    }
    @Test void cleanInsufficientResourcesAndDiscardRemainDistinct() throws Exception {
        var id=fresh();long b=batch(id,1,2,"DIRTY","INVENTORY",0);jdbc.update("UPDATE game SET stamina=0,cleaner_amount=0 WHERE game_id=?",id.toString());
        var i=item(screen(id),b);assertEquals("INSUFFICIENT_STAMINA",i.path("clean").path("unavailableCode").asText());assertTrue(i.path("discard").path("allowed").asBoolean());
        jdbc.update("UPDATE game SET stamina=1 WHERE game_id=?",id.toString());assertEquals("INSUFFICIENT_CLEANER",item(screen(id),b).path("clean").path("unavailableCode").asText());
        var discard=item(screen(id),b).path("discard").path("projected");service.discard(id,b);matches(discard,id);
    }
    @Test void repairPreviewUsesBothStoresAndPredictsCompletion() throws Exception {
        var id=fresh();var repair=service.state(id).repairs().get(0);
        assertEquals("INSUFFICIENT_MATERIALS",screen(id).path("actions").path("repairs").get(0).path("preview").path("unavailableCode").asText());
        for(var ingredient:repair.ingredients())batch(id,ingredient.materialId(),ingredient.requiredQuantity(),"CLEAN","CHEST",0);
        var p=screen(id).path("actions").path("repairs").get(0).path("preview");assertTrue(p.path("allowed").asBoolean());service.craft(id,repair.partId());matches(p.path("projected"),id);
        assertEquals("ALREADY_REPAIRED",screen(id).path("actions").path("repairs").get(0).path("preview").path("unavailableCode").asText());
    }
    @Test void endedAndLegacyGamesStayReadableWithActionsDisabled() throws Exception {
        var id=fresh();jdbc.update("UPDATE game SET status='FAILED',failure_reason='TIME_LIMIT',elapsed_hours=240 WHERE game_id=?",id.toString());
        var p=screen(id).path("actions").path("rests").get(0).path("preview");assertFalse(p.path("allowed").asBoolean());assertTrue(p.path("projected").isNull());assertEquals("GAME_ENDED",p.path("unavailableCode").asText());
        jdbc.update("UPDATE game SET rules_version=1 WHERE game_id=?",id.toString());assertEquals("LEGACY_GAME",screen(id).path("actions").path("escape").path("unavailableCode").asText());
    }
    @Test void escapePreviewPredictsSuccessAndMissingGameIs404() throws Exception {
        var id=fresh();jdbc.update("UPDATE game_repair SET repaired=TRUE WHERE game_id=?",id.toString());var p=screen(id).path("actions").path("escape");
        assertTrue(p.path("allowed").asBoolean());assertEquals("SUCCESS",p.path("projected").path("status").asText());service.escape(id);matches(p.path("projected"),id);
        mvc.perform(get("/api/games/"+UUID.randomUUID()+"/screen")).andExpect(status().isNotFound());
    }
}
