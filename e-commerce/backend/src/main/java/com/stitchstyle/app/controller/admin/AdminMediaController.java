package com.stitchstyle.app.controller.admin;

import com.stitchstyle.app.dto.response.MediaResponse;
import com.stitchstyle.app.dto.response.PageResponse;
import com.stitchstyle.app.service.CurrentUserService;
import com.stitchstyle.app.service.MediaService;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/media")
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
public class AdminMediaController {

    private final MediaService mediaService;
    private final CurrentUserService currentUserService;

    public AdminMediaController(MediaService mediaService, CurrentUserService currentUserService) {
        this.mediaService = mediaService;
        this.currentUserService = currentUserService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public MediaResponse upload(@RequestPart("file") MultipartFile file) {
        return mediaService.upload(file, currentUserService.optional().orElse(null));
    }

    @GetMapping
    public PageResponse<MediaResponse> list(@RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(mediaService.list(PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100))));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long id) {
        mediaService.delete(id);
    }
}
