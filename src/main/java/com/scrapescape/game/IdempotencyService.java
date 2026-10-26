package com.scrapescape.game;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class IdempotencyService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public IdempotencyService(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
    public record Result(JsonNode body,boolean replayed) {}

    @Transactional
    public Result execute(String key,String operation,Object payload,Supplier<?> action) {
        if(key==null)return new Result(json.valueToTree(action.get()),false);
        if(!key.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
            throw new GameException(HttpStatus.BAD_REQUEST,"INVALID_IDEMPOTENCY_KEY","Idempotency-Key는 UUID 형식이어야 합니다.");
        key=key.toLowerCase(Locale.ROOT);
        try {
            String fingerprint=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                (operation+"\n"+json.writeValueAsString(payload)).getBytes(StandardCharsets.UTF_8)));
            // The unique row lock waits for another request with this key to commit or roll back.
            jdbc.update("INSERT INTO api_request(request_key,fingerprint) VALUES (?,?) ON DUPLICATE KEY UPDATE request_key=request_key",key,fingerprint);
            var record=jdbc.queryForObject("SELECT fingerprint,response_json FROM api_request WHERE request_key=? FOR UPDATE",
                (r,n)->new Stored(r.getString(1),r.getString(2)),key);
            if(!fingerprint.equals(record.fingerprint()))
                throw GameException.conflict("IDEMPOTENCY_KEY_REUSED","같은 요청 번호를 다른 행동이나 입력에 사용할 수 없습니다.");
            if(record.response()!=null)return new Result(json.readTree(record.response()),true);
            JsonNode response=json.valueToTree(action.get());
            String gameId=response.hasNonNull("gameId")?response.get("gameId").asText():response.path("state").path("gameId").asText();
            UUID.fromString(gameId);
            jdbc.update("UPDATE api_request SET game_id=?,response_json=? WHERE request_key=?",gameId,json.writeValueAsString(response),key);
            return new Result(response,false);
        } catch(JsonProcessingException|NoSuchAlgorithmException e){throw new IllegalStateException("요청 결과 저장·복원 중 오류",e);}
    }
    private record Stored(String fingerprint,String response) {}
}
