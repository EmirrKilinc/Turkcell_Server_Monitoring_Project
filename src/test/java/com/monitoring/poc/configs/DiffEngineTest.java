package com.monitoring.poc.configs;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DiffEngineTest {

    @Test
    void identicalTextsProduceOnlyContextLines() {
        String text = "a\nb\nc";
        List<DiffEngine.DiffLine> lines = DiffEngine.diff(text, text);

        assertThat(lines).hasSize(3);
        assertThat(lines).allMatch(l -> l.type() == DiffEngine.LineType.CONTEXT);
        assertThat(DiffEngine.summarize(lines)).isEqualTo("+0 -0");
    }

    @Test
    void addedOnlyLinesAreAllAdd() {
        List<DiffEngine.DiffLine> lines = DiffEngine.diff("a\nb", "a\nb\nc\nd");

        assertThat(lines).filteredOn(l -> l.type() == DiffEngine.LineType.ADD).hasSize(2);
        assertThat(DiffEngine.summarize(lines)).isEqualTo("+2 -0");
    }

    @Test
    void removedOnlyLinesAreAllDel() {
        List<DiffEngine.DiffLine> lines = DiffEngine.diff("a\nb\nc\nd", "a\nb");

        assertThat(lines).filteredOn(l -> l.type() == DiffEngine.LineType.DEL).hasSize(2);
        assertThat(DiffEngine.summarize(lines)).isEqualTo("+0 -2");
    }

    @Test
    void mixedChangeProducesBothAddAndDel() {
        List<DiffEngine.DiffLine> lines = DiffEngine.diff("a\nb\nc", "a\nx\nc");

        assertThat(lines).filteredOn(l -> l.type() == DiffEngine.LineType.DEL).hasSize(1);
        assertThat(lines).filteredOn(l -> l.type() == DiffEngine.LineType.ADD).hasSize(1);
        assertThat(lines).filteredOn(l -> l.type() == DiffEngine.LineType.CONTEXT).hasSize(2);
        assertThat(DiffEngine.summarize(lines)).isEqualTo("+1 -1");
    }

    @Test
    void emptyBaselineProducesAllAddLines() {
        List<DiffEngine.DiffLine> lines = DiffEngine.diff("", "a\nb");

        assertThat(lines).hasSize(2);
        assertThat(lines).allMatch(l -> l.type() == DiffEngine.LineType.ADD);
    }
}
