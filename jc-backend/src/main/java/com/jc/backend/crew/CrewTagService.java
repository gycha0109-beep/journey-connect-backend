package com.jc.backend.crew;

import com.jc.backend.common.DomainException;
import com.jc.backend.database.DatabaseRole;
import com.jc.backend.database.DatabaseTransactional;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@DatabaseTransactional(role = DatabaseRole.APP, readOnly = true)
public class CrewTagService {

    private static final int MAX_TAGS = 5;
    private static final Pattern TAG_SLUG = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");

    private final CrewRepository crews;
    private final JdbcTemplate jdbc;

    public CrewTagService(CrewRepository crews, JdbcTemplate jdbc) {
        this.crews = crews;
        this.jdbc = jdbc;
    }

    public List<CrewTagDtos.TagView> list(Long crewId) {
        requireCrew(crewId);
        return jdbc.query(
                """
                select t.id, t.slug, t.name_ko, t.name_en
                from public.crew_tags ct
                join public.tags t on t.id = ct.tag_id
                where ct.crew_id = ? and t.is_active = true
                order by t.sort_order asc, t.slug asc
                """,
                (rs, rowNum) -> new CrewTagDtos.TagView(
                        rs.getLong("id"),
                        rs.getString("slug"),
                        rs.getString("name_ko"),
                        rs.getString("name_en")),
                crewId);
    }

    @DatabaseTransactional(role = DatabaseRole.APP)
    public List<CrewTagDtos.TagView> replace(
            Long ownerId,
            Long crewId,
            CrewTagDtos.ReplaceRequest request) {
        Crew crew = crews.findByIdForUpdate(crewId)
                .orElseThrow(() -> crewNotFound(crewId));
        if (!crew.getOwner().getId().equals(ownerId)) {
            throw new DomainException(
                    HttpStatus.FORBIDDEN,
                    "CREW_OWNER_REQUIRED",
                    "크루장만 태그를 변경할 수 있습니다.");
        }

        List<String> slugs = normalize(request == null ? null : request.tagSlugs());
        Map<String, TagRow> tags = resolveActiveTags(slugs);

        jdbc.update("delete from public.crew_tags where crew_id = ?", crewId);
        for (String slug : slugs) {
            jdbc.update(
                    "insert into public.crew_tags (crew_id, tag_id) values (?, ?)",
                    crewId,
                    tags.get(slug).id());
        }

        List<CrewTagDtos.TagView> result = new ArrayList<>(slugs.size());
        for (String slug : slugs) {
            TagRow tag = tags.get(slug);
            result.add(new CrewTagDtos.TagView(tag.id(), tag.slug(), tag.nameKo(), tag.nameEn()));
        }
        return List.copyOf(result);
    }

    private Map<String, TagRow> resolveActiveTags(List<String> slugs) {
        if (slugs.isEmpty()) {
            return Map.of();
        }

        String placeholders = String.join(",", Collections.nCopies(slugs.size(), "?"));
        List<TagRow> rows = jdbc.query(
                "select id, slug, name_ko, name_en from public.tags "
                        + "where is_active = true and slug in (" + placeholders + ")",
                (rs, rowNum) -> new TagRow(
                        rs.getLong("id"),
                        rs.getString("slug"),
                        rs.getString("name_ko"),
                        rs.getString("name_en")),
                slugs.toArray());

        Map<String, TagRow> bySlug = new LinkedHashMap<>();
        for (TagRow row : rows) {
            bySlug.put(row.slug(), row);
        }
        for (String slug : slugs) {
            if (!bySlug.containsKey(slug)) {
                throw new DomainException(
                        HttpStatus.BAD_REQUEST,
                        "CREW_TAG_NOT_FOUND",
                        "사용할 수 없는 크루 태그입니다: " + slug);
            }
        }
        return bySlug;
    }

    private List<String> normalize(List<String> requested) {
        if (requested == null) {
            throw new DomainException(
                    HttpStatus.BAD_REQUEST,
                    "CREW_TAGS_REQUIRED",
                    "크루 태그 목록이 필요합니다.");
        }
        if (requested.size() > MAX_TAGS) {
            throw new DomainException(
                    HttpStatus.BAD_REQUEST,
                    "CREW_TAG_LIMIT_EXCEEDED",
                    "크루 태그는 최대 5개까지 지정할 수 있습니다.");
        }

        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String raw : requested) {
            if (raw == null || raw.isBlank()) {
                throw new DomainException(
                        HttpStatus.BAD_REQUEST,
                        "CREW_TAG_INVALID",
                        "빈 크루 태그는 사용할 수 없습니다.");
            }
            String slug = raw.trim().toLowerCase(Locale.ROOT).replace('_', '-');
            if (!TAG_SLUG.matcher(slug).matches()) {
                throw new DomainException(
                        HttpStatus.BAD_REQUEST,
                        "CREW_TAG_INVALID",
                        "크루 태그 형식이 올바르지 않습니다: " + raw);
            }
            if (!normalized.add(slug)) {
                throw new DomainException(
                        HttpStatus.BAD_REQUEST,
                        "CREW_TAG_DUPLICATE",
                        "중복된 크루 태그는 지정할 수 없습니다: " + slug);
            }
        }
        return List.copyOf(normalized);
    }

    private Crew requireCrew(Long crewId) {
        return crews.findWithOwnerAndRegionById(crewId)
                .orElseThrow(() -> crewNotFound(crewId));
    }

    private DomainException crewNotFound(Long crewId) {
        return new DomainException(
                HttpStatus.NOT_FOUND,
                "CREW_NOT_FOUND",
                "크루를 찾을 수 없습니다: " + crewId);
    }

    private record TagRow(long id, String slug, String nameKo, String nameEn) {}
}
