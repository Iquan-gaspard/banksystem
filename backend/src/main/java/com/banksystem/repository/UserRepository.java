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
import com.banksystem.model.UserCredentials;

/** 資料層：透過 Stored Procedure 存取 users。 */
@Repository
public class UserRepository {

    private static final String RESULT = "result";

    private static final RowMapper<User> USER_MAPPER = (rs, rowNum) -> new User(
            rs.getString("user_id"),
            rs.getString("user_name"),
            rs.getString("email"),
            rs.getString("account"));

    private static final RowMapper<UserCredentials> CREDENTIALS_MAPPER = (rs, rowNum) -> new UserCredentials(
            rs.getString("user_id"),
            rs.getString("password_hash"));

    private final SimpleJdbcCall getCall;
    private final SimpleJdbcCall credentialsCall;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.credentialsCall = new SimpleJdbcCall(jdbcTemplate)
                .withProcedureName("sp_user_get_credentials")
                .withoutProcedureColumnMetaDataAccess()
                .declareParameters(new SqlParameter("p_user_id", Types.VARCHAR))
                .returningResultSet(RESULT, CREDENTIALS_MAPPER);

        this.getCall = new SimpleJdbcCall(jdbcTemplate)
                .withProcedureName("sp_user_get")
                .withoutProcedureColumnMetaDataAccess()
                .declareParameters(new SqlParameter("p_user_id", Types.VARCHAR))
                .returningResultSet(RESULT, USER_MAPPER);
    }

    public Optional<User> findById(String userId) {
        Map<String, Object> out = getCall.execute(new MapSqlParameterSource("p_user_id", userId));
        return UserRepository.<User>resultOf(out).stream().findFirst();
    }

    public Optional<UserCredentials> findCredentials(String userId) {
        Map<String, Object> out = credentialsCall.execute(new MapSqlParameterSource("p_user_id", userId));
        return UserRepository.<UserCredentials>resultOf(out).stream().findFirst();
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> resultOf(Map<String, Object> out) {
        return (List<T>) out.get(RESULT);
    }
}
