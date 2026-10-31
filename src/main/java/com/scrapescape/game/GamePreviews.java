package com.scrapescape.game;

import java.util.*;
import java.util.stream.*;
import static com.scrapescape.game.GameModels.*;

final class GamePreviews {
    private GamePreviews() {}
    private enum Action { REST, MOVE, CLEAN, DISCARD, REPAIR, TREAT, ESCAPE }
    static SafeActions actions(State s) {
        List<Item> items=Stream.concat(s.inventory().stream(),s.chest().stream()).toList();
        var rests=IntStream.rangeClosed(1,8).mapToObj(h->new RestPreview(h,preview(s,Action.REST,null,h,null))).toList();
        var batches=items.stream().map(b->{
            Storage to=b.storage()==Storage.INVENTORY?Storage.CHEST:Storage.INVENTORY;
            String clean=b.condition()!=Condition.DIRTY||b.expiresInHours()==null||b.expiresInHours()<=0?"NOT_CLEANABLE":
                s.cleanerAmount()<b.quantity()?"INSUFFICIENT_CLEANER":null;
            long occupied=items.stream().filter(i->i.storage()==to).count();
            int headroom=b.condition()==Condition.CLEAN?items.stream().filter(i->i.storage()==to&&i.condition()==Condition.CLEAN&&i.materialId()==b.materialId())
                .mapToInt(i->s.stackLimit()-i.quantity()).sum():0;
            long capacity=to==Storage.INVENTORY?s.inventoryCapacity():s.chestCapacity();
            String move=headroom+Math.max(0,capacity-occupied)*s.stackLimit()<b.quantity()?"STORAGE_FULL":null;
            return new ItemActionPreview(b.batchId(),to,preview(s,Action.MOVE,move,0,b),preview(s,Action.CLEAN,clean,1,b),preview(s,Action.DISCARD,null,0,b));
        }).toList();
        var repairs=s.repairs().stream().map(r->new RepairActionPreview(r.partId(),preview(s,Action.REPAIR,
            r.repaired()?"ALREADY_REPAIRED":!r.canCraft()?"INSUFFICIENT_MATERIALS":null,3,null))).toList();
        return new SafeActions(rests,batches,repairs,preview(s,Action.TREAT,s.medicalKitAmount()<1?"INSUFFICIENT_MEDICAL_KIT":null,1,null),
            preview(s,Action.ESCAPE,s.escape().allowed()?null:"REPAIRS_INCOMPLETE",0,null));
    }
    private static SafeActionPreview preview(State s,Action action,String code,int hours,Item selected) {
        code=s.rulesVersion()!=2?"LEGACY_GAME":s.status()!=Status.RUNNING?"GAME_ENDED":code;
        int cost=0;
        if(code!=null)return new SafeActionPreview(false,code,message(code),hours,cost,null);
        int end=s.elapsedHours()+hours,stamina=s.stamina()-cost,contamination=s.contamination(),cleaner=s.cleanerAmount(),kits=s.medicalKitAmount();
        boolean diseased=s.diseased();
        String failure=action==Action.MOVE||action==Action.DISCARD?null:end>=GameRules.DEADLINE?"TIME_LIMIT":
            s.treatmentRemainingHours()!=null&&hours>=s.treatmentRemainingHours()?"UNTREATED_DISEASE":null;
        if(action==Action.REST){stamina=Math.min(s.maxStamina(),stamina+GameRules.restRecovery(hours));contamination=Math.max(0,contamination-GameRules.restDecontamination(hours));}
        if(action==Action.CLEAN)cleaner-=selected.quantity();
        if(action==Action.TREAT&&failure==null){kits--;diseased=false;contamination=Math.max(0,contamination-60);}
        Status status=failure!=null?Status.FAILED:action==Action.ESCAPE?Status.SUCCESS:Status.RUNNING;
        List<Long> expiring=Stream.concat(s.inventory().stream(),s.chest().stream())
            .filter(b->b.condition()==Condition.DIRTY&&b.expiresInHours()!=null&&hours>=b.expiresInHours())
            .filter(b->selected==null||b.batchId()!=selected.batchId()||(action!=Action.CLEAN&&action!=Action.DISCARD))
            .map(Item::batchId).toList();
        return new SafeActionPreview(true,null,null,hours,cost,new ActionProjection(end,stamina,contamination,cleaner,kits,diseased,status,failure,expiring));
    }
    private static String message(String code) {
        return switch(code) {
            case "LEGACY_GAME" -> "이전 규칙의 게임입니다. 새 게임을 시작하세요.";
            case "GAME_ENDED" -> "종료된 게임에서는 행동할 수 없습니다.";
            case "NOT_CLEANABLE" -> "이 재료는 세척할 수 없습니다.";
            case "INSUFFICIENT_STAMINA" -> "행동할 체력이 부족합니다.";
            case "INSUFFICIENT_CLEANER" -> "세척액이 부족합니다.";
            case "STORAGE_FULL" -> "이동할 보관 공간이 부족합니다.";
            case "ALREADY_REPAIRED" -> "이미 수리한 부품입니다.";
            case "INSUFFICIENT_MATERIALS" -> "세척된 재료가 부족합니다.";
            case "INSUFFICIENT_MEDICAL_KIT" -> "의료 키트가 없습니다.";
            case "REPAIRS_INCOMPLETE" -> "모든 부품을 먼저 수리하세요.";
            default -> throw new IllegalArgumentException(code);
        };
    }
}
