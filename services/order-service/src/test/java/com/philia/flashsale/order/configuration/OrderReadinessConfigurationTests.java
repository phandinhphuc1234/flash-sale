package com.philia.flashsale.order.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class OrderReadinessConfigurationTests {

    @Test
    void validatesPostgresWithABoundedQuery() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet result = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("select 1")).thenReturn(result);
        when(result.next()).thenReturn(true);
        when(result.getInt(1)).thenReturn(1);

        assertThat(OrderReadinessConfiguration.postgresAvailable(dataSource)).isTrue();
        verify(statement).setQueryTimeout(1);
    }

    @Test
    void reportsUnavailableWhenValidationQueryFails() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenThrow(new IllegalStateException("database down"));

        assertThat(OrderReadinessConfiguration.postgresAvailable(dataSource)).isFalse();
    }
}
