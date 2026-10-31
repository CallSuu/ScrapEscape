package com.scrapescape.game;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import java.util.*;
import java.util.random.RandomGenerator;
import static com.scrapescape.game.GameModels.*;

@Service
public class GameService {
    private final GameRepository repo;
    private final RandomGenerator random;
    private final SecretRoomService secrets;
    private final SecretRoomRepository secretRepo;
    public GameService(GameRepository repo,RandomGenerator random,SecretRoomService secrets,SecretRoomRepository secretRepo) {
        this.repo=repo;this.random=random;this.secrets=secrets;this.secretRepo=secretRepo;
    }

    @Transactional public State create() {
        UUID id=UUID.randomUUID();var parts=new ArrayList<>(repo.partIds());
        if(parts.size()!=7)throw new IllegalStateException("부품 초기 데이터가 올바르지 않습니다.");
        for(int i=parts.size()-1;i>0;i--)Collections.swap(parts,i,random.nextInt(i+1));
        var storms=new ArrayList<Storm>();
        // Start-to-start intervals are 24..48h; 1..3h durations cannot overlap.
        for(int start=24+random.nextInt(25);start<GameRules.DEADLINE;start+=24+random.nextInt(25))
            storms.add(new Storm(start,start+1+random.nextInt(3)));
        repo.create(id,parts.subList(0,4),storms);return state(id);
    }
    @Transactional(readOnly=true) public State state(UUID id) {
        Game g=repo.game(id,false);var items=repo.items(g);var repairs=repo.repairs(g,items);
        int repaired=(int)repairs.stream().filter(Repair::repaired).count();
        var storms=repo.storms(id);
        Storm current=storms.stream().filter(s->s.startHour()<=g.elapsed()&&g.elapsed()<s.endHour()).findFirst().orElse(null);
        Storm next=storms.stream().filter(s->s.startHour()>g.elapsed()).findFirst().orElse(null);
        String common=g.rulesVersion()!=2?"이전 규칙의 게임입니다. 새 게임을 시작하세요.":g.status()!=Status.RUNNING?"종료된 게임입니다.":null;
        String treatmentReason=common!=null?common:g.medicalKits()==0?"의료 키트가 없습니다.":null;
        String escapeReason=common!=null?common:repaired!=repairs.size()||repairs.size()!=4?"모든 부품을 먼저 수리하세요.":null;
        int deadline=g.rulesVersion()==1?168:GameRules.DEADLINE;
        return new State(id,g.status(),g.elapsed(),(8+g.elapsed())/24+1,(8+g.elapsed())%24,Math.max(0,deadline-g.elapsed()),
            GameRules.isNight(g.elapsed()),g.stamina(),GameRules.MAX_STAMINA,g.cleaner(),GameRules.CLEANER_CAPACITY,g.rulesVersion()==1?3:GameRules.INVENTORY_CAPACITY,
            g.rulesVersion()==1?8:GameRules.CHEST_CAPACITY,items.stream().filter(i->i.storage()==Storage.INVENTORY).toList(),
            items.stream().filter(i->i.storage()==Storage.CHEST).toList(),repairs,
            new Statistics(g.explorations(),g.dayExplorations(),g.nightExplorations(),g.emptyExplorations(),g.collected(),
                g.cleaned(),g.discarded(),repaired,g.diseaseCount(),g.treatmentCount(),g.medicalKitsUsed(),g.maxContamination(),g.stormExposure()),
            GameRules.STACK_LIMIT,g.medicalKits(),2,g.contamination(),g.diseased(),
            g.diseased()&&g.diseaseStarted()!=null?Math.max(0,72-(g.elapsed()-g.diseaseStarted())):null,
            g.consecutive(),current,next,new ActionAvailability(treatmentReason==null,treatmentReason,1,0),
            new ActionAvailability(escapeReason==null,escapeReason,0,0),g.failureReason(),g.rulesVersion(),secrets.state(g));
    }
    @Transactional(readOnly=true) public GameScreen screen(UUID id) {
        State snapshot=state(id);
        return new GameScreen(snapshot,locations(id),GamePreviews.actions(snapshot));
    }
    @Transactional(readOnly=true) public List<LocationCard> locations(UUID id) {
        Game game=repo.game(id,false);var rooms=secrets.state(game).rooms();
        return repo.cards(game).stream().map(card->card.withSecretRoom(rooms.stream().filter(r->r.locationId()==card.locationId()).findFirst().orElse(null))).toList();
    }
    @Transactional(readOnly=true) public List<SecretRoomCard> secretRooms(UUID id) { return secrets.state(repo.game(id,false)).rooms(); }
    @Transactional(readOnly=true) public SecretRoomCard secretRoom(UUID id,long location) { return secrets.card(repo.game(id,false),location); }
    @Transactional public ActionResult deferRoom(UUID id,long location) {
        Game game=running(id);secrets.defer(game,location);
        return result(id,"비밀의 방 위치를 기억했습니다. 나중에 다시 방문할 수 있습니다.",null);
    }
    @Transactional public ActionResult openRoom(UUID id,long location,RoomMode mode) {
        if(mode==null)throw invalid("방 방문 방식을 선택하세요.");
        Game game=lockRunning(id);var outcome=secrets.open(game,location,mode);
        String message=switch(outcome.outcome()) {
            case MATERIALS -> "비밀의 방에서 재료 "+outcome.loot().collectedQuantity()+"개를 가져왔습니다.";
            case CLEANER -> "세척액을 발견해 "+outcome.loot().cleanerAdded()+"회분을 충전했습니다.";
            case MEDICAL_KIT -> "의료 키트를 발견해 "+outcome.loot().medicalKitsAdded()+"개를 보관했습니다.";
            case MATERIAL_LOSS -> "위험을 피하다 미세척 재료 "+outcome.lostQuantity()+"개를 잃었습니다.";
            case CONTAMINATION -> "오염된 공간에 노출되어 오염도가 추가로 15 증가했습니다.";
        };
        if(outcome.loot().leftBehindQuantity()>0)message+=" 공간이 부족해 "+outcome.loot().leftBehindQuantity()+"개는 두고 왔습니다.";
        return result(id,message,outcome.loot(),null,outcome);
    }

