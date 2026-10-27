package com.scrapescape.game;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.datasource.url=${MYSQL_TEST_URL}")
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="MYSQL_TEST_URL",matches=".*scrap_escape_test.*")
class IdempotencyIntegrationTest {
    @Autowired GameService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    final Set<UUID> created=new HashSet<>();
    UUID fresh(){var id=service.create().gameId();created.add(id);return id;}
    String key(){return UUID.randomUUID().toString();}
    String path(UUID id){return "/api/games/"+id;}
    JsonNode rest(UUID id,String key,int hours) throws Exception {
        return mapper.readTree(mvc.perform(post(path(id)+"/rests").header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON)
            .content("{\"hours\":"+hours+"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    @AfterEach void cleanup(){for(var id:created)jdbc.update("DELETE FROM game WHERE game_id=?",id.toString());}
    @Test void repeatedRestReturnsOriginalResponseWithoutAdvancingTwice() throws Exception {
        var id=fresh();String key=key();var first=rest(id,key,1);var second=rest(id,key,1);
        assertEquals(first,second);assertEquals(1,service.state(id).elapsedHours());
    }
    @Test void repeatedCreationReturnsSameGame() throws Exception {
        String key=key();String first=mvc.perform(post("/api/games").header("Idempotency-Key",key)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID id=UUID.fromString(mapper.readTree(first).get("gameId").asText());created.add(id);
        String second=mvc.perform(post("/api/games").header("Idempotency-Key",key)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        // Track an unexpected second game too, so a RED test never leaves fixtures behind.
        created.add(UUID.fromString(mapper.readTree(second).get("gameId").asText()));
        assertEquals(mapper.readTree(first),mapper.readTree(second));
    }
    @Test void sameKeyDifferentPayloadOrRouteIsRejected() throws Exception {
        var id=fresh();String key=key();rest(id,key,1);
        mvc.perform(post(path(id)+"/rests").header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content("{\"hours\":2}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        mvc.perform(post(path(id)+"/escape").header("Idempotency-Key",key)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        assertEquals(1,service.state(id).elapsedHours());
    }
    @Test void differentKeysAndRequestsWithoutKeysRemainSeparateActions() throws Exception {
        var id=fresh();rest(id,key(),1);rest(id,key(),1);
        for(int i=0;i<2;i++)mvc.perform(post(path(id)+"/rests").contentType(MediaType.APPLICATION_JSON).content("{\"hours\":1}")).andExpect(status().isOk());
        assertEquals(4,service.state(id).elapsedHours());
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM api_request WHERE game_id=?",Integer.class,id.toString()));
    }
    @Test void repeatedExplorationReusesLootAndDoesNotConsumeExtraStamina() throws Exception {
        var id=fresh();String key=key();var request=post(path(id)+"/explorations").header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content("{\"locationId\":1}");
        var first=mvc.perform(request).andExpect(status().isOk()).andExpect(header().string("Idempotency-Replayed","false")).andReturn().getResponse().getContentAsString();
        var second=mvc.perform(request).andExpect(status().isOk()).andExpect(header().string("Idempotency-Replayed","true")).andReturn().getResponse().getContentAsString();
        assertEquals(mapper.readTree(first),mapper.readTree(second));assertEquals(6,service.state(id).elapsedHours());assertEquals(13,service.state(id).stamina());assertEquals(1,service.state(id).statistics().explorations());
    }
    @Test void sameKeyIsGlobalAndCannotBeReusedOnAnotherGame() throws Exception {
        var a=fresh();var b=fresh();String key=key();rest(a,key,1);
        mvc.perform(post(path(b)+"/rests").header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content("{\"hours\":1}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        assertEquals(0,service.state(b).elapsedHours());
    }
    @Test void concurrentSameKeyExecutesOneActionAndReplaysOtherResponse() throws Exception {
        var id=fresh();String key=key();var executor=Executors.newFixedThreadPool(2);
        try {
            Callable<String> call=()->mvc.perform(post(path(id)+"/rests").header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content("{\"hours\":1}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            var results=executor.invokeAll(List.of(call,call));assertEquals(mapper.readTree(results.get(0).get()),mapper.readTree(results.get(1).get()));
            assertEquals(1,service.state(id).elapsedHours());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM api_request WHERE game_id=?",Integer.class,id.toString()));
        } finally {executor.shutdownNow();}
    }
    @Test void completedResponseIsHistoricalAndReplayDoesNotChangeLatestState() throws Exception {
        var id=fresh();String firstKey=key();var first=rest(id,firstKey,1);rest(id,key(),3);
        jdbc.update("INSERT INTO game_secret_room(game_id,location_id,discovered_hour) VALUES (?,1,4)",id.toString());
        var before=service.state(id);assertEquals(first,rest(id,firstKey,1));assertEquals(before,service.state(id));
        assertEquals(4,service.state(id).elapsedHours());assertTrue(service.secretRoom(id,1).immediateAvailable());
    }
    @Test void failedDomainActionIsNotCachedAndCanBeRetriedAfterRecovery() throws Exception {
        var id=fresh();String key=key();
        mvc.perform(post(path(id)+"/treatments").header("Idempotency-Key",key)).andExpect(status().isConflict());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM api_request WHERE request_key=?",Integer.class,key));
        jdbc.update("UPDATE game SET medical_kit_amount=1,contamination=100,diseased=TRUE,disease_started_hour=0 WHERE game_id=?",id.toString());
        var first=mvc.perform(post(path(id)+"/treatments").header("Idempotency-Key",key)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var second=mvc.perform(post(path(id)+"/treatments").header("Idempotency-Key",key)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(mapper.readTree(first),mapper.readTree(second));assertEquals(1,service.state(id).elapsedHours());assertEquals(40,service.state(id).contamination());assertEquals(0,service.state(id).medicalKitAmount());
    }
    @Test void successfulDeletionAndEndedEscapeCanBeReplayed() throws Exception {
        var id=fresh();jdbc.update("INSERT INTO item_batch(game_id,material_id,quantity,storage_type,acquired_hour,item_condition) VALUES (?,1,2,'INVENTORY',0,'DIRTY')",id.toString());
        long batch=jdbc.queryForObject("SELECT MAX(batch_id) FROM item_batch WHERE game_id=?",Long.class,id.toString());String deleteKey=key();
        String first=mvc.perform(delete(path(id)+"/items/"+batch).header("Idempotency-Key",deleteKey)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String second=mvc.perform(delete(path(id)+"/items/"+batch).header("Idempotency-Key",deleteKey)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(mapper.readTree(first),mapper.readTree(second));assertEquals(2,service.state(id).statistics().discarded());
        jdbc.update("UPDATE game_repair SET repaired=TRUE WHERE game_id=?",id.toString());String escapeKey=key();
        first=mvc.perform(post(path(id)+"/escape").header("Idempotency-Key",escapeKey)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        second=mvc.perform(post(path(id)+"/escape").header("Idempotency-Key",escapeKey)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(mapper.readTree(first),mapper.readTree(second));assertEquals(GameModels.Status.SUCCESS,service.state(id).status());
    }
    @Test void failedGameOutcomeWithHttp200IsCached() throws Exception {
        var id=fresh();jdbc.update("UPDATE game SET elapsed_hours=235 WHERE game_id=?",id.toString());String key=key();
        var first=rest(id,key,8);assertEquals("FAILED",first.get("state").get("status").asText());assertEquals(first,rest(id,key,8));assertEquals(243,service.state(id).elapsedHours());
    }
    @Test void invalidKeysAreRejectedWithoutExecutingOrCreatingHistory() throws Exception {
        var id=fresh();for(String key:new String[]{"","not-a-uuid","1-1-1-1-1"," ","a".repeat(100)})
            mvc.perform(post(path(id)+"/rests").header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content("{\"hours\":1}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_IDEMPOTENCY_KEY"));
        assertEquals(0,service.state(id).elapsedHours());assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM api_request WHERE game_id=?",Integer.class,id.toString()));
    }
    @Test void keyCaseAndJsonFormattingDoNotChangeLogicalRequest() throws Exception {
        var id=fresh();String key=key();rest(id,key.toUpperCase(Locale.ROOT),1);
        mvc.perform(post(path(id)+"/rests").header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("ignored",1,"hours",1))))
            .andExpect(status().isOk()).andExpect(header().string("Idempotency-Replayed","true"));
        assertEquals(1,service.state(id).elapsedHours());
    }
    @Test void lateCacheFailureRollsBackGameAndRequestHistoryTogether() throws Exception {
        var id=fresh();String key=key();var before=service.state(id);
        jdbc.execute("CREATE TRIGGER test_request_rollback BEFORE UPDATE ON api_request FOR EACH ROW BEGIN IF NEW.response_json IS NOT NULL THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='request rollback'; END IF; END");
        try {
            mvc.perform(post(path(id)+"/rests").header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content("{\"hours\":1}")).andExpect(status().isInternalServerError());
            assertEquals(before,service.state(id));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM api_request WHERE request_key=?",Integer.class,key));
        } finally {jdbc.execute("DROP TRIGGER test_request_rollback");}
        rest(id,key,1);assertEquals(1,service.state(id).elapsedHours());
    }

}
