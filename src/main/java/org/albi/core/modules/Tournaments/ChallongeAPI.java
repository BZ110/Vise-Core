package org.albi.core.modules.Tournaments;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.albi.core.Core;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

public class ChallongeAPI {

    private final String apiKey;
    private final String username;
    private final HttpClient client;
    private final Gson gson;
    private static final String BASE_URL = "https://api.challonge.com/v1/";

    public ChallongeAPI(String apiKey, String username) {
        this.apiKey = apiKey;
        this.username = username;
        this.client = HttpClient.newHttpClient();
        this.gson = new Gson();
    }

    private String getUrl(String endpoint) {
        String url = BASE_URL + endpoint;
        if (url.contains("?")) {
            url += "&api_key=" + apiKey;
        } else {
            url += "?api_key=" + apiKey;
        }
        return url;
    }

    public CompletableFuture<JsonObject> createTournament(String name, String urlPrefix) {
        JsonObject payload = new JsonObject();
        JsonObject tournament = new JsonObject();
        tournament.addProperty("name", name);
        tournament.addProperty("tournament_type", "single elimination");
        // Using a random string for URL to prevent collision
        tournament.addProperty("url", urlPrefix + "_" + System.currentTimeMillis());
        payload.add("tournament", tournament);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getUrl("tournaments.json")))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        Core.getInstance().getLogger().warning("Challonge Create Error: " + response.body());
                    }
                    return gson.fromJson(response.body(), JsonObject.class);
                });
    }

    public CompletableFuture<JsonObject> addParticipant(String tournamentUrl, String participantName, String miscRef) {
        JsonObject payload = new JsonObject();
        JsonObject participant = new JsonObject();
        participant.addProperty("name", participantName);
        participant.addProperty("misc", miscRef);
        payload.add("participant", participant);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getUrl("tournaments/" + tournamentUrl + "/participants.json")))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        Core.getInstance().getLogger().warning("Challonge Part Error: " + response.body());
                    }
                    return gson.fromJson(response.body(), JsonObject.class);
                });
    }

    public CompletableFuture<Boolean> startTournament(String tournamentUrl) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getUrl("tournaments/" + tournamentUrl + "/start.json")))
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> response.statusCode() == 200);
    }

    public CompletableFuture<JsonArray> getMatches(String tournamentUrl) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getUrl("tournaments/" + tournamentUrl + "/matches.json")))
                .header("Accept", "application/json")
                .GET()
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> gson.fromJson(response.body(), JsonArray.class));
    }

    public CompletableFuture<JsonArray> getParticipants(String tournamentUrl) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getUrl("tournaments/" + tournamentUrl + "/participants.json")))
                .header("Accept", "application/json")
                .GET()
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> gson.fromJson(response.body(), JsonArray.class));
    }

    public CompletableFuture<Boolean> updateMatch(String tournamentUrl, long matchId, String scoresCsv, long winnerId) {
        JsonObject payload = new JsonObject();
        JsonObject match = new JsonObject();
        match.addProperty("scores_csv", scoresCsv);
        match.addProperty("winner_id", winnerId);
        payload.add("match", match);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getUrl("tournaments/" + tournamentUrl + "/matches/" + matchId + ".json")))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> response.statusCode() == 200);
    }

    public CompletableFuture<Boolean> concludeTournament(String tournamentUrl) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getUrl("tournaments/" + tournamentUrl + "/finalize.json")))
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> response.statusCode() == 200);
    }

}
