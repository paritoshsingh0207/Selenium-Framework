package com.paritosh.photosmigrator.google;

public final class GooglePhotosEndpoints {
    public static final String PICKER_SCOPE = "https://www.googleapis.com/auth/photospicker.mediaitems.readonly";
    public static final String LIBRARY_APPEND_SCOPE = "https://www.googleapis.com/auth/photoslibrary.appendonly";
    public static final String LIBRARY_READ_APP_CREATED_SCOPE = "https://www.googleapis.com/auth/photoslibrary.readonly.appcreateddata";
    public static final String DESTINATION_SCOPES = LIBRARY_APPEND_SCOPE + " " + LIBRARY_READ_APP_CREATED_SCOPE;
    public static final String PICKER_BASE = "https://photospicker.googleapis.com/v1";
    public static final String LIBRARY_UPLOADS = "https://photoslibrary.googleapis.com/v1/uploads";
    public static final String LIBRARY_MEDIA_ITEMS = "https://photoslibrary.googleapis.com/v1/mediaItems";
    public static final String LIBRARY_BATCH_CREATE = "https://photoslibrary.googleapis.com/v1/mediaItems:batchCreate";

    private GooglePhotosEndpoints() { }
}
