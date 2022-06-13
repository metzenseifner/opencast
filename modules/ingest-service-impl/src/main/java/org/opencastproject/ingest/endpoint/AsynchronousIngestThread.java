package org.opencastproject.ingest.endpoint;

import org.opencastproject.security.api.Organization;
import org.opencastproject.security.api.User;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class AsynchronousIngestThread extends Thread {

    private static AtomicInteger asynchronousChildThreadCount = new AtomicInteger(0);
    private final ThreadLocal<Organization> organization;
    private final ThreadLocal<User> user;

    private final Runnable executable;
    public AsynchronousIngestThread(Runnable executable, Organization orgUnit, User user) {
        super(String.format("AsynchronousIngestThread-%s", asynchronousChildThreadCount));
        this.executable = executable;
        this.organization = ThreadLocal.withInitial(() -> orgUnit);
        this.user = ThreadLocal.withInitial(() -> user);
        AsynchronousIngestThread.asynchronousChildThreadCount.incrementAndGet();
    }

    @Override
    public void run() {
        this.executable.run();
    }

}
