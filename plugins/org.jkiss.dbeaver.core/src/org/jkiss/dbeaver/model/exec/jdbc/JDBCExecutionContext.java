/*
 * Copyright (c) 2012, Serge Rieder and others. All Rights Reserved.
 */

package org.jkiss.dbeaver.model.exec.jdbc;

import org.jkiss.dbeaver.model.exec.DBCException;
import org.jkiss.dbeaver.model.exec.DBCExecutionContext;
import org.jkiss.dbeaver.model.exec.DBCStatementType;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * JDBC connection
 */
public interface JDBCExecutionContext extends DBCExecutionContext, Connection {

    Connection getOriginal();

    @Override
    JDBCStatement prepareStatement(
        DBCStatementType type,
        String query,
        boolean scrollable,
        boolean updatable,
        boolean returnGeneratedKeys) throws DBCException;

    @Override
    JDBCDatabaseMetaData getMetaData()
        throws SQLException;

    @Override
    JDBCStatement createStatement()
        throws SQLException;

    @Override
    JDBCStatement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability)
        throws SQLException;

    @Override
    JDBCPreparedStatement prepareStatement(String sql)
        throws SQLException;

    @Override
    JDBCCallableStatement prepareCall(String sql)
        throws SQLException;

    @Override
    JDBCPreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency)
        throws SQLException;

    @Override
    JDBCCallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency)
        throws SQLException;

    @Override
    JDBCPreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability)
        throws SQLException;

    @Override
    JDBCCallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability)
        throws SQLException;

    @Override
    JDBCPreparedStatement prepareStatement(String sql, int autoGeneratedKeys)
        throws SQLException;

    @Override
    JDBCPreparedStatement prepareStatement(String sql, int[] columnIndexes)
        throws SQLException;

    @Override
    JDBCPreparedStatement prepareStatement(String sql, String[] columnNames)
        throws SQLException;

    @Override
    void close();

}
