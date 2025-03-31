package dev.imb11.skinshuffleproxy;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.*;

public class TestSkinUpload {

    public static void main(String[] args) throws Exception {
        // Get the port, skin URL and file path either from command-line args or use defaults.
        int port = 28433;
        String skinUrl = args.length > 1 ? args[1] : "https://www.minecraftskins.com/uploads/skins/2025/03/12/jimmy-mouthwashing--original-by-aesthxtic--23114880.png?v781";
        String filePath = args.length > 2 ? args[2] : "./test-skin.png";

        // Build the WebSocket URI assuming the app is running locally.
        URI uri = URI.create("ws://localhost:" + port + "/skin-gateway");
        HttpClient client = HttpClient.newHttpClient();
        BlockingQueue<String> responses = new LinkedBlockingQueue<>();

        // Connect to the WebSocket endpoint.
        WebSocket webSocket = client.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .buildAsync(uri, new SocketListener(responses))
                .join();

        // --- Upload URL Skin ---
        // Construct JSON request for URL-based skin upload. The JSON fields should match your SkinUploadRequest.
        // For this example, "type" is set to "url", and "model" is set to a default value.
        String urlSkinRequest = String.format("{\"type\":\"url\",\"url\":\"%s\",\"model\":\"default\"}", skinUrl);
        System.out.println("Sending URL skin upload request: " + urlSkinRequest);
        webSocket.sendText(urlSkinRequest, true).join();

        // Block and wait for the response.
        String urlResponse = responses.poll(30, TimeUnit.SECONDS);
        if (urlResponse != null) {
            System.out.println("URL upload response: " + urlResponse);
        } else {
            System.err.println("Timed out waiting for URL upload response");
        }

        // --- Upload File Skin ---
        // Read file bytes from the given file path.
        byte[] fileBytes;
        try {
            fileBytes = Files.readAllBytes(Path.of(filePath));
        } catch (IOException e) {
            System.err.println("Error reading file from path: " + filePath);
            e.printStackTrace();
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Error reading file").join();
            return;
        }

        // Send the file bytes as a binary message.
        System.out.println("Sending binary data from file: " + filePath);
        webSocket.sendBinary(ByteBuffer.wrap(fileBytes), true).join();

        // Next, send the JSON request indicating that a file upload is being performed.
        String fileSkinRequest = "{\"type\":\"file\",\"model\":\"default\"}";
        System.out.println("Sending file skin upload request: " + fileSkinRequest);
        webSocket.sendText(fileSkinRequest, true).join();

        // Block and wait for the file upload response.
        String fileResponse = responses.poll(30, TimeUnit.SECONDS);
        if (fileResponse != null) {
            System.out.println("File upload response: " + fileResponse);
        } else {
            System.err.println("Timed out waiting for file upload response");
        }

        // Close the WebSocket connection.
        webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Test complete").join();
        System.out.println("Test complete. Connection closed.");
    }

    private record SocketListener(BlockingQueue<String> responses) implements WebSocket.Listener {
        @Override
        public void onOpen(WebSocket webSocket) {
            System.out.println("Connected to server");
            WebSocket.Listener.super.onOpen(webSocket);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            String message = data.toString();
            System.out.println("Received text message: " + message);
            responses.offer(message);
            return WebSocket.Listener.super.onText(webSocket, data, last);
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            // If needed, handle binary messages here.
            System.out.println("Received binary message of " + data.remaining() + " bytes");
            return WebSocket.Listener.super.onBinary(webSocket, data, last);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            System.out.println("WebSocket closed with status " + statusCode + " and reason: " + reason);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            System.err.println("WebSocket error: " + error.getMessage());
            WebSocket.Listener.super.onError(webSocket, error);
        }
    }
}