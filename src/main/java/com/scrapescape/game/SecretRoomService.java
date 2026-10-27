package com.scrapescape.game;

import org.springframework.stereotype.Service;
import java.util.*;
import java.util.random.RandomGenerator;
import static com.scrapescape.game.GameModels.*;

/** Called inside GameService's transaction after locking the game row. */
@Service
public class SecretRoomService {
    private final GameRepository games;
    private final SecretRoomRepository rooms;
    private final RandomGenerator random;
    public SecretRoomService(GameRepository games,SecretRoomRepository rooms,RandomGenerator random){this.games=games;this.rooms=rooms;this.random=random;}

    SecretFindings discover(Game game,long location) {
        if(game.status()!=Status.RUNNING)return new SecretFindings(false,0,false,null);
        boolean keyFound=random.nextInt(100)<10;int keysAdded=0;
        if(keyFound&&rooms.keys(game.id()).amount()<2){rooms.addKey(game.id());keysAdded=1;}
        int roomRoll=random.nextInt(100);
        boolean discovered=roomRoll<15&&!rooms.discovered(game.id(),location);
        if(discovered)rooms.find(game.id(),location,game.elapsed());
        return new SecretFindings(keyFound,keysAdded,discovered,discovered?location:null);
    }
    SecretState state(Game game) {
        var keys=rooms.keys(game.id());var rows=rooms.rooms(game.id());var cards=new ArrayList<SecretRoomCard>();
        int opened=0,good=0,bad=0,collected=0,lost=0;
        for(var row:rows) {
            cards.add(card(game,row,keys.amount()));
            if(row.openedHour()!=null){opened++;if(row.outcome()==RoomOutcome.MATERIAL_LOSS||row.outcome()==RoomOutcome.CONTAMINATION)bad++;else good++;}
            collected+=row.collected();lost+=row.lost();
        }
        return new SecretState(keys.amount(),2,10,15,cards,new SecretStatistics(rows.size(),opened,rows.size()-opened,keys.acquired(),keys.used(),good,bad,collected,lost));
    }
    SecretRoomCard card(Game game,long location){return card(game,rooms.room(game.id(),location),rooms.keys(game.id()).amount());}
    private SecretRoomCard card(Game game,SecretRoomRepository.Room row,int keys) {
        SecretRoomOutcome history=null;
        if(row.openedHour()!=null) {
            List<MaterialLoot> materials=row.materialId()==null?List.of():List.of(new MaterialLoot(row.materialId(),
                games.candidates(row.locationId()).stream().filter(m->m.materialId()==row.materialId()).map(Candidate::name).findFirst().orElse("재료"),row.materialQuantity(),row.collected()));
            var loot=new Loot(row.materialQuantity(),row.collected(),row.materialQuantity()-row.collected(),materials,row.outcome()==RoomOutcome.CLEANER,row.cleanerAdded(),row.medicalAdded());
            history=new SecretRoomOutcome(row.locationId(),row.mode(),row.outcome(),row.durationHours(),row.staminaCost(),row.contaminationIncrease(),row.stormExposureHours(),loot,row.lost());
        }
        return new SecretRoomCard(row.locationId(),row.name(),"?",row.discoveredHour(),row.openedHour()!=null,
            row.immediateAvailable()&&row.openedHour()==null,preview(game,row,RoomMode.IMMEDIATE,keys),preview(game,row,RoomMode.REVISIT,keys),row.openedHour(),history);
    }
    private RoomVisitPreview preview(Game game,SecretRoomRepository.Room row,RoomMode mode,int keys) {
        boolean immediate=mode==RoomMode.IMMEDIATE;
        int hours=immediate?1:GameRules.duration(game)-2,cost=immediate?1:GameRules.staminaCost(game);
        int exposure=games.exposure(game.id(),game.elapsed(),game.elapsed()+hours);
        int base=immediate?0:10+(game.consecutive()>=2?5:0),increase=base+exposure*5;
        String reason=game.rulesVersion()!=2?"이전 규칙의 게임입니다. 새 게임을 시작하세요.":game.status()!=Status.RUNNING?"종료된 게임입니다.":
            row.openedHour()!=null?"이미 개방한 비밀의 방입니다.":immediate&&!row.immediateAvailable()?"즉시 입장 기회가 끝났습니다. 기억한 방으로 다시 방문하세요.":
            keys<1?"비밀의 열쇠가 없습니다.":game.stamina()<cost?"입장할 체력이 부족합니다.":null;
        boolean deadlineRisk=game.elapsed()+hours>=GameRules.DEADLINE||game.diseased()&&game.diseaseStarted()!=null&&game.elapsed()+hours-game.diseaseStarted()>=72;
        return new RoomVisitPreview(reason==null,reason,hours,cost,1,base,exposure,exposure*5,increase,15,
            Math.max(0,GameRules.INVENTORY_CAPACITY-games.slots(game.id(),Storage.INVENTORY)),deadlineRisk);
    }
    void defer(Game game,long location) {
        var row=rooms.room(game.id(),location);
        if(row.openedHour()!=null)throw GameException.conflict("ROOM_ALREADY_OPENED","이미 개방한 비밀의 방입니다.");
        rooms.defer(game.id(),location);
    }
    SecretRoomOutcome open(Game game,long location,RoomMode mode) {
        var room=rooms.room(game.id(),location);
        var preview=preview(game,room,mode,rooms.keys(game.id()).amount());
        if(!preview.allowed()) {
            String code=room.openedHour()!=null?"ROOM_ALREADY_OPENED":mode==RoomMode.IMMEDIATE&&!room.immediateAvailable()?"IMMEDIATE_UNAVAILABLE":
                rooms.keys(game.id()).amount()<1?"INSUFFICIENT_SECRET_KEY":"INSUFFICIENT_STAMINA";
            throw GameException.conflict(code,preview.unavailableReason());
        }
        // Capture loss candidates at entry, before this visit expires old dirty batches.
        var dirty=games.items(game).stream().filter(i->i.storage()==Storage.INVENTORY&&i.condition()==Condition.DIRTY).toList();
        rooms.invalidateImmediate(game.id());rooms.consumeKey(game.id());
        int roll=random.nextInt(100),total=0,collected=0,lost=0,cleaner=0,medical=0,extraContamination=0;
        List<MaterialLoot> materials=List.of();RoomOutcome outcome;
        if(roll<50) {
            outcome=RoomOutcome.MATERIALS;var candidates=games.candidates(location);
            var material=candidates.get(random.nextInt(candidates.size()));total=3+random.nextInt(2);
            collected=preview.rewardFreeSlots()>0?total:0;
            if(collected>0)games.add(game.id(),material.materialId(),collected,game.elapsed()+preview.durationHours());
            materials=List.of(new MaterialLoot(material.materialId(),material.name(),total,collected));
        } else if(roll<80) {
            if(random.nextInt(2)==0){outcome=RoomOutcome.CLEANER;cleaner=Math.min(2,GameRules.CLEANER_CAPACITY-game.cleaner());}
            else {outcome=RoomOutcome.MEDICAL_KIT;medical=game.medicalKits()<2?1:0;}
        } else if(roll<90&&!dirty.isEmpty()) {
            outcome=RoomOutcome.MATERIAL_LOSS;int remaining=Math.min(1+random.nextInt(2),dirty.stream().mapToInt(Item::quantity).sum());
            int first=random.nextInt(dirty.size());
            for(int i=0;i<dirty.size()&&remaining>0;i++) {
                var batch=dirty.get((first+i)%dirty.size());int amount=Math.min(remaining,batch.quantity());
                games.consume(game.id(),batch,amount);remaining-=amount;lost+=amount;
            }
        } else {outcome=RoomOutcome.CONTAMINATION;extraContamination=15;}
        rooms.rewards(game.id(),collected,cleaner,medical);
        games.progress(game.id(),preview.durationHours(),-preview.staminaCost(),0);
        if(mode==RoomMode.REVISIT)rooms.revisit(game.id());
        int increase=preview.expectedContaminationIncrease()+extraContamination;
        boolean newDisease=!game.diseased()&&random.nextInt(100)<GameRules.diseaseProbability(Math.min(100,game.contamination()+increase));
        games.contaminate(game.id(),increase,preview.stormExposureHours(),newDisease,game.elapsed()+preview.durationHours());
        var loot=new Loot(total,collected,total-collected,materials,outcome==RoomOutcome.CLEANER,cleaner,medical);
        var result=new SecretRoomOutcome(location,mode,outcome,preview.durationHours(),preview.staminaCost(),increase,preview.stormExposureHours(),loot,lost);
        rooms.opened(game.id(),result,game.elapsed()+preview.durationHours());games.finish(game.id());
        return result;
    }
}
