package org.example.jakartaee.cross;

import jakarta.annotation.Priority;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Interceptor
@Priority(Interceptor.Priority.APPLICATION)
@Log
public class LoggingInterceptor {

    @AroundInvoke
    public Object logMethodEntry(InvocationContext ctx) throws Exception {
        String c = ctx.getMethod().getDeclaringClass().getName();
        String method = ctx.getMethod().getName();
        Logger logger = LoggerFactory.getLogger(c);
        logger.info("Entering method: {}", method);
        return ctx.proceed();
    }
}
