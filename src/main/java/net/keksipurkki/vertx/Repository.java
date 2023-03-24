package net.keksipurkki.vertx;

import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.SqlClient;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@Slf4j
public class Repository {

    private final SqlClient client;

    Repository(SqlClient client) {
        this.client = client;
    }

    public Future<List<SomeDao>> executeBuggyQuery() {

        var sql = "select * from indicate_the_error_please where table_name = 'ROOT_CAUSE';";

        var query = this.client.preparedQuery(sql)
                               .mapping(SomeDao::create)
                               .execute();

        return query
            .recover(capture(new DataAccessException("Data access exception")))
            .map(this::toStream)
            .map(this::toList);

    }

    public Future<SomeDao> executeWorkingQuery() {

        var sql = "select 1;";

        var query = this.client.preparedQuery(sql)
                               .mapping(SomeDao::create)
                               .execute();

        return query
            .recover(capture(new DataAccessException("Data access exception")))
            .map(this::toStream)
            .map(findFirstOrThrow(new DataAccessException("No rows found")));

    }

    private <T> Function<Throwable, Future<RowSet<T>>> capture(DataAccessException context) {
        return throwable -> Future.failedFuture(context.withCause(throwable));
    }

    private <T> Stream<T> toStream(RowSet<T> input) {
        return StreamSupport.stream(input.spliterator(), false);
    }

    private <T> Function<Stream<T>, T> findFirstOrThrow(DataAccessException exception) {
        return s -> s.findFirst().orElseThrow(() -> exception);
    }

    private <T> List<T> toList(Stream<T> stream) {
        return stream.toList();
    }

    static class SomeDao {
        private SomeDao() {
        }

        public static SomeDao create(Row row) {
            log.trace("Database row is {}", row.toJson());
            return new SomeDao();
        }
    }

}
