package com.mycraft.worldchestloot;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ConditionEvaluatorTest {
    @Test
    public void comparesNumbersAndText() {
        assertPassed("%level% >= 100", token -> "100");
        assertPassed("%health% < 20.5", token -> "20");
        assertPassed("%name% == server", token -> "Server");
        assertPassed("%name% != other", token -> "server");
    }

    @Test
    public void appliesAndBeforeOrAndSupportsParentheses() {
        assertPassed("true || false && false", token -> null);
        assertFailed("(true || false) && false", token -> null);
        assertPassed("(%level% >= 100 || %health% >= 20) && true",
                token -> token.equals("%level%") ? "1" : "20");
    }

    @Test
    public void shortCircuitsInlineBooleanOperators() {
        assertPassed("true || %missing% == value", token -> null);
        assertFailed("false && %missing% == value", token -> null);
    }

    @Test
    public void stopsAtFirstFailedListCondition() {
        AtomicInteger resolutions = new AtomicInteger();
        ConditionEvaluator.Result result = ConditionEvaluator.evaluate(Arrays.asList(
                "%level% >= 100 --message 等级未满100级",
                "%health% >= 20 --message 生命未满20点"), token -> {
            resolutions.incrementAndGet();
            return token.equals("%level%") ? "99" : "20";
        });
        assertFalse(result.isPassed());
        assertEquals("等级未满100级", result.getFailureMessage());
        assertEquals(1, resolutions.get());
    }

    @Test
    public void returnsNoCustomMessageWhenOneIsNotConfigured() {
        ConditionEvaluator.Result result = ConditionEvaluator.evaluate(
                Collections.singletonList("false"), token -> null);
        assertFalse(result.isPassed());
        assertNull(result.getFailureMessage());
    }

    @Test
    public void treatsMissingOrUnresolvedPlaceholdersAsFailure() {
        assertFailed("%level% >= 100", null);
        assertFailed("%level% >= 100", token -> token);
        assertFailed("%level% >= 100", token -> null);
    }

    @Test
    public void treatsMalformedExpressionsAsFailure() {
        assertFailed("%level% = 100", token -> "100");
        assertFailed("(%level% >= 100", token -> "100");
        assertFailed("hello > world", token -> null);
        assertFailed("", token -> null);
    }

    @Test
    public void allowsSpacesAndMessageMarkersInsideQuotedText() {
        ConditionEvaluator.Result result = ConditionEvaluator.evaluate(Collections.singletonList(
                "\"hello --message world\" == \"hello --message world\" --message should not fail"),
                token -> null);
        assertTrue(result.isPassed());
    }

    private void assertPassed(String expression, ConditionEvaluator.PlaceholderResolver resolver) {
        assertTrue(ConditionEvaluator.evaluate(Collections.singletonList(expression), resolver).isPassed());
    }

    private void assertFailed(String expression, ConditionEvaluator.PlaceholderResolver resolver) {
        assertFalse(ConditionEvaluator.evaluate(Collections.singletonList(expression), resolver).isPassed());
    }
}
