package org.example.jakartaee.cross;

import jakarta.annotation.Priority;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Interceptor
@Locked
@Priority(Interceptor.Priority.APPLICATION)
public class ReadWriteLockInterceptor {
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    Logger logger = LoggerFactory.getLogger(ReadWriteLockInterceptor.class);

    @AroundInvoke
    public Object lock(InvocationContext ctx) throws Exception {
        boolean write = ctx.getMethod().isAnnotationPresent(WriteLock.class);
        Lock lock = write ? rwLock.writeLock() : rwLock.readLock();
        logger.info("Running lock for {} lock", write ? "write" : "read");
        lock.lock();
        try {
            return ctx.proceed();
        } finally {
            lock.unlock();
        }
    }
}
