package com.banksystem.repository;

import java.sql.Types;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;
import org.springframework.stereotype.Repository;

import com.banksystem.model.AuditLog;

/** 資料層：透過 Stored Procedure 查詢操作紀錄（寫入由異動用的 SP 負責）。 */
@Repository
public class AuditLogRepository {

    private static final String RESULT = "result";

    private static final RowMapper<AuditLog> AUDIT_MAPPER = (rs, rowNum) -> new AuditLog(
            rs.getLong("id"),
            rs.getString("actor_user_id"),
            rs.getString("action"),
            rs.getLong("like_sn"),
            rs.getString("before_data"),
            rs.getString("after_data"),
            rs.getString("client_ip"),
            rs.getTimestamp("created_at").toLocalDateTime());

    private final SimpleJdbcCall listByUserCall;

    public AuditLogRepository(JdbcTemplate jdbcTemplate) {
        this.listByUserCall = new SimpleJdbcCall(jdbcTemplate)
                .withProcedureName("sp_audit_list_by_user")
                .withoutProcedureColumnMetaDataAccess()
                .declareParameters(
                        new SqlParameter("p_user_id", Types.VARCHAR),
                        new SqlParameter("p_limit", Types.INTEGER))
                .returningResultSet(RESULT, AUDIT_MAPPER);
    }

    @SuppressWarnings("unchecked")
    public List<AuditLog> findByOwner(String userId, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("p_user_id", userId)
                .addValue("p_limit", limit);
        return (List<AuditLog>) listByUserCall.execute(params).get(RESULT);
    }
}
