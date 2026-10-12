package com.raxim.myscoutee.domain.platform.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.geo.GeoJsonMultiPolygon;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexType;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexed;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "countryPartitions")
public class CountryPartition implements com.raxim.myscoutee.domain.platform.api.CountryDirectory.Country {
    @Id
    private String id;

    @Indexed(name = "country_partition_key", unique = true)
    private String partitionKey;

    @Indexed(name = "country_partition_code", unique = true, sparse = true)
    private String countryCode;

    private String countryName;
    private String displayName;
    private String source;
    private String osmType;
    private Long osmId;
    private Integer specificity;
    private String importedAtIso;

    @GeoSpatialIndexed(type = GeoSpatialIndexType.GEO_2DSPHERE)
    private GeoJsonMultiPolygon geometry;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPartitionKey() {
        return partitionKey;
    }

    public void setPartitionKey(String partitionKey) {
        this.partitionKey = partitionKey;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getCountryName() {
        return countryName;
    }

    public void setCountryName(String countryName) {
        this.countryName = countryName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getOsmType() {
        return osmType;
    }

    public void setOsmType(String osmType) {
        this.osmType = osmType;
    }

    public Long getOsmId() {
        return osmId;
    }

    public void setOsmId(Long osmId) {
        this.osmId = osmId;
    }

    public Integer getSpecificity() {
        return specificity;
    }

    public void setSpecificity(Integer specificity) {
        this.specificity = specificity;
    }

    public String getImportedAtIso() {
        return importedAtIso;
    }

    public void setImportedAtIso(String importedAtIso) {
        this.importedAtIso = importedAtIso;
    }

    public GeoJsonMultiPolygon getGeometry() {
        return geometry;
    }

    public void setGeometry(GeoJsonMultiPolygon geometry) {
        this.geometry = geometry;
    }
}
