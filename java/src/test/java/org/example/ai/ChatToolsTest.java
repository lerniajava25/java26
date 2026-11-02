package org.example.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatToolsTest {

    @Test
    void testFindFilesByGlob() {
        String result = Chat.findFiles(".", "*.java");
        System.out.println("findFiles result:\n" + result);

        assertTrue(result.contains("Found "));
        assertTrue(result.contains("Chat.java"));
        assertFalse(result.contains(".class"), "Should not include target class files");
        assertFalse(result.contains(".git"), "Should not include .git files");
    }

    @Test
    void testFindFilesSpecific() {
        String result = Chat.findFiles(".", "pom.xml");
        assertTrue(result.contains("pom.xml"));
    }

    @Test
    void testSearchDirectoryLiteral() {
        String result = Chat.searchDirectory(".", "ask_question", "*.java", false);
        System.out.println("searchDirectory result:\n" + result);

        assertTrue(result.contains("Found "));
        assertTrue(result.contains("Chat.java:"));
        assertTrue(result.contains("ask_question"));
        assertFalse(result.contains(".git"), "Should not search inside .git");
    }

    @Test
    void testSearchDirectoryRegex() {
        String result = Chat.searchDirectory(".", "public static final String HOST.*", "*.java", true);
        assertTrue(result.contains("Chat.java:"));
    }
}
