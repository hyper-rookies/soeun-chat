package nhnad.soeun_chat.domain.share.controller;

import lombok.RequiredArgsConstructor;
import nhnad.soeun_chat.domain.conversation.dto.ConversationResponse;
import nhnad.soeun_chat.domain.share.dto.ShareCreateResponse;
import nhnad.soeun_chat.domain.share.service.ShareService;
import nhnad.soeun_chat.global.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/share")
@RequiredArgsConstructor
public class ShareController {

    private final ShareService shareService;

    @PostMapping("/{conversationId}")
    public ResponseEntity<ApiResponse<ShareCreateResponse>> createShareLink(
            @AuthenticationPrincipal String userId,
            @PathVariable String conversationId
    ) {
        return ResponseEntity.ok(ApiResponse.of(shareService.generateShareToken(conversationId, userId)));
    }

    @GetMapping("/{token}")
    public ResponseEntity<ApiResponse<ConversationResponse>> getSharedConversation(
            @PathVariable String token
    ) {
        return ResponseEntity.ok(ApiResponse.of(shareService.getSharedConversation(token)));
    }
}
