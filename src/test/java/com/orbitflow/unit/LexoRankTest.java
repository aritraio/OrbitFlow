package com.orbitflow.unit;

import com.orbitflow.common.util.LexoRank;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class LexoRankTest {
    @Test
    void initialRankIsStable() {
        assertThat(LexoRank.initial()).isEqualTo("a0");
    }

    @Test
    void betweenTwoRanksIsOrdered() {
        String a = "a0";
        String b = "a0n";
        String mid = LexoRank.between(a, b);
        assertThat(mid).isNotEqualTo(a);
        assertThat(mid).isNotEqualTo(b);
        assertThat(a.compareTo(mid)).isLessThan(0);
        assertThat(mid.compareTo(b)).isLessThan(0);
    }

    @Test
    void incrementAndDecrementPreserveOrder() {
        String base = "a0";
        String inc = LexoRank.increment(base);
        String dec = LexoRank.decrement(base);
        assertThat(dec.compareTo(base)).isLessThan(0);
        assertThat(base.compareTo(inc)).isLessThan(0);
    }

    @Test
    void nullBoundsReturnInitialOrEdge() {
        assertThat(LexoRank.between(null, null)).isEqualTo("a0");
        String onlyNext = LexoRank.between(null, "m0");
        assertThat(onlyNext.compareTo("m0")).isLessThan(0);
        String onlyPrev = LexoRank.between("m0", null);
        assertThat("m0".compareTo(onlyPrev)).isLessThan(0);
    }

    @Test
    void rebalancePreservesCountAndOrder() {
        List<String> fresh = LexoRank.rebalance(5);
        assertThat(fresh).hasSize(5);
        for (int i = 1; i < fresh.size(); i++) {
            assertThat(fresh.get(i - 1).compareTo(fresh.get(i))).isLessThan(0);
        }
    }

    @Test
    void needsRebalanceDetectsLongRanks() {
        assertThat(LexoRank.needsRebalance(List.of("a0", "a0n"))).isFalse();
        assertThat(LexoRank.needsRebalance(List.of("a0", "a0nnnnnnnnnnnnnnnnnnn"))).isTrue();
    }
}
