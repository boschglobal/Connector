/*
 *  Copyright (c) 2020 - 2022 Microsoft Corporation
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Microsoft Corporation - initial API and implementation
 *       ZF Friedrichshafen AG - added private property support
 *
 */

package org.eclipse.edc.connector.store.sql.assetindex.schema;


import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.sql.translation.SqlConditionExpression;
import org.eclipse.edc.sql.translation.SqlQueryStatement;

import java.util.List;

import static java.lang.String.format;

public class BaseSqlDialectStatements implements AssetStatements {

    @Override
    public String getInsertAssetTemplate() {
        return executeStatement()
                .column(getAssetIdColumn())
                .column(getCreatedAtColumn())
                .insertInto(getAssetTable());
    }

    @Override
    public String getInsertDataAddressTemplate() {
        return executeStatement()
                .column(getDataAddressAssetIdFkColumn())
                .jsonColumn(getDataAddressPropertiesColumn())
                .insertInto(getDataAddressTable());
    }

    @Override
    public String getInsertPropertyTemplate() {
        return executeStatement()
                .column(getPropertyAssetIdFkColumn())
                .column(getAssetPropertyNameColumn())
                .column(getAssetPropertyValueColumn())
                .column(getAssetPropertyTypeColumn())
                .column(getAssetPropertyIsPrivateColumn())
                .insertInto(getAssetPropertyTable());
    }

    @Override
    public String getCountAssetByIdClause() {
        return format("SELECT COUNT(*) AS %s FROM %s WHERE %s = ?",
                getCountVariableName(),
                getAssetTable(),
                getAssetIdColumn());
    }

    @Override
    public String getFindPropertyByIdTemplate() {
        return format("SELECT * FROM %s WHERE %s = ?",
                getAssetPropertyTable(),
                getPropertyAssetIdFkColumn());
    }

    @Override
    public String getFindDataAddressByIdTemplate() {
        return format("SELECT * FROM %s WHERE %s = ?",
                getDataAddressTable(),
                getDataAddressAssetIdFkColumn());
    }

    @Override
    public String getSelectAssetTemplate() {
        return format("SELECT * FROM %s AS a", getAssetTable());
    }

    @Override
    public String getDeleteAssetByIdTemplate() {
        return executeStatement()
                .delete(getAssetTable(), getAssetIdColumn());
    }

    @Override
    public String getUpdateDataAddressTemplate() {
        return executeStatement()
                .jsonColumn(getDataAddressPropertiesColumn())
                .update(getDataAddressTable(), getDataAddressAssetIdFkColumn());
    }

    @Override
    public String getDeletePropertyByIdTemplate() {
        return executeStatement()
                .delete(getAssetPropertyTable(), getPropertyAssetIdFkColumn());
    }

    @Override
    public String getCountVariableName() {
        return "COUNT";
    }

    @Override
    public String getQuerySubSelectTemplate() {
        return format("EXISTS (SELECT 1 FROM %s WHERE %s = a.%s AND %s = ? AND %s",
                getAssetPropertyTable(),
                getPropertyAssetIdFkColumn(),
                getAssetIdColumn(),
                getAssetPropertyNameColumn(),
                getAssetPropertyValueColumn());
    }

    @Override
    public SqlQueryStatement createQuery(QuerySpec querySpec) {
        var criteria = querySpec.getFilterExpression();
        var conditions = criteria.stream().map(SqlConditionExpression::new).toList();
        var validation = conditions.stream()
                .map(SqlConditionExpression::isValidExpression)
                .reduce(Result::merge)
                .orElse(Result.success());

        if (validation.failed()) {
            throw new IllegalArgumentException(validation.getFailureDetail());
        }

        var statement = new SqlQueryStatement(getSelectAssetTemplate(), querySpec.getLimit(), querySpec.getOffset());

        conditions.forEach(condition -> statement
                .addWhereClause(this.toSubSelect(condition), condition.toStatementParameter().toArray()));

        return statement;
    }

    @Override
    public SqlQueryStatement createQuery(List<Criterion> criteria) {
        return createQuery(QuerySpec.Builder.newInstance()
                .filter(criteria)
                .offset(0)
                .limit(Integer.MAX_VALUE)
                .build());
    }

    @Override
    public String getSelectAssetByIdTemplate() {
        return format("SELECT * FROM %s WHERE %s=?", getAssetTable(), getAssetIdColumn());
    }

    /**
     * Concatenates all SELECT statements on all properties into one big statement, or returns "" if list is empty.
     */
    private String concatSubSelects(List<String> subSelects) {
        if (subSelects.isEmpty()) {
            return "";
        }
        return format(" WHERE %s", String.join(" AND ", subSelects));
    }

    /**
     * Converts a {@linkplain Criterion} into a dynamically assembled SELECT statement.
     */
    private String toSubSelect(SqlConditionExpression c) {
        return format("%s %s %s)", getQuerySubSelectTemplate(),
                c.getCriterion().getOperator(),
                c.toValuePlaceholder());
    }

}
