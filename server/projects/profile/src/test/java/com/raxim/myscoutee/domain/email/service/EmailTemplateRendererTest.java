package com.raxim.myscoutee.domain.email.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

@org.junit.jupiter.api.Tag("standard")
class EmailTemplateRendererTest {
    private final EmailTemplateRenderer renderer = new EmailTemplateRenderer();

    @Test
    void rendersAtStyleVariablesAndEscapesHtmlBodies() {
        String rendered = renderer.renderHtml(
                "<p>Hello @user_name</p><p>@cta_label</p>",
                Map.of(
                        "user_name", "<Alex>",
                        "cta_label", "Open & Continue"));

        assertThat(rendered).contains("&lt;Alex&gt;");
        assertThat(rendered).contains("Open &amp; Continue");
    }

    @Test
    void keepsUnknownAtTokensUntouched() {
        String rendered = renderer.renderText(
                "support@myscoutee.local @unknown_token @user_first_name",
                Map.of("user_first_name", "Sam"));

        assertThat(rendered).isEqualTo("support@myscoutee.local @unknown_token Sam");
    }

    @Test
    void doesNotConsumeSentencePunctuationAfterAtStyleVariables() {
        String rendered = renderer.renderText(
                "Welcome back, @user_first_name. Open @product_name!",
                Map.of(
                        "user_first_name", "Ada",
                        "product_name", "Community Hub"));

        assertThat(rendered)
                .isEqualTo("Welcome back, Ada. Open Community Hub!");
    }
}
