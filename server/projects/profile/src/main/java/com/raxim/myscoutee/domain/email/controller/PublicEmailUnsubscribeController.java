package com.raxim.myscoutee.domain.email.controller;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.raxim.myscoutee.domain.localization.api.TranslationBundles;
import com.raxim.myscoutee.domain.email.service.EmailPresentationService;
import com.raxim.myscoutee.domain.email.service.EmailUnsubscribeService;
import com.raxim.myscoutee.domain.platform.api.DeploymentBranding;
import org.springframework.web.util.HtmlUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Small public confirmation document, independent of the authenticated application. */
@RestController
@RequestMapping({"/public/email/unsubscribe", "/api/public/email/unsubscribe"})
public class PublicEmailUnsubscribeController {
    private final EmailUnsubscribeService subscriptions;
    private final EmailPresentationService presentation;
    private final DeploymentBranding branding;
    private final TranslationBundles i18n;
    public record TokenRequest(@NotBlank @Size(max = 2048) String token) {
        @Override public String toString() { return "TokenRequest[redacted]"; }
    }

    public PublicEmailUnsubscribeController(EmailUnsubscribeService subscriptions,
            EmailPresentationService presentation, DeploymentBranding branding, TranslationBundles i18n) {
        this.subscriptions = subscriptions; this.presentation = presentation; this.branding = branding; this.i18n = i18n;
    }

    @PostMapping("/status")
    public ResponseEntity<Map<String, Boolean>> status(@Valid @RequestBody TokenRequest body) {
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .body(Map.of("unsubscribed", subscriptions.status(body.token())));
    }

    @PostMapping
    public ResponseEntity<Map<String, Boolean>> unsubscribe(@Valid @RequestBody TokenRequest body) {
        subscriptions.unsubscribe(body.token());
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(Map.of("unsubscribed", true));
    }

    @GetMapping(produces = "text/html;charset=UTF-8")
    public ResponseEntity<String> page(@RequestHeader(value = "Accept-Language", required = false) String language,
            @RequestParam(required = false) String lang) {
        var bundle = i18n.getBundle(lang, language, null);
        var labels = bundle.data();
        var brand = branding.resolve();
        String nonce = UUID.randomUUID().toString();
        String html = """
                <!doctype html><html lang="%s"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
                <meta name="robots" content="noindex,nofollow"><meta name="referrer" content="no-referrer"><title>%s · %s</title>
                <style nonce="%s">
                *{box-sizing:border-box}body{margin:0;min-height:100dvh;display:grid;place-items:center;padding:24px 16px;background:linear-gradient(145deg,#eef8f8,#f2f0fa);color:#203651;font:16px/1.6 system-ui,sans-serif}
                main{width:min(100%%,460px);padding:32px;border:1px solid #d3e4e5;border-radius:22px;background:#fff;box-shadow:0 14px 48px #22354b12;text-align:center}
                img{width:72px;height:72px;object-fit:contain}h1{font-size:24px;line-height:1.3;margin:20px 0 12px}p{margin:12px 0 24px;color:#52667d}
                button{display:block;width:fit-content;max-width:100%%;margin:0 auto;min-height:42px;border:0;border-radius:24px;background:#207c80;color:white;font:600 15px system-ui;cursor:pointer;padding:10px 24px}
                button:disabled{opacity:.6;cursor:default}button[aria-busy=true]:before{content:"";display:inline-block;width:14px;height:14px;margin-right:10px;border:2px solid #ffffff66;border-top-color:white;border-radius:50%%;animation:spin .7s linear infinite}
                a{display:inline-block;margin-top:24px;color:#24668b;text-underline-offset:4px}small{display:block;color:#60758a}a:focus-visible,button:focus-visible{outline:3px solid #589bdf;outline-offset:4px}
                @keyframes spin{to{transform:rotate(360deg)}}@media(prefers-reduced-motion:reduce){button[aria-busy=true]:before{animation:none}}@media(max-width:380px){main{padding:24px 20px}}
                </style></head><body><main>
                <img src="%s" alt="%s"><small>%s</small><h1>%s</h1>
                <p id="message" role="status" aria-live="polite" data-confirm="%s" data-success="%s" data-invalid="%s" data-error="%s">%s</p>
                <button id="confirm" type="button" disabled aria-busy="true" data-action="%s" data-pending="%s" data-retry="%s">%s</button>
                <a href="https://myscoutee.com" rel="noreferrer">%s ↗</a>
                <noscript><p>%s</p></noscript></main>
                <script nonce="%s">
                (()=>{'use strict';
                  const token=new URLSearchParams(location.hash.slice(1)).get('token')||'';
                  const button=document.getElementById('confirm'),message=document.getElementById('message');
                  const endpoint=location.pathname.replace(/\\/$/,'');let validated=false;
                  async function request(confirm){
                    button.disabled=true;button.setAttribute('aria-busy','true');button.textContent=button.dataset.pending;
                    try{
                      const response=await fetch(endpoint+(confirm?'':'/status'),{method:'POST',credentials:'omit',headers:{'Content-Type':'application/json'},body:JSON.stringify({token}),signal:AbortSignal.timeout(10000)});
                      if(response.status===400){message.textContent=message.dataset.invalid;button.hidden=true;return;}
                      if(!response.ok)throw Error('unavailable');
                      const data=await response.json();validated=true;
                      message.textContent=data.unsubscribed?message.dataset.success:message.dataset.confirm;
                      button.hidden=data.unsubscribed;button.textContent=button.dataset.action;
                    }catch{message.textContent=message.dataset.error;button.textContent=validated?button.dataset.action:button.dataset.retry;}
                    finally{button.disabled=false;button.setAttribute('aria-busy','false');}
                  }
                  button.addEventListener('click',()=>request(validated));
                  if(!/^[A-Za-z0-9_-]{20,2048}$/.test(token)){message.textContent=message.dataset.invalid;button.hidden=true;button.setAttribute('aria-busy','false');}
                  else request(false);
                })();
                </script></body></html>
                """.formatted(e(bundle.lang()), label(labels,"title"), e(brand.productName()), nonce,
                        e(presentation.logoUrl(brand.logoUrl())), e(brand.productName()), e(brand.productName()), label(labels,"title"),
                        label(labels,"confirm"),label(labels,"success"),label(labels,"invalid"),label(labels,"error"),label(labels,"loading"),
                        label(labels,"action"),label(labels,"pending"),label(labels,"retry"),label(labels,"loading"),label(labels,"landing"),label(labels,"javascript"),nonce);
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .header("Referrer-Policy", "no-referrer")
                .header("Content-Security-Policy", "default-src 'none'; img-src 'self' https: http:; style-src 'nonce-" + nonce
                        + "'; script-src 'nonce-" + nonce + "'; connect-src 'self'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'")
                .body(html);
    }
    private static String label(Map<String, String> labels, String key) { return e(labels.get("email.unsubscribe." + key)); }
    private static String e(String value) { return HtmlUtils.htmlEscape(value == null ? "" : value); }
}
