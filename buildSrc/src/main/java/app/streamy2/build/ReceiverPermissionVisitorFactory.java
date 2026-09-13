package app.streamy2.build;

import com.android.build.api.instrumentation.AsmClassVisitorFactory;
import com.android.build.api.instrumentation.ClassContext;
import com.android.build.api.instrumentation.ClassData;
import com.android.build.api.instrumentation.InstrumentationParameters;
import org.objectweb.asm.ClassVisitor;

/** Apply the narrow permission-name change only to the AndroidX ContextCompat class. */
public abstract class ReceiverPermissionVisitorFactory
        implements AsmClassVisitorFactory<InstrumentationParameters.None> {
    @Override public boolean isInstrumentable(ClassData data) {
        return data.getClassName().equals("androidx.core.content.ContextCompat");
    }

    @Override public ClassVisitor createClassVisitor(ClassContext context, ClassVisitor next) {
        return new ReceiverPermissionClassVisitor(next);
    }
}
