package ng.proptech.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import ng.proptech.config.AppProperties;
import ng.proptech.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Local-disk file storage for property photos and NIN document scans.
 *
 * In this prototype everything lands under {@code proptech.storage.upload-dir} (default "./uploads",
 * relative to wherever the Spring Boot process is started) and is served back out at "/media/**"
 * (see {@link ng.proptech.config.WebConfig}). Swapping this class for an S3/Cloudinary-backed
 * implementation later is a one-file change - nothing else in the codebase touches the filesystem.
 */
@Service
public class FileStorageService {

    private final Path root;
    private final Path publicSubdir;
    private final String publicBaseUrl;

    public FileStorageService(AppProperties props) {
        this.root = Paths.get(props.storage().uploadDir()).toAbsolutePath().normalize();
        this.publicSubdir = this.root.resolve("public");
        this.publicBaseUrl = props.storage().publicBaseUrl();
        try {
            Files.createDirectories(publicSubdir);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create upload directory at " + publicSubdir, e);
        }
    }

    /** The directory WebConfig maps to "/media/**". */
    public Path publicDir() {
        return publicSubdir;
    }

    /**
     * Saves an uploaded photo under a random file name (never trust the client-supplied name) and
     * returns the fully-qualified public URL to store on the entity.
     */
    public String storePublic(MultipartFile file, String subfolder) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Uploaded file is empty.");
        }
        String extension = extensionOf(file.getOriginalFilename());
        String fileName = UUID.randomUUID() + extension;
        Path folder = publicSubdir.resolve(subfolder).normalize();
        if (!folder.startsWith(publicSubdir)) {
            // Defends against a subfolder value like "../../etc" ever reaching this method.
            throw ApiException.badRequest("Invalid destination folder.");
        }
        try {
            Files.createDirectories(folder);
            Path target = folder.resolve(fileName);
            file.transferTo(target);
            return publicBaseUrl + "/media/" + subfolder + "/" + fileName;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store uploaded file", e);
        }
    }

    /**
     * Saves a sensitive document (e.g. a NIN slip scan) OUTSIDE the "/media/**"-served public directory and
     * returns an opaque storage key, not a URL - this file is never meant to be publicly reachable.
     */
    public String storePrivate(MultipartFile file, String subfolder) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Uploaded file is empty.");
        }
        String extension = extensionOf(file.getOriginalFilename());
        String fileName = UUID.randomUUID() + extension;
        Path folder = root.resolve("private").resolve(subfolder).normalize();
        if (!folder.startsWith(root)) {
            throw ApiException.badRequest("Invalid destination folder.");
        }
        try {
            Files.createDirectories(folder);
            Path target = folder.resolve(fileName);
            file.transferTo(target);
            return "private/" + subfolder + "/" + fileName;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store uploaded file", e);
        }
    }

    private String extensionOf(String originalFilename) {
        if (originalFilename == null) return "";
        int dot = originalFilename.lastIndexOf('.');
        if (dot < 0 || dot == originalFilename.length() - 1) return "";
        String ext = originalFilename.substring(dot).toLowerCase();
        // Whitelist: only ever accept image extensions regardless of what the client claims.
        return switch (ext) {
            case ".jpg", ".jpeg", ".png", ".webp" -> ext;
            default -> ".jpg";
        };
    }
}
