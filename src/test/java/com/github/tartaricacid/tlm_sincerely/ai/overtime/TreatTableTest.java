package com.github.tartaricacid.tlm_sincerely.ai.overtime;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TreatTable} 的单元测试：配置条目的解析与容错。
 */
class TreatTableTest {

    @Test
    void parsesNormalEntries() {
        TreatTable table = TreatTable.parse(List.of("minecraft:cake=300", "minecraft:cookie=60"));
        assertEquals(300, table.secondsFor("minecraft:cake"));
        assertEquals(60, table.secondsFor("minecraft:cookie"));
        assertEquals(0, table.secondsFor("minecraft:apple"));
    }

    @Test
    void keepsConfigOrder() {
        TreatTable table = TreatTable.parse(List.of("b:x=1", "a:y=2", "c:z=3"));
        assertEquals(List.of("b:x", "a:y", "c:z"), List.copyOf(table.itemIds()));
    }

    @Test
    void ignoresMalformedEntriesButKeepsTheGoodOnes() {
        TreatTable table = TreatTable.parse(Arrays.asList(
                "minecraft:cake=300",
                null,
                "",
                "no_equals_sign",
                "=300",
                "minecraft:bread=",
                "minecraft:bread=abc",
                "minecraft:bread=0",
                "minecraft:bread=-5",
                "bread=60",
                "mod:Item With Space=60",
                "a:b:c=60"));
        assertEquals(List.of("minecraft:cake"), List.copyOf(table.itemIds()));
    }

    @Test
    void lastDuplicateWins() {
        TreatTable table = TreatTable.parse(List.of("minecraft:cake=100", "minecraft:cake=200"));
        assertEquals(200, table.secondsFor("minecraft:cake"));
    }

    @Test
    void secondsAreCappedAtTheMaximum() {
        TreatTable table = TreatTable.parse(List.of("minecraft:cake=999999"));
        assertEquals(TreatTable.MAX_SECONDS, table.secondsFor("minecraft:cake"));
    }

    @Test
    void idsAreCaseInsensitive() {
        TreatTable table = TreatTable.parse(List.of("Minecraft:Cake=300"));
        assertEquals(300, table.secondsFor("minecraft:cake"));
        assertEquals(300, table.secondsFor("MINECRAFT:CAKE"));
    }

    @Test
    void whitespaceAroundPartsIsTolerated() {
        TreatTable table = TreatTable.parse(List.of("  minecraft:cake = 300  "));
        assertEquals(300, table.secondsFor("minecraft:cake"));
    }

    @Test
    void nullAndEmptyInputsGiveAnEmptyTable() {
        assertTrue(TreatTable.parse(null).isEmpty());
        assertTrue(TreatTable.parse(List.of()).isEmpty());
        assertTrue(TreatTable.empty().isEmpty());
        assertEquals(0, TreatTable.empty().secondsFor("minecraft:cake"));
        assertEquals(0, TreatTable.parse(List.of("minecraft:cake=300")).secondsFor(null));
    }
}
