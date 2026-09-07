package com.paritosh.photosmigrator.local;

import java.nio.file.Path;
import java.util.List;

public final class LocalInventoryCli {
    private LocalInventoryCli() { }

    public static boolean isInventoryCommand(String[] args) {
        return args != null && args.length > 0 && "inventory".equalsIgnoreCase(args[0]);
    }

    public static int run(String[] args) {
        if (!isInventoryCommand(args)) return 2;
        if (hasFlag(args, "--help") || hasFlag(args, "-h")) {
            printUsage();
            return 0;
        }

        String takeout = option(args, "--takeout");
        if (takeout == null || takeout.isBlank()) {
            System.err.println("Missing required option: --takeout <folder-or-zip>");
            printUsage();
            return 2;
        }
        String ledger = option(args, "--ledger");
        Path ledgerPath = ledger == null || ledger.isBlank()
                ? Path.of("photos-migration-ledger.xlsx")
                : Path.of(ledger);

        try {
            TakeoutScanner scanner = new TakeoutScanner();
            List<Path> archives = scanner.discoverArchives(Path.of(takeout));
            System.out.println("Found " + archives.size() + " Takeout archive(s). Scanning media without extracting them...");
            List<TakeoutMediaItem> items = scanner.scan(Path.of(takeout));
            new TakeoutInventoryLedger().write(ledgerPath, items);

            long duplicates = items.stream().filter(TakeoutMediaItem::duplicate).count();
            long uniqueBytes = items.stream().filter(item -> !item.duplicate()).mapToLong(TakeoutMediaItem::sizeBytes).sum();
            System.out.println("Inventory complete.");
            System.out.println("Media entries : " + items.size());
            System.out.println("Duplicates    : " + duplicates);
            System.out.printf("Unique size   : %.3f GiB%n", uniqueBytes / 1024d / 1024d / 1024d);
            System.out.println("Excel ledger  : " + ledgerPath.toAbsolutePath().normalize());
            System.out.println("No media was uploaded or deleted.");
            return 0;
        } catch (RuntimeException e) {
            System.err.println("Inventory failed: " + e.getMessage());
            return 1;
        }
    }

    private static String option(String[] args, String name) {
        for (int i = 1; i < args.length - 1; i++) {
            if (name.equalsIgnoreCase(args[i])) return args[i + 1];
        }
        return null;
    }

    private static boolean hasFlag(String[] args, String flag) {
        for (String arg : args) if (flag.equalsIgnoreCase(arg)) return true;
        return false;
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  java -jar google-photos-migrator.jar inventory --takeout <folder-or-zip> [--ledger <file.xlsx>]");
        System.out.println();
        System.out.println("This first phase only inventories Google Takeout ZIPs, hashes media, detects duplicates,");
        System.out.println("reads common Google Photos JSON sidecar timestamps, and writes an Excel migration ledger.");
    }
}
