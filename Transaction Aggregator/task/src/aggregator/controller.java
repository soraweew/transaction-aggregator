package aggregator;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@RestController
public class controller {

    @GetMapping("/aggregate")
    public List<Transaction> getAggregate(@RequestParam String account) {

        String apiUrl1 = String.format("http://localhost:8888/transactions?account=%s", account);
        String apiUrl2 = String.format("http://localhost:8889/transactions?account=%s", account);

        List<Transaction> transactions = new ArrayList<>();

        for (String url : List.of(apiUrl1, apiUrl2)) {
            transactions.addAll(getTransactions(url));
        }

        transactions.sort(Comparator.comparing(Transaction::getTimestamp).reversed());

        return transactions;
    }

    private List<Transaction> getTransactions(String url) {
        RestTemplate restTemplate = new RestTemplate();

        int retryCount = 0;
        while (retryCount <= 5) {
            try {
                ResponseEntity<List<Transaction>> response = restTemplate
                        .exchange(url, HttpMethod.GET, null, new ParameterizedTypeReference<>() {
                        });

                return response.getBody();
            } catch (RestClientException ignored) {
            }

            retryCount++;
        }

        return List.of();
    }
}
