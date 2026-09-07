package com.orbitflow.attachment;

import com.orbitflow.common.security.UserPrincipal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class AttachmentController {
    private final AttachmentService service;

    @PostMapping("/api/v1/tasks/{taskId}/attachments/reserve")
    @ResponseStatus(HttpStatus.CREATED)
    public AttachmentService.ReservationDto reserve(@AuthenticationPrincipal UserPrincipal p,
                                                    @PathVariable UUID taskId,
                                                    @RequestBody Map<String, Object> body) {
        Object size = body.get("sizeBytes");
        long sizeBytes = size instanceof Number n ? n.longValue() : 0L;
        return service.reserveUpload(p.getId(), taskId,
                (String) body.get("fileName"), (String) body.get("contentType"), sizeBytes);
    }

    @PostMapping("/api/v1/attachments/{attachmentId}/complete")
    public AttachmentService.AttachmentDto complete(@AuthenticationPrincipal UserPrincipal p,
                                                    @PathVariable UUID attachmentId,
                                                    @RequestBody(required = false) Map<String, Object> body) {
        byte[] inline = null;
        if (body != null && body.get("contentBase64") instanceof String s && !s.isBlank()) {
            inline = java.util.Base64.getDecoder().decode(s);
        }
        return service.completeUpload(p.getId(), attachmentId, inline);
    }

    @GetMapping("/api/v1/tasks/{taskId}/attachments")
    public List<AttachmentService.AttachmentDto> list(@AuthenticationPrincipal UserPrincipal p,
                                                      @PathVariable UUID taskId) {
        return service.listForTask(p.getId(), taskId);
    }

    @GetMapping("/api/v1/attachments/{attachmentId}/download")
    public AttachmentService.AttachmentDto download(@AuthenticationPrincipal UserPrincipal p,
                                                    @PathVariable UUID attachmentId) {
        return service.downloadUrl(p.getId(), attachmentId);
    }
}
