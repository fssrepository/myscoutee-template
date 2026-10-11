package com.raxim.myscoutee.domain.accounts.api;
import com.raxim.myscoutee.domain.accounts.dto.AuthDtos.UserMenuCountersDto;
public interface ProfileReadState {
    UserMenuCountersDto queryMenuCounters(String userId);
}
