package com.raxim.myscoutee.domain.chat.service;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import org.springframework.scheduling.annotation.Scheduled;
import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import com.raxim.myscoutee.common.config.mongo.MongoSessionMode;
import com.raxim.myscoutee.common.config.mongo.MongoSessionModeContext;

/** Ephemeral room membership and opaque signaling only. Never handles or stores media. */
@Service
public class ChatCallSignalingService {
    public record Request(String action, String callId, String targetSessionId, String bundle,
                          String data, Boolean video, String nodeId, String clientId, Long epoch, Boolean muted) {}
    public record Participant(String sessionId, String userId, String bundle, String clientId, boolean video,
                              boolean moderator, boolean mutedByAdmin) {}
    public record Event(String type, String chatId, String kind, String callId, String senderSessionId,
                        String selfSessionId, List<Participant> participants, String data,
                        Boolean video, String nodeId, String error, long epoch) {}
    public record StunNode(String id, String label, List<String> urls) {}
    private record RoomKey(MongoSessionMode mode, String chatId) {}
    private static final class Room {
        final String id;
        final String nodeId;
        final boolean video;
        long epoch = 0;
        final Map<String, Participant> participants = new LinkedHashMap<>();
        final java.util.Set<String> mutedUsers = new java.util.HashSet<>();
        Room(Request request) { id = request.callId(); nodeId = request.nodeId(); video = Boolean.TRUE.equals(request.video()); }
    }

    private final ChatService chats;
    private final ChatWebSocketSessionRegistry sessions;
    private final List<StunNode> nodes;
    private final String healthHost;
    private final int healthPort;
    private final int publicPort;
    private volatile boolean available;
    private final Map<RoomKey, Room> rooms = new ConcurrentHashMap<>();

