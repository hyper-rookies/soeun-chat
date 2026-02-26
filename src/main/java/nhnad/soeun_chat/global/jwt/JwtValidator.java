package nhnad.soeun_chat.global.jwt;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.nimbusds.jose.JWSAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URL;

@Component
public class JwtValidator {

    private final ConfigurableJWTProcessor<SecurityContext> jwtProcessor;

    public JwtValidator(@Value("${aws.cognito.region}") String region,
                        @Value("${aws.cognito.user-pool-id}") String userPoolId) throws Exception {
        String jwksUrl = String.format(
                "https://cognito-idp.%s.amazonaws.com/%s/.well-known/jwks.json",
                region, userPoolId
        );

        JWKSource<SecurityContext> jwkSource = new RemoteJWKSet<>(new URL(jwksUrl));
        JWSVerificationKeySelector<SecurityContext> keySelector =
                new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, jwkSource);

        jwtProcessor = new DefaultJWTProcessor<>();
        jwtProcessor.setJWSKeySelector(keySelector);
    }

    public JWTClaimsSet validate(String token) throws Exception {
        return jwtProcessor.process(token, null);
    }
}