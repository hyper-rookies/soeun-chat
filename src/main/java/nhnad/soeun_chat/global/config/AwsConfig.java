package nhnad.soeun_chat.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.athena.AthenaClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@Configuration
public class AwsConfig {

    @Value("${aws.region}")
    private String region;

    @Value("${aws.bedrock.region}")
    private String bedrockRegion;

    // SQL 생성 등 동기 호출용
    @Bean
    public BedrockRuntimeClient bedrockRuntimeClient() {
        return BedrockRuntimeClient.builder()
                .region(Region.of(bedrockRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    // 스트리밍(converseStream) 전용
    @Bean
    public BedrockRuntimeAsyncClient bedrockRuntimeAsyncClient() {
        return BedrockRuntimeAsyncClient.builder()
                .region(Region.of(bedrockRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Bean
    public DynamoDbClient dynamoDbClient() {
        return DynamoDbClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Bean
    public AthenaClient athenaClient() {
        return AthenaClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
