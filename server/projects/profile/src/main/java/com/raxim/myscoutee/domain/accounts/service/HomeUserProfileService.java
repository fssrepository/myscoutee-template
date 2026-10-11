package com.raxim.myscoutee.domain.accounts.service;

import org.springframework.stereotype.Service;
import com.raxim.myscoutee.domain.accounts.api.ProfileReadState;
import com.raxim.myscoutee.domain.accounts.api.SelectedProfile;
import com.raxim.myscoutee.domain.accounts.document.Users;
import com.raxim.myscoutee.domain.accounts.dto.AuthDtos.UserMenuCountersDto;
import com.raxim.myscoutee.domain.accounts.dto.AuthDtos.UserChatCountersDto;
import com.raxim.myscoutee.domain.accounts.dto.AuthDtos.UserSupportCaseCountersDto;
import com.raxim.myscoutee.domain.accounts.repository.UsersRepository;

@Service
public class HomeUserProfileService implements ProfileReadState {
    private final SelectedProfile selectedProfile;
    private final UsersRepository usersRepository;

    public HomeUserProfileService(SelectedProfile selectedProfile, UsersRepository usersRepository) {
        this.selectedProfile = selectedProfile;
        this.usersRepository = usersRepository;
    }

    public UserMenuCountersDto queryMenuCounters(String requestedUserId) {
        String userId = selectedProfile.resolveSelectedUserId(requestedUserId);
        Users user = userId == null || userId.isBlank() ? null : usersRepository.findByUserId(userId).orElse(null);
        if (!SelectedProfileService.isUsableUserDocument(user)) user = null;
        var activities = user == null ? null : user.getActivities();
        var chat = activities == null ? null : activities.chat();
        var cases = chat == null ? null : chat.supportCases();
        var support = new UserSupportCaseCountersDto(
                cases == null ? 0 : count(cases.pending()), cases == null ? 0 : count(cases.warned()),
                cases == null ? 0 : count(cases.picked()), cases == null ? 0 : count(cases.solved()),
                cases == null ? 0 : count(cases.blocked()), cases == null ? 0 : count(cases.all()));
        return new UserMenuCountersDto(activities == null ? 0 : count(activities.chats()),
                new UserChatCountersDto(chat == null ? 0 : count(chat.all()), chat == null ? 0 : count(chat.appSupport()),
                        support, chat == null ? 0 : count(chat.contacts())),
                activities == null ? 0 : count(activities.notifications()));
    }

    private int count(Integer value) { return value == null ? 0 : Math.max(0, value); }
}
