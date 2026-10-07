package prototype;

import java.security.SecureRandom;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** The token vault: issue a token, look it up, revoke it. Only the bank's own services call it. */
@SpringBootApplication
@RestController
public class TokenService {

    public record TokenRecord(String accountId, String customerId, boolean active) {
    }

    public record IssueRequest(String accountId, String customerId) {
    }

    public record RevokeRequest(String customerId) {
    }

    private static final long SMALLEST_12_DIGIT_NUMBER = 100_000_000_000L;
    private static final long COUNT_OF_12_DIGIT_NUMBERS = 900_000_000_000L;

    // The vault: token -> what it stands for. In memory, so it is lost on restart.
    private final Map<String, TokenRecord> vault = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public static void main(String[] args) {
        SpringApplication.run(TokenService.class, args);
    }

    /** Returns the active token for this account and customer. Creates it the first time. */
    @PostMapping("/tokens")
    public Map<String, String> issue(@RequestBody IssueRequest request) {
        TokenRecord wanted = new TokenRecord(request.accountId(), request.customerId(), true);
        for (Map.Entry<String, TokenRecord> entry : vault.entrySet()) {
            if (entry.getValue().equals(wanted)) {
                return Map.of("token", entry.getKey());
            }
        }
        // A random 12-digit number, so it looks like an account number.
        String token = String.valueOf(SMALLEST_12_DIGIT_NUMBER + random.nextLong(COUNT_OF_12_DIGIT_NUMBERS));
        vault.put(token, wanted);
        return Map.of("token", token);
    }

    /** Kills every token that belongs to this customer. */
    @PostMapping("/tokens/revoke")
    public void revoke(@RequestBody RevokeRequest request) {
        vault.replaceAll((token, stored) -> stored.customerId().equals(request.customerId())
                ? new TokenRecord(stored.accountId(), stored.customerId(), false)
                : stored);
    }

    /** What a token stands for and whether it still works. This is detokenization. */
    @GetMapping("/tokens/{token}")
    public ResponseEntity<TokenRecord> lookup(@PathVariable String token) {
        return ResponseEntity.of(Optional.ofNullable(vault.get(token)));
    }
}
