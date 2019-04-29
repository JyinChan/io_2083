package nio.small;

import nio.small.core.ConnectionConfig;

public interface ConnectionInitializer {

    void initialize(ConnectionConfig builder);
}
