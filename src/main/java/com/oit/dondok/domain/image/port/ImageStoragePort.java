package com.oit.dondok.domain.image.port;

import java.io.InputStream;
import java.time.Duration;

public interface ImageStoragePort {

  PresignedUpload createPresignedUpload(ImageObjectKey key, String contentType, Duration ttl);

  ImageObjectMetadata head(ImageObjectKey key);

  InputStream open(ImageObjectKey key);

  void put(ImageObjectKey key, byte[] bytes, String contentType);
}
