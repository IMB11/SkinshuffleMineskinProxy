package dev.imb11.skinshuffleproxy;

import com.google.gson.Gson;
import dev.imb11.skinshuffleproxy.data.SkinQueryResult;
import dev.imb11.skinshuffleproxy.data.SkinUploadRequest;
import io.javalin.Javalin;
import org.mineskin.Java11RequestHandler;
import org.mineskin.MineSkinClient;
import org.mineskin.data.CodeAndMessage;
import org.mineskin.data.JobInfo;
import org.mineskin.data.Visibility;
import org.mineskin.exception.MineSkinRequestException;
import org.mineskin.request.GenerateRequest;
import org.mineskin.response.MineSkinResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class AppMain {
    private static MineSkinClient mineskinClient;
    private static final Gson gson = new Gson();
    private static final ConcurrentMap<String, byte[]> receivedFiles = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        System.out.println("Launching SkinShuffle WebSocket Gateway");

        Properties props = loadProgramProperties();
        if (props == null) return;

        String mineskinApiKey = props.getProperty("token.mineskin");
        String appUserAgent = props.getProperty("app.useragent");
        int appPort = Integer.parseInt(props.getProperty("app.port"));

        if ("REPLACE_ME".equals(mineskinApiKey)) {
            System.err.println("Error: Please replace 'REPLACE_ME' with your actual MineSkin API key in local.properties");
            return;
        }

        mineskinClient = MineSkinClient.builder()
                .apiKey(mineskinApiKey)
                .requestHandler(Java11RequestHandler::new)
                .userAgent(appUserAgent)
                .build();

        Javalin app = Javalin.create();

        app.ws("/skin-gateway", ws -> {
            ws.onConnect(ctx -> {
                System.out.println("Client connected: " + ctx.session);
            });

            ws.onClose((ctx) -> {
                System.out.println("Client disconnected: " + ctx.session + " (Status: " + ctx.closeStatus() + ", Reason: " + ctx.reason() + ")");
            });

            ws.onBinaryMessage(ctx -> {
                byte[] receivedChunk = ctx.data();
                String sessionId = ctx.sessionId();

                // Append the received chunk to the stored data
                byte[] existingData = receivedFiles.getOrDefault(sessionId, new byte[0]);
                byte[] newData = new byte[existingData.length + receivedChunk.length];
                System.arraycopy(existingData, 0, newData, 0, existingData.length);
                System.arraycopy(receivedChunk, 0, newData, existingData.length, receivedChunk.length);
                receivedFiles.put(sessionId, newData);
            });

            ws.onMessage((ctx) -> {
                System.out.println("Received message from " + ctx.sessionId() + ": " + ctx.message());

                SkinUploadRequest request = ctx.messageAsClass(SkinUploadRequest.class);
                GenerateRequest requestBody;
                if (request.type().equals("file")) {
                    byte[] fileData = receivedFiles.get(ctx.sessionId());
                    receivedFiles.remove(ctx.sessionId());
                    try (var input = new ByteArrayInputStream(fileData)) {
                        requestBody = GenerateRequest.upload(input)
                                .name("SkinShuffle")
                                .visibility(Visibility.UNLISTED);
                    } catch (Exception e) {
                        requestBody = null;
                        e.printStackTrace();
                    }
                } else {
                    String url = request.url();
                    requestBody = GenerateRequest.url(url)
                            .name("SkinShuffle")
                            .visibility(Visibility.UNLISTED);
                }

                if (requestBody == null) {
                    ctx.send("FailedGenerateRequest");
                    return;
                }

                var result = mineskinClient.queue().submit(requestBody).thenCompose(queueResponse -> {
                            JobInfo job = queueResponse.getJob();
                            return job.waitForCompletion(mineskinClient);
                        })
                        .thenCompose(jobResponse -> jobResponse.getOrLoadSkin(mineskinClient))
                        .thenApply(skinInfo -> new SkinQueryResult(false, null, request.model(), skinInfo.texture().data().signature(), skinInfo.texture().data().value()))
                        .exceptionally(throwable -> {
                            throwable.printStackTrace();
                            if (throwable instanceof CompletionException completionException) {
                                throwable = completionException.getCause();
                            }

                            if (throwable instanceof MineSkinRequestException requestException) {
                                // get error details
                                MineSkinResponse<?> response = requestException.getResponse();
                                Optional<CodeAndMessage> detailsOptional = response.getErrorOrMessage();
                                detailsOptional.ifPresent(details -> {
                                    System.out.println(details.code() + ": " + details.message());
                                });
                            }
                            ctx.send("FailedRequest");
                            return null;
                        }).join();

                if (result != null) {
                    ctx.send(result);
                }
            });

            ws.onError((ctx) -> {
                System.err.println("WebSocket error occurred for session " + ctx.session + ": " + ctx.error());
                ctx.error().printStackTrace();
                ctx.send("Fail");
            });
        });

        System.out.println("SkinShuffle WebSocket Gateway started on port " + appPort);
        app.start(appPort);
    }

    private static Properties loadProgramProperties() {
        Properties properties = new Properties();
        try (InputStream input = AppMain.class.getClassLoader().getResourceAsStream("local.properties")) {
            if (input == null) {
                System.out.println("Sorry, unable to find local.properties");
                return null;
            }
            properties.load(input);
            return properties;
        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }
    }
}