package nhnad.soeun_chat.global;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import nhnad.soeun_chat.global.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Health", description = "서버 상태 확인")
@RestController
public class HealthController {

    @Operation(
            summary = "헬스 체크",
            description = "서버가 정상 동작 중인지 확인합니다. **인증 불필요.** ALB·로드밸런서 헬스 체크 엔드포인트로도 사용됩니다."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "서버 정상",
            content = @Content(
                    mediaType = "application/json",
                    examples = @ExampleObject(value = "{\"success\":true,\"data\":\"OK\"}")
            )
    )
    @SecurityRequirements
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<String>> health() {
        return ResponseEntity.ok(ApiResponse.of("OK"));
    }
}
