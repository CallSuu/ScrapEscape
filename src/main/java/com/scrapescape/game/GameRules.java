package com.scrapescape.game;
final class GameRules {
    private GameRules() {}
    static boolean isNight(int elapsed) { int hour=Math.floorMod(8+elapsed,24);return hour>=20||hour<6; }
    static int loot(boolean night,int roll) {
        if(roll<0||roll>=100) throw new IllegalArgumentException("roll must be 0..99");
        if(roll<5) return 0;
        if(night) return roll<25?2:roll<60?3:4;
        return roll<20?1:roll<55?2:3;
    }
    static int restRecovery(int hours) { return hours+(hours>=6?2:0); }
}
