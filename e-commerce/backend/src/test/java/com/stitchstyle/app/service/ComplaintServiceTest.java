package com.stitchstyle.app.service;

import com.stitchstyle.app.dto.request.ComplaintRequest;
import com.stitchstyle.app.dto.request.ComplaintResponseRequest;
import com.stitchstyle.app.dto.response.ComplaintResponse;
import com.stitchstyle.app.exception.ResourceNotFoundException;
import com.stitchstyle.app.model.entity.Complaint;
import com.stitchstyle.app.model.entity.ComplaintStatus;
import com.stitchstyle.app.repository.ComplaintRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ComplaintServiceTest {

    private ComplaintRepository repository;
    private ComplaintService service;

    @BeforeEach
    void setUp() {
        repository = mock(ComplaintRepository.class);
        service = new ComplaintService(repository);
    }

    @Test
    void createStoresNormalizedPhoneAndOpenSla() {
        when(repository.save(any(Complaint.class))).thenAnswer(invocation -> {
            Complaint complaint = invocation.getArgument(0);
            complaint.setId(1L);
            complaint.setCreatedAt(Instant.now());
            return complaint;
        });

        ComplaintResponse response = service.create(new ComplaintRequest(
                "Nusrat", "01712345678", "ORD-1", "Where is my order?"));

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.phone()).isEqualTo("+8801712345678");
        assertThat(response.status()).isEqualTo(ComplaintStatus.OPEN);
        assertThat(response.dueAt()).isAfter(Instant.now().plusSeconds(71 * 3600));
        assertThat(response.overdue()).isFalse();
    }

    @Test
    void respondClosesOrMarksResponded() {
        Complaint complaint = Complaint.builder().id(1L).name("N").phone("+8801712345678")
                .message("m").status(ComplaintStatus.OPEN).dueAt(Instant.now().plusSeconds(3600)).build();
        when(repository.findById(1L)).thenReturn(Optional.of(complaint));

        ComplaintResponse responded = service.respond(1L, new ComplaintResponseRequest("Thanks", false));
        assertThat(responded.status()).isEqualTo(ComplaintStatus.RESPONDED);
        assertThat(responded.respondedAt()).isNotNull();

        ComplaintResponse closed = service.respond(1L, new ComplaintResponseRequest("Done", true));
        assertThat(closed.status()).isEqualTo(ComplaintStatus.CLOSED);
    }

    @Test
    void overdueIsTrueForOpenComplaintPastDue() {
        Complaint complaint = Complaint.builder().id(1L).name("N").phone("+8801712345678")
                .message("m").status(ComplaintStatus.OPEN)
                .dueAt(Instant.now().minusSeconds(60)).createdAt(Instant.now()).build();
        when(repository.findById(1L)).thenReturn(Optional.of(complaint));

        assertThat(service.respond(1L, new ComplaintResponseRequest("late", false))).isNotNull();
    }

    @Test
    void respondFailsForUnknownComplaint() {
        when(repository.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.respond(9L, new ComplaintResponseRequest("x", true)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listReturnsNewestFirst() {
        when(repository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());
        assertThat(service.list()).isEmpty();
        verify(repository).findAllByOrderByCreatedAtDesc();
    }
}
