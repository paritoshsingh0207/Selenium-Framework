package com.paritosh.photosmigrator;

import com.paritosh.photosmigrator.local.LocalInventoryCli;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PhotosMigratorApplication {
    public static void main(String[] args) {
        if (LocalInventoryCli.isInventoryCommand(args)) {
            int exitCode = LocalInventoryCli.run(args);
            if (exitCode != 0) System.exit(exitCode);
            return;
        }
        SpringApplication.run(PhotosMigratorApplication.class, args);
    }
}
