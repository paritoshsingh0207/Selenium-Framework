package com.paritosh.photosmigrator;

import com.paritosh.photosmigrator.local.LocalMigratorCli;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PhotosMigratorApplication {
    public static void main(String[] args) {
        if (LocalMigratorCli.isLocalCommand(args)) {
            int exitCode = LocalMigratorCli.run(args);
            if (exitCode != 0) System.exit(exitCode);
            return;
        }
        SpringApplication.run(PhotosMigratorApplication.class, args);
    }
}
