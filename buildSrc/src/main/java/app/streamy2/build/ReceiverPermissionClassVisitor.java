package app.streamy2.build;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Only rename AndroidX's receiver permission; never remove its permission check. */
public final class ReceiverPermissionClassVisitor extends ClassVisitor {
    private static final String OLD = ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION";
    private static final String CURRENT = OLD + "_2026";

    public ReceiverPermissionClassVisitor(ClassVisitor next) { super(Opcodes.ASM9, next); }

    private int runtimeReplacements;

    @Override public FieldVisitor visitField(int access, String name, String descriptor,
                                             String signature, Object value) {
        return super.visitField(access, name, descriptor, signature,
                OLD.equals(value) ? CURRENT : value);
    }

    @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                                               String signature, String[] exceptions) {
        MethodVisitor method = super.visitMethod(access, name, descriptor, signature, exceptions);
        return new MethodVisitor(Opcodes.ASM9, method) {
            @Override public void visitLdcInsn(Object value) {
                if (OLD.equals(value)) {
                    value = CURRENT;
                    runtimeReplacements++;
                }
                super.visitLdcInsn(value);
            }
        };
    }

    @Override public void visitEnd() {
        if (runtimeReplacements != 1) {
            throw new IllegalStateException("AndroidX receiver permission code changed; "
                    + "review the signing-key migration before releasing this build.");
        }
        super.visitEnd();
    }
}
