package com.nevtan.drive.service;

import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

public interface CloudStorageService {

    void upload(String objectKey, InputStream inputStream, long contentLength, String contentType)
            throws IOException;

    Resource download(String objectKey);

    void delete(String objectKey);

    URI generateDownloadUrl(String objectKey);
}
