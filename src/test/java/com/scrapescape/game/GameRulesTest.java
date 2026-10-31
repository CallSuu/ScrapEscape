package com.scrapescape.game;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GameRulesTest {
    @Test void nightUsesDepartureTimeAndWrapsAtMidnight() {
        assertFalse(GameRules.isNight(0));
        assertTrue(GameRules.isNight(12));
        assertTrue(GameRules.isNight(21));
        assertFalse(GameRules.isNight(22));
        assertTrue(GameRules.isNight(36));
    }
    @Test void lootMatchesAllHundredProbabilityBuckets() {
        int[] day = new int[5], night = new int[5];
        for(int i=0;i<100;i++) { day[GameRules.loot(false,i)]++; night[GameRules.loot(true,i)]++; }
        assertArrayEquals(new int[]{5,15,35,45,0},day);
        assertArrayEquals(new int[]{5,0,20,35,40},night);
    }
    @Test void longRestReceivesBonus() {
        assertEquals(4,GameRules.restRecovery(4));
        assertEquals(8,GameRules.restRecovery(6));
        assertEquals(10,GameRules.restRecovery(8));
    }
    @Test void diseaseProbabilityUsesNonOverlappingBoundaries() {
        for(int c=0;c<=100;c++)assertEquals(c<60?0:c<80?30:c<90?40:60,GameRules.diseaseProbability(c));
    }

    @Test void cleanerRewardsHaveSeventyThirtyBucketsAndRestReducesFivePerHour() {
        int[] counts=new int[3];for(int i=0;i<100;i++)counts[GameRules.cleanerReward(i)]++;
        assertArrayEquals(new int[]{0,70,30},counts);assertEquals(10,GameRules.CLEANER_CAPACITY);
        assertEquals(10,GameRules.restDecontamination(2));assertEquals(20,GameRules.restDecontamination(4));assertEquals(40,GameRules.restDecontamination(8));
    }
}
