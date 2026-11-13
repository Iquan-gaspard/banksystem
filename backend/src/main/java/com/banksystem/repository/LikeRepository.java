package com.banksystem.repository;

import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.SqlOutParameter;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;
import org.springframework.stereotype.Repository;

import com.banksystem.model.LikeAmount;
import com.banksystem.model.LikeInput;
import com.banksystem.model.LikeItem;

/**
 * 資料層：透過 Stored Procedure 存取喜好清單（like_list + product）。
 * 所有參數都以具名參數綁定（CallableStatement），不做任何 SQL 字串拼接，防止 SQL Injection。
 * 交易由業務層 @Transactional 控制；本類別使用的 JdbcTemplate 會自動加入目前的交易。
 */
@Repository
public class LikeRepository {

    private static final String RESULT = "result";

    private static final RowMapper<LikeItem> LIKE_MAPPER = (rs, rowNum) -> new LikeItem(
            rs.getLong("sn"),
            rs.getLong("product_no"),
            rs.getString("product_name"),
            rs.getBigDecimal("price"),
            rs.getBigDecimal("fee_rate"),
            rs.getInt("purchase_quantity"),
            rs.getString("account"),
            rs.getBigDecimal("total_fee"),
            rs.getBigDecimal("total_amount"),
            rs.getString("email"));

    private static final SqlParameter[] WRITE_PARAMS = {
            new SqlParameter("p_product_name", Types.VARCHAR),
            new SqlParameter("p_price", Types.DECIMAL),
            new SqlParameter("p_fee_rate", Types.DECIMAL),
            new SqlParameter("p_purchase_quantity", Types.INTEGER),
            new SqlParameter("p_account", Types.VARCHAR),
            new SqlParameter("p_total_fee", Types.DECIMAL),
            new SqlParameter("p_total_amount", Types.DECIMAL)
    };

    private final SimpleJdbcCall listByUserCall;
    private final SimpleJdbcCall getCall;
    private final SimpleJdbcCall createCall;
    private final SimpleJdbcCall updateCall;
    private final SimpleJdbcCall deleteCall;

    public LikeRepository(JdbcTemplate jdbcTemplate) {
        this.listByUserCall = new SimpleJdbcCall(jdbcTemplate)
                .withProcedureName("sp_like_list_by_user")
                .withoutProcedureColumnMetaDataAccess()
                .declareParameters(new SqlParameter("p_user_id", Types.VARCHAR))
                .returningResultSet(RESULT, LIKE_MAPPER);

        this.getCall = new SimpleJdbcCall(jdbcTemplate)
                .withProcedureName("sp_like_get")
                .withoutProcedureColumnMetaDataAccess()
                .declareParameters(
                        new SqlParameter("p_user_id", Types.VARCHAR),
                        new SqlParameter("p_sn", Types.BIGINT))
                .returningResultSet(RESULT, LIKE_MAPPER);

        // 參數宣告順序必須與 SP 定義一致
        this.createCall = new SimpleJdbcCall(jdbcTemplate)
                .withProcedureName("sp_like_create")
                .withoutProcedureColumnMetaDataAccess()
                .declareParameters(new SqlParameter("p_user_id", Types.VARCHAR))
                .declareParameters(WRITE_PARAMS)
                .declareParameters(new SqlOutParameter("p_sn", Types.BIGINT));

        this.updateCall = new SimpleJdbcCall(jdbcTemplate)
                .withProcedureName("sp_like_update")
                .withoutProcedureColumnMetaDataAccess()
                .declareParameters(
                        new SqlParameter("p_user_id", Types.VARCHAR),
                        new SqlParameter("p_sn", Types.BIGINT))
                .declareParameters(WRITE_PARAMS)
                .declareParameters(new SqlOutParameter("p_affected", Types.INTEGER));

        this.deleteCall = new SimpleJdbcCall(jdbcTemplate)
                .withProcedureName("sp_like_delete")
                .withoutProcedureColumnMetaDataAccess()
                .declareParameters(
                        new SqlParameter("p_user_id", Types.VARCHAR),
                        new SqlParameter("p_sn", Types.BIGINT),
                        new SqlOutParameter("p_affected", Types.INTEGER));
    }

    public List<LikeItem> findByUserId(String userId) {
        return resultOf(listByUserCall.execute(new MapSqlParameterSource("p_user_id", userId)));
    }

    public Optional<LikeItem> findByUserIdAndSn(String userId, long sn) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("p_user_id", userId)
                .addValue("p_sn", sn);
        return resultOf(getCall.execute(params)).stream().findFirst();
    }

    /** 新增 product + like_list，回傳新的 sn。 */
    public long create(String userId, LikeInput input, LikeAmount amount) {
        MapSqlParameterSource params = writeParams(input, amount).addValue("p_user_id", userId);
        Number sn = (Number) createCall.execute(params).get("p_sn");
        return sn.longValue();
    }

    /** 更新 product + like_list；找不到（或不屬於該使用者）回傳 false。 */
    public boolean update(String userId, long sn, LikeInput input, LikeAmount amount) {
        MapSqlParameterSource params = writeParams(input, amount)
                .addValue("p_user_id", userId)
                .addValue("p_sn", sn);
        return affected(updateCall.execute(params));
    }

    /** 刪除 like_list + product；找不到（或不屬於該使用者）回傳 false。 */
    public boolean delete(String userId, long sn) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("p_user_id", userId)
                .addValue("p_sn", sn);
        return affected(deleteCall.execute(params));
    }

    private static MapSqlParameterSource writeParams(LikeInput input, LikeAmount amount) {
        return new MapSqlParameterSource()
                .addValue("p_product_name", input.productName())
                .addValue("p_price", input.price())
                .addValue("p_fee_rate", input.feeRate())
                .addValue("p_purchase_quantity", input.purchaseQuantity())
                .addValue("p_account", input.account())
                .addValue("p_total_fee", amount.totalFee())
                .addValue("p_total_amount", amount.totalAmount());
    }

    private static boolean affected(Map<String, Object> out) {
        Number affected = (Number) out.get("p_affected");
        return affected != null && affected.intValue() > 0;
    }

    @SuppressWarnings("unchecked")
    private static List<LikeItem> resultOf(Map<String, Object> out) {
        return (List<LikeItem>) out.get(RESULT);
    }
}
