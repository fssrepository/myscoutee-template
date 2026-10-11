package com.raxim.myscoutee.domain.email.service;

import com.raxim.myscoutee.domain.email.api.EmailSendRequest;

import java.net.URI;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import com.raxim.myscoutee.domain.localization.api.TranslationBundles;
import com.raxim.myscoutee.domain.platform.api.DeploymentBranding;

@Service
public class EmailPresentationService {
    private static final Pattern BODY = Pattern.compile("(?is)<body[^>]*>(.*?)</body>");
    private final DeploymentBranding branding;
    private final TranslationBundles i18n;
    private final EmailUnsubscribeService subscriptions;
    public EmailPresentationService(DeploymentBranding branding, TranslationBundles i18n, EmailUnsubscribeService subscriptions) {
        this.branding = branding; this.i18n = i18n; this.subscriptions = subscriptions;
    }

    public EmailSendRequest decorate(EmailSendRequest request, String unsubscribeUrl) {
        var brand = branding.resolve();
        String language = request.metadata() == null ? "en" : request.metadata().getOrDefault("locale", "en");
        Map<String, String> labels = i18n.getBundle(language, "en", null).data();
        String sender = text(request.fromName()).isBlank() ? brand.productName() : request.fromName();
        String senderLine = sender + " <" + text(request.fromEmail()) + ">";
        String logo = logoUrl(brand.logoUrl());
        String body = text(request.htmlBody());
        if (body.isBlank()) body = "<div style=\"white-space:pre-wrap;line-height:1.7\">" + escape(request.textBody()) + "</div>";
        else {
            var match = BODY.matcher(body);
            if (match.find()) body = match.group(1);
        }
        String html = """
                <!doctype html><html><body style="margin:0;background:#f1f5fa;color:#203651;font-family:Arial,sans-serif">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0"><tr><td align="center" style="padding:24px 12px">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:680px;background:white;border:1px solid #d9e3f0;border-radius:18px;overflow:hidden">
                <tr><td style="padding:24px 28px;background:#f5f8fc;border-bottom:1px solid #d9e3f0"><img src="%s" width="64" height="64" alt="%s" style="display:block;object-fit:contain"><h2 style="margin:14px 0 0;font-size:22px">%s</h2></td></tr>
                <tr><td style="padding:24px 28px;font-size:16px">%s</td></tr>
                <tr><td style="padding:20px 28px;background:#f5f8fc;border-top:1px solid #d9e3f0;color:#53667d;font-size:13px;line-height:1.7">
                <div>%s: %s</div><a href="%s" style="color:#285f9d">%s</a><br><a href="https://myscoutee.com" style="color:#285f9d">MyScoutee</a>
                </td></tr></table></td></tr></table></body></html>
                """.formatted(escape(logo), escape(brand.productName()), escape(request.subject()), body,
                        escape(labels.get("email.sender")), escape(senderLine), escape(unsubscribeUrl), escape(labels.get("email.unsubscribe.action")));
        String plain = brand.productName() + "\n\n" + text(request.textBody()) + "\n\n"
                + labels.get("email.sender") + ": " + senderLine + "\n"
                + labels.get("email.unsubscribe.action") + ": " + unsubscribeUrl + "\nhttps://myscoutee.com\n";
        return new EmailSendRequest(request.toEmail(), request.toName(), request.subject(), html, plain,
                request.fromEmail(), sender, request.replyTo(), request.category(), request.metadata());
    }

    public String logoUrl(String value) {
        URI logo = URI.create(subscriptions.publicBaseUrl() + "/").resolve(value);
        if (!java.util.Set.of("http", "https").contains(logo.getScheme())) throw new IllegalStateException("Email logo URL is invalid.");
        return logo.toString();
    }
    private static String text(String value) { return value == null ? "" : value; }
    private static String escape(String value) { return HtmlUtils.htmlEscape(text(value)); }
}
