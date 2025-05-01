package dev.imb11.skinshuffleproxy;

import com.google.gson.Gson;
import dev.imb11.skinshuffleproxy.data.SkinQueryResult;
import dev.imb11.skinshuffleproxy.data.SkinUploadRequest;
import dev.imb11.skinshuffleproxy.status.StatusInfo;
import io.javalin.Javalin;
import io.javalin.http.HttpStatus;
import org.mineskin.Java11RequestHandler;
import org.mineskin.MineSkinClient;
import org.mineskin.data.CodeAndMessage;
import org.mineskin.data.JobInfo;
import org.mineskin.data.Visibility;
import org.mineskin.exception.MineSkinRequestException;
import org.mineskin.request.GenerateRequest;
import org.mineskin.response.MineSkinResponse;

import java.io.ByteArrayInputStream;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class AppMain {
    private static MineSkinClient MINESKIN_CLIENT;
    private static final Gson GSON = new Gson();
    private static final ConcurrentMap<String, byte[]> RECIEVED_SKIN_FILES_CACHE = new ConcurrentHashMap<>();

    // Environment variable names
    private static final String ENV_MINESKIN_TOKEN = "TOKEN_MINESKIN";
    private static final String ENV_APP_PORT = "APP_PORT";
    private static final String ENV_APP_USERAGENT = "APP_USERAGENT";

    public static int UPTIME_SKINS_PROCESSED = 0;
    public static final Date UPTIME_START_DATE = new Date();

    public static void main(String[] args) {
        System.out.println("Launching SkinShuffle WebSocket Gateway");

        // Get required configuration from environment variables
        String mineskinApiKey = System.getenv(ENV_MINESKIN_TOKEN);
        if (mineskinApiKey == null || mineskinApiKey.isEmpty() || mineskinApiKey.equals("REPLACE_ME")) {
            System.err.println("Error: " + ENV_MINESKIN_TOKEN + " environment variable must be set");
            System.exit(1);
            return;
        }

        // Get optional configuration with defaults
        String appUserAgent = getEnvOrDefault(ENV_APP_USERAGENT, "SkinShuffle/Proxy");
        int appPort = Integer.parseInt(getEnvOrDefault(ENV_APP_PORT, "28433"));

        MINESKIN_CLIENT = MineSkinClient.builder()
                .apiKey(mineskinApiKey)
                .requestHandler(Java11RequestHandler::new)
                .userAgent(appUserAgent)
                .build();

        Javalin app = Javalin.create();

        app.get("/", context -> {
            context.status(HttpStatus.OK);
            StatusInfo info = new StatusInfo(UPTIME_START_DATE, UPTIME_SKINS_PROCESSED);
            context.result(GSON.toJson(info));
        });

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
                byte[] existingData = RECIEVED_SKIN_FILES_CACHE.getOrDefault(sessionId, new byte[0]);
                byte[] newData = new byte[existingData.length + receivedChunk.length];
                System.arraycopy(existingData, 0, newData, 0, existingData.length);
                System.arraycopy(receivedChunk, 0, newData, existingData.length, receivedChunk.length);
                RECIEVED_SKIN_FILES_CACHE.put(sessionId, newData);
            });

            ws.onMessage((ctx) -> {
                System.out.println("Received message from " + ctx.sessionId() + ": " + ctx.message());

                SkinUploadRequest request = ctx.messageAsClass(SkinUploadRequest.class);
                GenerateRequest requestBody;
                if (request.type().equals("file")) {
                    byte[] fileData = RECIEVED_SKIN_FILES_CACHE.get(ctx.sessionId());
                    RECIEVED_SKIN_FILES_CACHE.remove(ctx.sessionId());
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

                var result = MINESKIN_CLIENT.queue().submit(requestBody).thenCompose(queueResponse -> {
                            JobInfo job = queueResponse.getJob();
                            return job.waitForCompletion(MINESKIN_CLIENT);
                        })
                        .thenCompose(jobResponse -> jobResponse.getOrLoadSkin(MINESKIN_CLIENT))
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
                    UPTIME_SKINS_PROCESSED++;
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

    /**
     * Gets a value from environment variables with a fallback to default
     * 
     * @param name Environment variable name
     * @param defaultValue Default value if environment variable is not set
     * @return The value from environment variable or default
     */
    private static String getEnvOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        if (value != null && !value.isEmpty()) {
            System.out.println("Using " + name + " from environment: " + value);
            return value;
        }
        
        System.out.println("Using default value for " + name + ": " + defaultValue);
        return defaultValue;
    }
}