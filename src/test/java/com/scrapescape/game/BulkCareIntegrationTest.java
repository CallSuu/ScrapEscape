package com.scrapescape.game;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.datasource.url=${MYSQL_TEST_URL}")
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="MYSQL_TEST_URL",matches=".*scrap_escape_test.*")
class BulkCareIntegrationTest {
    @Autowired GameService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    final Set<UUID> games=new HashSet<>();
    UUID fresh(){var id=service.create().gameId();games.add(id);return id;}
    long batch(UUID id,int material,int qty,String condition,String storage){
        jdbc.update("INSERT INTO item_batch(game_id,material_id,quantity,storage_type,acquired_hour,item_condition) VALUES (?,?,?,?,0,?)",id.toString(),material,qty,storage,condition);
        return jdbc.queryForObject("SELECT MAX(batch_id) FROM item_batch WHERE game_id=?",Long.class,id.toString());
    }
    @AfterEach void cleanup(){for(var id:games)jdbc.update("DELETE FROM game WHERE game_id=?",id.toString());}
    String body(long... ids) throws Exception {return json.writeValueAsString(Map.of("batchIds",ids));}
    void supply(UUID id,int amount){jdbc.update("UPDATE game SET cleaner_amount=?,stamina=0 WHERE game_id=?",amount,id.toString());}
    @Test void cleanerShortageRollsBackAllSelectedBatches() throws Exception {
        var id=fresh();long a=batch(id,1,2,"DIRTY","INVENTORY"),b=batch(id,2,3,"DIRTY","CHEST");supply(id,1);
        mvc.perform(post("/api/games/"+id+"/items/clean").contentType(MediaType.APPLICATION_JSON).content(body(a,b))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INSUFFICIENT_CLEANER"));
        assertEquals(0,service.state(id).elapsedHours());assertEquals(1,service.state(id).cleanerAmount());assertEquals(0,service.state(id).statistics().cleaned());
    }
    @Test void cleansBothStoragesAtZeroStaminaAndProtectsSelectedExpiringBatches() throws Exception {
        var id=fresh();long a=batch(id,1,2,"DIRTY","INVENTORY"),b=batch(id,2,3,"DIRTY","CHEST"),c=batch(id,3,1,"DIRTY","CHEST");supply(id,2);jdbc.update("UPDATE game SET elapsed_hours=47 WHERE game_id=?",id.toString());
        mvc.perform(post("/api/games/"+id+"/items/clean").contentType(MediaType.APPLICATION_JSON).content(body(a,b))).andExpect(status().isOk()).andExpect(jsonPath("$.state.elapsedHours").value(49)).andExpect(jsonPath("$.state.stamina").value(0)).andExpect(jsonPath("$.state.cleanerAmount").value(0)).andExpect(jsonPath("$.state.statistics.cleaned").value(5));
        assertEquals("UNUSABLE",jdbc.queryForObject("SELECT item_condition FROM item_batch WHERE batch_id=?",String.class,c));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM item_batch WHERE game_id=? AND item_condition='CLEAN'",Integer.class,id.toString()));
    }
    @Test void cleanReplayWorksAfterMergedBatchIsDeleted() throws Exception {
        var id=fresh();long a=batch(id,1,2,"DIRTY","INVENTORY"),b=batch(id,1,3,"DIRTY","INVENTORY");supply(id,2);var key=UUID.randomUUID().toString();String path="/api/games/"+id+"/items/clean",payload=body(a,b);
        var first=mvc.perform(post(path).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var second=mvc.perform(post(path).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isOk()).andExpect(header().string("Idempotency-Replayed","true")).andReturn().getResponse().getContentAsString();assertEquals(json.readTree(first),json.readTree(second));assertEquals(1,service.state(id).inventory().size());assertEquals(5,service.state(id).inventory().get(0).quantity());
    }
    @Test void discardsMultipleStoragesAndReplaysWithoutDoubleStatistics() throws Exception {
        var id=fresh();long a=batch(id,1,2,"CLEAN","INVENTORY"),b=batch(id,2,3,"DIRTY","CHEST");String path="/api/games/"+id+"/items",key=UUID.randomUUID().toString();
        for(int i=0;i<2;i++)mvc.perform(delete(path).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(body(a,b))).andExpect(status().isOk()).andExpect(jsonPath("$.state.statistics.discarded").value(5));assertEquals(0,service.state(id).elapsedHours());assertTrue(service.state(id).chest().isEmpty());assertTrue(service.state(id).inventory().isEmpty());
    }
    @Test void invalidOrForeignSelectionsDoNotPartiallyMutate() throws Exception {
        var id=fresh();long a=batch(id,1,1,"DIRTY","INVENTORY"),b=batch(fresh(),2,1,"DIRTY","CHEST");supply(id,8);
        for(String suffix:List.of("/items/clean","/items")){
            var path="/api/games/"+id+suffix;
            for(long[] ids:List.of(new long[]{},new long[]{a,a},new long[]{0}))mvc.perform((suffix.endsWith("clean")?post(path):delete(path)).contentType(MediaType.APPLICATION_JSON).content(body(ids))).andExpect(status().isBadRequest());
            mvc.perform((suffix.endsWith("clean")?post(path):delete(path)).contentType(MediaType.APPLICATION_JSON).content(body(a,b))).andExpect(status().isNotFound());
        }
        assertEquals(1,service.state(id).inventory().size());assertEquals(8,service.state(id).cleanerAmount());
    }
    @Test void uncleanableSelectionAndEndedGameAreRejected() throws Exception {
        var id=fresh();long a=batch(id,1,1,"DIRTY","INVENTORY"),b=batch(id,2,1,"CLEAN","CHEST");supply(id,2);
        mvc.perform(post("/api/games/"+id+"/items/clean").contentType(MediaType.APPLICATION_JSON).content(body(a,b))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOT_CLEANABLE"));assertEquals(0,service.state(id).statistics().cleaned());
        jdbc.update("UPDATE game SET status='FAILED',failure_reason='TIME_LIMIT' WHERE game_id=?",id.toString());
        mvc.perform(delete("/api/games/"+id+"/items").contentType(MediaType.APPLICATION_JSON).content(body(a,b))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("GAME_ENDED"));assertEquals(1,service.state(id).inventory().size());
    }
}