    public ChatCallSignalingService(ChatService chats, ChatWebSocketSessionRegistry sessions,
            @Value("${config.chat-call-stun-urls:}") String stunUrls,
            @Value("${config.chat-call-stun-health-host:stun}") String healthHost,
            @Value("${config.chat-call-stun-health-port:3478}") int healthPort,
            @Value("${config.chat-call-stun-port:3478}") int publicPort) {
        this.chats = chats;
        this.sessions = sessions;
        this.healthHost = healthHost; this.healthPort = healthPort; this.publicPort = publicPort;
        var urls = Arrays.stream(stunUrls.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        if (urls.stream().anyMatch(s -> !s.matches("stuns?:[A-Za-z0-9.\\[\\]:-]+"))) {
            throw new IllegalArgumentException("Chat call discovery accepts STUN URLs only.");
        }
        var configured = new java.util.ArrayList<StunNode>();
        for (int i = 0; i < urls.size(); i++) {
            configured.add(new StunNode("owned-" + i, urls.get(i), List.of(urls.get(i))));
        }
        nodes = List.copyOf(configured);
    }

    public synchronized void handle(WebSocketSession session, String chatId, String userId, Request request) throws IOException {
        var attributes = session.getAttributes();
        long minute = System.currentTimeMillis() / 60000;
        if (!Long.valueOf(minute).equals(attributes.get("callRateMinute"))) {
            attributes.put("callRateMinute", minute); attributes.put("callRateCount", 0);
        }
        int count = (int) attributes.getOrDefault("callRateCount", 0) + 1;
        attributes.put("callRateCount", count);
        if (count > 512) throw new IllegalArgumentException("Call signaling rate exceeded.");
        if (request == null || !chats.hasChatAccess(chatId, userId)) {
            throw new IllegalArgumentException("Chat call access denied.");
        }
        var key = new RoomKey(MongoSessionModeContext.currentMode(), chatId);
        if ("configuration".equals(request.action())) {
            sessions.sendEvent(session, Map.of("type", "call", "chatId", chatId, "kind", "configuration", "nodes", nodesFor(session), "available", available));
            var room = rooms.get(key);
            if (room != null) send(session, chatId, "invite", room, null, null, null, null);
            return;
        }
        if (request.callId() == null || !UUID.fromString(request.callId()).toString().equals(request.callId())) {
            throw new IllegalArgumentException("Invalid call ID.");
        }
        var room = rooms.get(key);
        if ("join".equals(request.action())) {
            if (!available || request.bundle() == null || request.bundle().length() > 8192
                    || !request.bundle().matches("[A-Za-z0-9+/]+={0,2}")
                    || request.clientId() == null || !UUID.fromString(request.clientId()).toString().equals(request.clientId())
                    || nodesFor(session).stream().noneMatch(n -> n.id().equals(request.nodeId()))) {
                throw new IllegalArgumentException("Invalid call configuration.");
            }
            if (room != null && !room.id.equals(request.callId())) {
                throw new IllegalArgumentException("Another call is already active in this chat.");
            }
            boolean created = room == null;
            if (created) { room = new Room(request); rooms.put(key, room); }
            if (!room.nodeId.equals(request.nodeId())) throw new IllegalArgumentException("Call discovery node mismatch.");
            if (room.participants.size() >= 16 && !room.participants.containsKey(session.getId())) throw new IllegalArgumentException("Call is full.");
            var participant = new Participant(session.getId(), userId, request.bundle(), request.clientId(), Boolean.TRUE.equals(request.video()),
                    chats.canModerateCall(chatId, userId), room.mutedUsers.contains(userId));
            if (room.participants.containsKey(session.getId()) && !room.participants.get(session.getId()).equals(participant)) {
                throw new IllegalArgumentException("Call identity cannot change.");
            }
            if (room.participants.values().stream().anyMatch(p -> p.clientId().equals(request.clientId()) && !p.sessionId().equals(session.getId()))) {
                throw new IllegalArgumentException("Duplicate call identity.");
            }
            room.participants.put(session.getId(), participant);
            publishRoster(key, room);
            if (created) {
                for (var ref : sessions.chatSessions(chatId)) {
                    if (ref.mode() == key.mode() && !ref.session().getId().equals(session.getId())
                            && ref.session().isOpen() && chats.hasChatAccess(chatId, ref.userId())) {
                        send(ref.session(), chatId, "invite", room, session.getId(), null, null, null);
                    }
                }
            }
            return;
        }
        if (room != null && room.id.equals(request.callId()) && "decline".equals(request.action())) {
            for (var ref : sessions.chatSessions(chatId)) {
                if (ref.mode() == key.mode() && room.participants.containsKey(ref.session().getId()) && ref.session().isOpen()) {
                    send(ref.session(), chatId, "declined", room, session.getId(), null, null, null);
                }
            }
            return;
        }
        if (room == null || !room.id.equals(request.callId()) || !room.participants.containsKey(session.getId())) {
            throw new IllegalArgumentException("Call membership required.");
        }
        if ("leave".equals(request.action())) {
            removeParticipant(key, room, session.getId());
            return;
        }
        if ("mute".equals(request.action())) {
            var target = room.participants.get(request.targetSessionId());
            if (!chats.canModerateCall(chatId, userId) || target == null || target.userId().equals(userId) || request.muted() == null) {
                throw new IllegalArgumentException("Call moderation denied.");
            }
            if (request.muted()) room.mutedUsers.add(target.userId()); else room.mutedUsers.remove(target.userId());
            publishRoster(key, room);
            return;
        }
        if ("commit".equals(request.action())) {
            if (!room.participants.keySet().iterator().next().equals(session.getId())
                    || request.epoch() == null || request.epoch() != room.epoch
                    || request.data() == null || request.data().length() > 49152) {
                throw new IllegalArgumentException("Invalid MLS coordinator commit.");
            }
            room.epoch++;
            broadcast(key, room, "mls", session.getId(), request.data());
            sessions.sendEvent(session, Map.of("type", "call", "chatId", chatId, "kind", "committed", "callId", room.id, "epoch", room.epoch));
            return;
        }
        if (!"signal".equals(request.action()) || request.data() == null || request.data().length() > 49152
                || !room.participants.containsKey(request.targetSessionId())
                || session.getId().equals(request.targetSessionId())) {
            throw new IllegalArgumentException("Invalid call signal.");
        }
        // Every MLS member consumes every application message, including messages for another media peer.
        broadcast(key, room, "signal", session.getId(), request.data());
    }

    private void broadcast(RoomKey key, Room room, String kind, String sender, String data) {
        for (var ref : sessions.chatSessions(key.chatId())) {
            if (ref.mode() == key.mode() && room.participants.containsKey(ref.session().getId())
                    && !ref.session().getId().equals(sender) && ref.session().isOpen() && chats.hasChatAccess(key.chatId(), ref.userId())) {
                try { send(ref.session(), key.chatId(), kind, room, sender, null, null, data); }
                catch (IOException ignored) { /* Socket cleanup removes the disconnected participant. */ }
            }
        }
    }

    private List<StunNode> nodesFor(WebSocketSession session) {
        if (!nodes.isEmpty()) return nodes;
        String host = session.getUri() == null ? "localhost" : session.getUri().getHost();
        if (host == null || !host.matches("[A-Za-z0-9.\\[\\]:-]+")) throw new IllegalArgumentException("Invalid STUN host.");
        if (host.contains(":") && !host.startsWith("[")) host = "[" + host + "]";
        return List.of(new StunNode("owned-0", "MyScoutee", List.of("stun:" + host + ":" + publicPort)));
    }

    @PostConstruct
    @Scheduled(fixedDelayString = "${config.chat-call-stun-health-interval-ms:5000}")
    public void refreshAvailability() {
        boolean next = probeStun();
        if (available == next) return;
        available = next;
        for (String chatId : sessions.chatIds()) {
            for (var ref : sessions.chatSessions(chatId)) {
                if (!ref.session().isOpen()) continue;
                try { sessions.sendEvent(ref.session(), Map.of("type", "call", "chatId", chatId, "kind", "availability", "available", next)); }
                catch (IOException ignored) { /* The handler owns socket cleanup. */ }
            }
        }
    }

    private boolean probeStun() {
        byte[] transaction = new byte[12]; new SecureRandom().nextBytes(transaction);
        byte[] request = ByteBuffer.allocate(20).putShort((short) 1).putShort((short) 0).putInt(0x2112a442).put(transaction).array();
        try (var socket = new DatagramSocket()) {
            socket.setSoTimeout(800);
            socket.connect(InetAddress.getByName(healthHost), healthPort);
            socket.send(new DatagramPacket(request, request.length));
            byte[] response = new byte[2048]; var packet = new DatagramPacket(response, response.length);
            socket.receive(packet);
            if (packet.getLength() < 20) return false;
            var buffer = ByteBuffer.wrap(response);
            return buffer.getShort() == 0x0101 && Short.toUnsignedInt(buffer.getShort()) <= packet.getLength() - 20
                    && buffer.getInt() == 0x2112a442 && Arrays.equals(transaction, Arrays.copyOfRange(response, 8, 20));
        } catch (IOException exception) { return false; }
    }

    public synchronized void disconnected(WebSocketSession session) {
        for (var entry : List.copyOf(rooms.entrySet())) {
            if (entry.getValue().participants.containsKey(session.getId())) {
                var previous = MongoSessionModeContext.currentMode();
                try {
                    MongoSessionModeContext.set(entry.getKey().mode());
                    removeParticipant(entry.getKey(), entry.getValue(), session.getId());
                } catch (IOException ignored) {
                    // A remaining participant's own socket cleanup removes it on disconnect.
                } finally { MongoSessionModeContext.set(previous); }
            }
        }
    }

    private void removeParticipant(RoomKey key, Room room, String sessionId) throws IOException {
        room.participants.remove(sessionId);
        if (room.participants.isEmpty()) {
            rooms.remove(key, room);
            for (var ref : sessions.chatSessions(key.chatId())) {
                if (ref.mode() == key.mode() && ref.session().isOpen() && chats.hasChatAccess(key.chatId(), ref.userId())) {
                    try { send(ref.session(), key.chatId(), "ended", room, null, null, null, null); }
                    catch (IOException ignored) { /* Continue notifying other chat viewers. */ }
                }
            }
        } else publishRoster(key, room);
    }

    private void publishRoster(RoomKey key, Room room) throws IOException {
        var refs = sessions.chatSessions(key.chatId()).stream().filter(ref -> ref.mode() == key.mode()
                && ref.session().isOpen() && room.participants.containsKey(ref.session().getId())).toList();
        for (var ref : refs) {
            if (!chats.hasChatAccess(key.chatId(), ref.userId())) room.participants.remove(ref.session().getId());
            else {
                var p = room.participants.get(ref.session().getId());
                room.participants.put(p.sessionId(), new Participant(p.sessionId(), p.userId(), p.bundle(), p.clientId(), p.video(),
                        p.moderator(), room.mutedUsers.contains(p.userId())));
            }
        }
        if (room.participants.isEmpty()) { rooms.remove(key, room); return; }
        var participants = List.copyOf(room.participants.values());
        for (var ref : refs) {
            if (room.participants.containsKey(ref.session().getId())) {
                try { send(ref.session(), key.chatId(), "roster", room, null, ref.session().getId(), participants, null); }
                catch (IOException ignored) { /* Keep notifying the remaining members. */ }
            }
        }
    }

    private void send(WebSocketSession session, String chatId, String kind, Room room, String sender,
                      String self, List<Participant> participants, String data) throws IOException {
        sessions.sendEvent(session, new Event("call", chatId, kind, room.id, sender, self, participants, data, room.video, room.nodeId, null, room.epoch));
    }
}
