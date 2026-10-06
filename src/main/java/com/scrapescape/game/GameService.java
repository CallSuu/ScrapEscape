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
    public GameService(GameRepository repo,RandomGenerator random) { this.repo=repo;this.random=random; }

    @Transactional
    public State create() {
        UUID id=UUID.randomUUID();var parts=new ArrayList<>(repo.partIds());
        if(parts.size()!=5) throw new IllegalStateException("부품 초기 데이터가 올바르지 않습니다.");
        // Fisher-Yates: 고장 부품을 중복 없이 선택한다.
        for(int i=parts.size()-1;i>0;i--) Collections.swap(parts,i,random.nextInt(i+1));
        repo.create(id,parts.subList(0,3));return state(id);
    }
    @Transactional(readOnly=true)
    public State state(UUID id) {
        Game g=repo.game(id,false);var items=repo.items(g);var repairs=repo.repairs(g,items);
        int repaired=(int)repairs.stream().filter(Repair::repaired).count();
        return new State(id,g.status(),g.elapsed(),(8+g.elapsed())/24+1,(8+g.elapsed())%24,
            Math.max(0,168-g.elapsed()),GameRules.isNight(g.elapsed()),g.stamina(),10,g.cleaner(),5,3,8,
            items.stream().filter(i->i.storage()==Storage.INVENTORY).toList(),
            items.stream().filter(i->i.storage()==Storage.CHEST).toList(),repairs,
            new Statistics(g.explorations(),g.dayExplorations(),g.nightExplorations(),g.emptyExplorations(),
                g.collected(),g.cleaned(),g.discarded(),repaired));
    }
    @Transactional(readOnly=true)
    public List<LocationCard> locations(UUID id) { return repo.cards(repo.game(id,false)); }

    @Transactional
    public ActionResult explore(UUID id,long location) {
        Game g=running(id);var candidates=repo.candidates(location);
        if(candidates.size()!=2) throw GameException.missing("탐색 장소를 찾을 수 없습니다.");
        if(repo.slots(id,Storage.INVENTORY)>=3) throw GameException.conflict("INVENTORY_FULL","인벤토리를 상자에 정리한 뒤 탐색하세요.");
        boolean night=GameRules.isNight(g.elapsed());int hours=night?7:6;int cost=night?3:2;
        requireStamina(g,cost);
        int quantity=GameRules.loot(night,random.nextInt(100));var material=candidates.get(random.nextInt(candidates.size()));
        boolean foundCleaner=random.nextInt(100)<50;int cleanerAdded=foundCleaner&&g.cleaner()<5?1:0;
        if(quantity>0) repo.add(id,material.materialId(),quantity,g.elapsed()+hours);
        repo.progress(id,hours,-cost,cleanerAdded);repo.exploration(id,night,quantity);
        repo.finish(id);
        String message=quantity==0?"구석까지 뒤졌지만 쓸 만한 부품은 없었습니다.":material.name()+" "+quantity+"개를 발견했습니다.";
        if(foundCleaner) message+=cleanerAdded==1?" 세척액 1회분도 얻었습니다.":" 세척액을 발견했지만 통이 가득 찼습니다.";
        return result(id,message,new Loot(quantity>0?material.materialId():null,quantity>0?material.name():null,quantity,foundCleaner,cleanerAdded));
    }
    @Transactional
    public ActionResult move(UUID id,long batchId,Storage to) {
        if(to==null) throw invalid("보관 위치를 선택하세요.");
        Game g=running(id);Item batch=repo.item(g,batchId);
        if(batch.storage()==to) throw GameException.conflict("SAME_STORAGE","이미 해당 위치에 보관 중입니다.");
        boolean canMerge=batch.condition()==Condition.CLEAN&&repo.items(g).stream()
            .anyMatch(b->b.condition()==Condition.CLEAN&&b.materialId()==batch.materialId()&&b.storage()==to);
        if(!canMerge&&repo.slots(id,to)>=(to==Storage.INVENTORY?3:8))
            throw GameException.conflict("STORAGE_FULL","이동할 보관 공간이 부족합니다.");
        repo.move(id,batchId,to);
        if(batch.condition()==Condition.CLEAN) repo.mergeClean(g,batch.materialId(),to);
        return result(id,"재료의 보관 위치를 변경했습니다.",null);
    }
    @Transactional
    public ActionResult clean(UUID id,long batchId) {
        Game g=running(id);Item batch=repo.item(g,batchId);
        if(batch.condition()!=Condition.DIRTY||g.elapsed()-batch.acquiredHour()>=48)
            throw GameException.conflict("NOT_CLEANABLE","이 재료는 세척할 수 없습니다.");
        requireStamina(g,1);
        if(g.cleaner()<1) throw GameException.conflict("INSUFFICIENT_CLEANER","세척액이 부족합니다.");
        // 시작 시 유효한 묶음은 세척 완료로 바꾼 후 시간을 진행해 보호한다.
        repo.clean(id,batch);repo.mergeClean(g,batch.materialId(),batch.storage());
        repo.progress(id,1,-1,-1);repo.finish(id);
        return result(id,batch.name()+" "+batch.quantity()+"개를 세척했습니다.",null);
    }
    @Transactional
    public ActionResult discard(UUID id,long batchId) {
        Game g=running(id);Item batch=repo.item(g,batchId);
        repo.delete(id,batchId);repo.discarded(id,batch.quantity());
        return result(id,"재료를 폐기하여 빈칸을 확보했습니다.",null);
    }
    @Transactional
    public ActionResult craft(UUID id,long partId) {
        Game g=running(id);var items=repo.items(g);
        Repair repair=repo.repairs(g,items).stream().filter(p->p.partId()==partId).findFirst()
            .orElseThrow(()->GameException.missing("이 게임의 수리 대상 부품이 아닙니다."));
        if(repair.repaired()) throw GameException.conflict("ALREADY_REPAIRED","이미 수리한 부품입니다.");
        if(!repair.canCraft()) throw GameException.conflict("INSUFFICIENT_MATERIALS","세척된 재료가 부족합니다.");
        for(var ingredient:repair.ingredients()) {
            int remaining=ingredient.requiredQuantity();
            for(var batch:items) {
                if(remaining==0) break;
                if(batch.materialId()!=ingredient.materialId()||batch.condition()!=Condition.CLEAN) continue;
                int amount=Math.min(remaining,batch.quantity());repo.consume(id,batch,amount);remaining-=amount;
            }
        }
        repo.repair(id,partId);repo.progress(id,3,0,0);repo.finish(id);
        return result(id,repair.name()+" 제작과 장착을 완료했습니다.",null);
    }
    @Transactional
    public ActionResult rest(UUID id,int hours) {
        if(hours<1||hours>8) throw invalid("휴식 시간은 1~8시간이어야 합니다.");
        Game g=running(id);repo.progress(id,hours,GameRules.restRecovery(hours),0);repo.finish(id);
        return result(id,hours+"시간 휴식했습니다.",null);
    }
    private Game running(UUID id) {
        Game g=repo.game(id,true);
        if(g.status()!=Status.RUNNING) throw GameException.conflict("GAME_ENDED","종료된 게임에서는 행동할 수 없습니다.");
        return g;
    }
    private void requireStamina(Game g,int cost) {
        if(g.stamina()<cost) throw GameException.conflict("INSUFFICIENT_STAMINA","행동할 체력이 부족합니다.");
    }
    private GameException invalid(String message) { return new GameException(HttpStatus.BAD_REQUEST,"INVALID_REQUEST",message); }
    private ActionResult result(UUID id,String message,Loot loot) {
        State state=state(id);
        if(state.status()==Status.SUCCESS) message+=" 비행기를 수리하고 탈출했습니다!";
        if(state.status()==Status.FAILED) message+=" 오염 노출 한계에 도달해 탈출에 실패했습니다.";
        return new ActionResult(message,state,loot);
    }
}
