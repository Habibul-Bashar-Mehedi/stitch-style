package com.stitchstyle.app.integration.media;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
@ConditionalOnProperty(name = "app.media.storage", havingValue = "local", matchIfMissing = true)
public class LocalMediaStorage implements MediaStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalMediaStorage.class);

    private final Path root;
    private final String publicBaseUrl;

    public LocalMediaStorage(@Value("${app.media.local-root:./data/media}") String root,
                             @Value("${app.media.public-base-url:/media}") String publicBaseUrl) {
        this.root = Path.of(root).toAbsolutePath().normalize();
        this.publicBaseUrl = publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
        try {
            Files.createDirectories(this.root);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create media root " + this.root, e);
        }
        // Logged because a relative app.media.local-root resolves against the
        // process working directory; seeing the absolute path is the quickest way
        // to diagnose "uploaded image is missing" when the app was started from
        // another directory.
        log.info("Media storage root: {}", this.root);
    }

    @Override
    public void store(String key, byte[] bytes, String contentType) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot store media " + key, e);
        }
    }

    @Override
    public byte[] load(String key) {
        try {
            return Files.readAllBytes(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read media " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot delete media " + key, e);
        }
    }

    @Override
    public String url(String key) {
        return publicBaseUrl + "/" + key;
    }

    private Path resolve(String key) {
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) throw new IllegalArgumentException("Invalid media key");
        return target;
    }
}
