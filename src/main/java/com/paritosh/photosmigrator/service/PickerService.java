package com.paritosh.photosmigrator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.paritosh.photosmigrator.google.PickerApiClient;
import com.paritosh.photosmigrator.model.AccountRole;
import com.paritosh.photosmigrator.model.PickedMediaItem;
import com.paritosh.photosmigrator.oauth.GoogleOAuthService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class PickerService {
    private final GoogleOAuthService oauth;
    private final PickerApiClient pickerApi;

    public PickerService(GoogleOAuthService oauth, PickerApiClient pickerApi) {
        this.oauth = oauth;
        this.pickerApi = pickerApi;
    }

    public JsonNode createSession(int maxItemCount) {
        return pickerApi.createSession(oauth.accessToken(AccountRole.SOURCE), maxItemCount);
    }

    public JsonNode getSession(String sessionId) {
        return pickerApi.getSession(oauth.accessToken(AccountRole.SOURCE), sessionId);
    }

    public void deleteSession(String sessionId) {
        pickerApi.deleteSession(oauth.accessToken(AccountRole.SOURCE), sessionId);
    }

    public List<PickedMediaItem> listAll(String sessionId) {
        String accessToken = oauth.accessToken(AccountRole.SOURCE);
        List<PickedMediaItem> items = new ArrayList<>();
        String pageToken = null;
        do {
            JsonNode page = pickerApi.listMediaItems(accessToken, sessionId, 100, pageToken);
            for (JsonNode node : page.path("mediaItems")) {
                JsonNode file = node.path("mediaFile");
                String processingStatus = file.path("mediaFileMetadata").path("videoMetadata").path("processingStatus").asText("");
                items.add(new PickedMediaItem(
                        node.path("id").asText(),
                        file.path("filename").asText(),
                        file.path("mimeType").asText(),
                        file.path("baseUrl").asText(),
                        node.path("type").asText(),
                        processingStatus));
            }
            pageToken = page.path("nextPageToken").asText("");
        } while (!pageToken.isBlank());
        return items;
    }
}
