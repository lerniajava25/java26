package org.example.jakartaee;

import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.example.jakartaee.cross.ReadLock;
import org.example.jakartaee.cross.WriteLock;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

class LockedArchitectureTest {

    @Test
    void methods_should_not_have_both_read_and_write_lock() {
        ArchCondition<JavaMethod> notHaveBothLocks = new ArchCondition<JavaMethod>("not have both @ReadLock and @WriteLock") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                boolean hasRead = method.isAnnotatedWith(ReadLock.class);
                boolean hasWrite = method.isAnnotatedWith(WriteLock.class);

                if (hasRead && hasWrite) {
                    String message = String.format("Method %s has both @ReadLock and @WriteLock", method.getFullName());
                    events.add(SimpleConditionEvent.violated(method, message));
                }
            }
        };

        methods()
                .should(notHaveBothLocks)
                .check(new ClassFileImporter().importPackages("org.example.jakartaee"));
    }
}
