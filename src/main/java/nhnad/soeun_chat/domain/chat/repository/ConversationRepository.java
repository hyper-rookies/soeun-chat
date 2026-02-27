package nhnad.soeun_chat.domain.chat.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Repository
@RequiredArgsConstructor
public class ConversationRepository {

    private static final String TABLE_NAME = "se_conversations";

    private final DynamoDbClient dynamoDbClient;

    public Optional<Map<String, AttributeValue>> findById(String conversationId) {
        GetItemResponse response = dynamoDbClient.getItem(GetItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(Map.of("conversationId", AttributeValue.fromS(conversationId)))
                .build());
        boolean found = response.hasItem();
        log.debug("대화 조회 - conversationId: {}, found: {}", conversationId, found);
        return found ? Optional.of(response.item()) : Optional.empty();
    }

    public void save(String conversationId, String userId) {
        String now = Instant.now().toString();
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("conversationId", AttributeValue.fromS(conversationId));
        item.put("userId", AttributeValue.fromS(userId));
        item.put("createdAt", AttributeValue.fromS(now));
        item.put("updatedAt", AttributeValue.fromS(now));

        dynamoDbClient.putItem(PutItemRequest.builder()
                .tableName(TABLE_NAME)
                .item(item)
                .build());
        log.info("대화 생성 - conversationId: {}, userId: {}", conversationId, userId);
    }

    public void updateTimestamp(String conversationId) {
        dynamoDbClient.updateItem(UpdateItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(Map.of("conversationId", AttributeValue.fromS(conversationId)))
                .updateExpression("SET updatedAt = :updatedAt")
                .expressionAttributeValues(Map.of(":updatedAt", AttributeValue.fromS(Instant.now().toString())))
                .build());
        log.debug("대화 타임스탬프 갱신 - conversationId: {}", conversationId);
    }
}
