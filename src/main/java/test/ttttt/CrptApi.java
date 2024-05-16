package test.ttttt;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class CrptApi {
    private final int requestLimit;
    private final long timeIntervalMillis;
    private final Semaphore semaphore;
    private final ScheduledExecutorService scheduler;
    private final AtomicInteger requestCount;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.requestLimit = requestLimit;
        this.timeIntervalMillis = timeUnit.toMillis(1); // Convert timeUnit to milliseconds
        this.semaphore = new Semaphore(requestLimit, true);
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.requestCount = new AtomicInteger(0);

        this.scheduler.scheduleAtFixedRate(() -> {
            semaphore.release(requestLimit - semaphore.availablePermits());
            requestCount.set(0);
        }, timeIntervalMillis, timeIntervalMillis, TimeUnit.MILLISECONDS);
    }

    public void createDocument(Document document, String signature) throws InterruptedException, IOException {
        semaphore.acquire();
        try {
            if (requestCount.incrementAndGet() > requestLimit) {
                throw new IllegalStateException("Request limit exceeded");
            }
            sendPostRequest(document, signature);
        } finally {
            semaphore.release();
        }
    }

    private void sendPostRequest(Document document, String signature) throws IOException {
        URL url = new URL("https://ismp.crpt.ru/api/v3/lk/documents/create");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Signature", signature);
        conn.setDoOutput(true);

        ObjectMapper objectMapper = new ObjectMapper();
        String jsonDocument = objectMapper.writeValueAsString(document);

        try (var os = conn.getOutputStream()) {
            byte[] input = jsonDocument.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            throw new IOException("Failed to create document, response code: " + responseCode);
        }
    }

    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(1, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    //Test
    public static void main(String[] args) throws InterruptedException {
        CrptApi api = new CrptApi(TimeUnit.SECONDS, 5);
        ExecutorService executor = Executors.newFixedThreadPool(10);
        for (int i = 0; i < 20; i++) {
            executor.submit(() -> {
                try {
                    Document.Description description = Document.Description.builder()
                            .participantInn("1234567890")
                            .build();

                    Document.Product product = Document.Product.builder()
                            .certificateDocument("Certificate123")
                            .certificateDocumentDate(new SimpleDateFormat("yyyy-MM-dd").parse("2024-01-23"))
                            .certificateDocumentNumber("Cert123Num")
                            .ownerInn("1234567890")
                            .producerInn("0987654321")
                            .productionDate(new SimpleDateFormat("yyyy-MM-dd").parse("2024-01-23"))
                            .tnvedCode("TNVED123")
                            .uitCode("UIT123")
                            .uituCode("UITU123")
                            .build();

                    List<Document.Product> products = new ArrayList<>();
                    products.add(product);

                    Document document = Document.builder()
                            .description(description)
                            .docId("Doc123")
                            .docStatus("Draft")
                            .docType("LP_INTRODUCE_GOODS")
                            .importRequest(true)
                            .ownerInn("1234567890")
                            .participantInn("1234567890")
                            .producerInn("0987654321")
                            .productionDate(new SimpleDateFormat("yyyy-MM-dd").parse("2024-01-23"))
                            .productionType("TypeA")
                            .products(products)
                            .regDate(new SimpleDateFormat("yyyy-MM-dd").parse("2024-01-23"))
                            .regNumber("Reg123")
                            .build();

                    String signature = "signature";
                    api.createDocument(document, signature);
                } catch (InterruptedException | IOException | ParseException e) {
                    e.printStackTrace();
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.MINUTES);
        api.shutdown();
    }


    @Data
    @Builder
    public static class Document {
        @JsonProperty("description")
        private Description description;

        @JsonProperty("doc_id")
        private String docId;

        @JsonProperty("doc_status")
        private String docStatus;

        @JsonProperty("doc_type")
        private String docType;

        @JsonProperty("importRequest")
        private boolean importRequest;

        @JsonProperty("owner_inn")
        private String ownerInn;

        @JsonProperty("participant_inn")
        private String participantInn;

        @JsonProperty("producer_inn")
        private String producerInn;

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        @JsonProperty("production_date")
        private Date productionDate;

        @JsonProperty("production_type")
        private String productionType;

        @JsonProperty("products")
        private List<Product> products;

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        @JsonProperty("reg_date")
        private Date regDate;

        @JsonProperty("reg_number")
        private String regNumber;

        @Data
        @Builder
        public static class Description {
            @JsonProperty("participantInn")
            private String participantInn;

        }

        @Data
        @Builder
        public static class Product {
            @JsonProperty("certificate_document")
            private String certificateDocument;

            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
            @JsonProperty("certificate_document_date")
            private Date certificateDocumentDate;

            @JsonProperty("certificate_document_number")
            private String certificateDocumentNumber;

            @JsonProperty("owner_inn")
            private String ownerInn;

            @JsonProperty("producer_inn")
            private String producerInn;

            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
            @JsonProperty("production_date")
            private Date productionDate;

            @JsonProperty("tnved_code")
            private String tnvedCode;

            @JsonProperty("uit_code")
            private String uitCode;

            @JsonProperty("uitu_code")
            private String uituCode;
        }
    }

}

