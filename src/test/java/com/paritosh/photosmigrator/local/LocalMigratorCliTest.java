package com.paritosh.photosmigrator.local;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LocalMigratorCliTest {
    @Test
    void parsesBinaryAndDecimalCapacities() {
        assertThat(LocalMigratorCli.parseBytes("12GiB")).isEqualTo(12L * 1024 * 1024 * 1024);
        assertThat(LocalMigratorCli.parseBytes("1.5GB")).isEqualTo(1_500_000_000L);
        assertThat(LocalMigratorCli.parseDestination("accountB@gmail.com=750MiB").accountLabel())
                .isEqualTo("accountB@gmail.com");
    }
}
