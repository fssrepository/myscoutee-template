package com.raxim.myscoutee.domain.platform.api;

import com.raxim.myscoutee.domain.platform.dto.OperatorConfigurationDtos.*;

/** Validated deployment changes; persistence, encryption and activation stay with platform. */
public interface DeploymentConfigurationManagement {
    DeploymentBrandingDto branding();
    DeploymentPublicConfigurationDto publicConfiguration();
    FirebasePublicConfigurationDto publicFirebaseConfiguration();
    OperatorConfigurationDto configuration();
    OperatorConfigurationDto update(OperatorConfigurationUpdateDto request);
    OperatorConfigurationDto activateFirebase();
    ConfigurationTestResultDto test(ConfigurationTestRequestDto request);
}
