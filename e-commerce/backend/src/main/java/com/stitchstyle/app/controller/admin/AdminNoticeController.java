package com.stitchstyle.app.controller.admin;

import com.stitchstyle.app.dto.request.NoticeRequest;
import com.stitchstyle.app.dto.response.NoticeResponse;
import com.stitchstyle.app.service.NoticeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/notices")
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
public class AdminNoticeController {

    private final NoticeService noticeService;

    public AdminNoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    @GetMapping
    public List<NoticeResponse> list() {
        return noticeService.adminList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NoticeResponse create(@Valid @RequestBody NoticeRequest request) {
        return noticeService.create(request);
    }

    @PutMapping("/{id}")
    public NoticeResponse update(@PathVariable Long id, @Valid @RequestBody NoticeRequest request) {
        return noticeService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        noticeService.delete(id);
    }
}