    @Transactional public ActionResult explore(UUID id,long location) {
        Game g=running(id);var candidates=repo.candidates(location);
        if(candidates.isEmpty())throw GameException.missing("탐색 장소를 찾을 수 없습니다.");
        int free=GameRules.INVENTORY_CAPACITY-repo.slots(id,Storage.INVENTORY);
        if(free<=0)throw GameException.conflict("INVENTORY_FULL","인벤토리를 상자에 정리한 뒤 탐색하세요.");
        boolean night=GameRules.isNight(g.elapsed());int hours=GameRules.duration(g),cost=GameRules.staminaCost(g);
        requireStamina(g,cost);
        int total=GameRules.loot(night,random.nextInt(100));
        int firstIndex=random.nextInt(candidates.size());var first=candidates.get(firstIndex);
        var generated=new LinkedHashMap<Candidate,Integer>();
        if(total==1)generated.put(first,1);
        else if(total>=2) {
            int secondIndex=random.nextInt(candidates.size()-1);if(secondIndex>=firstIndex)secondIndex++;
            int firstQuantity=1+random.nextInt(total-1);
            generated.put(first,firstQuantity);generated.put(candidates.get(secondIndex),total-firstQuantity);
        }
        boolean cleanerFound=true;int cleanerAdded=Math.min(GameRules.cleanerReward(random.nextInt(100)),GameRules.CLEANER_CAPACITY-g.cleaner());
        var found=new ArrayList<MaterialLoot>();int collected=0;
        for(var entry:generated.entrySet()) {
            int accepted=free>0?entry.getValue():0;
            if(accepted>0){repo.add(id,entry.getKey().materialId(),accepted,g.elapsed()+hours);free--;collected+=accepted;}
            found.add(new MaterialLoot(entry.getKey().materialId(),entry.getKey().name(),entry.getValue(),accepted));
        }
        int medicalAdded=location==6&&g.medicalKits()<2?1:0;
        repo.progress(id,hours,-cost,cleanerAdded);repo.exploration(id,night,total,collected,medicalAdded);
        int exposure=repo.exposure(id,g.elapsed(),g.elapsed()+hours);
        int increase=10+(g.consecutive()>=2?5:0)+exposure*5;
        boolean newlyDiseased=!g.diseased()&&random.nextInt(100)<GameRules.diseaseProbability(Math.min(100,g.contamination()+increase));
        repo.contaminate(id,increase,exposure,newlyDiseased,g.elapsed()+hours);repo.finish(id);
        String message=total==0?"구석까지 뒤졌지만 쓸 만한 부품은 없었습니다.":"재료 "+collected+"개를 가져왔습니다.";
        if(total>collected)message+=" 공간이 부족해 "+(total-collected)+"개는 현장에 두고 왔습니다.";
        message+=cleanerAdded>0?" 세척액 "+cleanerAdded+"회분도 얻었습니다.":" 세척액 통이 가득 찼습니다.";
        if(location==6)message+=medicalAdded==1?" 의료 키트 1개를 얻었습니다.":" 의료 키트 보유량이 가득 찼습니다.";
        if(exposure>0)message+=" 폭풍에 "+exposure+"시간 노출되어 오염도가 추가로 "+(exposure*5)+" 증가했습니다.";
        if(newlyDiseased)message+=" 질병이 발생했습니다. 72시간 안에 치료하세요.";
        var discoveries=secrets.discover(repo.game(id,false),location);
        if(discoveries.keyFound())message+=discoveries.keysAdded()>0?" 비밀의 열쇠 1개를 얻었습니다.":" 열쇠를 발견했지만 최대 보유량입니다.";
        if(discoveries.roomDiscovered())message+=" 비밀의 방을 발견하고 위치를 기억했습니다. 입장은 선택할 수 있습니다.";
        return result(id,message,new Loot(total,collected,total-collected,found,cleanerFound,cleanerAdded,medicalAdded),discoveries,null);
    }
    @Transactional public ActionResult move(UUID id,long batchId,Storage to) {
        if(to==null)throw invalid("보관 위치를 선택하세요.");
        Game g=running(id);Item batch=repo.item(g,batchId);
        if(batch.storage()==to)throw GameException.conflict("SAME_STORAGE","이미 해당 위치에 보관 중입니다.");
        var items=repo.items(g);
        int headroom=batch.condition()==Condition.CLEAN?items.stream()
            .filter(b->b.storage()==to&&b.condition()==Condition.CLEAN&&b.materialId()==batch.materialId())
            .mapToInt(b->GameRules.STACK_LIMIT-b.quantity()).sum():0;
        int free=GameRules.capacity(to)-repo.slots(id,to);
        if(headroom+Math.max(0,free)*GameRules.STACK_LIMIT<batch.quantity())
            throw GameException.conflict("STORAGE_FULL","이동할 보관 공간이 부족합니다.");
        if(batch.condition()!=Condition.CLEAN)repo.move(id,batchId,to);
        else {
            int remaining=batch.quantity();
            for(var target:items) {
                if(remaining==0)break;
                if(target.storage()!=to||target.condition()!=Condition.CLEAN||target.materialId()!=batch.materialId())continue;
                int amount=Math.min(remaining,GameRules.STACK_LIMIT-target.quantity());
                if(amount>0){repo.increase(id,target.batchId(),amount);remaining-=amount;}
            }
            if(remaining==0)repo.delete(id,batchId);
            else {repo.setQuantity(id,batchId,remaining);repo.move(id,batchId,to);}
            repo.mergeClean(g,batch.materialId(),to);
        }
        return result(id,"재료의 보관 위치를 변경했습니다.",null);
    }
    @Transactional public ActionResult moveMany(UUID id,List<Long> batchIds,Storage to) {
        if(to==null||batchIds==null||batchIds.isEmpty()||batchIds.size()>20||batchIds.stream().anyMatch(b->b==null||b<=0)
            ||new HashSet<>(batchIds).size()!=batchIds.size())throw invalid("중복 없는 재료 묶음 1~20개와 보관 위치를 선택하세요.");
        Game g=running(id);
        for(long batchId:batchIds)if(repo.item(g,batchId).storage()==to)
            throw GameException.conflict("SAME_STORAGE","이미 해당 위치에 보관 중인 재료가 포함되어 있습니다.");
        // One outer transaction rolls back all prior moves if any later move cannot fit.
        for(long batchId:batchIds)move(id,batchId,to);
        return result(id,"선택한 재료 "+batchIds.size()+"묶음을 이동했습니다.",null);
    }
    private List<Item> selectedBatches(Game game,List<Long> batchIds) {
        if(batchIds==null||batchIds.isEmpty()||batchIds.size()>20||batchIds.stream().anyMatch(b->b==null||b<=0)
            ||new HashSet<>(batchIds).size()!=batchIds.size())throw invalid("중복 없는 재료 묶음 1~20개를 선택하세요.");
        return batchIds.stream().map(b->repo.item(game,b)).toList();
    }
    @Transactional public ActionResult cleanMany(UUID id,List<Long> batchIds) {
        Game g=running(id);var batches=selectedBatches(g,batchIds);
        int cleanerCost=batches.stream().mapToInt(Item::quantity).sum();
        if(g.cleaner()<cleanerCost)throw GameException.conflict("INSUFFICIENT_CLEANER","세척액이 부족합니다.");
        for(var batch:batches)if(batch.condition()!=Condition.DIRTY||g.elapsed()-batch.acquiredHour()>=48)
            throw GameException.conflict("NOT_CLEANABLE","세척할 수 없는 재료가 포함되어 있습니다.");
        // Clean every selected row before merging or advancing time, which may remove rows.
        for(var batch:batches)repo.clean(id,batch);
        for(var batch:batches)repo.mergeClean(g,batch.materialId(),batch.storage());
        repo.progress(id,batches.size(),0,-cleanerCost);repo.finish(id);
        return result(id,"선택한 재료 "+batches.size()+"묶음을 세척했습니다.",null);
    }
    @Transactional public ActionResult discardMany(UUID id,List<Long> batchIds) {
        Game g=running(id);var batches=selectedBatches(g,batchIds);
        for(var batch:batches){repo.delete(id,batch.batchId());repo.discarded(id,batch.quantity());}
        return result(id,"선택한 재료 "+batches.size()+"묶음을 폐기했습니다.",null);
    }
    @Transactional public ActionResult clean(UUID id,long batchId) {
        Game g=running(id);Item batch=repo.item(g,batchId);
        if(batch.condition()!=Condition.DIRTY||g.elapsed()-batch.acquiredHour()>=48)
            throw GameException.conflict("NOT_CLEANABLE","이 재료는 세척할 수 없습니다.");
        if(g.cleaner()<batch.quantity())throw GameException.conflict("INSUFFICIENT_CLEANER","세척액이 부족합니다.");
        repo.clean(id,batch);repo.mergeClean(g,batch.materialId(),batch.storage());
        repo.progress(id,1,0,-batch.quantity());repo.finish(id);
        return result(id,batch.name()+" "+batch.quantity()+"개를 세척했습니다.",null);
    }
    @Transactional public ActionResult discard(UUID id,long batchId) {
        Game g=running(id);Item batch=repo.item(g,batchId);repo.delete(id,batchId);repo.discarded(id,batch.quantity());
        return result(id,"재료를 폐기하여 빈칸을 확보했습니다.",null);
    }
    @Transactional public ActionResult craft(UUID id,long partId) {
        Game g=running(id);var items=repo.items(g);
        Repair repair=repo.repairs(g,items).stream().filter(p->p.partId()==partId).findFirst()
            .orElseThrow(()->GameException.missing("이 게임의 수리 대상 부품이 아닙니다."));
        if(repair.repaired())throw GameException.conflict("ALREADY_REPAIRED","이미 수리한 부품입니다.");
        if(!repair.canCraft())throw GameException.conflict("INSUFFICIENT_MATERIALS","세척된 재료가 부족합니다.");
        for(var ingredient:repair.ingredients()) {
            int remaining=ingredient.requiredQuantity();
            for(var batch:items) {
                if(remaining==0)break;
                if(batch.materialId()!=ingredient.materialId()||batch.condition()!=Condition.CLEAN)continue;
                int amount=Math.min(remaining,batch.quantity());repo.consume(id,batch,amount);remaining-=amount;
            }
        }
        repo.repair(id,partId);repo.progress(id,3,0,0);repo.finish(id);
        return result(id,repair.name()+" 제작과 장착을 완료했습니다.",null);
    }
    @Transactional public ActionResult rest(UUID id,int hours) {
        if(hours<1||hours>8)throw invalid("휴식 시간은 1~8시간이어야 합니다.");
        running(id);repo.progress(id,hours,GameRules.restRecovery(hours),0);repo.restHealth(id,hours);repo.finish(id);
        return result(id,hours+"시간 휴식했습니다.",null);
    }
    @Transactional public ActionResult treat(UUID id) {
        Game g=running(id);if(g.medicalKits()==0)throw GameException.conflict("INSUFFICIENT_MEDICAL_KIT","의료 키트가 없습니다.");
        repo.progress(id,1,0,0);repo.finish(id);
        // A treatment finishing at or after the deadline cannot undo failure.
        if(repo.game(id,false).status()==Status.RUNNING)repo.treat(g);
        return result(id,"의료 키트 사용을 시도했습니다.",null);
    }
    @Transactional public ActionResult escape(UUID id) {
        Game g=running(id);repo.finish(id);
        if(repo.game(id,false).status()==Status.FAILED)return result(id,"탈출 기한에 도달했습니다.",null);
        var repairs=repo.repairs(g,repo.items(g));
        if(repairs.size()!=4||repairs.stream().anyMatch(p->!p.repaired()))throw GameException.conflict("REPAIRS_INCOMPLETE","모든 부품을 먼저 수리하세요.");
        repo.escape(id);return result(id,"비행기를 타고 도시를 탈출했습니다!",null);
    }
    private Game running(UUID id) {
        Game game=lockRunning(id);secretRepo.invalidateImmediate(id);return game;
    }
    private Game lockRunning(UUID id) {
        Game g=repo.game(id,true);
        if(g.rulesVersion()!=2)throw GameException.conflict("LEGACY_GAME","이전 규칙의 게임은 조회만 가능합니다. 새 게임을 시작하세요.");
        if(g.status()!=Status.RUNNING)throw GameException.conflict("GAME_ENDED","종료된 게임에서는 행동할 수 없습니다.");
        return g;
    }
    private void requireStamina(Game g,int cost) { if(g.stamina()<cost)throw GameException.conflict("INSUFFICIENT_STAMINA","행동할 체력이 부족합니다."); }
    private GameException invalid(String message) { return new GameException(HttpStatus.BAD_REQUEST,"INVALID_REQUEST",message); }
    private ActionResult result(UUID id,String message,Loot loot) { return result(id,message,loot,null,null); }
    private ActionResult result(UUID id,String message,Loot loot,SecretFindings discoveries,SecretRoomOutcome room) {
        State state=state(id);
        if(state.status()==Status.FAILED)message+=" "+("UNTREATED_DISEASE".equals(state.failureReason())?"질병을 기한 내 치료하지 못했습니다.":"오염 노출 한계에 도달했습니다.");
        return new ActionResult(message,state,loot,discoveries,room);
    }
}
