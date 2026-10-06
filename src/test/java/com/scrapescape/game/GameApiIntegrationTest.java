package com.scrapescape.game;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest(properties="spring.datasource.url=${MYSQL_TEST_URL}")
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="MYSQL_TEST_URL",matches=".*scrap_escape_test.*")
class GameApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Test void malformedUuidHasConsistentError() throws Exception {
        mvc.perform(get("/api/games/not-an-id")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
    @Test void fractionalOrMissingRestHoursAreRejected() throws Exception {
        String id="00000000-0000-0000-0000-000000000001";
        for(String body:new String[]{"{\"hours\":1.5}","{}","{\"hours\":9}"}) {
            mvc.perform(post("/api/games/"+id+"/rests").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
    }
    @Test void invalidStorageDoesNotReachDatabase() throws Exception {
        mvc.perform(patch("/api/games/00000000-0000-0000-0000-000000000001/items/1/storage")
            .contentType(MediaType.APPLICATION_JSON).content("{\"storage\":\"OTHER\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
    @Test void unknownGameReturns404WithoutSqlDetails() throws Exception {
        mvc.perform(get("/api/games/00000000-0000-0000-0000-000000000001"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"))
            .andExpect(content().string(not(containsString("SELECT"))));
    }
    @Test void frameworkErrorsPreserveTheirHttpStatus() throws Exception {
        mvc.perform(put("/api/games")).andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("$.code").value("HTTP_ERROR"));
        mvc.perform(post("/api/games/00000000-0000-0000-0000-000000000001/rests")
            .contentType(MediaType.TEXT_PLAIN).content("hello")).andExpect(status().isUnsupportedMediaType());
        mvc.perform(get("/not-a-route")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("HTTP_ERROR"));
    }
}
