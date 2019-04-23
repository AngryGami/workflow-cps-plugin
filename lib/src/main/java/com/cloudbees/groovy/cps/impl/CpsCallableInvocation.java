package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.Next;

import java.util.List;

import static java.util.Arrays.*;
import java.util.Collections;
import javax.annotation.CheckForNull;

/**
 * When an CPS-interpreted method is invoked, it immediately throws this error
 * to signal that the method execution needs to be interpreted.
 *
 * <p>
 * The instance captures everything necessary to invoke a function,
 * which is
 * {@linkplain #call the definition of the function},
 * {@linkplain #receiver object that the function is invoked on}, and
 * {@linkplain #arguments actual arguments to the function}.
 *
 * When we invoke CPS-transformed closure or function, this throwable gets thrown.
 *
 * @author Kohsuke Kawaguchi
 */
public class CpsCallableInvocation extends Error/*not really an error but we want something that doesn't change signature*/ {
    public final String methodName;
    public final CpsCallable call;
    public final Object receiver;
    public final List arguments;

    @Deprecated
    public CpsCallableInvocation(CpsCallable call, Object receiver, Object... arguments) {
        this("?", call, receiver, arguments);
    }

    /**
     * @param methodName see {@link #checkMismatch}
     */
    public CpsCallableInvocation(String methodName, CpsCallable call, Object receiver, Object... arguments) {
        this.methodName = methodName;
        this.call = call;
        this.receiver = receiver;
        this.arguments = arguments != null ? asList(arguments) : Collections.emptyList();
    }

    public Next invoke(Env caller, SourceLocation loc, Continuation k) {
        return call.invoke(caller, loc, receiver,arguments,k);
    }

    /**
     * To be called prior to {@link #invoke}.
     * @param expectedMethodNames possible values for {@link #methodName}
     * @see <a href="https://issues.jenkins-ci.org/browse/JENKINS-31314">JENKINS-31314</a>
     */
    void checkMismatch(Object expectedReceiver, List<String> expectedMethodNames) {
        if (!expectedMethodNames.contains(methodName)) {
            MismatchHandler handler = handlers.get();
            if (handler != null) {
                handler.handle(expectedReceiver, expectedMethodNames.get(0), receiver, methodName);
            }
        }
    }

    /** @see #registerMismatchHandler */
    @FunctionalInterface
    public interface MismatchHandler {
        void handle(Object expectedReceiver, String expectedMethodName, Object actualReceiver, String actualMethodName);
    }

    private static final ThreadLocal<MismatchHandler> handlers = new ThreadLocal<>();

    /** @see #checkMismatch */
    public static void registerMismatchHandler(@CheckForNull MismatchHandler handler) {
        handlers.set(handler);
    }
    
    /**
     * Creates a {@link Block} that performs this invocation and pass the result to the given {@link Continuation}.
     */
    public Block asBlock() {
        return new Block() {
            public Next eval(Env e, Continuation k) {
                return invoke(e, null, k);
            }
        };
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }

    @Override
    public String toString() {
        return "CpsCallableInvocation{methodName=" + methodName + ", call=" + call + ", receiver=" + receiver + ", arguments=" + arguments + '}';
    }

}
