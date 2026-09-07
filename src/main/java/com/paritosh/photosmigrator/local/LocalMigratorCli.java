package com.paritosh.photosmigrator.local;

import com.paritosh.photosmigrator.local.oauth.DesktopOAuthClientConfig;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class LocalMigratorCli {
    private static final int DEFAULT_MIGRATION_BATCH = 5;

    private LocalMigratorCli() { }

    public static boolean isLocalCommand(String[] args) {
        if (args == null || args.length == 0) return false;
        String command = args[0].toLowerCase(Locale.ROOT);
        return command.equals("inventory") || command.equals("plan") || command.equals("migrate");
    }

    public static int run(String[] args) {
        if (args == null || args.length == 0) return 2;
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "inventory" -> LocalInventoryCli.run(args);
            case "plan" -> runPlan(args);
            case "migrate" -> runMigrate(args);
            default -> 2;
        };
    }

    private static int runPlan(String[] args) {
        if (hasFlag(args, "--help") || hasFlag(args, "-h")) {
            printPlanUsage();
            return 0;
        }
        String ledgerValue = option(args, "--ledger");
        if (ledgerValue == null || ledgerValue.isBlank()) {
            System.err.println("Missing required option: --ledger <file.xlsx>");
            printPlanUsage();
            return 2;
        }

        List<String> destinationValues = options(args, "--destination");
        if (destinationValues.isEmpty()) {
            System.err.println("Add at least one --destination <email=size>, for example accountB@gmail.com=12GiB");
            return 2;
        }

        try {
            List<DestinationPlan> destinations = destinationValues.stream()
                    .map(LocalMigratorCli::parseDestination)
                    .toList();
            for (DestinationPlan destination : destinations) requireEmail(destination.accountLabel());

            Path ledger = Path.of(ledgerValue);
            TakeoutInventoryLedger ledgerService = new TakeoutInventoryLedger();
            List<TakeoutMediaItem> items = ledgerService.readItems(ledger);
            AllocationResult result = new DestinationAllocator().allocate(items, destinations);
            ledgerService.applyAllocation(ledger, result);

            System.out.println("Destination plan written to: " + ledger.toAbsolutePath().normalize());
            for (DestinationPlan destination : destinations) {
                long assigned = result.assignedBytes().getOrDefault(destination.accountLabel(), 0L);
                System.out.printf("%-30s %8.3f / %8.3f GiB%n",
                        destination.accountLabel(), toGiB(assigned), toGiB(destination.maxBytes()));
            }
            if (result.unassignedItemIds().isEmpty()) {
                System.out.println("All unique media fits within the configured destination capacities.");
                return 0;
            }
            System.err.println(result.unassignedItemIds().size() + " unique media item(s) remain unassigned due to capacity.");
            return 3;
        } catch (RuntimeException e) {
            System.err.println("Planning failed: " + e.getMessage());
            return 1;
        }
    }

    private static int runMigrate(String[] args) {
        if (hasFlag(args, "--help") || hasFlag(args, "-h")) {
            printMigrateUsage();
            return 0;
        }
        String ledgerValue = option(args, "--ledger");
        String credentialsValue = option(args, "--credentials");
        if (ledgerValue == null || ledgerValue.isBlank() || credentialsValue == null || credentialsValue.isBlank()) {
            System.err.println("migrate requires --ledger <file.xlsx> and --credentials <credentials.json>");
            printMigrateUsage();
            return 2;
        }

        int maxItems;
        try {
            String maxItemsValue = option(args, "--max-items");
            maxItems = maxItemsValue == null ? DEFAULT_MIGRATION_BATCH : Integer.parseInt(maxItemsValue);
            if (maxItems <= 0) throw new NumberFormatException("must be > 0");
        } catch (NumberFormatException e) {
            System.err.println("--max-items must be a positive integer");
            return 2;
        }

        try {
            Path ledger = Path.of(ledgerValue);
            DesktopOAuthClientConfig oauthConfig = DesktopOAuthClientConfig.fromJson(Path.of(credentialsValue));
            LocalMigrationRunner runner = new LocalMigrationRunner(
                    new DesktopDestinationAuthenticator(oauthConfig),
                    new LocalLibraryUploadClient());

            System.out.println("Migration safety limit: " + maxItems + " item(s) for this run.");
            System.out.println("The utility will open Google sign-in only for destination accounts that have pending work.");
            System.out.println("It will never delete anything from the primary Google Photos account.");
            LocalMigrationRunResult result = runner.run(ledger, maxItems);

            System.out.println("Migration run complete.");
            System.out.println("Processed               : " + result.processed());
            System.out.println("Created in destination  : " + result.uploaded());
            System.out.println("Verified                : " + result.verified());
            System.out.println("Waiting video processing: " + result.waiting());
            System.out.println("Failed                  : " + result.failed());
            System.out.println("Needs reconciliation    : " + result.reconciliationRequired());
            System.out.println("Ledger checkpoint       : " + ledger.toAbsolutePath().normalize());

            if (result.reconciliationRequired() > 0) return 4;
            return result.failed() > 0 ? 3 : 0;
        } catch (RuntimeException e) {
            System.err.println("Migration failed safely: " + e.getMessage());
            System.err.println("No source deletion was attempted. Review the Excel ledger before retrying.");
            return 1;
        }
    }

    static DestinationPlan parseDestination(String value) {
        int equals = value.lastIndexOf('=');
        if (equals <= 0 || equals == value.length() - 1) {
            throw new IllegalArgumentException("Invalid destination '" + value + "'. Expected email=size, e.g. accountB@gmail.com=12GiB");
        }
        String label = value.substring(0, equals).trim();
        long maxBytes = parseBytes(value.substring(equals + 1).trim());
        return new DestinationPlan(label, maxBytes);
    }

    static long parseBytes(String value) {
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace(" ", "");
        double multiplier = 1d;
        String number = normalized;
        if (normalized.endsWith("GIB")) {
            multiplier = 1024d * 1024d * 1024d;
            number = normalized.substring(0, normalized.length() - 3);
        } else if (normalized.endsWith("GB")) {
            multiplier = 1_000_000_000d;
            number = normalized.substring(0, normalized.length() - 2);
        } else if (normalized.endsWith("MIB")) {
            multiplier = 1024d * 1024d;
            number = normalized.substring(0, normalized.length() - 3);
        } else if (normalized.endsWith("MB")) {
            multiplier = 1_000_000d;
            number = normalized.substring(0, normalized.length() - 2);
        } else if (normalized.endsWith("B")) {
            number = normalized.substring(0, normalized.length() - 1);
        }
        double parsed = Double.parseDouble(number);
        if (!Double.isFinite(parsed) || parsed <= 0) throw new IllegalArgumentException("Capacity must be greater than zero");
        double bytes = parsed * multiplier;
        if (bytes > Long.MAX_VALUE) throw new IllegalArgumentException("Capacity is too large");
        return (long) bytes;
    }

    private static void requireEmail(String value) {
        int at = value.indexOf('@');
        if (at <= 0 || at == value.length() - 1 || value.contains(" ")) {
            throw new IllegalArgumentException("Destination label must be the exact Google account email: " + value);
        }
    }

    private static String option(String[] args, String name) {
        for (int i = 1; i < args.length - 1; i++) {
            if (name.equalsIgnoreCase(args[i])) return args[i + 1];
        }
        return null;
    }

    private static List<String> options(String[] args, String name) {
        List<String> values = new ArrayList<>();
        for (int i = 1; i < args.length - 1; i++) {
            if (name.equalsIgnoreCase(args[i])) values.add(args[i + 1]);
        }
        return values;
    }

    private static boolean hasFlag(String[] args, String flag) {
        for (String arg : args) if (flag.equalsIgnoreCase(arg)) return true;
        return false;
    }

    private static double toGiB(long bytes) {
        return bytes / 1024d / 1024d / 1024d;
    }

    private static void printPlanUsage() {
        System.out.println("Usage:");
        System.out.println("  PhotosMigrator.exe plan --ledger <file.xlsx> \\");
        System.out.println("      --destination accountB@gmail.com=12GiB --destination accountC@gmail.com=12GiB");
        System.out.println();
        System.out.println("Unique items are ordered by capture time and assigned in contiguous chronological blocks");
        System.out.println("without exceeding the configured per-account byte limits.");
    }

    private static void printMigrateUsage() {
        System.out.println("Usage:");
        System.out.println("  PhotosMigrator.exe migrate --ledger <file.xlsx> --credentials <credentials.json> [--max-items 5]");
        System.out.println();
        System.out.println("Default max-items is 5 for a cautious first run. Google sign-in is local; OAuth tokens are not committed to GitHub.");
    }
}
