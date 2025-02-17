package com.redhat.devtools.lsp4ij.features.completion.snippet;

import com.redhat.devtools.lsp4ij.features.completion.snippet.handler.LspSnippetNode;
import org.junit.Test;

import static com.redhat.devtools.lsp4ij.features.completion.snippet.LspSnippetAssert.*;

public class EscapeTest {
    @Test
    public void escapedFreestandingDollar() {
        LspSnippetNode[] actual = LspSnippetAssert.parse("te\\$xt");
        assertEquals(actual, LspSnippetAssert.text("te"), LspSnippetAssert.text("$"), LspSnippetAssert.text("xt"));

    }

    @Test
    public void escapedTabstopDollar() {
        LspSnippetNode[] actual = LspSnippetAssert.parse("a = \\$0");
        assertEquals(actual, LspSnippetAssert.text("a = "), LspSnippetAssert.text("$"), LspSnippetAssert.text("0"));
    }

    @Test
    public void escapedClosingCurlyBracket() {
        LspSnippetNode[] actual = LspSnippetAssert.parse("func foo() {\\}");
        assertEquals(actual, LspSnippetAssert.text("func foo() {"), LspSnippetAssert.text("}"));
    }

    @Test
    public void escapedClosingCurlyBracketWithTabstop() {
        // Actual snippet from gopls
        LspSnippetNode[] actual = LspSnippetAssert.parse("func() (success bool) {$0\\}");
        assertEquals(actual, LspSnippetAssert.text("func() (success bool) {"), LspSnippetAssert.tabstop(0), LspSnippetAssert.text("}"));
    }

    @Test(expected = ParseException.class)
    public void escapedMismatchEscapedClosingCurlyBracket() {
        LspSnippetNode[] actual = LspSnippetAssert.parse("func foo() {${0\\}");
    }

    @Test
    public void escapedSlash() {
        LspSnippetNode[] actual = LspSnippetAssert.parse("te\\\\xt");
        assertEquals(actual, LspSnippetAssert.text("te"), LspSnippetAssert.text("\\"), LspSnippetAssert.text("xt"));
    }

    @Test
    public void escapedCommaInChoice() {
        LspSnippetNode[] actual = LspSnippetAssert.parse("${1|one,two,thr\\,ee|}");
        assertEquals(actual, LspSnippetAssert.choice(1, "one", "two", "thr,ee"));
        actual = LspSnippetAssert.parse("${1|one,two,\\,three|}");
        assertEquals(actual, LspSnippetAssert.choice(1, "one", "two", ",three"));
        actual = LspSnippetAssert.parse("${1|one,two,three\\,|}");
        assertEquals(actual, LspSnippetAssert.choice(1, "one", "two", "three,"));
        actual = LspSnippetAssert.parse("${1|one,two,three\\,,four|}");
        assertEquals(actual, LspSnippetAssert.choice(1, "one", "two", "three,", "four"));
    }

    @Test
    public void escapedBarInChoice() {
        LspSnippetNode[] actual = LspSnippetAssert.parse("${1|one,two,thr\\|ee|}");
        assertEquals(actual, LspSnippetAssert.choice(1, "one", "two", "thr|ee"));
        actual = LspSnippetAssert.parse("${1|one,two,\\|three|}");
        assertEquals(actual, LspSnippetAssert.choice(1, "one", "two", "|three"));
        actual = LspSnippetAssert.parse("${1|one,two,three\\||}");
        assertEquals(actual, LspSnippetAssert.choice(1, "one", "two", "three|"));
        actual = LspSnippetAssert.parse("${1|one,two,three\\|,four|}");
        assertEquals(actual, LspSnippetAssert.choice(1, "one", "two", "three|", "four"));
    }
}
