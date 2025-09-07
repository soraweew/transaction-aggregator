import org.hyperskill.hstest.dynamic.DynamicTest;
import org.hyperskill.hstest.dynamic.input.DynamicTesting;
import org.hyperskill.hstest.exception.outcomes.WrongAnswer;
import org.hyperskill.hstest.mocks.web.response.HttpResponse;
import org.hyperskill.hstest.stage.SpringTest;
import org.hyperskill.hstest.testcase.CheckResult;
import org.hyperskill.hstest.testing.expect.json.builder.JsonArrayBuilder;
import org.hyperskill.hstest.testing.expect.json.builder.JsonObjectBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.hyperskill.hstest.testing.expect.Expectation.expect;
import static org.hyperskill.hstest.testing.expect.json.JsonChecker.isArray;
import static org.hyperskill.hstest.testing.expect.json.JsonChecker.isObject;

public class ApplicationTests extends SpringTest {
    private final DataServer dataServer1 = new DataServer("server-1", 8888, "033", "128");
    private final DataServer dataServer2 = new DataServer("server-2", 8889, "033", "128");

    CheckResult testAggregate(String account) {
        CheckResult result = null;
        var start = Instant.now();
        var response = get("/aggregate")
                .addParam("account", account)
                .send();

        System.out.println(getRequestDetails(response));

        var delay = Duration.between(start, Instant.now()).getSeconds();
        if (delay > 4) {
            var message = "It appears your application doesn't use asynchronous requests and/or proper data caching";
            result = CheckResult.wrong(message);
        }

        if (result == null && response.getStatusCode() == 200) {
            var list1 = dataServer1.getTransactions(account);
            var list2 = dataServer2.getTransactions(account);
            var expected = Stream.of(list1, list2)
                    .flatMap(List::stream)
                    .sorted(Comparator.comparing(Transaction::timestamp).reversed())
                    .toList();
            try {
                checkJson(response, expected);
                result = CheckResult.correct();
            } catch (WrongAnswer e) {
                result = CheckResult.wrong(e.getFeedbackText());
            }
        } else if (result == null) {
            result = CheckResult.wrong("Expected response status code 200 but got " + response.getStatusCode());
        }

        if (!result.isCorrect()) {
            dataServer1.stop();
            dataServer2.stop();
        }

        return result;
    }

    CheckResult stopMockServers() {
        dataServer1.stop();
        dataServer2.stop();
        return CheckResult.correct();
    }

    private void checkJson(HttpResponse response, List<Transaction> expected) {
        JsonArrayBuilder arrayBuilder = isArray(expected.size());
        for (var tx : expected) {
            JsonObjectBuilder objectBuilder = isObject()
                    .value("id", tx.id())
                    .value("serverId", tx.serverId())
                    .value("account", tx.account())
                    .value("amount", tx.amount())
                    .value("timestamp", tx.timestamp());
            arrayBuilder = arrayBuilder.item(objectBuilder);
        }
        expect(response.getContent()).asJson().check(arrayBuilder);
    }

    private String getRequestDetails(HttpResponse response) {
        var uri = response.getRequest().getUri();
        var method = response.getRequest().getMethod();
        return "\nRequest: %s %s".formatted(method, uri);
    }

    @DynamicTest
    DynamicTesting[] dt = new DynamicTesting[] {
            () -> testAggregate("033"),
            () -> testAggregate("128"),
            () -> testAggregate("255"),
            () -> testAggregate("033"),
            () -> testAggregate("033"),
            () -> testAggregate("128"),
            () -> testAggregate("128"),
            () -> testAggregate("255"),
            () -> testAggregate("255"),
            this::stopMockServers
    };
}
