package jrm.misc;

import java.util.function.Consumer;
import java.util.function.Function;

import lombok.experimental.UtilityClass;

/**
 * Utility class providing helper methods to safely execute functions or consumers that might throw exceptions, with fallback values
 * or alternative actions.
 * 
 * @author optyfr
 */
public @UtilityClass class ExceptionUtils {
    /**
     * Executes a test function on an input object and passes the result to a consumer. If an exception is thrown, nothing is passed
     * to the consumer.
     * 
     * @param <T> the type of the input to the test function
     * @param <R> the type of the result of the test function
     * @param result the consumer that receives the successfully computed result
     * @param test the function to execute
     * @param t the input argument for the function
     */
    public <T, R> void unthrow(Consumer<R> result, Function<T, R> test, T t) {
        unthrow(result, test, t, null);
    }

    /**
     * Executes a test function on an input object and passes the result to a consumer, falling back to a default value if an
     * exception occurs.
     * 
     * @param <T> the type of the input to the test function
     * @param <R> the type of the result of the test function
     * @param result the consumer that receives the result or the default value
     * @param test the function to execute
     * @param t the input argument for the function
     * @param def the default fallback value to use in case of an exception
     */
    public <T, R> void unthrow(Consumer<R> result, Function<T, R> test, T t, R def) {
        final var r = ExceptionUtils.test(test, t, def);
        if (r != null)
            result.accept(r);
    }

    /**
     * Executes a test function on an input object and passes the result to a consumer, falling back to a dynamic value computed
     * from a fallback function if an exception occurs.
     * 
     * @param <T> the type of the input to the test function
     * @param <R> the type of the result of the test function
     * @param result the consumer that receives the computed result
     * @param test the function to execute
     * @param t the input argument for the function
     * @param def the fallback function that produces a default value in case of an exception
     */
    public <T, R> void unthrowF(Consumer<R> result, Function<T, R> test, T t, Function<Function<T, R>, R> def) {
        final var r = ExceptionUtils.testF(test, t, def);
        if (r != null)
            result.accept(r);
    }

    /**
     * Evaluates a function with an input, catching any exceptions and returning a default value if one is thrown.
     * 
     * @param <T> the type of the input to the function
     * @param <R> the type of the result of the function
     * @param test the function to evaluate
     * @param t the input argument for the function
     * @param def the default value to return if an exception occurs
     * 
     * @return the successfully evaluated result, or {@code def} if an exception was caught
     */
    public <T, R> R test(Function<T, R> test, T t, R def) {
        try {
            return test.apply(t);
        } catch (Exception _) {
            return def;
        }
    }

    /**
     * Evaluates a function with an input, catching any exceptions and using a fallback function to generate the returned value.
     * 
     * @param <T> the type of the input to the function
     * @param <R> the type of the result of the function
     * @param testFn the function to evaluate
     * @param input the input argument for the function
     * @param fallbackFn the fallback function that takes the failed test function and returns a default value
     * 
     * @return the successfully evaluated result, or the value produced by {@code fallbackFn} if an exception was caught
     */
    public <T, R> R testF(Function<T, R> testFn, T input, Function<Function<T, R>, R> fallbackFn) {
        try {
            return testFn.apply(input);
        } catch (Exception _) {
            return fallbackFn.apply(testFn);
        }
    }
}
