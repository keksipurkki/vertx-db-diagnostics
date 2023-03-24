package net.keksipurkki.vertx;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Launcher;
import io.vertx.core.Promise;
import io.vertx.core.logging.SLF4JLogDelegate;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.SqlClient;
import lombok.extern.slf4j.Slf4j;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.utility.DockerImageName;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@Slf4j
public final class App extends AbstractVerticle {

    private static final Boolean DEBUG = Boolean.TRUE;
    private static final DockerImageName DATABASE_IMAGE = DockerImageName.parse("postgres:15-alpine");

    private static final PostgreSQLContainer<?> database;

    static {
        var db = new PostgreSQLContainer<>(DATABASE_IMAGE);

        if (DEBUG) {
            db = db.withLogConsumer(new Slf4jLogConsumer(log));
        }

        log.info("Starting {}", db);
        db.start();

        database = db;
    }

    public static void main(String... args) {
        var sysProps = String.format("-Dvertx.logger-delegate-factory-class-name=%s", SLF4JLogDelegate.class.getName());
        var app = App.class.getName();
        log.debug("Main verticle = {}. SysProps = {}", app, sysProps);
        Launcher.executeCommand("run", app, sysProps);
    }

    @Override
    public void start(Promise<Void> promise) {
        log.info("Starting {}", App.class.getName());

        var credentials = String.format("user=%s&password=%s", database.getUsername(), database.getPassword());
        var uri = String.format("postgresql://localhost:%d/%s?%s", database.getFirstMappedPort(), database.getDatabaseName(), credentials);
        System.out.println(database.getJdbcUrl());
        log.debug("Database = {}. Connection = {}", DATABASE_IMAGE, uri);

        var postgres = PgPool.pool(vertx, uri);
        var connectionTest = postgres.query(database.getTestQueryString()).execute();

        connectionTest.onComplete(ar -> {
            if (ar.succeeded()) {
                log.info("Database connection works. Proceeding...");
                queries(postgres, promise);
            } else {
                log.warn("Database connection failed", ar.cause());
                promise.fail(ar.cause());
            }
        });

    }

    private void queries(SqlClient client, Promise<Void> promise) {

        var repository = new Repository(client);
        var goodQuery = repository.executeWorkingQuery();
        var badQuery = repository.executeBuggyQuery();

        goodQuery.onComplete(ar -> {
            if (ar.failed()) {
                throw new IllegalStateException("Was not supposed to fail");
            } else {
                log.info("SQL query succeed as expected. Please verify the exception below");
                assertThat(ar.result(), is(instanceOf(Repository.SomeDao.class)));
            }
        });

        badQuery.onComplete(ar -> {
            if (ar.failed()) {
                log.info("SQL query failed as expected. Please verify the exception below");
                assertThat(ar.cause(), is(instanceOf(DataAccessException.class)));
                ar.cause().printStackTrace();
            } else {
                throw new IllegalStateException("Was not supposed to succeed");
            }
        });

        CompositeFuture.all(goodQuery, badQuery).onComplete(ar -> {
            promise.complete();
        });

    }

}
