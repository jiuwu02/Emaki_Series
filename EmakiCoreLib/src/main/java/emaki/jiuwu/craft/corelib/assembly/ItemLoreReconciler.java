package emaki.jiuwu.craft.corelib.assembly;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

final class ItemLoreReconciler {

    Reconciliation reconcile(List<String> previousManagedLore,
            List<String> currentLore,
            List<String> newManagedLore) {
        List<String> previous = safe(previousManagedLore);
        List<String> current = safe(currentLore);
        List<String> next = safe(newManagedLore);
        List<ExternalSegment> externalSegments = extractExternalSegments(previous, current);
        if (externalSegments.isEmpty()) {
            return new Reconciliation(next, List.of(), List.of());
        }

        int[] oldToNew = lcsMapping(previous, next);
        List<List<String>> gapLines = new ArrayList<>(next.size() + 1);
        for (int index = 0; index <= next.size(); index++) {
            gapLines.add(new ArrayList<>());
        }
        List<String> externalLines = new ArrayList<>();
        for (ExternalSegment segment : externalSegments) {
            int gap = resolveGap(segment, oldToNew, previous.size(), next.size());
            gapLines.get(gap).addAll(segment.lines());
            externalLines.addAll(segment.lines());
        }

        List<String> merged = new ArrayList<>(next.size() + externalLines.size());
        for (int gap = 0; gap <= next.size(); gap++) {
            merged.addAll(gapLines.get(gap));
            if (gap < next.size()) {
                merged.add(next.get(gap));
            }
        }
        return new Reconciliation(merged, externalLines, externalSegments);
    }

    List<ExternalSegment> extractExternalSegments(List<String> managedLore, List<String> actualLore) {
        List<String> managed = safe(managedLore);
        List<String> actual = safe(actualLore);
        if (actual.isEmpty()) {
            return List.of();
        }
        if (managed.isEmpty()) {
            return List.of(new ExternalSegment(-1, 0, 0, actual));
        }

        int[] oldToActual = lcsMapping(managed, actual);
        int[] actualToOld = new int[actual.size()];
        Arrays.fill(actualToOld, -1);
        for (int oldIndex = 0; oldIndex < oldToActual.length; oldIndex++) {
            int actualIndex = oldToActual[oldIndex];
            if (actualIndex >= 0) {
                actualToOld[actualIndex] = oldIndex;
            }
        }

        List<ExternalSegment> segments = new ArrayList<>();
        int previousOldIndex = -1;
        int actualIndex = 0;
        while (actualIndex < actual.size()) {
            int matchedOldIndex = actualToOld[actualIndex];
            if (matchedOldIndex >= 0) {
                previousOldIndex = matchedOldIndex;
                actualIndex++;
                continue;
            }
            int start = actualIndex;
            while (actualIndex < actual.size() && actualToOld[actualIndex] < 0) {
                actualIndex++;
            }
            int nextOldIndex = actualIndex < actual.size() ? actualToOld[actualIndex] : managed.size();
            segments.add(new ExternalSegment(
                    previousOldIndex,
                    nextOldIndex,
                    Math.max(0, previousOldIndex + 1),
                    actual.subList(start, actualIndex)
            ));
        }
        return segments.isEmpty() ? List.of() : List.copyOf(segments);
    }

    boolean preservesExternalProjection(List<String> managedLore,
            List<String> mergedLore,
            List<String> expectedExternalLines) {
        List<String> expected = safe(expectedExternalLines);
        List<String> actual = new ArrayList<>();
        for (ExternalSegment segment : extractExternalSegments(managedLore, mergedLore)) {
            actual.addAll(segment.lines());
        }
        return actual.equals(expected) && safe(mergedLore).size() == safe(managedLore).size() + expected.size();
    }

