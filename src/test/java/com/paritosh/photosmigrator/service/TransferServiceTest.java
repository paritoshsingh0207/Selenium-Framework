package com.paritosh.photosmigrator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.paritosh.photosmigrator.google.LibraryUploadClient;
import com.paritosh.photosmigrator.google.PickerApiClient;
import com.paritosh.photosmigrator.model.AccountRole;
import com.paritosh.photosmigrator.model.MigrationItem;
import com.paritosh.photosmigrator.model.MigrationStatus;
import com.paritosh.photosmigrator.model.PickedMediaItem;
import com.paritosh.photosmigrator.model.TransferRunResult;
import com.paritosh.photosmigrator.oauth.GoogleOAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {
    @Mock
    private GoogleOAuthService oauth;
    @Mock
    private PickerService pickerService;
    @Mock
    private PickerApiClient pickerApi;
    @Mock
    private LibraryUploadClient libraryApi;
    @Mock
    private MigrationLedgerService ledger;

    private TransferService service;

    @BeforeEach
    void setUp() {
        service = new TransferService(oauth, pickerService, pickerApi, libraryApi, ledger, 3);
        ObjectNode session = new ObjectMapper().createObjectNode();
        session.put("mediaItemsSet", true);
        when(pickerService.getSession("session-1")).thenReturn(session);
        when(oauth.accessToken(AccountRole.SOURCE)).thenReturn("source-token");
        when(oauth.accessToken(AccountRole.DESTINATION)).thenReturn("destination-token");
    }

    @Test
    void sourceVideoProcessingWaitsWithoutConsumingRetry() {
        PickedMediaItem picked = new PickedMediaItem(
                "source-1", "clip.mp4", "video/mp4", "https://example/base", "VIDEO", "PROCESSING");
        when(pickerService.listAll("session-1")).thenReturn(List.of(picked));
        when(ledger.items("migration-1")).thenReturn(List.of());

        TransferRunResult result = service.transfer("migration-1", "session-1", 25);

        assertEquals(0, result.processed());
        assertEquals(0, result.failed());
        assertEquals(1, result.waiting());

        ArgumentCaptor<MigrationItem> item = ArgumentCaptor.forClass(MigrationItem.class);
        verify(ledger).upsert(eq("migration-1"), item.capture());
        assertEquals(MigrationStatus.WAITING_SOURCE_READY, item.getValue().status());
        assertEquals(0, item.getValue().attempts());
        verify(pickerApi, never()).openMedia(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void destinationVideoReadyIsVerifiedWithoutUploadingAgain() {
        PickedMediaItem picked = new PickedMediaItem(
                "source-2", "ready.mp4", "video/mp4", "https://example/base2", "VIDEO", "READY");
        MigrationItem previous = new MigrationItem(
                "source-2", "ready.mp4", "video/mp4", 1234L, "abc123",
                MigrationStatus.WAITING_DESTINATION_READY, 1, "source-2", "dest-2", Instant.now(), "processing");

        when(pickerService.listAll("session-1")).thenReturn(List.of(picked));
        when(ledger.items("migration-2")).thenReturn(List.of(previous));
        when(libraryApi.getMediaItem("destination-token", "dest-2"))
                .thenReturn(new LibraryUploadClient.DestinationMediaItem("dest-2", "video/mp4", "READY"));

        TransferRunResult result = service.transfer("migration-2", "session-1", 25);

        assertEquals(0, result.processed());
        assertEquals(1, result.verified());
        assertEquals(0, result.waiting());

        ArgumentCaptor<MigrationItem> item = ArgumentCaptor.forClass(MigrationItem.class);
        verify(ledger).upsert(eq("migration-2"), item.capture());
        assertEquals(MigrationStatus.VERIFIED, item.getValue().status());
        assertEquals(1, item.getValue().attempts());
        verify(pickerApi, never()).openMedia(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyBoolean());
    }
}
