package nhnad.soeun_chat.domain.report.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nhnad.soeun_chat.domain.conversation.dto.ConversationResponse;
import nhnad.soeun_chat.domain.conversation.dto.MessageItem;
import nhnad.soeun_chat.domain.report.dto.ReportDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReportS3Loader {

    @Value("${aws.s3.bucket}")
    private String s3Bucket;

    private final S3Client s3Client;
    private final ObjectMapper objectMapper;

    public ConversationResponse load(Map<String, AttributeValue> item, String s3Key) {
        try {
            ResponseInputStream<GetObjectResponse> s3Object = s3Client.getObject(
                    GetObjectRequest.builder()
                            .bucket(s3Bucket)
                            .key(s3Key)
                            .build()
            );

            ReportDocument doc = objectMapper.readValue(s3Object, ReportDocument.class);
            log.info("리포트 S3 로드 완료 - conversationId: {}, s3Key: {}", doc.conversationId(), s3Key);

            List<MessageItem> messages = doc.messages().stream()
                    .map(msg -> new MessageItem(
                            null,
                            msg.role(),
                            msg.content(),
                            doc.createdAt(),
                            msg.chartType(),
                            msg.structuredData()
                    ))
                    .toList();

            long now = Instant.now().toEpochMilli();
            return new ConversationResponse(
                    doc.conversationId(),
                    doc.title(),
                    attrNOrDefault(item, "createdAt", now),
                    attrNOrDefault(item, "updatedAt", now),
                    messages
            );
        } catch (Exception e) {
            log.error("리포트 S3 로드 실패 - s3Key: {}", s3Key, e);
            throw new RuntimeException("리포트 S3 로드 중 오류 발생", e);
        }
    }

    private Long attrNOrDefault(Map<String, AttributeValue> item, String key, Long defaultValue) {
        AttributeValue v = item.get(key);
        return (v != null && v.n() != null) ? Long.parseLong(v.n()) : defaultValue;
    }
}
