package dev.onrai.punishments.storage;

import dev.onrai.punishments.model.Punishment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface Storage extends AutoCloseable {
    void initialize() throws Exception;
    void remember(UUID uuid, String name, String ip) throws Exception;
    Optional<UUID> lookupUuid(String name) throws Exception;
    Optional<String> lookupIp(String name) throws Exception;
    void insert(Punishment punishment) throws Exception;
    List<Punishment> findActive(UUID uuid, String ip, long now) throws Exception;
    List<Punishment> history(UUID uuid, String ip, int offset, int limit) throws Exception;
    long historyCount(UUID uuid, String ip) throws Exception;
    int revoke(UUID uuid, String ip, boolean mute, String actor, long now) throws Exception;
    @Override void close() throws Exception;
}
