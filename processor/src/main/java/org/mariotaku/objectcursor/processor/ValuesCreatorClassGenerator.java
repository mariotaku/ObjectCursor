package org.mariotaku.objectcursor.processor;

import android.content.ContentValues;
import com.squareup.javapoet.*;
import org.mariotaku.library.objectcursor.annotation.CursorObject;
import org.mariotaku.library.objectcursor.internal.ParameterizedTypeImpl;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Created by mariotaku on 15/11/28.
 */
public class ValuesCreatorClassGenerator {
    public static final String VALUES_CREATOR_SUFFIX = "ValuesCreator";

    private final CursorObjectClassInfo objectClassInfo;
    private final ClassName creatorClassName;
    private final String creatorClassNameWithoutPackage;

    ValuesCreatorClassGenerator(CursorObjectClassInfo objectClassInfo, Elements elements) {
        this.objectClassInfo = objectClassInfo;
        final String packageName = String.valueOf(elements.getPackageOf(objectClassInfo.objectType).getQualifiedName());
        final String binaryName = String.valueOf(elements.getBinaryName(objectClassInfo.objectType));
        creatorClassNameWithoutPackage = binaryName.substring(packageName.length() + 1) + VALUES_CREATOR_SUFFIX;
        creatorClassName = ClassName.get(packageName, creatorClassNameWithoutPackage);
    }

    private static String getConverterFieldName(TypeName converterClass) {
        return converterClass.toString().replaceAll("[^\\w\\d]", "_").toUpperCase(Locale.US);
    }

    void writeContent(Appendable appendable, Elements elements, Types types) throws IOException {
        final TypeSpec.Builder builder = TypeSpec.classBuilder(creatorClassNameWithoutPackage);
        TypeElement superClass = (TypeElement) types.asElement(objectClassInfo.getSuperclass());

        ClassName parentCreatorClass = null;
        // Super class has ValuesCreator implementation
        if (superClass.getAnnotation(CursorObject.class) != null) {
            parentCreatorClass = CursorObjectClassInfo.getSuffixedClassName(elements, superClass, VALUES_CREATOR_SUFFIX);
        }

        builder.addModifiers(Modifier.PUBLIC);

        builder.addFields(getConverterFields());

        builder.addFields(getTypeFields());

        builder.addMethod(createWriteToMethod(parentCreatorClass));

        builder.addMethod(createCreateMethod());

        JavaFile.builder(objectClassInfo.getPackageName(), builder.build()).build().writeTo(appendable);
    }

    private List<FieldSpec> getConverterFields() {
        List<FieldSpec> fieldSpecs = new ArrayList<>();
        for (ClassName converterClass : objectClassInfo.customConverters()) {
            fieldSpecs.add(FieldSpec.builder(converterClass, getConverterFieldName(converterClass), Modifier.FINAL, Modifier.STATIC)
                    .initializer("new $T()", converterClass)
                    .build());
        }
        return fieldSpecs;
    }

    private List<FieldSpec> getTypeFields() {
        List<FieldSpec> fieldSpecs = new ArrayList<>();
        for (TypeName typeName : objectClassInfo.customTypes()) {
            // String field is not a custom type
            if (CursorObjectClassInfo.STRING.equals(typeName)) continue;
            final FieldSpec.Builder builder = FieldSpec.builder(ParameterizedType.class,
                    getConverterFieldName(typeName), Modifier.FINAL, Modifier.STATIC);
            if (typeName instanceof ParameterizedTypeName) {
                ParameterizedTypeName parameterizedTypeName = (ParameterizedTypeName) typeName;
                Object[] formatArgs = new Object[2 + parameterizedTypeName.typeArguments.size()];
                final StringBuilder placeHolders = new StringBuilder();
                formatArgs[0] = ParameterizedTypeImpl.class;
                formatArgs[1] = parameterizedTypeName.rawType;
                for (int i = 0, j = parameterizedTypeName.typeArguments.size(); i < j; i++) {
                    placeHolders.append(", $T.class");
                    formatArgs[i + 2] = parameterizedTypeName.typeArguments.get(i);
                }
                final String format = String.format(Locale.ROOT, "$T.get($T.class, null%s)", placeHolders);
                builder.initializer(format, formatArgs);
            } else {
                builder.initializer("$T.get($T.class, null)", ParameterizedTypeImpl.class, typeName);
            }
            fieldSpecs.add(builder.build());
        }
        return fieldSpecs;
    }

