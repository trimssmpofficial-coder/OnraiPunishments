package dev.onrai.punishments.command;

import dev.onrai.punishments.OnraiPunishments;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Runs only on the plugin's I/O workers. For never-seen online-mode players uses Mojang's public profile API. */
final class IdentityResolver {
    record Identity(UUID id, String name, String ip) {}
    private static final Pattern UUID_JSON = Pattern.compile("\\\"id\\\"\\s*:\\s*\\\"([0-9a-fA-F]{32})\\\"");
    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
    private final OnraiPunishments plugin;
    IdentityResolver(OnraiPunishments plugin) { this.plugin = plugin; }

    Optional<Identity> resolve(String name) throws Exception {
        if (name == null || !name.matches("[A-Za-z0-9_]{3,16}")) return Optional.empty();
        Optional<UUID> known = plugin.storage().lookupUuid(name);
        Optional<String> ip = plugin.storage().lookupIp(name);
        if (known.isPresent()) return Optional.of(new Identity(known.get(), name, ip.orElse(null)));
        if (!plugin.getServer().getOnlineMode()) {
            UUID offlineId = UUID.nameUUIDFromBytes(("OfflinePlayer:"+name).getBytes(StandardCharsets.UTF_8));
            return Optional.of(new Identity(offlineId,name,null));
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.mojang.com/users/profiles/minecraft/" + name))
                .header("Accept", "application/json").timeout(Duration.ofSeconds(5)).GET().build();
        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404 || response.statusCode() == 204) return Optional.empty();
        if (response.statusCode() != 200) throw new IllegalStateException("Mojang lookup HTTP " + response.statusCode());
        Matcher matcher = UUID_JSON.matcher(response.body());
        if (!matcher.find()) return Optional.empty();
        String raw = matcher.group(1);
        UUID id = UUID.fromString(raw.replaceFirst("([0-9a-fA-F]{8})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{12})","$1-$2-$3-$4-$5"));
        return Optional.of(new Identity(id,name,null));
    }
}
