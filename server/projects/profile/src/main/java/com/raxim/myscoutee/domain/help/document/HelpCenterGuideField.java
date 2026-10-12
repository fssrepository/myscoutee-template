package com.raxim.myscoutee.domain.help.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "helpCenterGuideFields")
@CompoundIndex(
        name = "help_guide_field_screen_id",
        def = "{'screenKey': 1, 'id': 1}",
        unique = true)
public class HelpCenterGuideField {
    @Id
    private String documentId;

    private String id;
    private String screenKey;
    private String group;
    private String i18nKey;
    private Integer order;

    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getScreenKey() { return screenKey; }
    public void setScreenKey(String screenKey) { this.screenKey = screenKey; }
    public String getGroup() { return group; }
    public void setGroup(String group) { this.group = group; }
    public String getI18nKey() { return i18nKey; }
    public void setI18nKey(String i18nKey) { this.i18nKey = i18nKey; }
    public Integer getOrder() { return order; }
    public void setOrder(Integer order) { this.order = order; }
}
