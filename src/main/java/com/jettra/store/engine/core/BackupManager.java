package com.jettra.store.engine.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.Stream;

/**
 * Handles backup and restore operations for the storage directory.
 */
public class BackupManager {

    private final Path storageDir;

    public BackupManager(Path storageDir) {
        this.storageDir = storageDir;
    }

    /**
     * Creates a hot backup of the current database files.
     * Note: In a fully ACID system, this would require a write lock or snapshotting.
     */
    public void createBackup() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path backupDir = storageDir.resolveSibling("jettra_backup_" + timestamp);

        try {
            Files.createDirectories(backupDir);
            
            try (Stream<Path> stream = Files.list(storageDir)) {
                stream.forEach(source -> {
                    try {
                        if (Files.isRegularFile(source)) {
                            Path destination = backupDir.resolve(source.getFileName());
                            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
                        }
                    } catch (IOException e) {
                        System.err.println("Failed to copy file during backup: " + source);
                    }
                });
            }
            System.out.println("Backup created successfully at: " + backupDir.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("Failed to create backup directory: " + e.getMessage());
            e.printStackTrace();
        }
    }
    /**
     * Restores the latest backup found in the sibling directory.
     * @return true if a backup was found and restored successfully, false otherwise.
     */
    public boolean restoreLatestBackup() {
        Path parentDir = storageDir.getParent();
        if (parentDir == null) {
            System.err.println("Cannot auto-restore: storage directory has no parent.");
            return false;
        }

        try {
            Path latestBackup = null;
            try (Stream<Path> stream = Files.list(parentDir)) {
                latestBackup = stream
                        .filter(Files::isDirectory)
                        .filter(p -> p.getFileName().toString().startsWith("jettra_backup_"))
                        .max((p1, p2) -> p1.getFileName().toString().compareTo(p2.getFileName().toString()))
                        .orElse(null);
            }

            if (latestBackup == null) {
                System.out.println("No backups found to restore.");
                return false;
            }

            System.out.println("Restoring from backup: " + latestBackup.toAbsolutePath());

            if (Files.exists(storageDir)) {
                try (Stream<Path> existingFiles = Files.list(storageDir)) {
                    existingFiles.forEach(f -> {
                        try {
                            if (Files.isRegularFile(f)) {
                                Files.delete(f);
                            }
                        } catch (IOException e) {
                            System.err.println("Failed to delete existing file before restore: " + f);
                        }
                    });
                }
            } else {
                Files.createDirectories(storageDir);
            }

            try (Stream<Path> backupFiles = Files.list(latestBackup)) {
                backupFiles.forEach(source -> {
                    try {
                        if (Files.isRegularFile(source)) {
                            Path destination = storageDir.resolve(source.getFileName());
                            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
                        }
                    } catch (IOException e) {
                        System.err.println("Failed to restore file: " + source);
                    }
                });
            }
            
            System.out.println("Restore completed successfully.");
            return true;

        } catch (IOException e) {
            System.err.println("Failed to restore backup: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}
