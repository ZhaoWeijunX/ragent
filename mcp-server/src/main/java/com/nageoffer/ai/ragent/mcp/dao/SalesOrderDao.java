/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nageoffer.ai.ragent.mcp.dao;

import com.nageoffer.ai.ragent.mcp.model.SalesOrder;
import com.nageoffer.ai.ragent.mcp.support.PeriodDateRange;
import com.nageoffer.ai.ragent.mcp.support.SqlQueryLogger;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class SalesOrderDao {

    private static final int MAX_ROWS = 5000;

    private static final RowMapper<SalesOrder> ROW_MAPPER = (rs, rowNum) -> SalesOrder.builder()
            .region(rs.getString("region"))
            .salesPerson(rs.getString("sales_person"))
            .product(rs.getString("product"))
            .customer(rs.getString("customer"))
            .amount(rs.getBigDecimal("amount"))
            .orderDate(rs.getObject("order_date", LocalDate.class))
            .build();

    private final JdbcTemplate jdbcTemplate;

    public List<SalesOrder> query(String region, String period, String product, String salesPerson) {
        LocalDate[] range = PeriodDateRange.resolve(period);
        StringBuilder sql = new StringBuilder("""
                SELECT region, sales_person, product, customer, amount, order_date
                FROM t_biz_sales_order
                WHERE deleted = 0
                  AND order_date BETWEEN ? AND ?
                """);
        List<Object> params = new ArrayList<>();
        params.add(range[0]);
        params.add(range[1]);

        if (region != null && !region.isBlank()) {
            sql.append(" AND region = ?");
            params.add(region);
        }
        if (product != null && !product.isBlank()) {
            sql.append(" AND product = ?");
            params.add(product);
        }
        if (salesPerson != null && !salesPerson.isBlank()) {
            sql.append(" AND sales_person = ?");
            params.add(salesPerson);
        }

        sql.append(" ORDER BY order_date DESC, amount DESC LIMIT ?");
        params.add(MAX_ROWS);

        SqlQueryLogger.log("sales_query", sql.toString(), params);
        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, params.toArray());
    }
}
