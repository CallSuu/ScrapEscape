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
class BulkMoveIntegrationTest {
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
    String path(UUID id){return "/api/games/"+id+"/items/storage";}
    String body(List<Long> ids,String storage) throws Exception {return json.writeValueAsString(Map.of("batchIds",ids,"storage",storage));}
    @Test void movesMultipleDirtyBatchesInOneRequestWithoutTimeCost() throws Exception {
        var id=fresh();long a=batch(id,1,2,"DIRTY","INVENTORY"),b=batch(id,2,3,"DIRTY","INVENTORY");
        mvc.perform(patch(path(id)).contentType(MediaType.APPLICATION_JSON).content(body(List.of(a,b),"CHEST"))).andExpect(status().isOk()).andExpect(jsonPath("$.state.chest.length()").value(2)).andExpect(jsonPath("$.state.inventory.length()").value(0)).andExpect(jsonPath("$.state.elapsedHours").value(0));
    }
    @Test void laterCapacityFailureRollsBackEarlierMoveAndRequestRecord() throws Exception {
        var id=fresh();for(int i=0;i<15;i++)batch(id,3,1,"DIRTY","CHEST");long a=batch(id,1,1,"DIRTY","INVENTORY"),b=batch(id,2,1,"DIRTY","INVENTORY");String key=UUID.randomUUID().toString();
        mvc.perform(patch(path(id)).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(body(List.of(a,b),"CHEST"))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STORAGE_FULL"));
        assertEquals(2,service.state(id).inventory().size());assertEquals(15,service.state(id).chest().size());assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM api_request WHERE request_key=?",Integer.class,key));
    }
    @Test void fullDestinationAcceptsCleanMergesAcrossSelectedBatches() throws Exception {
        var id=fresh();batch(id,1,2,"CLEAN","CHEST");for(int i=0;i<15;i++)batch(id,2,1,"DIRTY","CHEST");long a=batch(id,1,2,"CLEAN","INVENTORY"),b=batch(id,1,2,"CLEAN","INVENTORY");
        mvc.perform(patch(path(id)).contentType(MediaType.APPLICATION_JSON).content(body(List.of(a,b),"CHEST"))).andExpect(status().isOk());assertEquals(16,service.state(id).chest().size());assertTrue(service.state(id).inventory().isEmpty());assertEquals(6,jdbc.queryForObject("SELECT quantity FROM item_batch WHERE game_id=? AND material_id=1",Integer.class,id.toString()));
    }
    @Test void replayReturnsOriginalResponseAfterSourceBatchesWereMergedAway() throws Exception {
        var id=fresh();batch(id,1,2,"CLEAN","CHEST");long a=batch(id,1,1,"CLEAN","INVENTORY"),b=batch(id,1,1,"CLEAN","INVENTORY");String key=UUID.randomUUID().toString(),body=body(List.of(a,b),"CHEST");
        String first=mvc.perform(patch(path(id)).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String replay=mvc.perform(patch(path(id)).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andExpect(header().string("Idempotency-Replayed","true")).andReturn().getResponse().getContentAsString();assertEquals(json.readTree(first),json.readTree(replay));
    }
    @Test void invalidSelectionsAndCrossGameBatchNeverMoveAnyItem() throws Exception {
        var id=fresh();long a=batch(id,1,1,"DIRTY","INVENTORY");var other=fresh();long b=batch(other,2,1,"DIRTY","INVENTORY");
        for(var ids:List.of(List.<Long>of(),List.of(a,a),List.of(0L),List.of(-1L))){mvc.perform(patch(path(id)).contentType(MediaType.APPLICATION_JSON).content(body(ids,"CHEST"))).andExpect(status().isBadRequest());}
        mvc.perform(patch(path(id)).contentType(MediaType.APPLICATION_JSON).content(body(List.of(a,b),"CHEST"))).andExpect(status().isNotFound());assertEquals(1,service.state(id).inventory().size());assertEquals(1,service.state(other).inventory().size());
    }
    @Test void endedGameAndAlreadyAtDestinationRejectTheWholeRequest() throws Exception {
        var id=fresh();long a=batch(id,1,1,"DIRTY","INVENTORY"),b=batch(id,2,1,"DIRTY","CHEST");
        mvc.perform(patch(path(id)).contentType(MediaType.APPLICATION_JSON).content(body(List.of(a,b),"CHEST"))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SAME_STORAGE"));assertEquals(1,service.state(id).inventory().size());
        jdbc.update("UPDATE game SET status='FAILED',failure_reason='TIME_LIMIT' WHERE game_id=?",id.toString());mvc.perform(patch(path(id)).contentType(MediaType.APPLICATION_JSON).content(body(List.of(a),"CHEST"))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("GAME_ENDED"));
    }
    @Test void newGamesStartAtFifteenAndRestPreviewMatchesNewCap() {
        var id=fresh();assertEquals(15,service.state(id).stamina());assertEquals(15,service.state(id).maxStamina());
        jdbc.update("UPDATE game SET stamina=14 WHERE game_id=?",id.toString());
        var preview=service.screen(id).actions().rests().stream().filter(r->r.hours()==6).findFirst().orElseThrow().preview();
        assertEquals(15,preview.projected().stamina());assertEquals(15,service.rest(id,6).state().stamina());
    }

}
