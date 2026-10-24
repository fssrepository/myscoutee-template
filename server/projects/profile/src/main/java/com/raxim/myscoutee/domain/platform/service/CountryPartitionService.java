package com.raxim.myscoutee.domain.platform.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.geo.Point;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.geo.GeoJsonMultiPolygon;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.geo.GeoJsonPolygon;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexType;
import org.springframework.data.mongodb.core.index.GeospatialIndex;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import com.raxim.myscoutee.domain.platform.document.CountryPartition;
import com.raxim.myscoutee.common.dto.SharedDtos.LocationCoordinatesDto;
import com.raxim.myscoutee.domain.platform.repository.CountryPartitionsRepository;

import jakarta.annotation.PostConstruct;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class CountryPartitionService implements com.raxim.myscoutee.domain.platform.api.CountryDirectory {
    private static final Logger LOGGER = LoggerFactory.getLogger(CountryPartitionService.class);
    private static final String OSM_SOURCE = "openstreetmap";
    private static final String NOMINATIM_SEARCH_URL = "https://nominatim.openstreetmap.org/search";
    private static final String NOMINATIM_USER_AGENT = "myscoutee-backend-country-partitions/1.0";
    private static final int DEFAULT_COUNTRY_SPECIFICITY = 100;
    private static final String COUNTRY_PARTITION_KEY_INDEX = "country_partition_key";
    private static final String COUNTRY_PARTITION_CODE_INDEX = "country_partition_code";
    private static final String COUNTRY_PARTITION_GEOMETRY_INDEX = "country_partition_geometry";
    private final CountryPartitionsRepository countryPartitionsRepository;
    private final MongoTemplate mongoTemplate;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public CountryPartitionService(
            CountryPartitionsRepository countryPartitionsRepository,
            MongoTemplate mongoTemplate,
            ObjectMapper objectMapper) {
        this.countryPartitionsRepository = countryPartitionsRepository;
        this.mongoTemplate = mongoTemplate;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        this.mongoTemplate.indexOps(CountryPartition.class)
                .createIndex(new GeospatialIndex("geometry")
                        .typed(GeoSpatialIndexType.GEO_2DSPHERE)
                        .named(COUNTRY_PARTITION_GEOMETRY_INDEX));
    }

    @PostConstruct
    public void initializeCountryPartitionIndexes() {
        try {
            ensureCountryPartitionIndexes();
        } catch (RuntimeException exception) {
            LOGGER.warn("Unable to initialize country partition indexes.", exception);
        }
    }

    public String resolvePartitionKey(LocationCoordinatesDto coordinates) {
        return resolvePartitionKey(coordinates, null);
    }

    public String resolvePartitionKey(LocationCoordinatesDto coordinates, String preferredPartitionKey) {
        CountryPartition partition = resolvePartition(coordinates, preferredPartitionKey);
        return partition == null ? null : partition.getPartitionKey();
    }

    public List<CountryPartition> supportedCountries() {
        Query query = Query.query(Criteria.where("geometry").ne(null)
                .and("partitionKey").nin(null, ""));
        query.fields().include("countryCode").include("countryName").exclude("_id");
        query.with(Sort.by("countryCode"));
        return mongoTemplate.find(query, CountryPartition.class);
    }

    public CountryPartition ensureCountryImported(String countryName, String countryCode, String partitionKey) {
        String normalizedCountryName = safeText(countryName);
        String normalizedCountryCode = normalizeCountryCode(countryCode);
        String normalizedPartitionKey = StringUtils.hasText(safeText(partitionKey))
                ? safeText(partitionKey)
                : defaultPartitionKey(normalizedCountryName, normalizedCountryCode);
        if (!StringUtils.hasText(normalizedCountryName)) {
            throw new IllegalArgumentException("Country name is required for OpenStreetMap import.");
        }

        CountryPartition existing = findCanonicalPartitionByPartitionKey(normalizedPartitionKey);
        if (existing != null && existing.getGeometry() != null) {
            return existing;
        }

        URI uri = buildNominatimSearchUri(normalizedCountryName, normalizedCountryCode, null);
        JsonNode match = fetchFirstCountryMatch(uri);
        CountryPartition document = existing == null ? new CountryPartition() : existing;
        document.setId(StringUtils.hasText(document.getId()) ? document.getId() : new ObjectId().toHexString());
        document.setPartitionKey(normalizedPartitionKey);
        document.setCountryCode(resolveCountryCode(normalizedCountryCode, match));
        document.setCountryName(resolveCountryName(normalizedCountryName, match));
        document.setDisplayName(readText(match.path("display_name")));
        document.setSource(OSM_SOURCE);
        document.setOsmType(readText(match.path("osm_type")));
        document.setOsmId(match.path("osm_id").isNumber() ? match.path("osm_id").asLong() : null);
        document.setSpecificity(DEFAULT_COUNTRY_SPECIFICITY);
        document.setImportedAtIso(Instant.now().toString());
        document.setGeometry(toMultiPolygon(match.path("geojson")));
        normalizeCountryPartitionDocument(document);
        return countryPartitionsRepository.save(document);
    }

    private CountryPartition findCanonicalPartitionByPartitionKey(String partitionKey) {
        List<CountryPartition> matchingPartitions = mongoTemplate.find(
                Query.query(Criteria.where("partitionKey").is(partitionKey)),
                CountryPartition.class);
        if (matchingPartitions.isEmpty()) {
            return null;
        }
        if (matchingPartitions.size() == 1) {
            CountryPartition partition = matchingPartitions.get(0);
            if (normalizeCountryPartitionDocument(partition)) {
                return countryPartitionsRepository.save(partition);
            }
            return partition;
        }
        return consolidateCountryPartitionGroup("partitionKey", partitionKey, matchingPartitions);
    }

    private CountryPartition consolidateCountryPartitionGroup(
            String fieldName,
            String fieldValue,
            List<CountryPartition> partitions) {
        CountryPartition canonical = selectCanonicalCountryPartition(partitions);
        List<CountryPartition> redundantPartitions = new ArrayList<>();
        for (CountryPartition partition : partitions) {
            if (isSameCountryPartition(canonical, partition)) {
                continue;
            }
            mergeCountryPartition(canonical, partition);
            redundantPartitions.add(partition);
        }

        if (!StringUtils.hasText(canonical.getId())) {
            canonical.setId(new ObjectId().toHexString());
        }
        normalizeCountryPartitionDocument(canonical);
        CountryPartition savedCanonical = countryPartitionsRepository.save(canonical);
        if (!redundantPartitions.isEmpty()) {
            countryPartitionsRepository.deleteAll(redundantPartitions);
            LOGGER.warn(
                    "Normalized {} duplicate countryPartitions records for {}='{}' by keeping id={}.",
                    redundantPartitions.size(),
                    fieldName,
                    fieldValue,
                    savedCanonical.getId());
        }
        return savedCanonical;
    }

    private CountryPartition selectCanonicalCountryPartition(List<CountryPartition> partitions) {
        return partitions.stream()
                .max(Comparator
                        .comparing((CountryPartition partition) -> partition.getGeometry() != null)
                        .thenComparing(partition -> partition.getSpecificity() == null ? Integer.MIN_VALUE : partition.getSpecificity())
                        .thenComparing(partition -> parseImportedAtIso(partition.getImportedAtIso()))
                        .thenComparing(partition -> StringUtils.hasText(partition.getCountryCode()))
                        .thenComparing(partition -> StringUtils.hasText(partition.getDisplayName()))
                        .thenComparing(partition -> safeText(partition.getId())))
                .orElseThrow(() -> new IllegalStateException("Country partition group must not be empty."));
    }

    private void mergeCountryPartition(CountryPartition target, CountryPartition source) {
        if (!StringUtils.hasText(target.getPartitionKey()) && StringUtils.hasText(source.getPartitionKey())) {
            target.setPartitionKey(source.getPartitionKey());
        }
        if (!StringUtils.hasText(target.getCountryCode()) && StringUtils.hasText(source.getCountryCode())) {
            target.setCountryCode(source.getCountryCode());
        }
        if (!StringUtils.hasText(target.getCountryName()) && StringUtils.hasText(source.getCountryName())) {
            target.setCountryName(source.getCountryName());
        }
        if (!StringUtils.hasText(target.getDisplayName()) && StringUtils.hasText(source.getDisplayName())) {
            target.setDisplayName(source.getDisplayName());
        }
        if (!StringUtils.hasText(target.getSource()) && StringUtils.hasText(source.getSource())) {
            target.setSource(source.getSource());
        }
        if (!StringUtils.hasText(target.getOsmType()) && StringUtils.hasText(source.getOsmType())) {
            target.setOsmType(source.getOsmType());
        }
        if (target.getOsmId() == null && source.getOsmId() != null) {
            target.setOsmId(source.getOsmId());
        }
        if (target.getSpecificity() == null
                || (source.getSpecificity() != null && source.getSpecificity() > target.getSpecificity())) {
            target.setSpecificity(source.getSpecificity());
        }
        if (target.getGeometry() == null && source.getGeometry() != null) {
            target.setGeometry(source.getGeometry());
        }
        if (parseImportedAtIso(source.getImportedAtIso()).isAfter(parseImportedAtIso(target.getImportedAtIso()))) {
            target.setImportedAtIso(source.getImportedAtIso());
        } else if (!StringUtils.hasText(target.getImportedAtIso()) && StringUtils.hasText(source.getImportedAtIso())) {
            target.setImportedAtIso(source.getImportedAtIso());
        }
    }

    private boolean isSameCountryPartition(CountryPartition left, CountryPartition right) {
        if (left == right) {
            return true;
        }
        return Objects.equals(left.getId(), right.getId());
    }

    private boolean normalizeCountryPartitionDocument(CountryPartition partition) {
        boolean changed = false;

        String normalizedCountryCode = normalizeCountryCode(partition.getCountryCode());
        if (!Objects.equals(normalizedCountryCode, partition.getCountryCode())) {
            partition.setCountryCode(normalizedCountryCode);
            changed = true;
        }

        String normalizedCountryName = safeText(partition.getCountryName());
        if (!Objects.equals(normalizedCountryName, partition.getCountryName())) {
            partition.setCountryName(normalizedCountryName);
            changed = true;
        }

        String normalizedPartitionKey = safeText(partition.getPartitionKey());
        if (!StringUtils.hasText(normalizedPartitionKey)
                && (StringUtils.hasText(normalizedCountryCode) || StringUtils.hasText(normalizedCountryName))) {
            normalizedPartitionKey = defaultPartitionKey(normalizedCountryName, normalizedCountryCode);
        }
        if (!Objects.equals(normalizedPartitionKey, partition.getPartitionKey())) {
            partition.setPartitionKey(normalizedPartitionKey);
            changed = true;
        }

        String normalizedDisplayName = safeText(partition.getDisplayName());
        if (!Objects.equals(normalizedDisplayName, partition.getDisplayName())) {
            partition.setDisplayName(normalizedDisplayName);
            changed = true;
        }

        String normalizedSource = safeText(partition.getSource());
        if (!Objects.equals(normalizedSource, partition.getSource())) {
            partition.setSource(normalizedSource);
            changed = true;
        }

        String normalizedOsmType = safeText(partition.getOsmType());
        if (!Objects.equals(normalizedOsmType, partition.getOsmType())) {
            partition.setOsmType(normalizedOsmType);
            changed = true;
        }

        String normalizedImportedAtIso = safeText(partition.getImportedAtIso());
        if (!Objects.equals(normalizedImportedAtIso, partition.getImportedAtIso())) {
            partition.setImportedAtIso(normalizedImportedAtIso);
            changed = true;
        }

        return changed;
    }

    private void ensureCountryPartitionIndexes() {
        dropConflictingCountryPartitionIndex("partitionKey", COUNTRY_PARTITION_KEY_INDEX, true, false);
        dropConflictingCountryPartitionIndex("countryCode", COUNTRY_PARTITION_CODE_INDEX, true, true);
        mongoTemplate.indexOps(CountryPartition.class)
                .createIndex(new Index()
                        .on("partitionKey", Sort.Direction.ASC)
                        .unique()
                        .named(COUNTRY_PARTITION_KEY_INDEX));
        mongoTemplate.indexOps(CountryPartition.class)
                .createIndex(new Index()
                        .on("countryCode", Sort.Direction.ASC)
                        .unique()
                        .sparse()
                        .named(COUNTRY_PARTITION_CODE_INDEX));
    }

    private void dropConflictingCountryPartitionIndex(
            String fieldName,
            String expectedIndexName,
            boolean expectedUnique,
            boolean expectedSparse) {
        String collectionName = mongoTemplate.getCollectionName(CountryPartition.class);
        for (Document index : mongoTemplate.getCollection(collectionName).listIndexes()) {
            String name = index.getString("name");
            if (!StringUtils.hasText(name) || "_id_".equals(name)) {
                continue;
            }

            Document keyDocument = index.get("key", Document.class);
            if (keyDocument == null || keyDocument.size() != 1 || !keyDocument.containsKey(fieldName)) {
                continue;
            }

            boolean unique = Boolean.TRUE.equals(index.getBoolean("unique"));
            boolean sparse = Boolean.TRUE.equals(index.getBoolean("sparse"));
            if (expectedIndexName.equals(name) && unique == expectedUnique && sparse == expectedSparse) {
                continue;
            }

            mongoTemplate.getCollection(collectionName).dropIndex(name);
            LOGGER.warn("Dropped conflicting index {} on countryPartitions.{}.", name, fieldName);
        }
    }

    private Instant parseImportedAtIso(String importedAtIso) {
        if (!StringUtils.hasText(importedAtIso)) {
            return Instant.EPOCH;
        }
        try {
            return Instant.parse(importedAtIso);
        } catch (DateTimeParseException ignored) {
            return Instant.EPOCH;
        }
    }

    private CountryPartition resolvePartition(LocationCoordinatesDto coordinates, String preferredPartitionKey) {
        if (coordinates == null
                || coordinates.latitude() == null
                || coordinates.longitude() == null
                || !Double.isFinite(coordinates.latitude())
                || !Double.isFinite(coordinates.longitude())
                || coordinates.latitude() < -90.0
                || coordinates.latitude() > 90.0
                || coordinates.longitude() < -180.0
                || coordinates.longitude() > 180.0) {
            return null;
        }

        GeoJsonPoint point = new GeoJsonPoint(coordinates.longitude(), coordinates.latitude());
        if (StringUtils.hasText(preferredPartitionKey)) {
            Query preferred = Query.query(Criteria.where("partitionKey").is(preferredPartitionKey)
                    .and("geometry").intersects(point));
            preferred.fields().include("partitionKey").exclude("_id");
            CountryPartition match = mongoTemplate.findOne(preferred, CountryPartition.class);
            if (match != null) return match;
        }
        // The existing 2dsphere index finds spatial candidates; never load all country geometries.
        Query query = Query.query(Criteria.where("geometry").intersects(point))
                .with(Sort.by(Sort.Direction.DESC, "specificity")).limit(1);
        query.fields().include("partitionKey").exclude("_id");
        return mongoTemplate.findOne(query, CountryPartition.class);
    }

    private URI buildNominatimSearchUri(String countryName, String countryCode, Double polygonThreshold) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(NOMINATIM_SEARCH_URL)
                .queryParam("format", "jsonv2")
                .queryParam("limit", 1)
                .queryParam("featureType", "country")
                .queryParam("polygon_geojson", 1)
                .queryParam("addressdetails", 1);

        if (polygonThreshold != null) {
            builder.queryParam("polygon_threshold", polygonThreshold);
        }

        if (StringUtils.hasText(countryName)) {
            builder.queryParam("country", countryName);
        }

        if (StringUtils.hasText(countryCode)) {
            builder.queryParam("countrycodes", countryCode.toLowerCase(Locale.ROOT));
        }

        return URI.create(builder.build(true).toUriString());
    }

    private JsonNode fetchFirstCountryMatch(URI uri) {
        HttpRequest httpRequest = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("Accept-Language", "en")
                .header("User-Agent", NOMINATIM_USER_AGENT)
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("OpenStreetMap country import failed with HTTP " + response.statusCode() + ".");
            }

            JsonNode root = objectMapper.readTree(response.body());
            if (!root.isArray() || root.isEmpty()) {
                throw new IllegalStateException("No OpenStreetMap country boundary found for the requested country.");
            }

            JsonNode first = root.get(0);
            if (first == null || first.path("geojson").isMissingNode() || first.path("geojson").isNull()) {
                throw new IllegalStateException("OpenStreetMap returned a country match without GeoJSON geometry.");
            }
            return first;
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Unable to download the OpenStreetMap country boundary.", exception);
        }
    }

    private GeoJsonMultiPolygon toMultiPolygon(JsonNode geoJson) {
        String type = readText(geoJson.path("type"));
        JsonNode coordinates = geoJson.path("coordinates");
        if (!coordinates.isArray() || coordinates.isEmpty()) {
            throw new IllegalArgumentException("OpenStreetMap returned an empty country geometry.");
        }

        if ("Polygon".equalsIgnoreCase(type)) {
            return new GeoJsonMultiPolygon(List.of(toPolygon(coordinates)));
        }
        if ("MultiPolygon".equalsIgnoreCase(type)) {
            List<GeoJsonPolygon> polygons = new ArrayList<>();
            for (JsonNode polygonCoordinates : coordinates) {
                polygons.add(toPolygon(polygonCoordinates));
            }
            return new GeoJsonMultiPolygon(polygons);
        }
        throw new IllegalArgumentException("Unsupported country geometry type: " + type);
    }

    private GeoJsonPolygon toPolygon(JsonNode polygonCoordinates) {
        if (!polygonCoordinates.isArray() || polygonCoordinates.isEmpty()) {
            throw new IllegalArgumentException("OpenStreetMap returned an invalid polygon geometry.");
        }

        GeoJsonPolygon polygon = new GeoJsonPolygon(toRingPoints(polygonCoordinates.get(0)));
        for (int ringIndex = 1; ringIndex < polygonCoordinates.size(); ringIndex += 1) {
            polygon = polygon.withInnerRing(toRingPoints(polygonCoordinates.get(ringIndex)));
        }
        return polygon;
    }

    private List<Point> toRingPoints(JsonNode ringCoordinates) {
        if (!ringCoordinates.isArray() || ringCoordinates.isEmpty()) {
            throw new IllegalArgumentException("OpenStreetMap returned an empty polygon ring.");
        }

        List<Point> points = new ArrayList<>();
        for (JsonNode coordinate : ringCoordinates) {
            if (!coordinate.isArray() || coordinate.size() < 2) {
                throw new IllegalArgumentException("OpenStreetMap returned an invalid polygon coordinate.");
            }
            points.add(new Point(coordinate.get(0).asDouble(), coordinate.get(1).asDouble()));
        }

        if (points.size() < 4) {
            throw new IllegalArgumentException("OpenStreetMap returned a polygon ring with too few coordinates.");
        }

        Point first = points.get(0);
        Point last = points.get(points.size() - 1);
        if (!samePoint(first, last)) {
            points.add(first);
        }
        return points;
    }

    private boolean samePoint(Point left, Point right) {
        return left != null
                && right != null
                && Double.compare(left.getX(), right.getX()) == 0
                && Double.compare(left.getY(), right.getY()) == 0;
    }

    private String defaultPartitionKey(String countryName, String countryCode) {
        if (StringUtils.hasText(countryCode)) {
            return "country:" + countryCode.toLowerCase(Locale.ROOT);
        }

        if (!StringUtils.hasText(countryName)) {
            throw new IllegalArgumentException("Unable to resolve country name for the imported partition.");
        }
        return "country:" + slugify(countryName);
    }

    private String resolveCountryCode(String countryCode, JsonNode match) {
        if (StringUtils.hasText(countryCode)) {
            return countryCode;
        }

        JsonNode address = match.path("address");
        return normalizeCountryCode(readText(address.path("country_code")));
    }

    private String resolveCountryName(String countryName, JsonNode match) {
        if (StringUtils.hasText(countryName)) {
            return countryName;
        }

        JsonNode address = match.path("address");
        String addressCountry = readText(address.path("country"));
        if (StringUtils.hasText(addressCountry)) {
            return addressCountry;
        }

        return readText(match.path("name"));
    }

    private String normalizeCountryCode(String countryCode) {
        String normalized = safeText(countryCode);
        return StringUtils.hasText(normalized) ? normalized.toUpperCase(Locale.ROOT) : null;
    }

    private String slugify(String value) {
        String normalized = safeText(value).toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder();
        boolean lastDash = false;
        for (int index = 0; index < normalized.length(); index += 1) {
            char current = normalized.charAt(index);
            boolean isAsciiLetterOrDigit = (current >= 'a' && current <= 'z') || (current >= '0' && current <= '9');
            if (isAsciiLetterOrDigit) {
                builder.append(current);
                lastDash = false;
            } else if (!lastDash && builder.length() > 0) {
                builder.append('-');
                lastDash = true;
            }
        }
        int length = builder.length();
        if (length > 0 && builder.charAt(length - 1) == '-') {
            builder.deleteCharAt(length - 1);
        }
        return builder.toString();
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private String readText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        return safeText(node.asString(""));
    }
}
