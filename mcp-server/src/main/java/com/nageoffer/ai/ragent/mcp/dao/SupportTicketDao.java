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

import com.nageoffer.ai.ragent.mcp.model.SupportTicket;
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
public class SupportTicketDao {

    private static final int MAX_ROWS = 2000;
    private static final int DEFAULT_LOOKBACK_DAYS = 30;

    private static final RowMapper<SupportTicket> ROW_MAPPER = (rs, rowNum) -> SupportTicket.builder()
            .ticketNo(rs.getString("ticket_no"))
            .region(rs.getString("region"))
            .customer(rs.getString("customer"))
            .product(rs.getString("product"))
            .status(rs.getString("status"))
            .priority(rs.getString("priority"))
            .category(rs.getString("category"))
            .engineer(rs.getString("engineer"))
            .title(rs.getString("title"))
            .createdDate(rs.getObject("created_date", LocalDate.class))
            .build();

    private final JdbcTemplate jdbcTemplate;

    public List<SupportTicket> query(String region, String status, String priority,
                                     String product, String customerName) {
        StringBuilder sql = new StringBuilder("""
                SELECT ticket_no, region, customer, product, status, priority,
                       category, engineer, title, created_date
                FROM t_biz_support_ticket
                WHERE deleted = 0
                  AND created_date >= ?
                """);
        List<Object> params = new ArrayList<>();
        params.add(LocalDate.now().minusDays(DEFAULT_LOOKBACK_DAYS));

        if (region != null && !region.isBlank()) {
            sql.append(" AND region = ?");
            params.add(region);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            params.add(status);
        }
        if (priority != null && !priority.isBlank()) {
            sql.append(" AND priority = ?");
            params.add(priority);
        }
        if (product != null && !product.isBlank()) {
            sql.append(" AND product = ?");
            params.add(product);
        }
        if (customerName != null && !customerName.isBlank()) {
            sql.append(" AND customer LIKE ?");
            params.add("%" + customerName + "%");
        }

        sql.append(" ORDER BY created_date DESC LIMIT ?");
        params.add(MAX_ROWS);

        SqlQueryLogger.log("ticket_query", sql.toString(), params);
        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, params.toArray());
    }
}
