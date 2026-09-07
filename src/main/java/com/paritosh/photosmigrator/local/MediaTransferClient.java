package com.paritosh.photosmigrator.local;

import com.paritosh.photosmigrator.local.oauth.AccessTokenProvider;

public interface MediaTransferClient {
    String uploadBytes(AccessTokenProvider tokens, TakeoutMediaItem item);

    CreatedMediaItem createMediaItem(AccessTokenProvider tokens, String uploadToken, String fileName);

    DestinationMediaItem verify(AccessTokenProvider tokens, String mediaItemId);

    record CreatedMediaItem(String id, String productUrl, String videoStatus) { }

    record DestinationMediaItem(String id, String mimeType, String videoStatus) { }
}
