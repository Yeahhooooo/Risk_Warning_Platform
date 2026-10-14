package com.riskwarning.common.reliability;

import com.riskwarning.common.utils.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class FileMergeRecoveryTest {
    @TempDir Path root;
    @Test void failedMergeRetainsSourceEvenWhenCleanupRequested() throws Exception {
        Path chunks = Files.createDirectory(root.resolve("chunks"));
        Files.writeString(chunks.resolve("0"), "source");
        Path invalidTarget = Files.createDirectory(root.resolve("target-directory"));
        assertThrows(RuntimeException.class, () -> FileUtils.union(chunks.toString(), invalidTarget.toString(), true));
        assertEquals("source", Files.readString(chunks.resolve("0")));
    }
    @Test void retryTruncatesPreviousOutputAndSortsChunkNumbers() throws Exception {
        Path chunks = Files.createDirectory(root.resolve("chunks"));
        for(int i=0;i<12;i++) Files.writeString(chunks.resolve(String.valueOf(i)), String.valueOf((char)('a'+i)));
        Path target = root.resolve("merged");
        Files.writeString(target, "old output longer than twelve bytes");
        FileUtils.union(chunks.toString(), target.toString(), false);
        assertEquals("abcdefghijkl", Files.readString(target));
        assertTrue(Files.exists(chunks.resolve("0")));
    }
}