    private int resolveGap(ExternalSegment segment,
            int[] oldToNew,
            int oldSize,
            int newSize) {
        int leftNewIndex = nearestMappedLeft(oldToNew, segment.leftOldIndex());
        int rightNewIndex = nearestMappedRight(oldToNew, segment.rightOldIndex());
        if (leftNewIndex >= 0 && rightNewIndex >= 0 && leftNewIndex < rightNewIndex) {
            return leftNewIndex + 1;
        }
        if (leftNewIndex >= 0) {
            return Math.min(newSize, leftNewIndex + 1);
        }
        if (rightNewIndex >= 0) {
            return Math.max(0, rightNewIndex);
        }
        if (oldSize <= 0) {
            return newSize;
        }
        double relativeGap = (double) segment.fallbackGap() / (double) oldSize;
        return Math.max(0, Math.min(newSize, (int) Math.round(relativeGap * newSize)));
    }

    private int nearestMappedLeft(int[] mapping, int fromOldIndex) {
        if (mapping == null || mapping.length == 0) {
            return -1;
        }
        for (int index = Math.min(fromOldIndex, mapping.length - 1); index >= 0; index--) {
            if (mapping[index] >= 0) {
                return mapping[index];
            }
        }
        return -1;
    }

    private int nearestMappedRight(int[] mapping, int fromOldIndex) {
        if (mapping == null || mapping.length == 0) {
            return -1;
        }
        for (int index = Math.max(0, fromOldIndex); index < mapping.length; index++) {
            if (mapping[index] >= 0) {
                return mapping[index];
            }
        }
        return -1;
    }

    private int[] lcsMapping(List<String> oldLines, List<String> newLines) {
        int oldSize = oldLines.size();
        int newSize = newLines.size();
        int[][] lengths = new int[oldSize + 1][newSize + 1];
        for (int oldIndex = oldSize - 1; oldIndex >= 0; oldIndex--) {
            for (int newIndex = newSize - 1; newIndex >= 0; newIndex--) {
                if (Objects.equals(oldLines.get(oldIndex), newLines.get(newIndex))) {
                    lengths[oldIndex][newIndex] = lengths[oldIndex + 1][newIndex + 1] + 1;
                } else {
                    lengths[oldIndex][newIndex] = Math.max(
                            lengths[oldIndex + 1][newIndex],
                            lengths[oldIndex][newIndex + 1]
                    );
                }
            }
        }

        int[] mapping = new int[oldSize];
        Arrays.fill(mapping, -1);
        int oldIndex = 0;
        int newIndex = 0;
        while (oldIndex < oldSize && newIndex < newSize) {
            if (Objects.equals(oldLines.get(oldIndex), newLines.get(newIndex))
                    && lengths[oldIndex][newIndex] == lengths[oldIndex + 1][newIndex + 1] + 1) {
                mapping[oldIndex] = newIndex;
                oldIndex++;
                newIndex++;
                continue;
            }
            if (lengths[oldIndex + 1][newIndex] > lengths[oldIndex][newIndex + 1]) {
                oldIndex++;
            } else {
                newIndex++;
            }
        }
        return mapping;
    }

    private List<String> safe(List<String> lines) {
        return lines == null || lines.isEmpty() ? List.of() : List.copyOf(lines);
    }

    record Reconciliation(List<String> lore,
            List<String> externalLines,
            List<ExternalSegment> externalSegments) {

        Reconciliation {
            lore = lore == null || lore.isEmpty() ? List.of() : List.copyOf(lore);
            externalLines = externalLines == null || externalLines.isEmpty() ? List.of() : List.copyOf(externalLines);
            externalSegments = externalSegments == null || externalSegments.isEmpty()
                    ? List.of()
                    : List.copyOf(externalSegments);
        }
    }

    record ExternalSegment(int leftOldIndex,
            int rightOldIndex,
            int fallbackGap,
            List<String> lines) {

        ExternalSegment {
            lines = lines == null || lines.isEmpty() ? List.of() : List.copyOf(lines);
        }
    }
}
