package com.raxim.myscoutee.domain.email.service;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

@Service
public class EmailTemplateRenderer implements com.raxim.myscoutee.domain.email.api.EmailRendering {
    private static final Pattern BRACED_PLACEHOLDER = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.-]+)\\s*\\}\\}");
    private static final Pattern TOKEN_PLACEHOLDER = Pattern.compile(
            "(?<![\\p{Alnum}._%+-])@([a-zA-Z][a-zA-Z0-9_-]*(?:\\.[a-zA-Z0-9_-]+)*)");

    public String renderSubject(String template, Map<String, String> variables) {
        return render(template, variables, false);
    }

    public String renderText(String template, Map<String, String> variables) {
        return render(template, variables, false);
    }

    public String renderHtml(String template, Map<String, String> variables) {
        return render(template, variables, true);
    }

    private String render(String template, Map<String, String> variables, boolean htmlEscape) {
        if (template == null || template.isBlank()) {
            return "";
        }
        return replacePlaceholders(replacePlaceholders(template, variables, htmlEscape, BRACED_PLACEHOLDER), variables, htmlEscape, TOKEN_PLACEHOLDER);
    }

    private String replacePlaceholders(String template, Map<String, String> variables, boolean htmlEscape, Pattern pattern) {
        Matcher matcher = pattern.matcher(template);
        StringBuffer rendered = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            if (variables == null || !variables.containsKey(key)) {
                matcher.appendReplacement(rendered, Matcher.quoteReplacement(matcher.group(0)));
                continue;
            }
            String value = safeText(variables.get(key));
            if (htmlEscape) {
                value = HtmlUtils.htmlEscape(value);
            }
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }
}
