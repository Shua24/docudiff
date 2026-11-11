package dev.docudiff.versioning;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SnapshotNameGeneratorTest {

    private static final LocalDate SNAPSHOT_DATE = LocalDate.of(2026, 11, 11);

    @Test
    @DisplayName("first snapshot of the day uses the plain document-date name")
    void firstSnapshotOfDayUsesPlainName() {
        SnapshotNameGenerator generator = new SnapshotNameGenerator();

        String snapshotName = generator.next("report", SNAPSHOT_DATE, List.of());

        assertEquals("report-snapshot-2026-11-11", snapshotName);
    }

    @Test
    @DisplayName("second snapshot of the same day gets the -2 suffix")
    void secondSnapshotOfDayGetsSuffixTwo() {
        SnapshotNameGenerator generator = new SnapshotNameGenerator();

        String snapshotName = generator.next(
                "report",
                SNAPSHOT_DATE,
                List.of("report-snapshot-2026-11-11"));

        assertEquals("report-snapshot-2026-11-11-2", snapshotName);
    }

    @Test
    @DisplayName("third snapshot of the same day gets the -3 suffix")
    void thirdSnapshotOfDayGetsSuffixThree() {
        SnapshotNameGenerator generator = new SnapshotNameGenerator();

        String snapshotName = generator.next(
                "report",
                SNAPSHOT_DATE,
                List.of(
                        "report-snapshot-2026-11-11",
                        "report-snapshot-2026-11-11-2"));

        assertEquals("report-snapshot-2026-11-11-3", snapshotName);
    }

    @Test
    @DisplayName("a snapshot name from another date does not consume a suffix")
    void otherDateDoesNotConsumeSuffix() {
        SnapshotNameGenerator generator = new SnapshotNameGenerator();

        String snapshotName = generator.next(
                "report",
                SNAPSHOT_DATE,
                List.of("report-snapshot-2026-11-10", "report-snapshot-2026-11-10-2"));

        assertEquals("report-snapshot-2026-11-11", snapshotName);
    }

    @Test
    @DisplayName("a gap in the suffix sequence is filled before extending it")
    void gapInSuffixSequenceIsFilled() {
        SnapshotNameGenerator generator = new SnapshotNameGenerator();

        String snapshotName = generator.next(
                "report",
                SNAPSHOT_DATE,
                List.of("report-snapshot-2026-11-11", "report-snapshot-2026-11-11-3"));

        assertEquals("report-snapshot-2026-11-11-2", snapshotName);
    }

    @Test
    @DisplayName("document names containing spaces are preserved verbatim")
    void documentNameWithSpacesIsPreserved() {
        SnapshotNameGenerator generator = new SnapshotNameGenerator();

        String snapshotName = generator.next("quarterly report", SNAPSHOT_DATE, List.of());

        assertEquals("quarterly report-snapshot-2026-11-11", snapshotName);
    }
}
