package ng.proptech.web;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import ng.proptech.domain.AppUser;
import ng.proptech.domain.PropertyType;
import ng.proptech.dto.PropertyDtos.*;
import ng.proptech.exception.ApiException;
import ng.proptech.repository.AppUserRepository;
import ng.proptech.security.AuthenticatedUser;
import ng.proptech.service.PropertyService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Public GET (search/detail) per SecurityConfig; property creation and merge decisions require an
 * authenticated AGENT (checked with @PreAuthorize since AppUser.role drives the ROLE_* authority).
 */
@RestController
@RequestMapping("/api/properties")
public class PropertyController {

    private final PropertyService propertyService;
    private final AppUserRepository userRepository;

    public PropertyController(PropertyService propertyService, AppUserRepository userRepository) {
        this.propertyService = propertyService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public List<PropertySummary> search(@RequestParam(required = false) UUID lgaId,
                                         @RequestParam(required = false) UUID settlementId,
                                         @RequestParam(required = false) PropertyType propertyType) {
        return propertyService.search(lgaId, settlementId, propertyType);
    }

    @GetMapping("/{id}")
    public PropertyDetail detail(@PathVariable UUID id) {
        return propertyService.detail(id);
    }

    /**
     * Multipart upload: "metadata" part is CreatePropertyRequest as JSON, "photos" parts are the image files.
     * On success, triggers the AI microservice call + pgvector duplicate scan inline (see PropertyService).
     */
    @PostMapping(consumes = "multipart/form-data")
    @PreAuthorize("hasRole('AGENT')")
    public ResponseEntity<PropertyUploadResponse> create(@Valid @RequestPart("metadata") CreatePropertyRequest metadata,
                                                           @RequestPart("photos") List<MultipartFile> photos,
                                                           @AuthenticationPrincipal AuthenticatedUser principal) {
        AppUser agent = currentUser(principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(propertyService.create(metadata, photos, agent));
    }

    @PostMapping("/{id}/merge-decision")
    @PreAuthorize("hasRole('AGENT')")
    public ResponseEntity<Void> decideMerge(@PathVariable UUID id, @Valid @RequestBody MergeDecisionRequest decision,
                                             @AuthenticationPrincipal AuthenticatedUser principal) {
        propertyService.decideMerge(id, currentUser(principal), decision);
        return ResponseEntity.noContent().build();
    }

    private AppUser currentUser(AuthenticatedUser principal) {
        return userRepository.findById(principal.userId()).orElseThrow(() -> ApiException.notFound("User not found."));
    }
}
