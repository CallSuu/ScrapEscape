package com.scrapescape.game;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;
import static com.scrapescape.game.GameModels.*;

@Repository
public class SecretRoomRepository {
    private final JdbcTemplate jdbc;
    public SecretRoomRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    record Keys(int amount,int acquired,int used) {}
    record Room(long locationId,String name,int discoveredHour,boolean immediateAvailable,Integer openedHour,
                RoomMode mode,RoomOutcome outcome,int durationHours,int staminaCost,int contaminationIncrease,
                int stormExposureHours,Long materialId,int materialQuantity,int collected,int lost,int cleanerAdded,int medicalAdded) {}
    Keys keys(UUID id){return jdbc.queryForObject("SELECT secret_key_amount,secret_keys_acquired,secret_keys_used FROM game WHERE game_id=?",
        (r,n)->new Keys(r.getInt(1),r.getInt(2),r.getInt(3)),id.toString());}
    List<Room> rooms(UUID id){return jdbc.query("""
        SELECT sr.*,l.name FROM game_secret_room sr JOIN location l ON l.location_id=sr.location_id
        WHERE sr.game_id=? ORDER BY sr.location_id
        """,(r,n)->new Room(r.getLong("location_id"),r.getString("name"),r.getInt("discovered_hour"),r.getBoolean("immediate_available"),
            r.getObject("opened_hour",Integer.class),r.getString("visit_mode")==null?null:RoomMode.valueOf(r.getString("visit_mode")),
            r.getString("outcome")==null?null:RoomOutcome.valueOf(r.getString("outcome")),r.getInt("duration_hours"),r.getInt("stamina_cost"),
            r.getInt("contamination_increase"),r.getInt("storm_exposure_hours"),r.getObject("material_id",Long.class),
            r.getInt("material_quantity"),r.getInt("materials_collected"),r.getInt("materials_lost"),r.getInt("cleaner_added"),r.getInt("medical_kits_added")),id.toString());}
    Room room(UUID id,long location){return rooms(id).stream().filter(r->r.locationId()==location).findFirst()
        .orElseThrow(()->GameException.missing("아직 발견하지 않은 비밀의 방입니다."));}
    boolean discovered(UUID id,long location){return jdbc.queryForObject("SELECT COUNT(*) FROM game_secret_room WHERE game_id=? AND location_id=?",Integer.class,id.toString(),location)>0;}
    void find(UUID id,long location,int hour){jdbc.update("INSERT INTO game_secret_room(game_id,location_id,discovered_hour) VALUES (?,?,?)",id.toString(),location,hour);}
    void addKey(UUID id){jdbc.update("UPDATE game SET secret_key_amount=secret_key_amount+1,secret_keys_acquired=secret_keys_acquired+1 WHERE game_id=?",id.toString());}
    void invalidateImmediate(UUID id){jdbc.update("UPDATE game_secret_room SET immediate_available=FALSE WHERE game_id=? AND immediate_available=TRUE",id.toString());}
    void defer(UUID id,long location){jdbc.update("UPDATE game_secret_room SET immediate_available=FALSE WHERE game_id=? AND location_id=?",id.toString(),location);}
    void consumeKey(UUID id){jdbc.update("UPDATE game SET secret_key_amount=secret_key_amount-1,secret_keys_used=secret_keys_used+1 WHERE game_id=?",id.toString());}
    void revisit(UUID id){jdbc.update("UPDATE game SET consecutive_explorations=consecutive_explorations+1 WHERE game_id=?",id.toString());}
    void rewards(UUID id,int collected,int cleaner,int medical){jdbc.update("UPDATE game SET collected=collected+?,cleaner_amount=cleaner_amount+?,medical_kit_amount=medical_kit_amount+? WHERE game_id=?",
        collected,cleaner,medical,id.toString());}
    void opened(UUID id,SecretRoomOutcome result,int hour){
        var materials=result.loot()==null?List.<MaterialLoot>of():result.loot().materials();
        jdbc.update("""
            UPDATE game_secret_room SET immediate_available=FALSE,opened_hour=?,visit_mode=?,outcome=?,duration_hours=?,stamina_cost=?,
            contamination_increase=?,storm_exposure_hours=?,material_id=?,material_quantity=?,materials_collected=?,materials_lost=?,cleaner_added=?,medical_kits_added=?
            WHERE game_id=? AND location_id=?
            """,hour,result.mode().name(),result.outcome().name(),result.durationHours(),result.staminaCost(),result.contaminationIncrease(),result.stormExposureHours(),
            materials.isEmpty()?null:materials.get(0).materialId(),result.loot()==null?0:result.loot().quantity(),
            result.loot()==null?0:result.loot().collectedQuantity(),result.lostQuantity(),result.loot()==null?0:result.loot().cleanerAdded(),
            result.loot()==null?0:result.loot().medicalKitsAdded(),id.toString(),result.locationId());
    }
}
