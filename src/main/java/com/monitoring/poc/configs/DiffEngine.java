package com.monitoring.poc.configs;

import java.util.ArrayList;
import java.util.List;

/**
 * Small self-written line-level LCS diff - deliberately not a new Maven
 * dependency (java-diff-utils), since this environment can't be assumed to
 * have reliable access to resolve one mid-build. Fine for POC-scale config
 * files (O(n*m) DP table).
 */
public final class DiffEngine {

    private DiffEngine() {
    }

    public enum LineType {
        CONTEXT, ADD, DEL
    }

    public record DiffLine(LineType type, Integer oldLineNumber, Integer newLineNumber, String text) {
    }

    public static List<DiffLine> diff(String oldText, String newText) {
        String[] oldLines = splitLines(oldText);
        String[] newLines = splitLines(newText);
        int n = oldLines.length;
        int m = newLines.length;

        int[][] lcs = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                lcs[i][j] = oldLines[i].equals(newLines[j])
                        ? lcs[i + 1][j + 1] + 1
                        : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
            }
        }

        List<DiffLine> result = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < n && j < m) {
            if (oldLines[i].equals(newLines[j])) {
                result.add(new DiffLine(LineType.CONTEXT, i + 1, j + 1, oldLines[i]));
                i++;
                j++;
            } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
                result.add(new DiffLine(LineType.DEL, i + 1, null, oldLines[i]));
                i++;
            } else {
                result.add(new DiffLine(LineType.ADD, null, j + 1, newLines[j]));
                j++;
            }
        }
        while (i < n) {
            result.add(new DiffLine(LineType.DEL, i + 1, null, oldLines[i]));
            i++;
        }
        while (j < m) {
            result.add(new DiffLine(LineType.ADD, null, j + 1, newLines[j]));
            j++;
        }
        return result;
    }

    public static String summarize(List<DiffLine> lines) {
        long added = lines.stream().filter(l -> l.type() == LineType.ADD).count();
        long removed = lines.stream().filter(l -> l.type() == LineType.DEL).count();
        return "+" + added + " -" + removed;
    }

    private static String[] splitLines(String text) {
        if (text == null || text.isEmpty()) {
            return new String[0];
        }
        return text.split("\n", -1);
    }
}
