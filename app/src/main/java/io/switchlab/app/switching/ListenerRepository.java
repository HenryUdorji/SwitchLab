package io.switchlab.app.switching;

import io.switchlab.iso.Dialect;
import io.switchlab.sw.ListenerSpec;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ListenerRepository {
  private final JdbcClient jdbc;

  public ListenerRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public List<ListenerSpec> enabled() {
    return jdbc.sql("SELECT dialect, port FROM listeners WHERE enabled ORDER BY port")
        .query((rs, i) -> new ListenerSpec(Dialect.fromCode(rs.getString(1)), rs.getInt(2)))
        .list();
  }
}