    private MethodSpec createWriteToMethod(ClassName parentCreatorClass) {
        final MethodSpec.Builder builder = MethodSpec.methodBuilder("writeTo");
        builder.addModifiers(Modifier.PUBLIC);
        builder.addModifiers(Modifier.STATIC);
        builder.addParameter(objectClassInfo.objectClassName, "instance");
        builder.addParameter(ContentValues.class, "values");

        for (Element element : objectClassInfo.beforeValueWrite) {
            builder.addStatement("instance.$L(values)", element.getSimpleName());
        }

        if (parentCreatorClass != null) {
            builder.addStatement("$T.writeTo(instance, values)", parentCreatorClass);
        }

        for (CursorObjectClassInfo.CursorFieldInfo fieldInfo : objectClassInfo.fieldInfoList) {
            if (!fieldInfo.annotation.excludeWrite()) {
                addSetValueStatement(builder, fieldInfo);
            }
        }

        for (Element element : objectClassInfo.afterValueWrite) {
            builder.addStatement("instance.$L(values)", element.getSimpleName());
        }

        return builder.build();
    }

    private MethodSpec createCreateMethod() {
        final MethodSpec.Builder builder = MethodSpec.methodBuilder("create");
        builder.addModifiers(Modifier.PUBLIC, Modifier.STATIC);
        builder.addParameter(objectClassInfo.objectClassName, "object");
        builder.returns(ContentValues.class);

        builder.addStatement("$T values = new $T()", ContentValues.class, ContentValues.class);
        builder.addStatement("writeTo(object, values)");
        builder.addStatement("return values");
        return builder.build();
    }

    private void addSetValueStatement(MethodSpec.Builder builder, CursorObjectClassInfo.CursorFieldInfo fieldInfo)
            throws UnsupportedFieldTypeException {
        boolean supported = true;
        TypeName fieldType = fieldInfo.type;
        try {
            fieldType = fieldType.unbox();
        } catch (UnsupportedOperationException e) {
            // Ignore
        }
        final String readAccessCode = fieldInfo.useGetter() ? fieldInfo.objectFieldGetter + "()" : fieldInfo.objectFieldName;
        if (fieldType == TypeName.BOOLEAN) {
            builder.addStatement("values.put($S, instance.$L)", fieldInfo.columnName, readAccessCode);
        } else if (fieldType == TypeName.BYTE) {
            builder.addStatement("values.put($S, instance.$L)", fieldInfo.columnName, readAccessCode);
        } else if (fieldType == TypeName.CHAR) {
            builder.addStatement("values.put($S, (int) instance.$L)", fieldInfo.columnName, readAccessCode);
        } else if (fieldType == TypeName.SHORT) {
            builder.addStatement("values.put($S, instance.$L)", fieldInfo.columnName, readAccessCode);
        } else if (fieldType == TypeName.INT) {
            builder.addStatement("values.put($S, instance.$L)", fieldInfo.columnName, readAccessCode);
        } else if (fieldType == TypeName.LONG) {
            builder.addStatement("values.put($S, instance.$L)", fieldInfo.columnName, readAccessCode);
        } else if (fieldType == TypeName.FLOAT) {
            builder.addStatement("values.put($S, instance.$L)", fieldInfo.columnName, readAccessCode);
        } else if (fieldType == TypeName.DOUBLE) {
            builder.addStatement("values.put($S, instance.$L)", fieldInfo.columnName, readAccessCode);
        } else if (fieldType.equals(CursorObjectClassInfo.STRING)) {
            builder.addStatement("values.put($S, instance.$L)", fieldInfo.columnName, readAccessCode);
        } else {
            final ClassName converterClass = objectClassInfo.getConverter(fieldInfo.objectFieldName);
            if (converterClass != null) {
                builder.addStatement("$L.writeField(values, instance.$L, $S, $L)", getConverterFieldName(converterClass),
                        readAccessCode, fieldInfo.columnName, getConverterFieldName(fieldType));
            } else if (fieldType instanceof ArrayTypeName) {
                if (((ArrayTypeName) fieldType).componentType == TypeName.BYTE) {
                    builder.addStatement("values.put($S, instance.$L)", fieldInfo.columnName, readAccessCode);
                } else {
                    supported = false;
                }
            } else {
                supported = false;
            }
        }
        if (!supported) {
            throw new UnsupportedFieldTypeException(String.format("Unsupported type %s in %s.%s", fieldInfo.type,
                    objectClassInfo.objectClassName, fieldInfo.objectFieldName));
        }
    }


    public void saveValuesCreatorFile(Filer filer, Elements elements, Types types) throws IOException {
        JavaFileObject fileObj = filer.createSourceFile(creatorClassName.toString());
        try (Writer writer = fileObj.openWriter()) {
            writeContent(writer, elements, types);
            writer.flush();
        }
    }
}
