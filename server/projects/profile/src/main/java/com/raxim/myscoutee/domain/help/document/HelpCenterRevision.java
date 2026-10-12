package com.raxim.myscoutee.domain.help.document;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = "helpCenterRevisions")
@CompoundIndexes({
        @CompoundIndex(name = "help_revision_system_context", def = "{'documentType': 1, 'contextKey': 1, 'isSystem': 1}"),
        @CompoundIndex(name = "help_revision_group_document_lang_context_version", def = "{'baseGroupId': 1, 'documentType': 1, 'lang': 1, 'contextKey': 1, 'version': -1}", unique = true),
        @CompoundIndex(name = "help_revision_group_document_lang_context_active", def = "{'baseGroupId': 1, 'documentType': 1, 'lang': 1, 'contextKey': 1, 'active': 1, 'version': -1}")
})
public class HelpCenterRevision {
    @Id
    private String id;
    private String baseGroupId;
    public String getBaseGroupId() { return baseGroupId; }
    public void setBaseGroupId(String value) { baseGroupId = value; }

    @Indexed(name = "help_revision_document_type")
    private String documentType;

    @Indexed(name = "help_revision_lang")
    private String lang;

    @Indexed(name = "help_revision_context_key")
    private String contextKey;

    @Field("isSystem")
    private boolean isSystem;
    public boolean getIsSystem() { return isSystem; }
    public void setIsSystem(boolean value) { isSystem = value; }

    private String languageLabel;
    private Integer version;

    @Indexed(name = "help_revision_active")
    private Boolean active;

    private String title;
    private String summary;
    private String description;
    private String headerColor;
    private String presentation;
    private List<HelpCenterSection> sections = new ArrayList<>();
    private String createdDate;
    private String createdUser;
    private String updatedDate;
    private String updatedUser;

    public static class HelpCenterSection {
        @Field("id")
        private String id;
    private String baseGroupId;
    public String getBaseGroupId() { return baseGroupId; }
    public void setBaseGroupId(String value) { baseGroupId = value; }
        private String icon;
        private String title;
        private String blurb;
        private String contentHtml;
        private List<String> imageUrls = new ArrayList<>();
        private String panelSpan;
        private Boolean optional;
        private String guideStepId;

        public String getGuideStepId() { return guideStepId; }
        public void setGuideStepId(String guideStepId) { this.guideStepId = guideStepId; }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getIcon() {
            return icon;
        }

        public void setIcon(String icon) {
            this.icon = icon;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getBlurb() {
            return blurb;
        }

        public void setBlurb(String blurb) {
            this.blurb = blurb;
        }

        public String getContentHtml() {
            return contentHtml;
        }

        public void setContentHtml(String contentHtml) {
            this.contentHtml = contentHtml;
        }

        public List<String> getImageUrls() {
            return imageUrls;
        }

        public void setImageUrls(List<String> imageUrls) {
            this.imageUrls = imageUrls == null ? new ArrayList<>() : new ArrayList<>(imageUrls);
        }

        public String getPanelSpan() {
            return panelSpan;
        }

        public void setPanelSpan(String panelSpan) {
            this.panelSpan = panelSpan;
        }

        public Boolean getOptional() {
            return optional;
        }

        public void setOptional(Boolean optional) {
            this.optional = optional;
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDocumentType() {
        return documentType;
    }

    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }

    public String getLang() {
        return lang;
    }

    public void setLang(String lang) {
        this.lang = lang;
    }

    public String getContextKey() {
        return contextKey;
    }

    public void setContextKey(String contextKey) {
        this.contextKey = contextKey;
    }

    public String getLanguageLabel() {
        return languageLabel;
    }

    public void setLanguageLabel(String languageLabel) {
        this.languageLabel = languageLabel;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getHeaderColor() {
        return headerColor;
    }

    public void setHeaderColor(String headerColor) {
        this.headerColor = headerColor;
    }

    public String getPresentation() { return presentation; }
    public void setPresentation(String presentation) { this.presentation = presentation; }

    public List<HelpCenterSection> getSections() {
        return sections;
    }

    public void setSections(List<HelpCenterSection> sections) {
        this.sections = sections == null ? new ArrayList<>() : new ArrayList<>(sections);
    }

    public String getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(String createdDate) {
        this.createdDate = createdDate;
    }

    public String getCreatedUser() {
        return createdUser;
    }

    public void setCreatedUser(String createdUser) {
        this.createdUser = createdUser;
    }

    public String getUpdatedDate() {
        return updatedDate;
    }

    public void setUpdatedDate(String updatedDate) {
        this.updatedDate = updatedDate;
    }

    public String getUpdatedUser() {
        return updatedUser;
    }

    public void setUpdatedUser(String updatedUser) {
        this.updatedUser = updatedUser;
    }
}
