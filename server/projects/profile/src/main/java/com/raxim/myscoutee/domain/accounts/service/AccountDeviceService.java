package com.raxim.myscoutee.domain.accounts.service;
import java.util.*;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import com.raxim.myscoutee.domain.accounts.api.AccountDevices;
import com.raxim.myscoutee.domain.accounts.document.Users;
import com.raxim.myscoutee.domain.accounts.repository.UsersRepository;
@Service
public class AccountDeviceService implements AccountDevices {
    private final UsersRepository usersRepository;
    public AccountDeviceService(UsersRepository users) { usersRepository = users; }
    public void register(String userId, String deviceId, String firebaseToken, String platform, Boolean enabled) {
        Users user = usersRepository.findByUserId(userId).orElse(null);
        if (user == null) {
            return;
        }

        String nowIso = Instant.now().toString();
        List<Users.UserDeviceRecord> devices = new ArrayList<>(user.getDevices() == null ? List.of() : user.getDevices());
        Users.UserDeviceRecord target = null;
        for (Users.UserDeviceRecord device : devices) {
            if (device == null) {
                continue;
            }
            if (StringUtils.hasText(deviceId) && deviceId.equals(normalizeId(device.getDeviceId()))) {
                target = device;
                break;
            }
            if (StringUtils.hasText(firebaseToken) && firebaseToken.equals(normalizeText(device.getFirebaseToken()))) {
                target = device;
                break;
            }
        }
        if (target == null) {
            target = new Users.UserDeviceRecord();
            devices.add(target);
        }
        target.setDeviceId(StringUtils.hasText(deviceId) ? deviceId : stableDeviceId(firebaseToken));
        target.setPlatform(normalizeText(platform));
        target.setFirebaseToken(firebaseToken);
        target.setNotificationsEnabled(enabled == null ? Boolean.TRUE : enabled);
        target.setLastRegisteredAtIso(nowIso);
        target.setLastSeenAtIso(nowIso);
        user.setDevices(devices);
        usersRepository.save(user);
    }
    public void remove(String userId, String deviceId, String firebaseToken) {
        Users user = usersRepository.findByUserId(userId).orElse(null);
        if (user == null || user.getDevices() == null || user.getDevices().isEmpty()) {
            return;
        }

        List<Users.UserDeviceRecord> retained = user.getDevices().stream()
                .filter(Objects::nonNull)
                .filter(device -> !matchesDevice(device, deviceId, firebaseToken))
                .toList();
        if (retained.size() == user.getDevices().size()) {
            return;
        }
        user.setDevices(new ArrayList<>(retained));
        usersRepository.save(user);
    }
    private boolean matchesDevice(Users.UserDeviceRecord device, String deviceId, String firebaseToken) {
        if (device == null) {
            return false;
        }
        if (StringUtils.hasText(deviceId) && deviceId.equals(normalizeId(device.getDeviceId()))) {
            return true;
        }
        return StringUtils.hasText(firebaseToken) && firebaseToken.equals(normalizeText(device.getFirebaseToken()));
    }
    private String stableDeviceId(String firebaseToken) {
        return UUID.nameUUIDFromBytes(normalizeText(firebaseToken).getBytes()).toString();
    }
    private String normalizeId(String value) { return value == null ? "" : value.trim(); }
    private String normalizeText(String value) { return value == null ? "" : value.trim(); }
}
