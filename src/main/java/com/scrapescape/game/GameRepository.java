package com.scrapescape.game;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;
import static com.scrapescape.game.GameModels.*;

@Repository
public class GameRepository {
    private final JdbcTemplate jdbc;
    public GameRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    Game game(UUID id,boolean lock) {
        var rows=jdbc.query("SELECT * FROM game WHERE game_id=?"+(lock?" FOR UPDATE":""),(r,n)->
            new Game(UUID.fromString(r.getString("game_id")),r.getInt("elapsed_hours"),r.getInt("stamina"),
                r.getInt("cleaner_amount"),Status.valueOf(r.getString("status")),r.getInt("explorations"),
                r.getInt("day_explorations"),r.getInt("night_explorations"),r.getInt("empty_explorations"),
                r.getInt("collected"),r.getInt("cleaned"),r.getInt("discarded"),r.getInt("rules_version"),
                r.getInt("medical_kit_amount"),r.getInt("contamination"),r.getBoolean("diseased"),
                r.getObject("disease_started_hour",Integer.class),r.getInt("consecutive_explorations"),r.getInt("disease_count"),
                r.getInt("treatment_count"),r.getInt("medical_kits_used"),r.getInt("max_contamination"),
                r.getInt("storm_exposure_hours"),r.getString("failure_reason")),id.toString());
        if(rows.isEmpty()) throw GameException.missing("게임을 찾을 수 없습니다.");
        return rows.get(0);
    }
    void create(UUID id,List<Long> broken,List<Storm> storms) {
        jdbc.update("INSERT INTO game(game_id,rules_version) VALUES (?,2)",id.toString());
        for(long part:broken) {
            jdbc.update("INSERT INTO game_repair(game_id,part_id,part_name) SELECT ?,part_id,name FROM part WHERE part_id=?",id.toString(),part);
            jdbc.update("INSERT INTO game_recipe SELECT ?,part_id,material_id,required_quantity FROM recipe WHERE part_id=?",id.toString(),part);
        }
        for(var storm:storms)jdbc.update("INSERT INTO radiation_storm(game_id,start_hour,end_hour) VALUES (?,?,?)",id.toString(),storm.startHour(),storm.endHour());
    }
    List<Long> partIds() { return jdbc.query("SELECT part_id FROM part ORDER BY part_id",(r,n)->r.getLong(1)); }
    List<Item> items(Game g) {
        return jdbc.query("""
            SELECT b.*,m.name FROM item_batch b JOIN material m ON m.material_id=b.material_id
            WHERE b.game_id=? ORDER BY b.acquired_hour,b.batch_id
            """,(r,n)->{
                var condition=Condition.valueOf(r.getString("item_condition"));
                return new Item(r.getLong("batch_id"),r.getLong("material_id"),r.getString("name"),r.getInt("quantity"),
                    Storage.valueOf(r.getString("storage_type")),r.getInt("acquired_hour"),condition,
                    condition==Condition.DIRTY?Math.max(0,48-(g.elapsed()-r.getInt("acquired_hour"))):null);
            },g.id().toString());
    }
    Item item(Game g,long batchId) {
        return items(g).stream().filter(b->b.batchId()==batchId).findFirst()
            .orElseThrow(()->GameException.missing("이 게임의 재료를 찾을 수 없습니다."));
    }
    int slots(UUID id,Storage storage) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM item_batch WHERE game_id=? AND storage_type=?",Integer.class,id.toString(),storage.name());
    }
    List<Repair> repairs(Game g,List<Item> items) {
        return jdbc.query("SELECT part_id,part_name,repaired FROM game_repair WHERE game_id=? ORDER BY part_id",(r,n)->{
            long part=r.getLong("part_id");boolean repaired=r.getBoolean("repaired");
            var ingredients=jdbc.query("""
                SELECT r.material_id,m.name,r.required_quantity FROM game_recipe r
                JOIN material m ON m.material_id=r.material_id WHERE r.game_id=? AND r.part_id=? ORDER BY r.material_id
                """,(ir,i)->{
                    long material=ir.getLong("material_id");
                    int available=items.stream().filter(b->b.condition()==Condition.CLEAN&&b.materialId()==material).mapToInt(Item::quantity).sum();
                    return new Ingredient(material,ir.getString("name"),ir.getInt("required_quantity"),available);
                },g.id().toString(),part);
            boolean canCraft=g.rulesVersion()==2&&g.status()==Status.RUNNING&&!repaired&&!ingredients.isEmpty()
                &&ingredients.stream().allMatch(i->i.availableQuantity()>=i.requiredQuantity());
            return new Repair(part,r.getString("part_name"),repaired,canCraft,ingredients);
        },g.id().toString());
    }
    List<Storm> storms(UUID id) {
        return jdbc.query("SELECT start_hour,end_hour FROM radiation_storm WHERE game_id=? ORDER BY start_hour",
            (r,n)->new Storm(r.getInt(1),r.getInt(2)),id.toString());
    }
    int exposure(UUID id,int start,int end) {
        return storms(id).stream().mapToInt(s->Math.max(0,Math.min(end,s.endHour())-Math.max(start,s.startHour()))).sum();
    }
    List<LocationCard> cards(Game g) {
        boolean night=GameRules.isNight(g.elapsed());int hours=GameRules.duration(g),cost=GameRules.staminaCost(g);
        int consecutive=g.consecutive()>=2?5:0,exposure=exposure(g.id(),g.elapsed(),g.elapsed()+hours);
        int increase=10+consecutive+5*exposure;
        String reason=g.rulesVersion()!=2?"이전 규칙의 게임입니다. 새 게임을 시작하세요.":g.status()!=Status.RUNNING?"종료된 게임입니다.":
            slots(g.id(),Storage.INVENTORY)>=GameRules.INVENTORY_CAPACITY?"인벤토리가 가득 찼습니다.":g.stamina()<cost?"탐색할 체력이 부족합니다.":null;
        List<RewardProbability> probabilities=night?List.of(new RewardProbability(0,5),new RewardProbability(2,20),new RewardProbability(3,35),new RewardProbability(4,40)):
            List.of(new RewardProbability(0,5),new RewardProbability(1,15),new RewardProbability(2,35),new RewardProbability(3,45));
        return jdbc.query("SELECT * FROM location ORDER BY location_id",(r,n)->new LocationCard(r.getLong("location_id"),
            r.getString("name"),r.getString("description"),candidates(r.getLong("location_id")),hours,cost,probabilities,GameRules.CLEANER_PROBABILITY_PERCENT,List.of(new RewardProbability(1,70),new RewardProbability(2,30)),reason==null,reason,
            r.getLong("location_id")==6?1:0,10,consecutive,exposure,5*exposure,increase,
            g.diseased()?0:GameRules.diseaseProbability(Math.min(100,g.contamination()+increase)),null));
    }
    List<Candidate> candidates(long location) {
        return jdbc.query("SELECT m.material_id,m.name FROM location_material lm JOIN material m ON m.material_id=lm.material_id WHERE lm.location_id=? ORDER BY m.material_id",
            (r,n)->new Candidate(r.getLong(1),r.getString(2),1),location);
    }
    void add(UUID id,long material,int quantity,int hour) {
        if(quantity<1||quantity>GameRules.STACK_LIMIT)throw new IllegalArgumentException("batch quantity must be 1..6");
        jdbc.update("INSERT INTO item_batch(game_id,material_id,quantity,storage_type,acquired_hour,item_condition) VALUES (?,?,?,'INVENTORY',?,'DIRTY')",
            id.toString(),material,quantity,hour);
    }
    void progress(UUID id,int hours,int staminaDelta,int cleanerDelta) {
        jdbc.update("UPDATE game SET elapsed_hours=elapsed_hours+?,stamina=LEAST(?,stamina+?),cleaner_amount=LEAST(?,cleaner_amount+?) WHERE game_id=?",
            hours,GameRules.MAX_STAMINA,staminaDelta,GameRules.CLEANER_CAPACITY,cleanerDelta,id.toString());
    }
    void exploration(UUID id,boolean night,int generated,int collected,int medicalAdded) {
        jdbc.update("""
            UPDATE game SET explorations=explorations+1,day_explorations=day_explorations+?,night_explorations=night_explorations+?,
            empty_explorations=empty_explorations+?,collected=collected+?,medical_kit_amount=medical_kit_amount+?,
            consecutive_explorations=consecutive_explorations+1 WHERE game_id=?
            """,night?0:1,night?1:0,generated==0?1:0,collected,medicalAdded,id.toString());
    }
    void contaminate(UUID id,int increase,int exposure,boolean newlyDiseased,int finishedHour) {
        jdbc.update("""
            UPDATE game SET max_contamination=GREATEST(max_contamination,LEAST(100,contamination+?)),
            contamination=LEAST(100,contamination+?),storm_exposure_hours=storm_exposure_hours+?,
            diseased=IF(?,TRUE,diseased),disease_started_hour=IF(?,?,disease_started_hour),disease_count=disease_count+?
            WHERE game_id=?
            """,increase,increase,exposure,newlyDiseased,newlyDiseased,finishedHour,newlyDiseased?1:0,id.toString());
    }
    void restHealth(UUID id,int hours) {
        jdbc.update("UPDATE game SET contamination=GREATEST(0,contamination-?),consecutive_explorations=IF(?,0,consecutive_explorations) WHERE game_id=?",GameRules.restDecontamination(hours),hours>=4,id.toString());
    }
    void treat(Game g) {
        jdbc.update("""
            UPDATE game SET medical_kit_amount=medical_kit_amount-1,contamination=GREATEST(0,contamination-60),
            diseased=FALSE,disease_started_hour=NULL,treatment_count=treatment_count+?,medical_kits_used=medical_kits_used+1 WHERE game_id=?
            """,g.diseased()?1:0,g.id().toString());
    }
    void move(UUID id,long batchId,Storage to) { jdbc.update("UPDATE item_batch SET storage_type=? WHERE game_id=? AND batch_id=?",to.name(),id.toString(),batchId); }
    void increase(UUID id,long batch,int amount) { jdbc.update("UPDATE item_batch SET quantity=quantity+? WHERE game_id=? AND batch_id=?",amount,id.toString(),batch); }
    void setQuantity(UUID id,long batch,int quantity) { jdbc.update("UPDATE item_batch SET quantity=? WHERE game_id=? AND batch_id=?",quantity,id.toString(),batch); }
    void clean(UUID id,Item batch) {
        jdbc.update("UPDATE item_batch SET item_condition='CLEAN' WHERE game_id=? AND batch_id=?",id.toString(),batch.batchId());
        jdbc.update("UPDATE game SET cleaned=cleaned+? WHERE game_id=?",batch.quantity(),id.toString());
    }
    void mergeClean(Game g,long material,Storage storage) {
        var matches=items(g).stream().filter(b->b.materialId()==material&&b.storage()==storage&&b.condition()==Condition.CLEAN).toList();
        int remaining=matches.stream().mapToInt(Item::quantity).sum();
        // Repack existing rows only; cleaning never needs a new slot.
        for(var batch:matches) {
            if(remaining==0)delete(g.id(),batch.batchId());
            else {
                int quantity=Math.min(GameRules.STACK_LIMIT,remaining);
                jdbc.update("UPDATE item_batch SET quantity=? WHERE game_id=? AND batch_id=?",quantity,g.id().toString(),batch.batchId());
                remaining-=quantity;
            }
        }
    }
    void delete(UUID id,long batchId) { jdbc.update("DELETE FROM item_batch WHERE game_id=? AND batch_id=?",id.toString(),batchId); }
    void discarded(UUID id,int quantity) { jdbc.update("UPDATE game SET discarded=discarded+? WHERE game_id=?",quantity,id.toString()); }
    void consume(UUID id,Item batch,int quantity) {
        if(quantity==batch.quantity())delete(id,batch.batchId());
        else jdbc.update("UPDATE item_batch SET quantity=quantity-? WHERE game_id=? AND batch_id=?",quantity,id.toString(),batch.batchId());
    }
    void repair(UUID id,long part) { jdbc.update("UPDATE game_repair SET repaired=TRUE WHERE game_id=? AND part_id=?",id.toString(),part); }
    void finish(UUID id) {
        Game g=game(id,false);
        jdbc.update("UPDATE item_batch SET item_condition='UNUSABLE' WHERE game_id=? AND item_condition='DIRTY' AND acquired_hour<=?",id.toString(),g.elapsed()-48);
        String failure=g.elapsed()>=GameRules.DEADLINE?"TIME_LIMIT":
            g.diseased()&&g.diseaseStarted()!=null&&g.elapsed()-g.diseaseStarted()>=72?"UNTREATED_DISEASE":null;
        if(failure!=null)jdbc.update("UPDATE game SET status='FAILED',failure_reason=? WHERE game_id=?",failure,id.toString());
    }
    void escape(UUID id) { jdbc.update("UPDATE game SET status='SUCCESS' WHERE game_id=?",id.toString()); }
}
