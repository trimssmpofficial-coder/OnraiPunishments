package dev.onrai.punishments.storage;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.*;
import dev.onrai.punishments.model.Punishment;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;

public final class MongoStorage implements Storage {
    private final FileConfiguration config;
    private MongoClient client;
    private MongoCollection<Document> records, profiles;
    public MongoStorage(FileConfiguration config) { this.config = config; }
    @Override public void initialize() {
        ConnectionString uri = new ConnectionString(config.getString("database.mongodb.uri", "mongodb://localhost:27017"));
        MongoClientSettings settings = MongoClientSettings.builder().applyConnectionString(uri)
            .applyToConnectionPoolSettings(b -> b.maxSize(Math.max(2, Math.min(16, config.getInt("database.mongodb.pool-size", 6)))))
            .applyToSocketSettings(b -> b.connectTimeout(5, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS))
            .applyToClusterSettings(b -> b.serverSelectionTimeout(5, TimeUnit.SECONDS)).build();
        client = MongoClients.create(settings);
        var db = client.getDatabase(config.getString("database.mongodb.database", "onrai_punishments"));
        records = db.getCollection("punishments"); profiles = db.getCollection("profiles");
        db.runCommand(new Document("ping", 1));
        records.createIndex(Indexes.ascending("targetUuid", "createdAt"));
        records.createIndex(Indexes.ascending("targetIp", "type"));
        records.createIndex(Indexes.ascending("targetNameLower", "createdAt"));
        profiles.createIndex(Indexes.ascending("nameLower", "lastSeen"));
    }
    @Override public void remember(UUID id, String name, String ip) {
        profiles.updateOne(eq("_id", id.toString()), combine(set("name",name),set("nameLower",name.toLowerCase(java.util.Locale.ROOT)),set("ip",ip),set("lastSeen",System.currentTimeMillis())),new UpdateOptions().upsert(true));
    }
    @Override public Optional<UUID> lookupUuid(String name) {
        Document doc = profiles.find(eq("nameLower",name.toLowerCase(java.util.Locale.ROOT))).sort(Sorts.descending("lastSeen")).first();
        if (doc != null) return Optional.of(UUID.fromString(doc.getString("_id")));
        Document punishment = records.find(and(eq("targetNameLower",name.toLowerCase(java.util.Locale.ROOT)),ne("targetUuid",null)))
            .sort(Sorts.descending("createdAt")).first();
        return punishment == null ? Optional.empty() : Optional.of(UUID.fromString(punishment.getString("targetUuid")));
    }
    @Override public Optional<String> lookupIp(String name) {
        Document doc = profiles.find(eq("nameLower",name.toLowerCase(java.util.Locale.ROOT))).sort(Sorts.descending("lastSeen")).first();
        return doc == null ? Optional.empty() : Optional.ofNullable(doc.getString("ip"));
    }
    @Override public void insert(Punishment p) {
        records.insertOne(new Document("_id",p.id().toString()).append("targetUuid",s(p.targetUuid()))
            .append("targetName",p.targetName()).append("targetNameLower",p.targetName() == null ? null : p.targetName().toLowerCase(java.util.Locale.ROOT)).append("targetIp",p.targetIp())
            .append("type",p.type().name()).append("actor",p.actor()).append("reason",p.reason())
            .append("createdAt",p.createdAt()).append("expiresAt",p.expiresAt())
            .append("revokedAt",p.revokedAt()).append("revokedBy",p.revokedBy()));
    }
    private static String s(UUID id) { return id == null ? null : id.toString(); }
    private static Bson match(UUID id, String ip) {
        List<Bson> cases = new ArrayList<>();
        if (id != null) cases.add(and(eq("targetUuid",s(id)),ne("type","IP_BAN")));
        if (ip != null) cases.add(and(eq("targetIp",ip),eq("type","IP_BAN")));
        return cases.isEmpty() ? eq("_id", "impossible") : or(cases);
    }
    private static Bson historyMatch(UUID id, String ip) {
        List<Bson> cases = new ArrayList<>();
        if (id != null) cases.add(eq("targetUuid",s(id)));
        if (ip != null) cases.add(and(eq("targetIp",ip),eq("type","IP_BAN")));
        return cases.isEmpty() ? eq("_id", "impossible") : or(cases);
    }
    private static Punishment from(Document d) {
        String uuid = d.getString("targetUuid");
        return new Punishment(UUID.fromString(d.getString("_id")),uuid == null ? null : UUID.fromString(uuid),
            d.getString("targetName"),d.getString("targetIp"),Punishment.Type.valueOf(d.getString("type")),
            d.getString("actor"),d.getString("reason"),d.getLong("createdAt"),d.getLong("expiresAt"),
            d.getLong("revokedAt"),d.getString("revokedBy"));
    }
    @Override public List<Punishment> findActive(UUID id, String ip, long now) {
        Bson filter = and(match(id,ip),lt("revokedAt",0L),or(lt("expiresAt",0L),gt("expiresAt",now)));
        return convert(records.find(filter).sort(Sorts.descending("createdAt")));
    }
    @Override public List<Punishment> history(UUID id, String ip, int offset, int limit) {
        return convert(records.find(historyMatch(id,ip)).sort(Sorts.descending("createdAt")).skip(offset).limit(limit));
    }
    @Override public long historyCount(UUID id, String ip) { return records.countDocuments(historyMatch(id,ip)); }
    @Override public int revoke(UUID id, String ip, boolean mute, String actor, long now) {
        Bson types = in("type", mute ? List.of("MUTE","TEMP_MUTE") : List.of("BAN","TEMP_BAN","IP_BAN"));
        // Match UUID even for IP_BAN when revoking: the stored IP may have changed.
        Bson active = and(historyMatch(id,ip),types,lt("revokedAt",0L),or(lt("expiresAt",0L),gt("expiresAt",now)));
        return Math.toIntExact(records.updateMany(active,combine(set("revokedAt",now),set("revokedBy",actor))).getModifiedCount());
    }
    private List<Punishment> convert(Iterable<Document> docs) { List<Punishment> out = new ArrayList<>(); for(Document d:docs) out.add(from(d)); return out; }
    @Override public void close() { if (client != null) client.close(); }
}
