package emaki.jiuwu.craft.corelib.db;

import java.util.concurrent.locks.ReentrantLock;

final class SingleWriterGate {

    private final ReentrantLock lock = new ReentrantLock();
    private final boolean required;

    private SingleWriterGate(boolean required) {
        this.required = required;
    }

    static SingleWriterGate forEngine(DbEngine engine) {
        return new SingleWriterGate(engine == DbEngine.SQLITE);
    }

    Scope enter() {
        if (required) {
            lock.lock();
        }
        return new Scope();
    }

    final class Scope implements AutoCloseable {

        @Override
        public void close() {
            if (required) {
                lock.unlock();
            }
        }
    }
}
