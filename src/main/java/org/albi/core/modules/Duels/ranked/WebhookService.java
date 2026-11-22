package org.albi.core.modules.Duels.ranked;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.albi.core.Core;
import org.albi.core.util.ConfigValues;
import org.albi.core.util.WebhookClient;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import java.util.UUID;

public final class WebhookService {
    private WebhookService() {}

    private static URI endpoint(Core plugin) {
        return ConfigValues.httpEndpoint(ConfigValues.envOrYaml(plugin.getConfig(),
                "ranked.webhook_url", "VISE_RANKED_WEBHOOK_URL"));
    }

    public static void sendMatchSummary(Core plugin, RankedMatchRecord record) {
        URI url = endpoint(plugin);
        if (url == null) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (!WebhookClient.postJson(url, summary(record))) {
                    plugin.getLogger().warning("[Ranked] The webhook rejected the match summary.");
                }
            } catch (IOException e) {
                plugin.getLogger().warning("[Ranked] Could not send the match summary (" + e.getClass().getSimpleName() + ").");
            }
        });
    }

    private static JsonObject summary(RankedMatchRecord record) {
        double avgReach = record.getHits().stream().mapToDouble(RankedMatchRecord.HitRecord::distance)
                .average().orElse(0);
        String winner = record.getWinner() == null ? "N/A"
                : record.getWinner().equals(record.getPlayer1()) ? record.getPlayer1Name() : record.getPlayer2Name();

        JsonArray fields = new JsonArray();
        fields.add(field("Players", record.getPlayer1Name() + " vs " + record.getPlayer2Name(), false));
        fields.add(field("Winner", winner, true));
        fields.add(field("Kit", record.getKitName(), true));
        fields.add(field("Elo changes", String.format(Locale.ROOT, "%+d / %+d",
                record.getEloChange1(), record.getEloChange2()), true));
        fields.add(field("Average reach", String.format(Locale.ROOT, "%.2f blocks", avgReach), true));
        fields.add(field("Status", record.isSuspicious() ? "Suspicious" : "Clean", true));

        JsonObject footer = new JsonObject();
        footer.addProperty("text", "Match ID " + record.getMatchId() + " · View with /matchinfo");
        JsonObject embed = new JsonObject();
        embed.addProperty("title", "Ranked match summary");
        embed.addProperty("color", record.isSuspicious() ? 15548997 : 3066993);
        embed.add("fields", fields);
        embed.add("footer", footer);
        JsonArray embeds = new JsonArray();
        embeds.add(embed);

        JsonObject payload = new JsonObject();
        payload.add("embeds", embeds);
        return payload;
    }

    private static JsonObject field(String name, String value, boolean inline) {
        JsonObject field = new JsonObject();
        field.addProperty("name", name);
        field.addProperty("value", value);
        field.addProperty("inline", inline);
        return field;
    }

    public static boolean sendMatchReport(Core plugin, RankedMatchRecord record, File reportFile) {
        URI url = endpoint(plugin);
        if (url == null) return false;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String boundary = "ViseCore-" + UUID.randomUUID();
            try {
                boolean sent = WebhookClient.post(url, "multipart/form-data; boundary=" + boundary,
                        output -> writeReport(output, boundary, record, reportFile));
                if (!sent) plugin.getLogger().warning("[Ranked] The webhook rejected the match report.");
            } catch (IOException e) {
                plugin.getLogger().warning("[Ranked] Could not send the match report (" + e.getClass().getSimpleName() + ").");
            }
        });
        return true;
    }

    private static void writeReport(OutputStream output, String boundary, RankedMatchRecord record, File file)
            throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("content", "Match report for `" + record.getMatchId() + "` ("
                + record.getPlayer1Name() + " vs " + record.getPlayer2Name() + ")");
        write(output, "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"payload_json\"\r\n"
                + "Content-Type: application/json\r\n\r\n"
                + payload + "\r\n"
                + "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"files[0]\"; filename=\"" + file.getName() + "\"\r\n"
                + "Content-Type: text/html\r\n\r\n");
        Files.copy(file.toPath(), output);
        write(output, "\r\n--" + boundary + "--\r\n");
    }

    private static void write(OutputStream output, String text) throws IOException {
        output.write(text.getBytes(StandardCharsets.UTF_8));
    }
}
