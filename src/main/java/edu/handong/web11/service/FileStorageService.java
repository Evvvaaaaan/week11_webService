package edu.handong.web11.service;

import edu.handong.web11.domain.Attachment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class FileStorageService {
    private static final Set<String> ALLOWED = Set.of("jpg", "jpeg", "png", "gif", "pdf", "txt");
    private final Path uploadDir;

    public FileStorageService(@Value("${app.upload-dir}") String path) throws IOException {
        uploadDir = Path.of(path).toAbsolutePath().normalize();
        Files.createDirectories(uploadDir);
    }

    // 업로드 파일을 검사한 뒤 UUID 이름으로 저장하고, DB에 넣을 Attachment를 만든다.
    public Attachment store(MultipartFile file) {
        // 파일 검증 1: 내용이 없는 빈 파일은 거부한다.
        if (file.isEmpty()) throw new IllegalArgumentException("빈 파일은 업로드할 수 없습니다.");
        // 사용자가 올린 파일의 원래 이름을 가져온다.
        String original = file.getOriginalFilename();
        // 파일 검증 2: 이름이 없거나 /, \, ..이 들어 있으면 다른 폴더를 가리킬 수 있어 거부한다.
        if (original == null || original.isBlank() || original.contains("/") || original.contains("\\") || original.contains(".."))
            throw new IllegalArgumentException("잘못된 파일 이름입니다.");
        // 파일 검증 3: 이름이 240자를 넘으면 거부한다.
        if (original.length() > 240) throw new IllegalArgumentException("파일 이름이 너무 깁니다.");
        // 마지막 점 뒤의 글자를 소문자 확장자로 꺼낸다. 점이 없으면 빈 문자열이다.
        int dot = original.lastIndexOf('.');
        String ext = dot < 0 ? "" : original.substring(dot + 1).toLowerCase(Locale.ROOT);
        // 파일 검증 4: 허용 목록에 없는 확장자는 거부한다.
        if (!ALLOWED.contains(ext)) throw new IllegalArgumentException("허용 형식: jpg, jpeg, png, gif, pdf, txt");
        // 파일 검증 5: 10MB를 넘는 파일은 거부한다.
        if (file.getSize() > 10L * 1024 * 1024) throw new IllegalArgumentException("파일은 10MB 이하만 가능합니다.");
        // 이름 충돌을 막기 위해 UUID로 저장 이름을 만든다.
        String stored = UUID.randomUUID() + "." + ext;
        // 저장 경로 생성: uploads 폴더 경로 뒤에 저장 이름을 붙이고 경로를 정리한다.
        Path target = uploadDir.resolve(stored).normalize();
        // 만든 경로가 uploads 폴더 밖이면 저장하지 않는다.
        if (!target.startsWith(uploadDir)) throw new IllegalArgumentException("잘못된 파일 경로입니다.");
        // 실제 파일 저장: 업로드된 파일 내용을 읽는 스트림을 연다. try가 끝나면 자동으로 닫힌다.
        try (InputStream input = file.getInputStream()) {
            // 스트림 내용을 target 경로에 파일로 복사한다.
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            // Teaching sample: MIME type is inferred by extension, not trusted from request headers.
            // 확장자에 맞는 Content-Type을 정한다.
            String contentType = switch (ext) {
                case "jpg", "jpeg" -> "image/jpeg";
                case "png" -> "image/png";
                case "gif" -> "image/gif";
                case "pdf" -> "application/pdf";
                default -> "text/plain";
            };
            // 원래 이름, 저장 이름, 형식, 크기를 담은 Attachment를 돌려준다. DB 저장은 PostService가 한다.
            return new Attachment(original, stored, contentType, file.getSize());
        } catch (IOException e) {
            // 저장 중 오류가 나면 일부만 만들어진 파일을 지운다.
            try { Files.deleteIfExists(target); } catch (IOException ignored) {}
            // 500 오류로 응답한다.
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "파일 저장 실패", e);
        }
    }

    public Resource load(Attachment attachment) {
        Path target = uploadDir.resolve(attachment.getStoredFilename()).normalize();
        if (!target.startsWith(uploadDir) || !Files.isRegularFile(target))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "파일이 없습니다.");
        return new PathResource(target);
    }

    public void deleteIfExists(Attachment attachment) {
        try {
            Files.deleteIfExists(uploadDir.resolve(attachment.getStoredFilename()).normalize());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "파일 삭제 실패", e);
        }
    }
}
