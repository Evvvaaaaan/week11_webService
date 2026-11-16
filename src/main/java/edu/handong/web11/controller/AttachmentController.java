package edu.handong.web11.controller;

import edu.handong.web11.domain.Attachment;
import edu.handong.web11.repository.AttachmentRepository;
import edu.handong.web11.service.FileStorageService;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/attachments")
public class AttachmentController {
    private final AttachmentRepository attachments;
    private final FileStorageService storage;
    public AttachmentController(AttachmentRepository attachments, FileStorageService storage) {
        this.attachments = attachments; this.storage = storage;
    }
    // 첨부파일 다운로드: 원래 파일 이름으로 내려받게 한다.
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        // 첨부파일 정보가 없으면 404
        Attachment file = attachments.findById(id).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "첨부파일이 없습니다."));
        // 저장 이름으로 uploads 폴더의 실제 파일을 찾는다. 파일이 없으면 404
        Resource resource = storage.load(file);
        // attachment로 설정하면 브라우저가 화면에 열지 않고 파일을 내려받는다.
        // 원래 파일 이름을 UTF-8로 넣어 한글 이름도 깨지지 않게 한다.
        String disposition = ContentDisposition.attachment()
            .filename(file.getOriginalFilename(), StandardCharsets.UTF_8)
            .build().toString();
        // HTTP 다운로드 응답: 상태 코드 200
        return ResponseEntity.ok()
            // 일반 바이너리 파일 형식으로 보낸다.
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            // 다운로드 방식과 파일 이름을 헤더에 넣는다.
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
            // 응답 본문에 실제 파일 내용을 담는다.
            .body(resource);
    }
    @GetMapping("/{id}/view")
    public ResponseEntity<Resource> view(@PathVariable Long id) {
        Attachment file = attachments.findById(id).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "첨부파일이 없습니다."));
        if (!file.getContentType().startsWith("image/"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미지 파일만 미리보기할 수 있습니다.");
        Resource resource = storage.load(file);
        String disposition = ContentDisposition.inline()
            .filename(file.getOriginalFilename(), StandardCharsets.UTF_8)
            .build().toString();
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(file.getContentType()))
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
            .body(resource);
    }
}
