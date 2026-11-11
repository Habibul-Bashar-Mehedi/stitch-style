package com.stitchstyle.app.service;

import com.stitchstyle.app.dto.request.ComplaintRequest;
import com.stitchstyle.app.dto.request.ComplaintResponseRequest;
import com.stitchstyle.app.dto.response.ComplaintResponse;
import com.stitchstyle.app.exception.ResourceNotFoundException;
import com.stitchstyle.app.model.entity.Complaint;
import com.stitchstyle.app.model.entity.ComplaintStatus;
import com.stitchstyle.app.repository.ComplaintRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class ComplaintService {

    private final ComplaintRepository repository;

    public ComplaintService(ComplaintRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ComplaintResponse create(ComplaintRequest request) {
        Complaint complaint = Complaint.builder()
                .name(request.name())
                .phone(OrderService.normalizePhone(request.phone()))
                .orderNumber(request.orderNumber())
                .message(request.message())
                .status(ComplaintStatus.OPEN)
                .dueAt(Instant.now().plusSeconds(72 * 3600))
                .build();
        repository.save(complaint);
        return toResponse(complaint);
    }

    public List<ComplaintResponse> list() {
        return repository.findAllByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
    }

    @Transactional
    public ComplaintResponse respond(Long id, ComplaintResponseRequest request) {
        Complaint complaint = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Complaint not found: " + id));
        complaint.setResponse(request.response());
        complaint.setRespondedAt(Instant.now());
        complaint.setStatus(Boolean.TRUE.equals(request.close()) ? ComplaintStatus.CLOSED : ComplaintStatus.RESPONDED);
        repository.save(complaint);
        return toResponse(complaint);
    }

    private ComplaintResponse toResponse(Complaint complaint) {
        boolean overdue = complaint.getStatus() == ComplaintStatus.OPEN
                && complaint.getDueAt().isBefore(Instant.now());
        return new ComplaintResponse(complaint.getId(), complaint.getName(), complaint.getPhone(),
                complaint.getOrderNumber(), complaint.getMessage(), complaint.getStatus(), complaint.getDueAt(),
                complaint.getRespondedAt(), complaint.getResponse(), complaint.getCreatedAt(), overdue);
    }
}
