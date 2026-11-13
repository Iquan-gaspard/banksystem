package com.banksystem.repository;

import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;
import org.springframework.stereotype.Repository;

import com.banksystem.model.User;

/** 資料層：透過 Stored Procedure 存取 users。 */
@Repository
public class UserRepository {

    private static final String RESULT = "result";

    private static final RowMapper<User> USER_MAPPER = (rs, rowNum) -> new User(
            rs.getString("user_id"),
            rs.getString("user_name"),
            rs.getString("email"),
            rs.getString("account"));

    private final SimpleJdbcCall listCall;
    private final SimpleJdbcCall getCall;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.listCall = new SimpleJdbcCall(jdbcTemplate)
                .withProcedureName("sp_user_list")
                .withoutProcedureColumnMetaDataAccess()
                .returningResultSet(RESULT, USER_MAPPER);

        this.getCall = new SimpleJdbcCall(jdbcTemplate)
                .withProcedureName("sp_user_get")
                .withoutProcedureColumnMetaDataAccess()
                .declareParameters(new SqlParameter("p_user_id", Types.VARCHAR))
                .returningResultSet(RESULT, USER_MAPPER);
    }

    public List<User> findAll() {
        return resultOf(listCall.execute());
    }

    public Optional<User> findById(String userId) {
        Map<String, Object> out = getCall.execute(new MapSqlParameterSource("p_user_id", userId));
        return resultOf(out).stream().findFirst();
    }

    @SuppressWarnings("unchecked")
    private static List<User> resultOf(Map<String, Object> out) {
        return (List<User>) out.get(RESULT);
    }
}
