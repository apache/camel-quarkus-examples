package org.apache.camel.quarkus.component.jdbc;

import java.sql.Connection;
import java.sql.Statement;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import io.agroal.api.AgroalDataSource;
import io.quarkus.agroal.DataSource;
import io.quarkus.runtime.StartupEvent;
import org.apache.camel.CamelContext;

@ApplicationScoped
public class CamelResource {

    @Inject
    @DataSource("camel-ds")
    AgroalDataSource dataSource;

    void startup(@Observes StartupEvent event, CamelContext context) throws Exception {
        context.getRouteController().startAllRoutes();
    }

    @PostConstruct
    void postConstruct() throws Exception {
        try (Connection con = dataSource.getConnection()) {
            try (Statement statement = con.createStatement()) {
                con.setAutoCommit(true);
                try {
                    statement.execute("drop table camel");
                } catch (Exception ignored) {
                }
                statement.execute("create table camel (id serial primary key, timestamp varchar(255))");
            }
        }
    }
}
