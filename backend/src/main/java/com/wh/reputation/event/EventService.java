package com.wh.reputation.event;

import com.wh.reputation.common.BadRequestException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

@Service
public class EventService {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final JdbcTemplate jdbcTemplate;

    public EventService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long create(CreateEventRequest req) {
        if (req == null) {
            throw new BadRequestException("body is required");
        }
        if (req.productId() == null) {
            throw new BadRequestException("productId is required");
        }
        if (req.name() == null || req.name().isBlank()) {
            throw new BadRequestException("name is required");
        }
        String type = normalizeType(req.type());
        if (type == null) {
            throw new BadRequestException("type must be activity or version");
        }
        LocalDate start = parseDate(req.startDate(), "startDate");
        LocalDate end = parseDate(req.endDate(), "endDate");
        if (end.isBefore(start)) {
            throw new BadRequestException("endDate must be >= startDate");
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                            insert into `event` (product_id, name, type, start_date, end_date, created_at)
                            values (?, ?, ?, ?, ?, ?)
                            """,
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, req.productId());
            ps.setString(2, req.name().trim());
            ps.setString(3, type);
            ps.setDate(4, Date.valueOf(start));
            ps.setDate(5, Date.valueOf(end));
            ps.setTimestamp(6, now);
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("failed to create event");
        }
        return key.longValue();
    }

    public List<EventItemDto> list(Long productId) {
        if (productId == null) {
            throw new BadRequestException("productId is required");
        }
        return jdbcTemplate.query("""
                        select e.id as id,
                               e.name as name,
                               e.type as type,
                               e.start_date as startDate,
                               e.end_date as endDate
                        from `event` e
                        where e.product_id = ?
                        order by e.start_date desc, e.id desc
                        """,
                (rs, rowNum) -> new EventItemDto(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("type"),
                        DATE_FORMAT.format(rs.getDate("startDate").toLocalDate()),
                        DATE_FORMAT.format(rs.getDate("endDate").toLocalDate())
                ),
                productId
        );
    }

    private static LocalDate parseDate(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(field + " is required");
        }
        try {
            return LocalDate.parse(value.trim(), DATE_FORMAT);
        } catch (DateTimeParseException e) {
            throw new BadRequestException("invalid date: " + value);
        }
    }

    private static String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        String v = type.trim().toLowerCase(Locale.ROOT);
        if (v.equals("activity") || v.equals("version")) {
            return v;
        }
        return null;
    }
}
