package com.scrapescape.game;

import static com.scrapescape.game.GameModels.*;

final class GameRules {
    static final int MAX_STAMINA=15, CLEANER_CAPACITY=8, CLEANER_PROBABILITY_PERCENT=80, DEADLINE=240, INVENTORY_CAPACITY=4, CHEST_CAPACITY=16, STACK_LIMIT=6;
    private GameRules() {}
    static boolean isNight(int elapsed) { int hour=Math.floorMod(8+elapsed,24);return hour>=20||hour<6; }
    static int loot(boolean night,int roll) {
        if(roll<0||roll>=100) throw new IllegalArgumentException("roll must be 0..99");
        if(roll<5) return 0;
        if(night) return roll<25?2:roll<60?3:4;
        return roll<20?1:roll<55?2:3;
    }
    static int restRecovery(int hours) { return hours+(hours>=6?2:0); }
    static int capacity(Storage storage) { return storage==Storage.INVENTORY?INVENTORY_CAPACITY:CHEST_CAPACITY; }
    static int diseaseProbability(int contamination) { return contamination<60?0:contamination<80?30:contamination<90?40:60; }
    static int duration(Game g) { return (isNight(g.elapsed())?7:6)+(g.diseased()?2:0); }
    static int staminaCost(Game g) { return (isNight(g.elapsed())?3:2)+(g.diseased()?2:0); }
}
