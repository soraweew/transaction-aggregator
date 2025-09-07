import com.google.gson.Gson;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class DataServer {
    private final HttpServer server;
    private final String serverId;
    private final Map<String, List<Transaction>> transactions;

    public DataServer(String id, int port, String... accounts) {
        serverId = id;
        transactions = generate(accounts);
        try {
            InetAddress address = InetAddress.getByName("127.0.0.1");
            server = HttpServer.create(new InetSocketAddress(address, port), 0);

            HttpContext pingContext = server.createContext("/transactions");
            pingContext.setHandler(new TransactionsHttpHandler(serverId, transactions));

            server.start();
        } catch (IOException e) {
            throw new RuntimeException("Failed to start the local data service: " + e.getMessage());
        }
    }

    public List<Transaction> getTransactions(String account) {
        return transactions.getOrDefault(account, List.of());
    }

    public void stop() {
        if (server != null) {
            server.stop(1);
        }
    }

    private Map<String, List<Transaction>> generate(String... accounts) {
        var rnd = new Random();
        Map<String, List<Transaction>> transactions = new HashMap<>(accounts.length);
        for (var account : accounts) {
            var size = rnd.nextInt(3, 10);
            List<Transaction> txList = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                var tx = new Transaction(
                        UUID.randomUUID().toString(),
                        serverId,
                        account,
                        String.valueOf(rnd.nextInt(10, 10_000)),
                        LocalDateTime.now()
                                .minusDays(rnd.nextLong(1, 30))
                                .plusHours(rnd.nextInt(0, 24))
                                .plusMinutes(rnd.nextInt(0, 60))
                                .plusSeconds(rnd.nextInt(0, 60))
                                .toString()
                );
                txList.add(tx);
            }
            transactions.put(account, txList);
        }
        return transactions;
    }
}

class TransactionsHttpHandler implements HttpHandler {
    private final String serverId;
    private final Gson gson = new Gson();
    private final Map<String, List<Transaction>> transactions;
    private final List<Integer> responseCodes;
    private int index = 0;
    private int delay = 800;

    public TransactionsHttpHandler(String serverId, Map<String, List<Transaction>> transactions) {
        this.serverId = serverId;
        this.transactions = transactions;
        responseCodes = Stream.of(
                        Stream.generate(() -> 503).limit(1).toList(),
                        Stream.generate(() -> 529).limit(3).toList(),
                        Stream.generate(() -> 200).limit(1).toList()
                )
                .flatMap(List::stream)
                .collect(Collectors.toCollection(ArrayList::new));
        Collections.shuffle(responseCodes);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if ("GET".equals(exchange.getRequestMethod())) {
            var tx = parseQuery(exchange);
            var code = responseCodes.get(index);
            String body = "";
            index = (index + 1) % responseCodes.size();

            if (code == 200) {
                sleep();
                body = gson.toJson(tx);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(code, body.length());
            } else {
                exchange.sendResponseHeaders(code, 0);
            }

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body.getBytes());
            }

        } else {
            var body = "Method not supported";
            exchange.sendResponseHeaders(405, body.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    private void sleep() {
        try {
            System.out.printf(serverId + " is executing a long query for %d ms...%n", delay);
            Thread.sleep(delay);
        } catch (InterruptedException ignored) {
            System.out.println("Thread " + Thread.currentThread().getName()
                    + " awaken from the sleep for " + delay + "ms");
        }
        delay *= 2;
    }

    private List<Transaction> parseQuery(HttpExchange exchange) {
        var query = exchange.getRequestURI().getQuery();
        if (query == null) {
            return List.of();
        }
        var queryParams = query.split("&");
        for (var param : queryParams) {
            var parts = param.split("=");
            if (parts.length < 2) {
                continue;
            }
            if ("account".equalsIgnoreCase(parts[0])) {
                return transactions.getOrDefault(parts[1], List.of());
            }
        }
        return List.of();
    }
}

record Transaction(
        String id,
        String serverId,
        String account,
        String amount,
        String timestamp
) {
}
