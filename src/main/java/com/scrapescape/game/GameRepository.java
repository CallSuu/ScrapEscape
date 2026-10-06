package com.scrapescape.game;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;
import static com.scrapescape.game.GameModels.*;

@Repository
public class GameRepository {
    private final JdbcTemplate jdbc;
    public GameRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    Game game(UUID id, boolean lock) {
        var rows=jdbc.query("SELECT * FROM game WHERE game_id=?"+(lock?" FOR UPDATE":""),
            (r,n)->new Game(UUID.fromString(r.getString("game_id")),r.getInt("elapsed_hours"),r.getInt("stamina"),
                r.getInt("cleaner_amount"),Status.valueOf(r.getString("status")),r.getInt("explorations"),
                r.getInt("day_explorations"),r.getInt("night_explorations"),r.getInt("empty_explorations"),
                r.getInt("collected"),r.getInt("cleaned"),r.getInt("discarded")),id.toString());
        if(rows.isEmpty()) throw GameException.missing("게임을 찾을 수 없습니다.");
        return rows.get(0);
    }
    void create(UUID id,List<Long> broken) {
        jdbc.update("INSERT INTO game(game_id) VALUES (?)",id.toString());
        for(long part:broken) jdbc.update("INSERT INTO game_repair(game_id,part_id) VALUES (?,?)",id.toString(),part);
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
    int slots(UUID gameId,Storage storage) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM item_batch WHERE game_id=? AND storage_type=?",Integer.class,
            gameId.toString(),storage.name());
    }
    List<Repair> repairs(Game g,List<Item> items) {
        return jdbc.query("""
            SELECT gr.part_id,p.name,gr.repaired FROM game_repair gr JOIN part p ON p.part_id=gr.part_id
            WHERE gr.game_id=? ORDER BY gr.part_id
            """,(r,n)->{
                long partId=r.getLong("part_id"); boolean repaired=r.getBoolean("repaired");
                var ingredients=jdbc.query("""
                    SELECT r.material_id,m.name,r.required_quantity FROM recipe r
                    JOIN material m ON m.material_id=r.material_id WHERE r.part_id=? ORDER BY r.material_id
                    """,(ir,i)-> {
                        long materialId=ir.getLong("material_id");
                        int available=items.stream().filter(b->b.condition()==Condition.CLEAN&&b.materialId()==materialId)
                            .mapToInt(Item::quantity).sum();
                        return new Ingredient(materialId,ir.getString("name"),ir.getInt("required_quantity"),available);
                    },partId);
                boolean canCraft=g.status()==Status.RUNNING&&!repaired&&!ingredients.isEmpty()
                    &&ingredients.stream().allMatch(i->i.availableQuantity()>=i.requiredQuantity());
                return new Repair(partId,r.getString("name"),repaired,canCraft,ingredients);
            },g.id().toString());
    }
    List<LocationCard> cards(Game g) {
        boolean night=GameRules.isNight(g.elapsed());
        String reason=g.status()!=Status.RUNNING?"종료된 게임입니다.":slots(g.id(),Storage.INVENTORY)>=3?"인벤토리가 가득 찼습니다.":
            g.stamina()<(night?3:2)?"탐색할 체력이 부족합니다.":null;
        List<RewardProbability> probs=night?List.of(new RewardProbability(0,5),new RewardProbability(2,20),
            new RewardProbability(3,35),new RewardProbability(4,40)):List.of(new RewardProbability(0,5),
            new RewardProbability(1,15),new RewardProbability(2,35),new RewardProbability(3,45));
        return jdbc.query("SELECT * FROM location ORDER BY location_id",(r,n)->new LocationCard(r.getLong("location_id"),
            r.getString("name"),r.getString("description"),candidates(r.getLong("location_id")),night?7:6,night?3:2,
            probs,50,reason==null,reason));
    }
    List<Candidate> candidates(long location) {
        return jdbc.query("SELECT material_id,name FROM material WHERE location_id=? ORDER BY material_id",
            (r,n)->new Candidate(r.getLong(1),r.getString(2),50),location);
    }
    void add(UUID id,long material,int quantity,int hour) {
        jdbc.update("INSERT INTO item_batch(game_id,material_id,quantity,storage_type,acquired_hour,item_condition) VALUES (?,?,?,'INVENTORY',?,'DIRTY')",
            id.toString(),material,quantity,hour);
    }
    void progress(UUID id,int hours,int staminaDelta,int cleanerDelta) {
        jdbc.update("UPDATE game SET elapsed_hours=elapsed_hours+?,stamina=LEAST(10,stamina+?),cleaner_amount=LEAST(5,cleaner_amount+?) WHERE game_id=?",
            hours,staminaDelta,cleanerDelta,id.toString());
    }
    void exploration(UUID id,boolean night,int quantity) {
        jdbc.update("UPDATE game SET explorations=explorations+1,day_explorations=day_explorations+?,night_explorations=night_explorations+?,empty_explorations=empty_explorations+?,collected=collected+? WHERE game_id=?",
            night?0:1,night?1:0,quantity==0?1:0,quantity,id.toString());
    }
    void move(UUID id,long batchId,Storage to) {
        jdbc.update("UPDATE item_batch SET storage_type=? WHERE game_id=? AND batch_id=?",to.name(),id.toString(),batchId);
    }
    void clean(UUID id,Item batch) {
        jdbc.update("UPDATE item_batch SET item_condition='CLEAN' WHERE game_id=? AND batch_id=?",id.toString(),batch.batchId());
        jdbc.update("UPDATE game SET cleaned=cleaned+? WHERE game_id=?",batch.quantity(),id.toString());
    }
    void mergeClean(Game g,long material,Storage storage) {
        var matches=items(g).stream().filter(b->b.materialId()==material&&b.storage()==storage&&b.condition()==Condition.CLEAN).toList();
        if(matches.size()<2) return;
        long keep=matches.get(0).batchId();int total=matches.stream().mapToInt(Item::quantity).sum();
        jdbc.update("UPDATE item_batch SET quantity=? WHERE game_id=? AND batch_id=?",total,g.id().toString(),keep);
        for(var b:matches) if(b.batchId()!=keep) delete(g.id(),b.batchId());
    }
    void delete(UUID id,long batchId) { jdbc.update("DELETE FROM item_batch WHERE game_id=? AND batch_id=?",id.toString(),batchId); }
    void discarded(UUID id,int quantity) { jdbc.update("UPDATE game SET discarded=discarded+? WHERE game_id=?",quantity,id.toString()); }
    void consume(UUID id,Item batch,int quantity) {
        if(quantity==batch.quantity()) delete(id,batch.batchId());
        else jdbc.update("UPDATE item_batch SET quantity=quantity-? WHERE game_id=? AND batch_id=?",quantity,id.toString(),batch.batchId());
    }
    void repair(UUID id,long partId) {
        jdbc.update("UPDATE game_repair SET repaired=TRUE WHERE game_id=? AND part_id=?",id.toString(),partId);
    }
    void finish(UUID id) {
        Game g=game(id,false);
        jdbc.update("UPDATE item_batch SET item_condition='UNUSABLE' WHERE game_id=? AND item_condition='DIRTY' AND acquired_hour<=?",
            id.toString(),g.elapsed()-48);
        int remaining=jdbc.queryForObject("SELECT COUNT(*) FROM game_repair WHERE game_id=? AND repaired=FALSE",Integer.class,id.toString());
        Status status=g.elapsed()>=168?Status.FAILED:remaining==0?Status.SUCCESS:Status.RUNNING;
        jdbc.update("UPDATE game SET status=? WHERE game_id=?",status.name(),id.toString());
    }
}
